package com.jobseekercopilot.systemdata.model;

public record FixtureStripeRequest(
        String datasetId,
        String datasetVersion,
        String scenario,
        String operation,
        String userId,
        String pricingPlanId,
        Long tokenAmount,
        Long priceGbpPence) {
}
