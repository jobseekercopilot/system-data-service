package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GovernedFixtureValidatorTest {
    private static final Path COMMITTED_FIXTURE =
            Path.of("fixtures/datasets/uk-software-developer-demo/1.0.0");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void acceptsTheCurrentGovernedSyntheticFixture() {
        assertThatCode(() -> validator(at("2026-07-22T00:00:00Z")).requireApproved(COMMITTED_FIXTURE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiredFixtureEvenWhenItsChecksumsRemainValid() {
        assertRejected(() -> validator(at("2027-07-11T00:00:00Z")).requireApproved(COMMITTED_FIXTURE));
    }

    @Test
    void rejectsTamperedPayload() throws IOException {
        Path fixture = copyFixture("tampered");
        ObjectNode jobs = (ObjectNode) objectMapper.readTree(fixture.resolve("jobs.json").toFile());
        ((ObjectNode) jobs.withArray("jobs").get(0)).put("companyName", "Unexpected Company");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(fixture.resolve("jobs.json").toFile(), jobs);

        assertRejected(() -> validator(at("2026-07-22T00:00:00Z")).requireApproved(fixture));
    }

    @Test
    void rejectsCapturedClassificationAndMissingGovernance() throws IOException {
        Path captured = copyFixture("captured");
        ObjectNode provenance = (ObjectNode) objectMapper.readTree(captured.resolve("provenance.json").toFile());
        provenance.put("classification", "LIVE_CAPTURED_FIXTURE");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(captured.resolve("provenance.json").toFile(), provenance);
        assertRejected(() -> validator(at("2026-07-22T00:00:00Z")).requireApproved(captured));

        Path missing = copyFixture("missing");
        Files.delete(missing.resolve("provenance.json"));
        assertRejected(() -> validator(at("2026-07-22T00:00:00Z")).requireApproved(missing));
    }

    private GovernedFixtureValidator validator(Clock clock) {
        return new GovernedFixtureValidator(objectMapper, new ChecksumUtil(), clock);
    }

    private Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessage("Fixture dataset is unavailable or has failed governance validation");
    }

    private Path copyFixture(String name) throws IOException {
        Path destination = tempDir.resolve(name);
        try (var paths = Files.walk(COMMITTED_FIXTURE)) {
            for (Path source : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = destination.resolve(COMMITTED_FIXTURE.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return destination;
    }
}
