package com.jobseekercopilot.systemdata.model;

import java.util.List;
import java.util.Map;

public record PersonaDefinition(
        String personaId,
        String journeyStyle,
        String purpose,
        List<String> capabilities,
        Map<String, Object> profile) {
}
