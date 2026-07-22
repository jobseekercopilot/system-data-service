package com.jobseekercopilot.systemdata.model;

public record FixtureStatusResponse(
        boolean enabled,
        String environment,
        String defaultDatasetId,
        String defaultDatasetVersion,
        String defaultScenario,
        boolean externalCallsEnabled) {
}
