package com.jobseekercopilot.systemdata.model;

import java.util.List;

public record FixtureJobSearchResponse(
        String datasetId,
        String datasetVersion,
        String scenario,
        String provider,
        String query,
        String location,
        int page,
        int pageSize,
        int totalResults,
        List<DemoJob> jobs) {
}
