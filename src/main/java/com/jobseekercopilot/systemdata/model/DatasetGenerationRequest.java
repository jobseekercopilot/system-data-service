package com.jobseekercopilot.systemdata.model;

import java.util.List;

public record DatasetGenerationRequest(
        String datasetId,
        String name,
        String version,
        String description,
        List<String> queries,
        List<String> locations,
        List<String> postcodes,
        List<String> providers,
        Integer maximumResultsPerProvider,
        Boolean includeLlmEnrichment,
        Boolean overwrite) {
}
