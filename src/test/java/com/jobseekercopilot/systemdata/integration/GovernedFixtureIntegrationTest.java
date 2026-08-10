package com.jobseekercopilot.systemdata.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.FixtureLlmRequest;
import com.jobseekercopilot.systemdata.exception.GatewayUnavailableException;
import com.jobseekercopilot.systemdata.service.DatasetStorageService;
import com.jobseekercopilot.systemdata.service.DatasetPathPolicy;
import com.jobseekercopilot.systemdata.service.DemoEnvironmentScenarioBuilder;
import com.jobseekercopilot.systemdata.service.FixtureService;
import com.jobseekercopilot.systemdata.service.GovernedFixtureValidator;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
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
        storage = new DatasetStorageService(objectMapper, properties,
                new DatasetPathPolicy(new SemanticVersionValidator()));
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

        assertThat(all.totalResults()).isEqualTo(9);
        assertThat(all.jobs()).hasSize(9).allSatisfy(job -> {
            assertThat(job.externalReference()).startsWith("SYNTH-JOB-");
            assertThat(job.sourceProvider()).endsWith("-gateway-fixture");
            assertThat(job.sourceUrl()).startsWith("https://jobs.example.test/");
        });
        for (String provider : new String[]{"adzuna", "jsearch", "reed"}) {
            var providerJobs = fixtureService.searchJobs(null, null, "DEMO_READY", provider,
                    null, null, 0, 20, null, null, null, null);
            assertThat(providerJobs.totalResults()).isEqualTo(3);
            assertThat(providerJobs.jobs()).hasSize(3)
                    .allMatch(job -> (provider + "-gateway-fixture").equals(job.sourceProvider()));
        }
        assertThat(fixtureService.job(null, null, all.jobs().get(0).id())).isEqualTo(all.jobs().get(0));
    }

    @Test
    void matchingFiltersRemainDeterministicAcrossProviders() {
        assertSingleMatch("adzuna", "Junior", "Manchester", "Junior Software Developer");
        assertSingleMatch("jsearch", "Angular", "Bristol", "Angular Developer");
        assertSingleMatch("reed", "Spring Boot", "Leeds", "Spring Boot Developer");
        assertSingleMatch("adzuna", "Software Developer", "RG1 1AA", "Java Software Developer");
        assertSingleMatch(
                "adzuna",
                "Software Developer",
                "Reading, South East (RG1 1AA)",
                "Java Software Developer");
    }

    @Test
    void noMatchFiltersReturnTrueEmptyResultsAcrossProviders() {
        for (String provider : new String[]{"adzuna", "jsearch", "reed"}) {
            var response = fixtureService.searchJobs(null, null, "DEMO_READY", provider,
                    "COBOL mainframe archaeologist", null, 0, 20, null, null, null, null);

            assertThat(response.totalResults()).isZero();
            assertThat(response.jobs()).isEmpty();
        }
    }

    @Test
    void invalidLocationsReturnTrueEmptyResultsAcrossProviders() {
        for (String provider : new String[]{"adzuna", "jsearch", "reed"}) {
            var response = fixtureService.searchJobs(null, null, "DEMO_READY", provider,
                    null, "Atlantis", 0, 20, null, null, null, null);

            assertThat(response.totalResults()).isZero();
            assertThat(response.jobs()).isEmpty();
        }
    }

    @Test
    void placeSearchSupportsCurrentLocationAutocompleteWithoutLiveCalls() {
        assertThat(fixtureService.places("Leeds", 10)).singleElement().satisfies(place -> {
            assertThat(place.name()).isEqualTo("Leeds, Yorkshire and The Humber");
            assertThat(place.postcode()).isEqualTo("LS1 1UR");
            assertThat(place.id()).matches("[0-9a-f-]{36}");
        });
        assertThat(fixtureService.places("South", 2)).hasSize(2);
        assertThat(fixtureService.places("x", 10)).isEmpty();
        assertThat(fixtureService.places("Atlantis", 10)).isEmpty();
    }

    @Test
    void llmFixtureAndNamedScenarioAreDeterministic() throws Exception {
        FixtureLlmRequest request = new FixtureLlmRequest(
                null, null, "DEMO_READY", "CV_COVER_LETTER_GENERATION", null, null);
        var firstLlm = fixtureService.llm(request);
        var secondLlm = fixtureService.llm(request);
        assertThat(firstLlm).isEqualTo(secondLlm);
        assertThat(firstLlm.provider()).isEqualTo("FIXTURE");
        assertThat(firstLlm.fixtureMode()).isTrue();
        var generatedDocuments = new ObjectMapper().readTree(firstLlm.response());
        assertThat(generatedDocuments.has("cv")).isTrue();
        assertThat(generatedDocuments.has("coverLetter")).isTrue();
        assertThat(generatedDocuments.path("cv").path("targetRole").asText())
                .isEqualTo("Java Software Developer");
        assertThat(generatedDocuments.path("coverLetter").path("companyName").asText())
                .isEqualTo("Northstar Digital Labs");
        assertThat(generatedDocuments.path("cv").path("personalSummary").asText())
                .contains("Spring Boot microservices", "Angular products", "automated testing");
        assertThat(generatedDocuments.path("coverLetter").path("bodyParagraphs")).hasSize(2);
        assertThat(generatedDocuments.path("claims")).hasSize(10);

        var dataset = storage.read(storage.datasetVersionDirectory(
                properties.getFixtures().getDefaultDatasetId(),
                properties.getFixtures().getDefaultDatasetVersion()));
        var builder = new DemoEnvironmentScenarioBuilder();
        var firstScenario = builder.build(dataset.jobs(), Instant.parse("2026-07-10T09:00:00Z"));
        var secondScenario = builder.build(dataset.jobs(), Instant.parse("2026-07-10T09:00:00Z"));

        assertThat(firstScenario).isEqualTo(secondScenario);
        assertThat(firstScenario.applications()).hasSize(9);
        assertThat(firstScenario.userId()).isEqualTo(builder.demoUserId());
        assertThat(firstScenario.profile().toString())
                .contains("workplaceArrangements=[HYBRID]");
    }

    @Test
    void providerFailureStateIsDeterministicAndDoesNotReadOrCallAProvider() {
        assertThatThrownBy(() -> fixtureService.searchJobs(null, null, "PROVIDER_FAILURE", null,
                null, null, 0, 20, null, null, null, null))
                .isInstanceOf(GatewayUnavailableException.class)
                .hasMessage("Synthetic provider fixture is unavailable");
    }

    private void assertSingleMatch(String provider, String query, String location, String expectedTitle) {
        var response = fixtureService.searchJobs(null, null, "DEMO_READY", provider,
                query, location, 0, 20, null, null, null, null);

        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.jobs()).singleElement()
                .satisfies(job -> {
                    assertThat(job.title()).isEqualTo(expectedTitle);
                    assertThat(job.sourceProvider()).isEqualTo(provider + "-gateway-fixture");
                });
    }
}
