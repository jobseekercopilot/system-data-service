package com.jobseekercopilot.systemdata.model;

import java.time.Instant;

public record EnvironmentOperationRequest(
        EnvironmentScenario scenario,
        String datasetId,
        String datasetVersion,
        Instant referenceDate) {
}
