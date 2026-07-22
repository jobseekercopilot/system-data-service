package com.jobseekercopilot.systemdata.model;

public record FixtureLlmRequest(
        String datasetId,
        String datasetVersion,
        String scenario,
        String operation,
        String fixtureKey,
        String promptHash) {
}
