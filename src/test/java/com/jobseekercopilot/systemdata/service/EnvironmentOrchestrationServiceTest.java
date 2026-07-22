package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.model.JobDataset;
import com.jobseekercopilot.systemdata.model.LocationDataset;
import com.jobseekercopilot.systemdata.model.NamedStateDefinition;
import com.jobseekercopilot.systemdata.model.NamedStateIdentity;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class EnvironmentOrchestrationServiceTest {
    private static final String USER_ID = new DemoEnvironmentScenarioBuilder().demoUserId();

    private SystemDataProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EnvironmentOrchestrationService service;
    private Map<String, List<String>> requestBodies;

    @BeforeEach
    void setUp() {
        properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setEnabled(true);
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        requestBodies = new LinkedHashMap<>();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        service = new EnvironmentOrchestrationService(
                properties,
                new SyntheticDatasetStorage(properties),
                new EnvironmentManagementGuard(properties, environment),
                new DemoEnvironmentScenarioBuilder(),
                noOpFixtureValidator(),
                new NamedStateRegistry(new ObjectMapper().findAndRegisterModules()),
                restTemplate);
    }

    @Test
    void repeatedResetIsBoundedToTheScenarioIdentityAndSafeToRetry() {
        for (int attempt = 0; attempt < 2; attempt++) {
            expectSuccess(DELETE, "http://localhost:8099/internal/system-data/scenario/demo-ready-v1/payments/" + USER_ID, "payment-service", "RESET");
            expectSuccess(DELETE, "http://localhost:8089/internal/system-data/scenario/demo-ready-v1/documents/" + USER_ID, "document-store-service", "RESET");
            expectSuccess(DELETE, "http://localhost:8088/internal/system-data/scenario/demo-ready-v1/applications/" + USER_ID, "application-tracker-service", "RESET");
            expectSuccess(DELETE, "http://localhost:8085/internal/system-data/scenario/demo-ready-v1/profiles/" + USER_ID, "user-profile-service", "RESET");
            expectSuccess(DELETE, "http://localhost:8084/internal/system-data/scenario/demo-ready-v1/users/" + USER_ID, "authentication-service", "RESET");
        }

        var first = service.reset(request(EnvironmentScenario.DEMO_READY));
        var second = service.reset(request(EnvironmentScenario.DEMO_READY));

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(first.services()).extracting(result -> result.service()).containsExactlyElementsOf(
                second.services().stream().map(result -> result.service()).toList());
        server.verify();
    }

    @Test
    void repeatedSeedUsesTheSameDeterministicScenarioPayloadAndIsSafeToRetry() {
        for (int attempt = 0; attempt < 2; attempt++) {
            expectSeedSequence();
        }

        var first = service.seed(request(EnvironmentScenario.DEMO_READY));
        var second = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(first.summary()).isEqualTo(second.summary());
        assertThat(first.services()).extracting(result -> result.service()).containsExactlyElementsOf(
                second.services().stream().map(result -> result.service()).toList());
        assertThat(requestBodies).hasSize(5);
        assertThat(requestBodies.values()).allSatisfy(bodies -> {
            assertThat(bodies).hasSize(2);
            assertThat(bodies.get(0)).isEqualTo(bodies.get(1));
        });
        requestBodies.forEach((url, bodies) -> {
            if (!url.endsWith("/seed/applications")) {
                assertThat(bodies.get(0)).contains(USER_ID);
            }
        });
        server.verify();
    }

    @Test
    void downstreamResetFailureStopsFurtherMutationAndMakesRetryExplicit() {
        server.expect(requestTo("http://localhost:8099/internal/system-data/scenario/demo-ready-v1/payments/" + USER_ID))
                .andExpect(method(DELETE))
                .andRespond(withServerError());

        var response = service.reset(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.services().get(0).status()).isEqualTo("FAILED");
        assertThat(response.services().subList(1, 5)).allMatch(result -> "SKIPPED".equals(result.status()));
        assertThat(response.warnings()).singleElement().asString().contains("retry is safe");
        assertThat(response.services().toString()).doesNotContain("localhost", "HttpServerErrorException");
        server.verify();
    }

    @Test
    void resetAndSeedNeverSeedsAfterIncompleteReset() {
        server.expect(requestTo("http://localhost:8099/internal/system-data/scenario/demo-ready-v1/payments/" + USER_ID))
                .andExpect(method(DELETE))
                .andRespond(withServerError());

        var response = service.resetAndSeed(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.warnings()).singleElement().asString().contains("Seed phase skipped");
        assertThat(response.services()).noneMatch(result -> "SEED".equals(result.operation()));
        server.verify();
    }

    @Test
    void redirectResponseCannotEscapeTheValidatedTargetBoundary() {
        server.expect(requestTo("http://localhost:8099/internal/system-data/scenario/demo-ready-v1/payments/" + USER_ID))
                .andExpect(method(DELETE))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.FOUND)
                        .header("Location", "https://payments.production.example/reset-all"));

        var response = service.reset(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.services().get(0).details()).isEmpty();
        assertThat(response.toString()).doesNotContain("production.example", "reset-all");
        server.verify();
    }

    @Test
    void downstreamSeedFailureStopsRemainingWritesAndRequiresResetAndSeedRetry() {
        expectSuccess(POST, "http://localhost:8084/internal/system-data/seed/user", "authentication-service", "SEED");
        server.expect(requestTo("http://localhost:8085/internal/system-data/seed/profiles/" + USER_ID))
                .andExpect(method(POST))
                .andRespond(withServerError());

        var response = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.services()).extracting(result -> result.status())
                .startsWith("SUCCESS", "FAILED", "SKIPPED", "SKIPPED", "SKIPPED");
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("reset-and-seed"));
        server.verify();
    }

    @Test
    void verifyReadsOnlyTheScenarioOwnedIdentity() {
        expectVerification(new NamedStateRegistry(new ObjectMapper().findAndRegisterModules())
                .require(EnvironmentScenario.DEMO_READY));

        var response = service.verify(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("SUCCESS");
        server.verify();
    }

    @Test
    void verificationFailsWhenMeasuredStateDoesNotMatchTheDefinition() {
        NamedStateDefinition definition = new NamedStateRegistry(new ObjectMapper().findAndRegisterModules())
                .require(EnvironmentScenario.LOGIN_SESSION);
        String userId = definition.identities().get(0).userId(definition.scenarioId());
        server.expect(requestTo("http://localhost:8084/internal/system-data/verify/users/" + userId))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"status\":\"SUCCESS\",\"recordsAffected\":0,\"details\":{\"exists\":false}}",
                        MediaType.APPLICATION_JSON));

        var response = service.verify(request(EnvironmentScenario.LOGIN_SESSION));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.services()).last().satisfies(result -> {
            assertThat(result.service()).isEqualTo("named-state-catalog");
            assertThat(result.warnings()).containsExactly(
                    "Prepared state does not match its named-state definition.");
        });
        server.verify();
    }

    @Test
    void statusDoesNotDiscloseValidatedTargetUrls() {
        Map<String, Object> status = service.status();

        assertThat(status).doesNotContainKey("targetServices");
        assertThat(status.toString()).doesNotContain("localhost", "8084", "8099");
    }

    @Test
    void exposesEveryVersionedStateWithoutTargetOrCredentialDetails() {
        assertThat(service.listStates()).hasSize(EnvironmentScenario.values().length)
                .allSatisfy(definition -> {
                    assertThat(definition.version()).matches("[1-9][0-9]*\\.[0-9]+\\.[0-9]+");
                    assertThat(definition.identities()).allSatisfy(identity ->
                            assertThat(identity.email()).endsWith("@example.com"));
                });
        assertThat(service.describe(EnvironmentScenario.PROVIDER_FAILURE).providerBehaviour())
                .isEqualTo("ALL_UNAVAILABLE");
    }

    @ParameterizedTest
    @EnumSource(value = EnvironmentScenario.class, names = {
            "EMPTY", "REGISTRATION_CLEAN", "LOGIN_SESSION", "PROFILE_LOCATION",
            "DUPLICATE_REGISTRATION", "CROSS_USER_SECURITY", "PROVIDER_FAILURE"})
    void namedStatePrepareVerifyAndResetAreRepeatableAndBounded(EnvironmentScenario scenario) {
        NamedStateDefinition definition = new NamedStateRegistry(new ObjectMapper().findAndRegisterModules())
                .require(scenario);
        expectPrepare(definition);
        expectPrepare(definition);
        expectVerification(definition);
        expectReset(definition);

        var first = service.prepare(request(scenario));
        var second = service.prepare(request(scenario));
        var verification = service.verify(request(scenario));
        var reset = service.reset(request(scenario));

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(second.status()).isEqualTo("SUCCESS");
        assertThat(first.summary()).isEqualTo(second.summary());
        assertThat(verification.status()).isEqualTo("SUCCESS");
        assertThat(reset.status()).isEqualTo("SUCCESS");
        server.verify();
    }

    private void expectPrepare(NamedStateDefinition definition) {
        expectReset(definition);
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : identity.seedComponents()) {
                if ("AUTHENTICATION".equals(component)) {
                    expectSuccess(POST, "http://localhost:8084/internal/system-data/seed/user", "authentication-service", "SEED");
                } else if ("USER_PROFILE".equals(component)) {
                    expectSuccess(POST, "http://localhost:8085/internal/system-data/seed/profiles/" + userId, "user-profile-service", "SEED");
                }
            }
        }
    }

    private void expectReset(NamedStateDefinition definition) {
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : identity.resetComponents()) {
                String url = switch (component) {
                    case "PAYMENT" -> "http://localhost:8099/internal/system-data/scenario/" + definition.scenarioId() + "/payments/" + userId;
                    case "DOCUMENTS" -> "http://localhost:8089/internal/system-data/scenario/" + definition.scenarioId() + "/documents/" + userId;
                    case "APPLICATIONS" -> "http://localhost:8088/internal/system-data/scenario/" + definition.scenarioId() + "/applications/" + userId;
                    case "USER_PROFILE" -> "http://localhost:8085/internal/system-data/scenario/" + definition.scenarioId() + "/profiles/" + userId;
                    case "AUTHENTICATION" -> "http://localhost:8084/internal/system-data/scenario/" + definition.scenarioId() + "/users/" + userId;
                    default -> throw new IllegalStateException(component);
                };
                expectSuccess(DELETE, url, "reset", "RESET");
            }
        }
    }

    private void expectVerification(NamedStateDefinition definition) {
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : List.of("AUTHENTICATION", "USER_PROFILE", "APPLICATIONS", "DOCUMENTS", "PAYMENT")) {
                if (!identity.resetComponents().contains(component)) continue;
                String url = switch (component) {
                    case "AUTHENTICATION" -> "http://localhost:8084/internal/system-data/verify/users/" + userId;
                    case "USER_PROFILE" -> "http://localhost:8085/internal/system-data/verify/profiles/" + userId;
                    case "APPLICATIONS" -> "http://localhost:8088/internal/system-data/verify/applications/" + userId;
                    case "DOCUMENTS" -> "http://localhost:8089/internal/system-data/verify/documents/" + userId;
                    case "PAYMENT" -> "http://localhost:8099/internal/system-data/verify/payments/" + userId;
                    default -> throw new IllegalStateException(component);
                };
                boolean seeded = identity.seedComponents().contains(component);
                int applications = expected(definition, "applications");
                int documents = expected(definition, "documents");
                int versions = expected(definition, "documentVersions");
                int ledger = expected(definition, "ledgerEntries");
                String details = switch (component) {
                    case "AUTHENTICATION", "USER_PROFILE" -> "{\"exists\":" + seeded + "}";
                    case "APPLICATIONS" -> "{\"applications\":" + applications + ",\"byStatus\":{}}";
                    case "DOCUMENTS" -> "{\"documents\":" + documents + ",\"documentVersions\":" + versions + "}";
                    case "PAYMENT" -> "{\"walletExists\":" + seeded + ",\"ledgerEntries\":" + ledger + ",\"balanceTokens\":0}";
                    default -> throw new IllegalStateException(component);
                };
                server.expect(requestTo(url))
                        .andExpect(method(GET))
                        .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"recordsAffected\":0,\"details\":" + details + "}",
                                MediaType.APPLICATION_JSON));
            }
        }
    }

    private int expected(NamedStateDefinition definition, String key) {
        Object value = definition.expected().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void expectSeedSequence() {
        expectSuccess(POST, "http://localhost:8084/internal/system-data/seed/user", "authentication-service", "SEED");
        expectSuccess(POST, "http://localhost:8085/internal/system-data/seed/profiles/" + USER_ID, "user-profile-service", "SEED");
        expectSuccess(POST, "http://localhost:8099/internal/system-data/seed/payments", "payment-service", "SEED");
        expectSuccess(POST, "http://localhost:8089/internal/system-data/seed/documents", "document-store-service", "SEED");
        expectSuccess(POST, "http://localhost:8088/internal/system-data/seed/applications", "application-tracker-service", "SEED");
    }

    private void expectSuccess(org.springframework.http.HttpMethod method, String url, String serviceName, String operation) {
        server.expect(requestTo(url))
                .andExpect(method(method))
                .andExpect(request -> {
                    if (POST.equals(method)) {
                        String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                        requestBodies.computeIfAbsent(url, ignored -> new ArrayList<>()).add(body);
                    }
                })
                .andRespond(withSuccess(
                        "{\"service\":\"" + serviceName + "\",\"operation\":\"" + operation
                                + "\",\"status\":\"SUCCESS\",\"recordsAffected\":1}",
                        MediaType.APPLICATION_JSON));
    }

    private EnvironmentOperationRequest request(EnvironmentScenario scenario) {
        return new EnvironmentOperationRequest(scenario, null, null, null);
    }

    private GovernedFixtureValidator noOpFixtureValidator() {
        return new GovernedFixtureValidator(new ObjectMapper(), new ChecksumUtil()) {
            @Override
            public void requireApproved(Path directory) {
                // Fixture governance has dedicated tests; orchestration uses an in-memory synthetic dataset here.
            }
        };
    }

    private static final class SyntheticDatasetStorage extends DatasetStorageService {
        private SyntheticDatasetStorage(SystemDataProperties properties) {
            super(new ObjectMapper(), properties, new DatasetPathPolicy(new SemanticVersionValidator()));
        }

        @Override
        public Path datasetVersionDirectory(String datasetId, String version) {
            return Path.of("synthetic", datasetId, version);
        }

        @Override
        public DatasetGenerationResult read(Path datasetDirectory) {
            return new DatasetGenerationResult(
                    null,
                    datasetDirectory,
                    new JobDataset("1.0", List.of()),
                    new LocationDataset("1.0", List.of()),
                    null);
        }
    }
}
