package com.jobseekercopilot.systemdata.model;

import java.util.List;
import java.util.Map;

public record EnvironmentServiceResult(
        String service,
        String operation,
        String status,
        int recordsAffected,
        Map<String, Object> details,
        List<String> warnings) {
    public static EnvironmentServiceResult skipped(String service, String reason) {
        return new EnvironmentServiceResult(service, "NONE", "SKIPPED", 0, Map.of("reason", reason), List.of());
    }
}
