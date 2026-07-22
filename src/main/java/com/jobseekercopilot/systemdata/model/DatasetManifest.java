package com.jobseekercopilot.systemdata.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DatasetManifest(
        String datasetId,
        String name,
        String version,
        String schemaVersion,
        Instant createdAt,
        String description,
        String status,
        List<String> sourceProviderNames,
        List<String> queries,
        List<String> locations,
        List<DatasetSource> sources,
        Map<String, Integer> recordCounts,
        String checksum,
        DatasetValidationResult validation,
        boolean sanitised,
        Map<String, Object> generationParameters,
        List<String> warnings,
        String parentDatasetVersion) {
}
