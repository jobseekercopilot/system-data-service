package com.jobseekercopilot.systemdata.model;

import java.time.Instant;

public record DemoLocation(
        String id,
        String postcode,
        String postcodeDistrict,
        String postcodeArea,
        String placeName,
        String district,
        String county,
        String region,
        String country,
        Double latitude,
        Double longitude,
        String sourceProvider,
        Instant sourceRetrievedAt) {
}
