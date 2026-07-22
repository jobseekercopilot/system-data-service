package com.jobseekercopilot.systemdata.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record DatasetGenerationResponse(
        String datasetId,
        String version,
        Path outputDirectory,
        boolean overwritten,
        Path backupDirectory,
        int jobCount,
        int locationCount,
        int duplicatesRemoved,
        List<String> warnings,
        Map<String, String> providerStatuses) {
}
