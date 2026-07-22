package com.jobseekercopilot.systemdata.model;

public record FixturePostcodeResponse(
        String postcode,
        String country,
        String region,
        String adminDistrict,
        double latitude,
        double longitude,
        boolean found,
        String fixtureSource) {
}
