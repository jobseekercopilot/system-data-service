package com.jobseekercopilot.systemdata.model;

public record FixturePlaceResponse(
        String id,
        String name,
        String postcode,
        String region,
        String adminDistrict,
        double latitude,
        double longitude) {
}
