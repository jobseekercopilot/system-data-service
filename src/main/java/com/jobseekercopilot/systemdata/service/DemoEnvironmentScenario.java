package com.jobseekercopilot.systemdata.service;

import java.util.List;
import java.util.Map;

public record DemoEnvironmentScenario(
        String scenarioId,
        String userId,
        String email,
        Map<String, Object> user,
        Map<String, Object> profile,
        Map<String, Object> payment,
        Map<String, Object> documents,
        List<Map<String, Object>> applications,
        List<String> selectedJobIds,
        Map<String, Integer> applicationsByStatus,
        List<String> warnings) {
}
