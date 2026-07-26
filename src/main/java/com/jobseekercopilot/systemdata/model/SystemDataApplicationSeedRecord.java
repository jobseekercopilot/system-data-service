package com.jobseekercopilot.systemdata.model;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Closed consumer representation of Application Tracker's OpenAPI 2.0.0 fixture record.
 *
 * <p>Ownership and fixture-scenario fields deliberately do not exist here. They belong to
 * {@link SystemDataApplicationSeedRequest}, preventing a record from selecting another boundary.
 */
public record SystemDataApplicationSeedRecord(
        UUID id,
        String jobId,
        String canonicalJobId,
        String provider,
        String externalJobId,
        String jobTitle,
        String companyName,
        String location,
        UUID cvDocumentId,
        UUID coverLetterDocumentId,
        String status,
        String createdAt,
        String updatedAt,
        String appliedAt) {

    private static final Set<String> STATUSES = Set.of(
            "DOCUMENTS_GENERATED",
            "APPLIED",
            "INTERVIEW",
            "UNSUCCESSFUL",
            "OFFER",
            "ACCEPTED",
            "REJECTED_BY_USER",
            "WITHDRAWN");

    public SystemDataApplicationSeedRecord {
        require(id, "id");
        requireText(jobId, "jobId", 200);
        requireOptionalText(canonicalJobId, "canonicalJobId", 200);
        requireOptionalText(provider, "provider", 64);
        requireOptionalText(externalJobId, "externalJobId", 200);
        requireText(jobTitle, "jobTitle", 300);
        requireText(companyName, "companyName", 300);
        requireOptionalText(location, "location", 300);
        require(cvDocumentId, "cvDocumentId");
        require(coverLetterDocumentId, "coverLetterDocumentId");
        if (status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("status is outside the Application Tracker contract");
        }
        LocalDateTime created = requireDateTime(createdAt, "createdAt");
        LocalDateTime updated = requireDateTime(updatedAt, "updatedAt");
        LocalDateTime applied = appliedAt == null ? null : requireDateTime(appliedAt, "appliedAt");
        if (updated.isBefore(created)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if (applied != null && applied.isBefore(created)) {
            throw new IllegalArgumentException("appliedAt must not precede createdAt");
        }
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be non-blank and no longer than " + maximumLength);
        }
    }

    private static void requireOptionalText(String value, String field, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be no longer than " + maximumLength);
        }
    }

    private static LocalDateTime requireDateTime(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an ISO local date-time", exception);
        }
    }
}
