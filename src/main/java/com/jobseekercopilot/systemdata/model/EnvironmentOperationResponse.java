package com.jobseekercopilot.systemdata.model;

import java.time.Instant;
import java.util.List;

public record EnvironmentOperationResponse(
        String operationId,
        EnvironmentScenario scenario,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<EnvironmentServiceResult> services,
        EnvironmentSummary summary,
        List<String> warnings) {
}
