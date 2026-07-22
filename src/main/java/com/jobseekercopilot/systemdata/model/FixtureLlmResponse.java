package com.jobseekercopilot.systemdata.model;

public record FixtureLlmResponse(
        String provider,
        String model,
        String response,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        String finishReason,
        String createdAt,
        String fixtureKey,
        boolean fixtureMode) {
}
