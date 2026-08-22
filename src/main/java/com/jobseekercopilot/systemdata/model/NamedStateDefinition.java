package com.jobseekercopilot.systemdata.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NamedStateDefinition(
        String scenarioId,
        String version,
        EnvironmentScenario scenario,
        String purpose,
        String providerBehaviour,
        String datasetId,
        String datasetVersion,
        Instant referenceDate,
        List<NamedStateIdentity> identities,
        Map<String, Object> expected) {
}
