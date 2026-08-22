package com.jobseekercopilot.systemdata.normaliser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.generated.adzunagateway.model.AdzunaJob;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdzunaJobNormaliserTest {
    private final TextSanitiser sanitiser = new TextSanitiser();
    private final AdzunaJobNormaliser normaliser = new AdzunaJobNormaliser(
            sanitiser, new DatasetIdGenerator(), new DemoJobQualityAssessor(sanitiser));

    @Test
    void convertsAdzunaShapeToStableSanitisedCanonicalJob() {
        AdzunaJob source = new AdzunaJob()
                .externalJobId("adzuna-123")
                .title("Senior Java Developer")
                .companyName("Synthetic Systems")
                .locationDisplayName("Reading - Hybrid")
                .description("<p>Build secure Spring Boot services for a fictional product team.</p>")
                .salaryMinimum(50000).salaryMaximum(70000)
                .redirectUrl("https://jobs.example.test/adzuna-123")
                .postedAt("2026-07-01");
        Instant retrieved = Instant.parse("2026-07-10T09:00:00Z");

        var first = normaliser.normalise(source, retrieved, "Java", "Reading");
        var second = normaliser.normalise(source, retrieved, "Java", "Reading");

        assertThat(first).isEqualTo(second);
        assertThat(first.sourceProvider()).isEqualTo("adzuna-gateway");
        assertThat(first.description()).doesNotContain("<p>");
        assertThat(first.remoteType()).isEqualTo("HYBRID");
        assertThat(first.sourceQuery()).isEqualTo("Java");
    }
}
