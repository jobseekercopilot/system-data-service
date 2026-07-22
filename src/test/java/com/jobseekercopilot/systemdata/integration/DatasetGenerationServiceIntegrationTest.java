package com.jobseekercopilot.systemdata.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaJob;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchResponse;
import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.postcodeiogateway.model.PostcodeLocation;
import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.model.DatasetGenerationRequest;
import com.jobseekercopilot.systemdata.normaliser.AdzunaJobNormaliser;
import com.jobseekercopilot.systemdata.normaliser.JSearchJobNormaliser;
import com.jobseekercopilot.systemdata.normaliser.PostcodeNormaliser;
import com.jobseekercopilot.systemdata.normaliser.ReedJobNormaliser;
import com.jobseekercopilot.systemdata.service.DatasetDeduplicationService;
import com.jobseekercopilot.systemdata.service.DatasetGenerationService;
import com.jobseekercopilot.systemdata.service.DatasetManifestService;
import com.jobseekercopilot.systemdata.service.DatasetSanitisationService;
import com.jobseekercopilot.systemdata.service.DatasetStorageService;
import com.jobseekercopilot.systemdata.service.DatasetValidationService;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.service.JobDatasetService;
import com.jobseekercopilot.systemdata.service.LocationDatasetService;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetGenerationServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesSmallDatasetWithMockedGatewayResponses() {
        SystemDataProperties properties = new SystemDataProperties();
        properties.setRepositoryDirectory(tempDir.resolve("dataset-repository"));
        properties.getGeneration().setFailIfAllProvidersFail(true);
        properties.getGeneration().setAllowPartialProviderFailure(true);
        properties.getGateways().getAdzuna().setEnabled(true);
        properties.getGateways().getPostcodeIo().setEnabled(true);

        TextSanitiser textSanitiser = new TextSanitiser();
        DatasetIdGenerator idGenerator = new DatasetIdGenerator();
        DemoJobQualityAssessor qualityAssessor = new DemoJobQualityAssessor(textSanitiser);
        AdzunaJobsApi adzuna = new AdzunaJobsApi() {
            @Override
            public AdzunaSearchResponse search(com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchRequest request) {
                return adzunaResponse();
            }
        };
        JSearchJobsApi jsearch = new JSearchJobsApi();
        ReedJobsApi reed = new ReedJobsApi();
        PostcodeApi postcode = new PostcodeApi() {
            @Override
            public PostcodeLocation getLocationByPostcode(String postcode) {
                return postcodeLocation();
            }
        };

        JobDatasetService jobDatasetService = new JobDatasetService(
                properties,
                adzuna,
                jsearch,
                reed,
                new AdzunaJobNormaliser(textSanitiser, idGenerator, qualityAssessor),
                new JSearchJobNormaliser(textSanitiser, idGenerator, qualityAssessor),
                new ReedJobNormaliser(textSanitiser, idGenerator, qualityAssessor));
        LocationDatasetService locationDatasetService = new LocationDatasetService(
                properties, postcode, new PostcodeNormaliser(idGenerator), idGenerator);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DatasetGenerationService service = new DatasetGenerationService(
                properties,
                jobDatasetService,
                locationDatasetService,
                new DatasetSanitisationService(textSanitiser),
                new DatasetDeduplicationService(),
                new DatasetValidationService(),
                new DatasetManifestService(objectMapper, new ChecksumUtil(), properties),
                new DatasetStorageService(objectMapper, properties),
                new SemanticVersionValidator(),
                qualityAssessor);

        var result = service.generate(new DatasetGenerationRequest(
                "uk-software-developer-demo",
                "UK Software Developer Demo",
                "1.0.0",
                "Test dataset",
                List.of("Software Developer"),
                List.of("London"),
                List.of("SW1A 1AA"),
                List.of("ADZUNA"),
                20,
                false,
                false));

        assertThat(Files.exists(result.outputDirectory().resolve("manifest.json"))).isTrue();
        assertThat(Files.exists(result.outputDirectory().resolve("jobs.json"))).isTrue();
        assertThat(Files.exists(result.outputDirectory().resolve("locations.json"))).isTrue();
        assertThat(Files.exists(result.outputDirectory().resolve("generation-report.json"))).isTrue();
        assertThat(result.jobs().jobs()).hasSize(1);
        assertThat(result.locations().locations()).hasSize(2);
        assertThat(result.report().rawResultCounts()).containsEntry("ADZUNA", 1);
        assertThat(result.outputDirectory()).endsWith(Path.of("uk-software-developer-demo", "1.0.0"));

        assertThatThrownBy(() -> service.generate(new DatasetGenerationRequest(
                "uk-software-developer-demo",
                "UK Software Developer Demo",
                "1.0.0",
                "Test dataset",
                List.of("Software Developer"),
                List.of("London"),
                List.of("SW1A 1AA"),
                List.of("ADZUNA"),
                20,
                false,
                false)))
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessageContaining("already exists");

        var overwritten = service.generate(new DatasetGenerationRequest(
                "uk-software-developer-demo",
                "UK Software Developer Demo",
                "1.0.0",
                "Test dataset replacement",
                List.of("Software Developer"),
                List.of("London"),
                List.of("SW1A 1AA"),
                List.of("ADZUNA"),
                20,
                false,
                true));

        assertThat(overwritten.outputDirectory()).isEqualTo(result.outputDirectory());
        assertThat(Files.exists(tempDir.resolve("dataset-repository/uk-software-developer-demo/backups"))).isTrue();
    }

    @Test
    void defaultConfigurationNeverCallsProviderGateways() {
        SystemDataProperties properties = new SystemDataProperties();
        AdzunaJobsApi adzuna = new AdzunaJobsApi() {
            @Override
            public AdzunaSearchResponse search(
                    com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchRequest request) {
                throw new AssertionError("disabled Adzuna gateway was called");
            }
        };
        JSearchJobsApi jsearch = new JSearchJobsApi() {
            @Override
            public com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchResponse search(
                    com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchRequest request) {
                throw new AssertionError("disabled JSearch gateway was called");
            }
        };
        ReedJobsApi reed = new ReedJobsApi() {
            @Override
            public com.jobseekercopilot.generated.reedgateway.model.ReedSearchResponse searchJobs(
                    String query, String location, Integer limit) {
                throw new AssertionError("disabled Reed gateway was called");
            }
        };
        PostcodeApi postcode = new PostcodeApi() {
            @Override
            public PostcodeLocation getLocationByPostcode(String postcode) {
                throw new AssertionError("disabled postcode gateway was called");
            }
        };
        TextSanitiser textSanitiser = new TextSanitiser();
        DatasetIdGenerator idGenerator = new DatasetIdGenerator();
        DemoJobQualityAssessor qualityAssessor = new DemoJobQualityAssessor(textSanitiser);
        JobDatasetService jobs = new JobDatasetService(
                properties,
                adzuna,
                jsearch,
                reed,
                new AdzunaJobNormaliser(textSanitiser, idGenerator, qualityAssessor),
                new JSearchJobNormaliser(textSanitiser, idGenerator, qualityAssessor),
                new ReedJobNormaliser(textSanitiser, idGenerator, qualityAssessor));
        LocationDatasetService locations = new LocationDatasetService(
                properties,
                postcode,
                new PostcodeNormaliser(idGenerator),
                idGenerator);

        assertThatThrownBy(() -> jobs.gather(List.of("Developer"), List.of("London"), null, 10))
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessageContaining("disabled");
        assertThat(locations.gather(List.of("SW1A 1AA")).attemptedLookups()).isZero();
    }

    private AdzunaSearchResponse adzunaResponse() {
        AdzunaJob job = new AdzunaJob()
                .externalJobId("adzuna-1")
                .title("Software Developer")
                .companyName("Demo Tech")
                .locationDisplayName("London")
                .description("<p>Build useful Java and Angular services for a product team.</p>")
                .latitude(new BigDecimal("51.5074"))
                .longitude(new BigDecimal("-0.1278"))
                .salaryMinimum(45000)
                .salaryMaximum(60000)
                .redirectUrl("https://example.test/jobs/adzuna-1");
        return new AdzunaSearchResponse().jobs(List.of(job)).totalAvailable(1);
    }

    private PostcodeLocation postcodeLocation() {
        return new PostcodeLocation()
                .postcode("SW1A 1AA")
                .adminDistrict("Westminster")
                .adminCounty("Greater London")
                .region("London")
                .country("England")
                .latitude(51.501)
                .longitude(-0.141);
    }
}
