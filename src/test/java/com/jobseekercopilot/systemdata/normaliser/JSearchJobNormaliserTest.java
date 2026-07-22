package com.jobseekercopilot.systemdata.normaliser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.generated.jsearchgateway.model.JSearchJob;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JSearchJobNormaliserTest {
    private final TextSanitiser sanitiser = new TextSanitiser();
    private final JSearchJobNormaliser normaliser = new JSearchJobNormaliser(
            sanitiser, new DatasetIdGenerator(), new DemoJobQualityAssessor(sanitiser));

    @Test
    void convertsUkJSearchShapeAndRejectsNonUkShapeForDemoSelection() {
        JSearchJob source = new JSearchJob()
                .externalJobId("jsearch-123")
                .title("Backend Developer")
                .companyName("Fictional Cloud")
                .locationDisplayName("Remote, United Kingdom")
                .description("<p>Develop and test deterministic backend APIs for a fictional company.</p>")
                .country("GB").remote(true).salaryMinimum(45000).salaryMaximum(65000)
                .salaryCurrency("GBP").salaryPeriod("YEAR")
                .primaryApplyUrl("https://jobs.example.test/jsearch-123")
                .postedAt("2026-07-02");
        Instant retrieved = Instant.parse("2026-07-10T09:00:00Z");

        var uk = normaliser.normalise(source, retrieved, "Backend", "Remote");
        source.country("US");
        var nonUk = normaliser.normalise(source, retrieved, "Backend", "Remote");

        assertThat(uk.sourceProvider()).isEqualTo("jsearch-gateway");
        assertThat(uk.remoteType()).isEqualTo("REMOTE");
        assertThat(uk.description()).doesNotContain("<p>");
        assertThat(nonUk.suitableForDemo()).isFalse();
    }
}
