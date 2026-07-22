package com.jobseekercopilot.systemdata.model;

import com.jobseekercopilot.systemdata.util.DeterministicIds;
import java.util.List;

public record NamedStateIdentity(
        String key,
        String email,
        String displayName,
        List<String> resetComponents,
        List<String> seedComponents) {

    public String userId(String scenarioId) {
        return DeterministicIds.uuidString(scenarioId + ":" + key + ":user");
    }
}
