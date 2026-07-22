package com.jobseekercopilot.systemdata.normaliser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.generated.reedgateway.model.ReedJobDto;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReedJobNormaliserTest {

    private final TextSanitiser textSanitiser = new TextSanitiser();
    private final ReedJobNormaliser normaliser = new ReedJobNormaliser(
            textSanitiser,
            new DatasetIdGenerator(),
            new DemoJobQualityAssessor(textSanitiser));

    @Test
    void convertsGeneratedReedDtoToCanonicalDemoJob() {
        ReedJobDto source = new ReedJobDto()
                .jobId("reed-123")
                .jobTitle("Senior Java Developer")
                .employerName("Example Tech")
                .locationName("Reading")
                .minimumSalary("45000")
                .maximumSalary("65000")
                .currency("GBP")
                .jobDescription("<p>Build Spring Boot APIs in a hybrid team.</p>")
                .jobUrl("https://example.test/job/reed-123")
                .employmentType("Permanent");

        var job = normaliser.normalise(source, Instant.parse("2026-07-10T08:00:00Z"));

        assertThat(job.sourceProvider()).isEqualTo("reed-gateway");
        assertThat(job.externalReference()).isEqualTo("reed-123");
        assertThat(job.title()).isEqualTo("Senior Java Developer");
        assertThat(job.companyName()).isEqualTo("Example Tech");
        assertThat(job.salaryMinimum()).isEqualTo(45000);
        assertThat(job.salaryMaximum()).isEqualTo(65000);
        assertThat(job.remoteType()).isEqualTo("HYBRID");
        assertThat(job.seniority()).isEqualTo("SENIOR");
        assertThat(job.description()).doesNotContain("<p>");
    }
}
