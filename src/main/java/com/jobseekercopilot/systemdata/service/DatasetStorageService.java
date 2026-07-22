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
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DatasetStorageService {
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final ObjectMapper objectMapper;
    private final SystemDataProperties properties;
    private Path lastBackupDirectory;

    public DatasetStorageService(ObjectMapper objectMapper, SystemDataProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Path save(DatasetManifest manifest, JobDataset jobs, LocationDataset locations, DatasetGenerationReport report) {
        return save(manifest, jobs, locations, report, false);
    }

    public Path save(DatasetManifest manifest, JobDataset jobs, LocationDataset locations, DatasetGenerationReport report,
                     boolean overwrite) {
        Path datasetRoot = properties.getRepositoryDirectory().resolve(manifest.datasetId());
        Path datasetDirectory = datasetRoot.resolve(manifest.version());
        Path tempDirectory = datasetRoot.resolve("." + manifest.version() + ".tmp-" + System.nanoTime());
        lastBackupDirectory = null;
        try {
            if (Files.exists(datasetDirectory)) {
                if (!overwrite) {
                    throw new DatasetGenerationException("Dataset version already exists: " + datasetDirectory);
                }
                lastBackupDirectory = backupExistingDataset(datasetRoot, datasetDirectory, manifest.version());
            }
            Files.createDirectories(tempDirectory);
            write(tempDirectory.resolve("manifest.json"), manifest);
            write(tempDirectory.resolve("jobs.json"), jobs);
            write(tempDirectory.resolve("locations.json"), locations);
            write(tempDirectory.resolve("generation-report.json"), report);
            Files.move(tempDirectory, datasetDirectory, StandardCopyOption.ATOMIC_MOVE);
            return datasetDirectory;
        } catch (IOException ex) {
            deleteQuietly(tempDirectory);
            throw new DatasetGenerationException("Failed to store generated dataset", ex);
        }
    }

    public DatasetGenerationResult read(Path datasetDirectory) {
        try {
            DatasetManifest manifest = objectMapper.readValue(datasetDirectory.resolve("manifest.json").toFile(), DatasetManifest.class);
            JobDataset jobs = objectMapper.readValue(datasetDirectory.resolve("jobs.json").toFile(), JobDataset.class);
            LocationDataset locations = objectMapper.readValue(datasetDirectory.resolve("locations.json").toFile(), LocationDataset.class);
            DatasetGenerationReport report = objectMapper.readValue(datasetDirectory.resolve("generation-report.json").toFile(), DatasetGenerationReport.class);
            return new DatasetGenerationResult(manifest, datasetDirectory, jobs, locations, report);
        } catch (IOException ex) {
            throw new DatasetGenerationException("Failed to read dataset " + datasetDirectory, ex);
        }
    }

    public List<String> listDatasetIds() throws IOException {
        if (!Files.exists(properties.getRepositoryDirectory())) {
            return List.of();
        }
        try (var stream = Files.list(properties.getRepositoryDirectory())) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }

    public List<DatasetVersionSummary> listVersions(String datasetId) throws IOException {
        Path datasetRoot = properties.getRepositoryDirectory().resolve(datasetId);
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
        return properties.getRepositoryDirectory().resolve(datasetId).resolve(version);
    }

    public Path lastBackupDirectory() {
        return lastBackupDirectory;
    }

    private Path backupExistingDataset(Path datasetRoot, Path datasetDirectory, String version) throws IOException {
        Path backupRoot = datasetRoot.resolve("backups");
        Files.createDirectories(backupRoot);
        Path backupDirectory = backupRoot.resolve(version + "-" + BACKUP_TIMESTAMP_FORMAT.format(Instant.now()));
        Files.move(datasetDirectory, backupDirectory, StandardCopyOption.ATOMIC_MOVE);
        return backupDirectory;
    }

    private void write(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
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
