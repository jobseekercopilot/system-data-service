package com.jobseekercopilot.systemdata.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemDataApplicationSeedRequestTest {

    @Test
    void acceptsAnEmptyScopedScenarioAndCopiesTheApplicationList() {
        List<SystemDataApplicationSeedRecord> records = new ArrayList<>();
        var request = new SystemDataApplicationSeedRequest(
                "2.0.0",
                "empty-v1",
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                records);

        records.add(record());

        assertThat(request.applications()).isEmpty();
        assertThatThrownBy(() -> request.applications().add(record()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnreviewedSchemaScenarioAndOversizedPayloads() {
        UUID ownerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        assertThatThrownBy(() -> new SystemDataApplicationSeedRequest(
                "1.0.0", "empty-v1", ownerId, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema version");
        assertThatThrownBy(() -> new SystemDataApplicationSeedRequest(
                "2.0.0", "production", ownerId, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenarioId");
        assertThatThrownBy(() -> new SystemDataApplicationSeedRequest(
                "2.0.0", "empty-v1", ownerId, java.util.Collections.nCopies(101, record())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void rejectsStatusAndTimestampDriftBeforeCallingTheProducer() {
        SystemDataApplicationSeedRecord valid = record();
        assertThat(valid.status()).isEqualTo("APPLIED");

        assertThatThrownBy(() -> record("UNKNOWN", "2026-07-10T09:00", "2026-07-10T10:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> record("APPLIED", "2026-07-10T10:00", "2026-07-10T09:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }

    private SystemDataApplicationSeedRecord record() {
        return record("APPLIED", "2026-07-10T09:00", "2026-07-10T10:00");
    }

    private SystemDataApplicationSeedRecord record(String status, String createdAt, String updatedAt) {
        return new SystemDataApplicationSeedRecord(
                UUID.fromString("10000000-0000-4000-8000-000000000001"),
                "job-1",
                "canonical-job-1",
                "fixture",
                "external-1",
                "Software Developer",
                "Example Ltd",
                "Reading",
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                1,
                "a".repeat(64),
                UUID.fromString("30000000-0000-4000-8000-000000000001"),
                UUID.fromString("30000000-0000-4000-8000-000000000001"),
                1,
                "b".repeat(64),
                status,
                createdAt,
                updatedAt,
                "2026-07-10T09:30");
    }
}
