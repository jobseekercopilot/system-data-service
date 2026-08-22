package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.systemdata.model.DemoJob;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetValidationServiceTest {
    private final DatasetValidationService service = new DatasetValidationService();

    @Test
    void invalidCanonicalRecordsFailValidationWithoutLeakingTheirContent() {
        DemoJob invalid = new DemoJob("synthetic-id", "ref", "fixture", null, "Company", "Company",
                "Reading", null, null, null, null, null, null, "GBP", "YEAR", null, null,
                "ONSITE", null, null, "UNSPECIFIED", null, null, List.of(), List.of(), List.of(),
                null, null, "https://jobs.example.test/synthetic-id", Instant.parse("2026-07-10T09:00:00Z"),
                null, null, Map.of(), Map.of(), false, 0);

        var validation = service.validate(List.of(invalid), List.of());

        assertThat(validation.valid()).isFalse();
        assertThat(validation.warnings()).doesNotContain("synthetic-id", "Company");
    }
}
