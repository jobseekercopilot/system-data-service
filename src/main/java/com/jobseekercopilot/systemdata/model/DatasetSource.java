package com.jobseekercopilot.systemdata.model;

import java.time.Instant;

public record DatasetSource(
        String gateway,
        String query,
        String location,
        Instant retrievedAt,
        String status,
        String warning) {
}
