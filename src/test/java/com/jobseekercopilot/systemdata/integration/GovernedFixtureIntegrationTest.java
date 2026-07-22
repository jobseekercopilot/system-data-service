package com.jobseekercopilot.systemdata.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.FixtureLlmRequest;
import com.jobseekercopilot.systemdata.service.DatasetStorageService;
import com.jobseekercopilot.systemdata.service.DemoEnvironmentScenarioBuilder;
import com.jobseekercopilot.systemdata.service.FixtureService;
import com.jobseekercopilot.systemdata.service.GovernedFixtureValidator;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernedFixtureIntegrationTest {
    private SystemDataProperties properties;
    private DatasetStorageService storage;
    private FixtureService fixtureService;

    @BeforeEach
    void setUp() {
        properties = new SystemDataProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        storage = new DatasetStorageService(objectMapper, properties);
        fixtureService = new FixtureService(
                properties,
                storage,
                objectMapper,
                new GovernedFixtureValidator(objectMapper, new ChecksumUtil()));
    }

    @Test
    void jobAndProviderFixturesUseTheApprovedSyntheticDataset() {
        var all = fixtureService.searchJobs(null, null, "DEMO_READY", null,
                null, null, 0, 20, null, null, null, null);
        var adzuna = fixtureService.searchJobs(null, null, "DEMO_READY", "adzuna",
                null, null, 0, 20, null, null, null, null);

        assertThat(all.totalResults()).isEqualTo(9);
        assertThat(all.jobs()).hasSize(9).allSatisfy(job -> {
            assertThat(job.externalReference()).startsWith("SYNTH-JOB-");
            assertThat(job.sourceProvider()).endsWith("-gateway-fixture");
            assertThat(job.sourceUrl()).startsWith("https://jobs.example.test/");
        });
        assertThat(adzuna.jobs()).hasSize(3)
                .allMatch(job -> "adzuna-gateway-fixture".equals(job.sourceProvider()));
        assertThat(fixtureService.job(null, null, all.jobs().get(0).id())).isEqualTo(all.jobs().get(0));
    }

    @Test
    void llmFixtureAndNamedScenarioAreDeterministic() {
        FixtureLlmRequest request = new FixtureLlmRequest(
                null, null, "DEMO_READY", "CV_COVER_LETTER_GENERATION", null, null);
        var firstLlm = fixtureService.llm(request);
        var secondLlm = fixtureService.llm(request);
        assertThat(firstLlm).isEqualTo(secondLlm);
        assertThat(firstLlm.provider()).isEqualTo("FIXTURE");
        assertThat(firstLlm.fixtureMode()).isTrue();

        var dataset = storage.read(storage.datasetVersionDirectory(
                properties.getFixtures().getDefaultDatasetId(),
                properties.getFixtures().getDefaultDatasetVersion()));
        var builder = new DemoEnvironmentScenarioBuilder();
        var firstScenario = builder.build(dataset.jobs(), Instant.parse("2026-07-10T09:00:00Z"));
        var secondScenario = builder.build(dataset.jobs(), Instant.parse("2026-07-10T09:00:00Z"));

        assertThat(firstScenario).isEqualTo(secondScenario);
        assertThat(firstScenario.applications()).hasSize(9);
        assertThat(firstScenario.userId()).isEqualTo(builder.demoUserId());
    }
}
