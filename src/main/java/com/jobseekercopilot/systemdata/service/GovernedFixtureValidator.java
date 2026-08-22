package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernedFixtureValidator {
    private static final Set<String> CHECKSUM_FILES = Set.of(
            "generation-report.json",
            "jobs.json",
            "llm-fixtures.json",
            "locations.json",
            "manifest.json",
            "provenance.json");

    private final ObjectMapper objectMapper;
    private final ChecksumUtil checksumUtil;
    private final Clock clock;

    @Autowired
    public GovernedFixtureValidator(ObjectMapper objectMapper, ChecksumUtil checksumUtil) {
        this(objectMapper, checksumUtil, Clock.systemUTC());
    }

    GovernedFixtureValidator(ObjectMapper objectMapper, ChecksumUtil checksumUtil, Clock clock) {
        this.objectMapper = objectMapper;
        this.checksumUtil = checksumUtil;
        this.clock = clock;
    }

    public void requireApproved(Path directory) {
        try {
            JsonNode manifest = read(directory.resolve("manifest.json"));
            JsonNode provenance = read(directory.resolve("provenance.json"));
            require("APPROVED_SYNTHETIC".equals(manifest.path("status").asText()));
            require(manifest.path("validation").path("valid").asBoolean(false));
            require("FULLY_SYNTHETIC".equals(provenance.path("classification").asText()));
            require("DETERMINISTIC_LOCAL_GENERATOR".equals(
                    provenance.path("creation").path("method").asText()));
            require(!provenance.path("creation").path("liveProvidersCalled").asBoolean(true));
            require("APPROVED_FOR_NON_PRODUCTION_TESTING".equals(
                    provenance.path("approval").path("status").asText()));
            require(!provenance.path("dataPolicy").path("containsRealPersonalData").asBoolean(true));
            require(!provenance.path("dataPolicy").path("containsCapturedProviderData").asBoolean(true));
            require(!provenance.path("dataPolicy").path("containsProviderCredentials").asBoolean(true));
            require("LicenseRef-JobSeekerCopilot-Proprietary".equals(
                    provenance.path("licence").path("identifier").asText()));
            require("PROHIBITED".equals(provenance.path("licence").path("redistribution").asText()));
            require(provenance.path("licence").path("externalDatasets").isArray()
                    && provenance.path("licence").path("externalDatasets").isEmpty());
            require(!provenance.path("approval").path("approvedBy").asText().isBlank());
            require(manifest.path("datasetId").asText().equals(provenance.path("datasetId").asText()));
            require(manifest.path("version").asText().equals(provenance.path("datasetVersion").asText()));
            require(!LocalDate.parse(provenance.path("lifecycle").path("expiresAt").asText())
                    .isBefore(LocalDate.now(clock)));
            verifyPayloadChecksums(directory, provenance.path("payloadChecksums"));
            verifyChecksumManifest(directory);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof DatasetGenerationException datasetGenerationException) {
                throw datasetGenerationException;
            }
            throw unavailable();
        }
    }

    private JsonNode read(Path file) throws IOException {
        require(Files.isRegularFile(file) && !Files.isSymbolicLink(file));
        return objectMapper.readTree(file.toFile());
    }

    private void verifyPayloadChecksums(Path directory, JsonNode checksums) throws IOException {
        for (String file : Set.of("jobs.json", "locations.json", "llm-fixtures.json")) {
            String expected = checksums.path(file).asText();
            String actual = checksumUtil.sha256(Files.readString(directory.resolve(file), StandardCharsets.UTF_8));
            require(expected.matches("[0-9a-f]{64}") && expected.equals(actual));
        }
    }

    private void verifyChecksumManifest(Path directory) throws IOException {
        Path checksumFile = directory.resolve("SHA256SUMS");
        require(Files.isRegularFile(checksumFile) && !Files.isSymbolicLink(checksumFile));
        Map<String, String> expected = Files.readAllLines(checksumFile, StandardCharsets.UTF_8).stream()
                .map(line -> line.split("  ", 2))
                .filter(parts -> parts.length == 2)
                .collect(java.util.stream.Collectors.toMap(parts -> parts[1], parts -> parts[0]));
        require(expected.keySet().equals(CHECKSUM_FILES));
        for (String file : CHECKSUM_FILES) {
            String actual = checksumUtil.sha256(Files.readString(directory.resolve(file), StandardCharsets.UTF_8));
            require(expected.get(file).matches("[0-9a-f]{64}") && expected.get(file).equals(actual));
        }
    }

    private void require(boolean condition) {
        if (!condition) {
            throw unavailable();
        }
    }

    private DatasetGenerationException unavailable() {
        return new DatasetGenerationException("Fixture dataset is unavailable or has failed governance validation");
    }
}
