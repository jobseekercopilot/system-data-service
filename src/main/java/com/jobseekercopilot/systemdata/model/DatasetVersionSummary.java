package com.jobseekercopilot.systemdata.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public record DatasetVersionSummary(
        String datasetId,
        String version,
        Instant createdAt,
        Path outputDirectory,
        Map<String, Integer> recordCounts) {
}
