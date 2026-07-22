package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.systemdata.model.DemoJob;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatasetDeduplicationServiceTest {

    private final DatasetDeduplicationService service = new DatasetDeduplicationService();

    @Test
    void deduplicatesByProviderAndExternalReferenceBeforeTitleFallback() {
        DemoJob first = job("job-1", "reed-gateway", "abc", "Java Developer", "Acme", "London", null);
        DemoJob duplicateReference = job("job-2", "reed-gateway", "abc", "Backend Developer", "Other", "Leeds", null);
        DemoJob sameTitleDifferentProvider = job("job-3", "adzuna-gateway", "abc", "Java Developer", "Acme", "London", null);

        var result = service.deduplicateJobs(List.of(first, duplicateReference, sameTitleDifferentProvider));

        assertThat(result).containsExactly(first, sameTitleDifferentProvider);
    }

    private DemoJob job(String id, String provider, String externalReference, String title, String company, String location, String url) {
        return new DemoJob(id, externalReference, provider, title, company, company, location, null, null, null, null,
                null, null, "GBP", "YEAR", null, null, "ONSITE", null, null, "UNSPECIFIED",
                "A useful job description long enough for tests.", "A useful job description", List.of(), List.of(),
                List.of(), null, null, url, Instant.parse("2026-07-10T08:00:00Z"), "Java Developer", "London",
                Map.of(), Map.of(), true, 0.8);
    }
}
