package com.jobseekercopilot.systemdata.model;

public record FixtureStripeResponse(
        String operation,
        String sessionId,
        String paymentIntentId,
        String eventId,
        String checkoutUrl,
        String status,
        boolean fixtureMode) {
}
