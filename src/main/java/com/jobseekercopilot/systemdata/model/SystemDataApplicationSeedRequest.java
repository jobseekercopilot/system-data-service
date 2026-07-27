package com.jobseekercopilot.systemdata.model;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Versioned owner-and-scenario envelope required by Application Tracker OpenAPI 3.0.0.
 */
public record SystemDataApplicationSeedRequest(
        String schemaVersion,
        String scenarioId,
        UUID userId,
        List<SystemDataApplicationSeedRecord> applications) {

    public static final String SCHEMA_VERSION = "2.0.0";
    private static final Pattern SCENARIO_ID =
            Pattern.compile("[a-z0-9][a-z0-9-]{1,54}-v[1-9][0-9]{0,6}");

    public SystemDataApplicationSeedRequest {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported Application Tracker fixture schema version");
        }
        if (scenarioId == null || !SCENARIO_ID.matcher(scenarioId).matches()) {
            throw new IllegalArgumentException("scenarioId is outside the Application Tracker contract");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (applications == null
                || applications.size() > 100
                || applications.stream().anyMatch(record -> record == null)) {
            throw new IllegalArgumentException("applications must contain at most 100 non-null records");
        }
        applications = List.copyOf(applications);
    }
}
