package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.model.DatasetGenerationReport;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.DatasetManifest;
import com.jobseekercopilot.systemdata.model.DatasetVersionSummary;
import com.jobseekercopilot.systemdata.model.JobDataset;
import com.jobseekercopilot.systemdata.model.LocationDataset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DatasetStorageService {
    private final ObjectMapper objectMapper;
    private final SystemDataProperties properties;
    private final DatasetPathPolicy pathPolicy;

    public DatasetStorageService(ObjectMapper objectMapper, SystemDataProperties properties,
                                 DatasetPathPolicy pathPolicy) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.pathPolicy = pathPolicy;
    }

    public Path save(DatasetManifest manifest, JobDataset jobs, LocationDataset locations, DatasetGenerationReport report) {
        return save(manifest, jobs, locations, report, false);
    }

    public Path save(DatasetManifest manifest, JobDataset jobs, LocationDataset locations, DatasetGenerationReport report,
                     boolean overwrite) {
        if (overwrite) {
            throw new DatasetGenerationException("Dataset versions are immutable and cannot be overwritten");
        }
        Path datasetRoot = pathPolicy.datasetRoot(properties.getOutputDirectory(), manifest.datasetId());
        Path datasetDirectory = pathPolicy.datasetVersion(properties.getOutputDirectory(), manifest.datasetId(), manifest.version());
        Path tempDirectory = datasetRoot.resolve("." + manifest.version() + ".tmp-" + System.nanoTime());
        try {
            if (Files.exists(datasetDirectory)) {
                throw new DatasetGenerationException("Dataset version already exists");
            }
            Files.createDirectories(tempDirectory);
            write(tempDirectory.resolve("manifest.json"), manifest);
            write(tempDirectory.resolve("jobs.json"), jobs);
            write(tempDirectory.resolve("locations.json"), locations);
            write(tempDirectory.resolve("generation-report.json"), report);
            write(tempDirectory.resolve("acquisition-review.json"), acquisitionReview(manifest));
            Files.move(tempDirectory, datasetDirectory, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return datasetDirectory;
        } catch (IOException ex) {
            deleteQuietly(tempDirectory);
            throw new DatasetGenerationException("Failed to store generated dataset", ex);
        }
    }

    public DatasetGenerationResult read(Path datasetDirectory) {
        Path safeDirectory = pathPolicy.requireContained(datasetDirectory,
                properties.getRepositoryDirectory(), properties.getOutputDirectory());
        try {
            DatasetManifest manifest = objectMapper.readValue(safeFile(safeDirectory, "manifest.json").toFile(), DatasetManifest.class);
            JobDataset jobs = objectMapper.readValue(safeFile(safeDirectory, "jobs.json").toFile(), JobDataset.class);
            LocationDataset locations = objectMapper.readValue(safeFile(safeDirectory, "locations.json").toFile(), LocationDataset.class);
            DatasetGenerationReport report = objectMapper.readValue(safeFile(safeDirectory, "generation-report.json").toFile(), DatasetGenerationReport.class);
            return new DatasetGenerationResult(manifest, safeDirectory, jobs, locations, report);
        } catch (IOException ex) {
            throw new DatasetGenerationException("Failed to read dataset", ex);
        }
    }

    public List<String> listDatasetIds() throws IOException {
        Path root = pathPolicy.requireContained(properties.getRepositoryDirectory(), properties.getRepositoryDirectory());
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .peek(pathPolicy::requireDatasetId)
                    .sorted()
                    .toList();
        }
    }

    public List<DatasetVersionSummary> listVersions(String datasetId) throws IOException {
        Path datasetRoot = pathPolicy.datasetRoot(properties.getRepositoryDirectory(), datasetId);
        if (!Files.exists(datasetRoot)) {
            return List.of();
        }
        try (var stream = Files.list(datasetRoot)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> !"backups".equals(path.getFileName().toString()))
                    .map(this::summary)
                    .sorted(Comparator.comparing(DatasetVersionSummary::version))
                    .toList();
        }
    }

    public Path datasetVersionDirectory(String datasetId, String version) {
        return pathPolicy.datasetVersion(properties.getRepositoryDirectory(), datasetId, version);
    }

    public Path lastBackupDirectory() {
        return null;
    }

    private void write(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private Path safeFile(Path directory, String name) {
        return pathPolicy.requireContained(directory.resolve(name), directory);
    }

    private LinkedHashMap<String, Object> acquisitionReview(DatasetManifest manifest) {
        var review = new LinkedHashMap<String, Object>();
        review.put("schemaVersion", "1.0");
        review.put("classification", "LIVE_ACQUISITION_QUARANTINED");
        review.put("status", "PENDING_PROVENANCE_REVIEW");
        review.put("datasetId", manifest.datasetId());
        review.put("version", manifest.version());
        review.put("termsApprovalReference", properties.getLiveAcquisition().getTermsApprovalReference());
        review.put("provenanceReviewer", properties.getLiveAcquisition().getProvenanceReviewer());
        review.put("approvedProviders", properties.getLiveAcquisition().getApprovedProviders());
        review.put("redistributionApproved", false);
        review.put("runtimeEligible", false);
        return review;
    }

    private DatasetVersionSummary summary(Path datasetDirectory) {
        DatasetGenerationResult result = read(datasetDirectory);
        return new DatasetVersionSummary(
                result.manifest().datasetId(),
                result.manifest().version(),
                result.manifest().createdAt(),
                datasetDirectory,
                result.manifest().recordCounts());
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // Best-effort cleanup for failed generation.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup for failed generation.
        }
    }
}
