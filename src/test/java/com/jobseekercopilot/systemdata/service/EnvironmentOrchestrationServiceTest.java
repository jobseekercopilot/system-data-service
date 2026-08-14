package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.model.NamedStateDefinition;
import com.jobseekercopilot.systemdata.model.NamedStateIdentity;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import com.jobseekercopilot.systemdata.util.DeterministicIds;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class EnvironmentOrchestrationServiceTest {
    private static final String USER_ID = new DemoEnvironmentScenarioBuilder().demoUserId();
    private static final String AUTHENTICATION_SEED_URL =
            "http://localhost:8084/internal/system-data/seed/user";
    private static final String APPLICATION_SEED_URL =
            "http://localhost:8088/internal/system-data/v1/application-scenarios";
    private static final String DOCUMENT_SEED_URL =
            "http://localhost:8089/internal/system-data/seed/documents";
    private static final String PAYMENT_SEED_URL =
            "http://localhost:8099/internal/system-data/seed/payments";

    private SystemDataProperties properties;
    private ObjectMapper objectMapper;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EnvironmentOrchestrationService service;
    private Map<String, List<String>> requestBodies;

    @BeforeEach
    void setUp() {
        properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setEnabled(true);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        requestBodies = new LinkedHashMap<>();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        service = new EnvironmentOrchestrationService(
                properties,
                new DatasetStorageService(
                        objectMapper,
                        properties,
                        new DatasetPathPolicy(new SemanticVersionValidator())),
                new EnvironmentManagementGuard(properties, environment),
                new DemoEnvironmentScenarioBuilder(),
                noOpFixtureValidator(),
                new NamedStateRegistry(objectMapper),
                new PersonaCatalog(objectMapper),
                restTemplate);
    }

    @Test
    void repeatedResetIsBoundedToTheScenarioIdentityAndSafeToRetry() {
        for (int attempt = 0; attempt < 2; attempt++) {
            expectSuccess(DELETE, runtimeOwnerUrl("http://localhost:8088", "demo-ready-v1", "alex-taylor", USER_ID), "application-tracker-service", "RESET");
            expectSuccess(DELETE, runtimeOwnerUrl("http://localhost:8089", "demo-ready-v1", "alex-taylor", USER_ID), "document-store-service", "RESET");
            expectSuccess(DELETE, runtimeOwnerUrl("http://localhost:8099", "demo-ready-v1", "alex-taylor", USER_ID), "payment-service", "RESET");
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
            assertThat(bodies.get(0)).contains(USER_ID);
        });
        assertApplicationEnvelope(
                requestBodies.get(APPLICATION_SEED_URL).get(0),
                requestBodies.get(DOCUMENT_SEED_URL).get(0));
        assertThat(first.summary().applications()).isEqualTo(9);
        assertThat(first.summary().applicationsByStatus()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "OFFER", 1,
                "INTERVIEW", 1,
                "APPLIED", 2,
                "DOCUMENTS_GENERATED", 3,
                "UNSUCCESSFUL", 1,
                "WITHDRAWN", 1));
        server.verify();
    }

    @Test
    void demoReadyUsesThePublishedNamedStateCredential() throws Exception {
        expectSeedSequence();

        var response = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("SUCCESS");
        JsonNode authenticationSeed =
                objectMapper.readTree(requestBodies.get(AUTHENTICATION_SEED_URL).get(0));
        assertThat(authenticationSeed.path("password").asText())
                .isEqualTo("PublicTestPassword123!");
        server.verify();
    }

    @Test
    void demoReadyProfileSeedUsesPublishedProfileDatePrecision() throws Exception {
        expectSeedSequence();

        var response = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("SUCCESS");
        String profileSeedUrl =
                "http://localhost:8085/internal/system-data/seed/profiles/" + USER_ID;
        JsonNode profile = objectMapper.readTree(requestBodies.get(profileSeedUrl).get(0));
        JsonNode qualifications = profile.path("qualifications");
        assertThat(qualifications).hasSize(1);
        String dateAchieved = qualifications.get(0).path("dateAchieved").asText();
        assertThat(dateAchieved)
                .isEqualTo("2021-06")
                .matches("[0-9]{4}-[0-9]{2}(?:-[0-9]{2})?");
        server.verify();
    }

    @Test
    void demoReadyPaymentSeedMatchesPublishedLedgerContract() throws Exception {
        expectSeedSequence();
        expectSeedSequence();

        var firstResponse = service.seed(request(EnvironmentScenario.DEMO_READY));
        var secondResponse = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(firstResponse.status()).isEqualTo("SUCCESS");
        assertThat(secondResponse.status()).isEqualTo("SUCCESS");
        JsonNode firstPayment = objectMapper.readTree(requestBodies.get(PAYMENT_SEED_URL).get(0));
        JsonNode secondPayment = objectMapper.readTree(requestBodies.get(PAYMENT_SEED_URL).get(1));
        JsonNode wallet = firstPayment.path("wallet");
        assertCanonicalUtcInstant(wallet.path("createdAt"));
        assertCanonicalUtcInstant(wallet.path("updatedAt"));

        List<JsonNode> firstTransactions = StreamSupport.stream(
                        firstPayment.path("transactions").spliterator(), false)
                .toList();
        assertThat(firstTransactions)
                .extracting(transaction -> transaction.path("balanceDeltaTokens").asLong())
                .containsExactly(600_000L, -40_000L, 0L, 8_000L, -35_000L, 0L, 5_000L);
        firstTransactions.forEach(transaction -> {
            assertCanonicalUtcInstant(transaction.path("createdAt"));
            assertThat(transaction.path("balanceDeltaTokens").asLong())
                    .isEqualTo(transaction.path("balanceAfter").asLong()
                            - transaction.path("balanceBefore").asLong());
        });

        List<String> firstOperationIds = firstTransactions.stream()
                .map(transaction -> transaction.path("operationId").asText())
                .toList();
        List<String> secondOperationIds = StreamSupport.stream(
                        secondPayment.path("transactions").spliterator(), false)
                .map(transaction -> transaction.path("operationId").asText())
                .toList();
        assertThat(firstOperationIds)
                .allSatisfy(operationId -> assertThat(operationId).isNotBlank())
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(secondOperationIds);
        server.verify();
    }

    @Test
    void demoReadyDocumentSeedCarriesStableOwnerApprovalAudit() throws Exception {
        expectSeedSequence();
        expectSeedSequence();

        var firstResponse = service.seed(request(EnvironmentScenario.DEMO_READY));
        var secondResponse = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(firstResponse.status()).isEqualTo("SUCCESS");
        assertThat(secondResponse.status()).isEqualTo("SUCCESS");
        JsonNode firstSeed = objectMapper.readTree(requestBodies.get(DOCUMENT_SEED_URL).get(0));
        JsonNode secondSeed = objectMapper.readTree(requestBodies.get(DOCUMENT_SEED_URL).get(1));
        JsonNode firstDocumentPayload = firstSeed.path("documents");
        assertThat(firstDocumentPayload).isEqualTo(secondSeed.path("documents"));

        List<JsonNode> documents =
                StreamSupport.stream(firstDocumentPayload.spliterator(), false).toList();
        assertThat(documents).hasSize(19).allSatisfy(document -> {
            assertThat(document.path("lifecycleState").asText()).isEqualTo("APPROVED");
            assertThat(document.path("approvedBy").asText())
                    .isEqualTo(firstSeed.path("userId").asText())
                    .isEqualTo(document.path("userId").asText());
            assertCanonicalLocalDateTime(document.path("approvedAt"));
            assertThat(document.path("approvedAt").asText())
                    .isEqualTo(document.path("createdAt").asText());
            if (document.path("active").asBoolean()) {
                assertThat(document.path("lifecycleState").asText()).isEqualTo("APPROVED");
            }
        });

        List<String> documentFamilies = documents.stream()
                .map(document -> document.path("documentFamilyId").asText())
                .distinct()
                .toList();
        Map<String, Long> currentByFamily = documents.stream()
                .filter(document -> document.path("active").asBoolean())
                .collect(Collectors.groupingBy(
                        document -> document.path("documentFamilyId").asText(),
                        LinkedHashMap::new,
                        Collectors.counting()));
        assertThat(currentByFamily.keySet()).containsExactlyInAnyOrderElementsOf(documentFamilies);
        assertThat(currentByFamily.values()).allMatch(count -> count == 1L);
        server.verify();
    }

    @Test
    void demoReadyDocumentSeedEmitsDeterministicStructurallyValidFiles() throws Exception {
        expectSeedSequence();
        expectSeedSequence();

        var firstResponse = service.seed(request(EnvironmentScenario.DEMO_READY));
        var secondResponse = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(firstResponse.status()).isEqualTo("SUCCESS");
        assertThat(secondResponse.status()).isEqualTo("SUCCESS");
        JsonNode firstSeed = objectMapper.readTree(requestBodies.get(DOCUMENT_SEED_URL).get(0));
        JsonNode secondSeed = objectMapper.readTree(requestBodies.get(DOCUMENT_SEED_URL).get(1));
        JsonNode firstFiles = firstSeed.path("files");
        JsonNode secondFiles = secondSeed.path("files");
        assertThat(firstFiles).isEqualTo(secondFiles);

        List<JsonNode> files = StreamSupport.stream(firstFiles.spliterator(), false).toList();
        List<JsonNode> uploadedFiles = files.stream()
                .filter(file -> "USER_UPLOADED".equals(file.path("source").asText()))
                .toList();
        assertThat(uploadedFiles).hasSize(1);
        JsonNode uploaded = uploadedFiles.get(0);
        assertThat(uploaded.path("fileType").asText()).isEqualTo("DOCX");
        assertThat(uploaded.path("mimeType").asText()).isEqualTo(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(uploaded.path("fileName").asText()).endsWith(".docx");

        byte[] uploadedContent = Base64.getDecoder().decode(uploaded.path("fileContent").asText());
        byte[] repeatedContent = Base64.getDecoder().decode(
                StreamSupport.stream(secondFiles.spliterator(), false)
                        .filter(file -> "USER_UPLOADED".equals(file.path("source").asText()))
                        .findFirst()
                        .orElseThrow()
                        .path("fileContent")
                        .asText());
        assertThat(uploadedContent)
                .isEqualTo(repeatedContent)
                .startsWith((byte) 'P', (byte) 'K', (byte) 3, (byte) 4);

        Map<String, byte[]> entries = readDocxEntries(uploadedContent);
        assertThat(entries.keySet()).containsExactly(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/document.xml");
        assertSafeDocxXml(entries);

        List<JsonNode> generatedFiles = files.stream()
                .filter(file -> "GENERATED".equals(file.path("source").asText()))
                .toList();
        assertThat(generatedFiles).hasSize(18);
        for (JsonNode file : generatedFiles) {
            assertThat(file.path("fileType").asText()).isEqualTo("PDF");
            assertThat(file.path("mimeType").asText()).isEqualTo("application/pdf");
            assertThat(file.path("fileName").asText()).endsWith(".pdf");
            assertSafeGeneratedPdf(
                    Base64.getDecoder().decode(file.path("fileContent").asText()),
                    file.path("fileName").asText());
        }
        server.verify();
    }

    @Test
    void emptyStateClearsOnlyItsOwnApplicationScenarioAndOwnerBoundary() {
        NamedStateDefinition definition = new NamedStateRegistry(objectMapper).require(EnvironmentScenario.EMPTY);
        NamedStateIdentity identity = definition.identities().get(0);
        String emptyUserId = identity.userId(definition.scenarioId());
        assertThat(emptyUserId).isNotEqualTo(USER_ID);
        expectReset(definition);

        var response = service.reset(request(EnvironmentScenario.EMPTY));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(runtimeOwnerUrl(
                "http://localhost:8088",
                definition.scenarioId(),
                identity.key(),
                emptyUserId))
                .doesNotContain("demo-ready-v1", USER_ID);
        server.verify();
    }

    @Test
    void downstreamResetFailureStopsFurtherMutationAndMakesRetryExplicit() {
        server.expect(requestTo(runtimeOwnerUrl(
                        "http://localhost:8088",
                        "demo-ready-v1",
                        "alex-taylor",
                        USER_ID)))
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
        server.expect(requestTo(runtimeOwnerUrl(
                        "http://localhost:8088",
                        "demo-ready-v1",
                        "alex-taylor",
                        USER_ID)))
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
        server.expect(requestTo(runtimeOwnerUrl(
                        "http://localhost:8088",
                        "demo-ready-v1",
                        "alex-taylor",
                        USER_ID)))
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
        expectSuccess(POST, AUTHENTICATION_SEED_URL, "authentication-service", "SEED");
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
    void applicationContractRejectionIsVisibleAndScopedResetAndSeedCanRecover() {
        expectSeedBeforeApplications();
        server.expect(requestTo(APPLICATION_SEED_URL))
                .andExpect(method(POST))
                .andRespond(withBadRequest());
        expectReset(new NamedStateRegistry(objectMapper).require(EnvironmentScenario.DEMO_READY));
        expectSeedSequence();

        var rejected = service.seed(request(EnvironmentScenario.DEMO_READY));
        var recovered = service.prepare(request(EnvironmentScenario.DEMO_READY));

        assertThat(rejected.status()).isEqualTo("FAILED");
        assertThat(rejected.services()).extracting(result -> result.status())
                .containsExactly("SUCCESS", "SUCCESS", "SUCCESS", "SUCCESS", "FAILED");
        assertThat(rejected.warnings()).anyMatch(warning -> warning.contains("reset-and-seed"));
        assertThat(rejected.services().get(4).details()).isEmpty();
        assertThat(recovered.status()).isEqualTo("SUCCESS");
        server.verify();
    }

    @Test
    void unavailableApplicationProducerIsVisibleWithoutLeakingTransportDetails() {
        expectSeedBeforeApplications();
        server.expect(requestTo(APPLICATION_SEED_URL))
                .andExpect(method(POST))
                .andRespond(withServerError());

        var response = service.seed(request(EnvironmentScenario.DEMO_READY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.services()).last().satisfies(result -> {
            assertThat(result.service()).isEqualTo("application-tracker-service");
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.details()).isEmpty();
            assertThat(result.warnings()).containsExactly(
                    "Downstream operation failed; no response details are exposed.");
        });
        assertThat(response.toString()).doesNotContain(APPLICATION_SEED_URL, "HttpServerErrorException");
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
    void crossUserVerificationFailsWhenEitherSyntheticOwnerHasResidualRuntimeState() {
        NamedStateDefinition definition = new NamedStateRegistry(
                new ObjectMapper().findAndRegisterModules())
                .require(EnvironmentScenario.CROSS_USER_SECURITY);
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            expectVerificationResult(
                    "http://localhost:8084/internal/system-data/verify/users/"
                            + userId,
                    "{\"exists\":true}");
            expectVerificationResult(
                    "http://localhost:8085/internal/system-data/verify/profiles/"
                            + userId,
                    "{\"exists\":true}");
            expectVerificationResult(
                    runtimeOwnerUrl(
                            "http://localhost:8088",
                            definition.scenarioId(),
                            identity.key(),
                            userId),
                    "{\"applications\":"
                            + ("claimant-b".equals(identity.key()) ? 1 : 0)
                            + ",\"byStatus\":{}}");
            expectVerificationResult(
                    runtimeOwnerUrl(
                            "http://localhost:8089",
                            definition.scenarioId(),
                            identity.key(),
                            userId),
                    "{\"documents\":0,\"documentVersions\":0}");
            expectVerificationResult(
                    runtimeOwnerUrl(
                            "http://localhost:8099",
                            definition.scenarioId(),
                            identity.key(),
                            userId),
                    "{\"walletExists\":false,\"ledgerEntries\":0,\"balanceTokens\":0}");
        }

        var response = service.verify(
                request(EnvironmentScenario.CROSS_USER_SECURITY));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.summary().applications()).isEqualTo(1);
        assertThat(response.services()).last().satisfies(result -> {
            assertThat(result.service()).isEqualTo("named-state-catalog");
            assertThat(result.status()).isEqualTo("FAILED");
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
            "DUPLICATE_REGISTRATION", "CROSS_USER_SECURITY", "REAL_WORLD_PERSONAS",
            "PROVIDER_FAILURE", "DEMO_READY"})
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

    @Test
    void registrationCleanupUsesTheRuntimeAccountIdCreatedByThePublicJourney() {
        NamedStateDefinition definition = new NamedStateRegistry(new ObjectMapper().findAndRegisterModules())
                .require(EnvironmentScenario.REGISTRATION_CLEAN);
        NamedStateIdentity identity = definition.identities().get(0);
        String runtimeUserId = "c1dfc1da-590e-47b3-9634-db65a2786f42";
        expectRegistrationResolution(identity, true, runtimeUserId);
        expectSuccess(DELETE,
                runtimeOwnerUrl("http://localhost:8088", "registration-clean-v1", "registration-primary", runtimeUserId),
                "application-tracker-service", "RESET");
        expectSuccess(DELETE,
                runtimeOwnerUrl("http://localhost:8089", "registration-clean-v1", "registration-primary", runtimeUserId),
                "document-store-service", "RESET");
        expectSuccess(DELETE,
                runtimeOwnerUrl("http://localhost:8099", "registration-clean-v1", "registration-primary", runtimeUserId),
                "payment-service", "RESET");
        expectSuccess(DELETE,
                "http://localhost:8085/internal/system-data/scenario/registration-clean-v1/profiles/" + runtimeUserId,
                "user-profile-service", "RESET");
        expectSuccess(DELETE,
                "http://localhost:8084/internal/system-data/scenario/registration-clean-v1/users/" + runtimeUserId,
                "authentication-service", "RESET");

        var result = service.reset(request(EnvironmentScenario.REGISTRATION_CLEAN));

        assertThat(result.status()).isEqualTo("SUCCESS");
        server.verify();
    }

    private void expectPrepare(NamedStateDefinition definition) {
        expectReset(definition);
        if (definition.scenario() == EnvironmentScenario.DEMO_READY) {
            expectSeedSequence();
            return;
        }
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : identity.seedComponents()) {
                if ("AUTHENTICATION".equals(component)) {
                    expectSuccess(POST, AUTHENTICATION_SEED_URL, "authentication-service", "SEED");
                } else if ("USER_PROFILE".equals(component)) {
                    expectSuccess(POST, "http://localhost:8085/internal/system-data/seed/profiles/" + userId, "user-profile-service", "SEED");
                }
            }
        }
    }

    private void expectReset(NamedStateDefinition definition) {
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            if (definition.scenario() == EnvironmentScenario.REGISTRATION_CLEAN) {
                expectRegistrationResolution(identity, false, null);
            }
            for (String component : List.of(
                    "APPLICATIONS",
                    "DOCUMENTS",
                    "PAYMENT",
                    "USER_PROFILE",
                    "AUTHENTICATION")) {
                if (!identity.resetComponents().contains(component)) {
                    continue;
                }
                String url = switch (component) {
                    case "APPLICATIONS" -> runtimeOwnerUrl("http://localhost:8088", definition.scenarioId(), identity.key(), userId);
                    case "DOCUMENTS" -> runtimeOwnerUrl("http://localhost:8089", definition.scenarioId(), identity.key(), userId);
                    case "PAYMENT" -> runtimeOwnerUrl("http://localhost:8099", definition.scenarioId(), identity.key(), userId);
                    case "USER_PROFILE" -> "http://localhost:8085/internal/system-data/scenario/" + definition.scenarioId() + "/profiles/" + userId;
                    case "AUTHENTICATION" -> "http://localhost:8084/internal/system-data/scenario/" + definition.scenarioId() + "/users/" + userId;
                    default -> throw new IllegalStateException(component);
                };
                expectSuccess(DELETE, url, "reset", "RESET");
            }
        }
    }

    private void expectRegistrationResolution(
            NamedStateIdentity identity,
            boolean exists,
            String runtimeUserId) {
        String details = exists
                ? "{\"exists\":true,\"userId\":\"" + runtimeUserId + "\"}"
                : "{\"exists\":false}";
        server.expect(requestTo(
                        "http://localhost:8084/internal/system-data/resolve/users?email="
                                + identity.email().replace("@", "%40")))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"status\":\"SUCCESS\",\"recordsAffected\":" + (exists ? 1 : 0)
                                + ",\"details\":" + details + "}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectVerification(NamedStateDefinition definition) {
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : List.of("AUTHENTICATION", "USER_PROFILE", "APPLICATIONS", "DOCUMENTS", "PAYMENT")) {
                if (!identity.resetComponents().contains(component)) continue;
                String url = switch (component) {
                    case "AUTHENTICATION" -> "http://localhost:8084/internal/system-data/verify/users/" + userId;
                    case "USER_PROFILE" -> "http://localhost:8085/internal/system-data/verify/profiles/" + userId;
                    case "APPLICATIONS" -> runtimeOwnerUrl("http://localhost:8088", definition.scenarioId(), identity.key(), userId);
                    case "DOCUMENTS" -> runtimeOwnerUrl("http://localhost:8089", definition.scenarioId(), identity.key(), userId);
                    case "PAYMENT" -> runtimeOwnerUrl("http://localhost:8099", definition.scenarioId(), identity.key(), userId);
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
        expectSeedBeforeApplications();
        expectSuccess(POST, APPLICATION_SEED_URL, "application-tracker-service", "SEED");
    }

    private void expectSeedBeforeApplications() {
        expectSuccess(POST, AUTHENTICATION_SEED_URL, "authentication-service", "SEED");
        expectSuccess(POST, "http://localhost:8085/internal/system-data/seed/profiles/" + USER_ID, "user-profile-service", "SEED");
        expectSuccess(POST, "http://localhost:8099/internal/system-data/seed/payments", "payment-service", "SEED");
        expectSuccess(POST, "http://localhost:8089/internal/system-data/seed/documents", "document-store-service", "SEED");
    }

    private void assertApplicationEnvelope(String body, String documentBody) {
        try {
            JsonNode envelope = objectMapper.readTree(body);
            JsonNode documents = objectMapper.readTree(documentBody).path("documents");
            assertThat(fieldNames(envelope)).containsExactlyInAnyOrder(
                    "schemaVersion", "scenarioId", "userId", "applications");
            assertThat(envelope.path("schemaVersion").asText()).isEqualTo("2.0.0");
            assertThat(envelope.path("scenarioId").asText()).isEqualTo("demo-ready-v1");
            assertThat(envelope.path("userId").asText()).isEqualTo(USER_ID);
            assertThat(envelope.path("applications")).hasSize(9);

            JsonNode first = envelope.path("applications").get(0);
            assertThat(fieldNames(first)).containsExactlyInAnyOrder(
                    "id", "jobId", "canonicalJobId", "provider", "externalJobId",
                    "jobTitle", "companyName", "location", "cvDocumentId",
                    "cvDocumentFamilyId", "cvDocumentVersion", "cvDocumentContentSha256",
                    "coverLetterDocumentId", "coverLetterDocumentFamilyId",
                    "coverLetterDocumentVersion", "coverLetterDocumentContentSha256",
                    "status", "createdAt", "updatedAt", "appliedAt");
            assertThat(first.has("userId")).isFalse();
            assertThat(first.has("fixtureScenarioId")).isFalse();
            assertThat(first.path("id").asText()).isEqualTo(DeterministicIds.uuidString(
                    "demo-ready-v1:application:0:" + first.path("jobId").asText()));
            assertThat(first.path("createdAt").asText()).isEqualTo("2026-06-19T09:00");
            assertThat(first.path("updatedAt").asText()).isEqualTo("2026-06-23T09:00");
            assertThat(first.path("appliedAt").asText()).isEqualTo("2026-06-21T09:00");

            envelope.path("applications").forEach(application -> {
                assertDocumentReference(documents, application, "cv");
                assertDocumentReference(documents, application, "coverLetter");
            });

            Map<String, Long> byStatus = StreamSupport.stream(
                            envelope.path("applications").spliterator(), false)
                    .collect(Collectors.groupingBy(
                            record -> record.path("status").asText(),
                            LinkedHashMap::new,
                            Collectors.counting()));
            assertThat(byStatus).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "OFFER", 1L,
                    "INTERVIEW", 1L,
                    "APPLIED", 2L,
                    "DOCUMENTS_GENERATED", 3L,
                    "UNSUCCESSFUL", 1L,
                    "WITHDRAWN", 1L));
        } catch (Exception exception) {
            throw new AssertionError("Application Tracker seed body was not valid JSON", exception);
        }
    }

    private void assertDocumentReference(JsonNode documents, JsonNode application, String prefix) {
        String id = application.path(prefix + "DocumentId").asText();
        JsonNode document = StreamSupport.stream(documents.spliterator(), false)
                .filter(candidate -> id.equals(candidate.path("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Referenced document was not seeded: " + id));
        assertThat(application.path(prefix + "DocumentFamilyId").asText())
                .isEqualTo(document.path("documentFamilyId").asText());
        assertThat(application.path(prefix + "DocumentVersion").asInt())
                .isEqualTo(document.path("version").asInt());
        assertThat(application.path(prefix + "DocumentContentSha256").asText())
                .matches("[a-f0-9]{64}")
                .isEqualTo(document.path("contentSha256").asText());
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    private void assertCanonicalUtcInstant(JsonNode timestamp) {
        String value = timestamp.asText();
        assertThat(value).endsWith("Z");
        assertThat(Instant.parse(value).toString()).isEqualTo(value);
    }

    private void assertCanonicalLocalDateTime(JsonNode timestamp) {
        String value = timestamp.asText();
        assertThat(value).isNotBlank();
        assertThat(LocalDateTime.parse(value).toString()).isEqualTo(value);
    }

    private Map<String, byte[]> readDocxEntries(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                assertThat(name)
                        .doesNotStartWith("/")
                        .doesNotContain("\\", "../", "/../", "\0");
                ByteArrayOutputStream entryContent = new ByteArrayOutputStream();
                zip.transferTo(entryContent);
                assertThat(entries.put(name, entryContent.toByteArray())).isNull();
                zip.closeEntry();
            }
        }
        return entries;
    }

    private void assertSafeDocxXml(Map<String, byte[]> entries) throws Exception {
        Document contentTypes = parseSafeXml(entries.get("[Content_Types].xml"));
        assertThat(contentTypes.getDocumentElement().getLocalName()).isEqualTo("Types");
        NodeList overrides = contentTypes.getElementsByTagNameNS("*", "Override");
        boolean hasMainDocument = false;
        for (int index = 0; index < overrides.getLength(); index++) {
            Element override = (Element) overrides.item(index);
            String contentType = override.getAttribute("ContentType");
            assertThat(contentType.toLowerCase())
                    .doesNotContain("macroenabled", "activex", "oleobject");
            if ("/word/document.xml".equals(override.getAttribute("PartName"))
                    && "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                            .equals(contentType)) {
                hasMainDocument = true;
            }
        }
        assertThat(hasMainDocument).isTrue();

        Document relationships = parseSafeXml(entries.get("_rels/.rels"));
        NodeList relationshipNodes =
                relationships.getElementsByTagNameNS("*", "Relationship");
        assertThat(relationshipNodes.getLength()).isEqualTo(1);
        Element relationship = (Element) relationshipNodes.item(0);
        assertThat(relationship.getAttribute("Target")).isEqualTo("word/document.xml");
        assertThat(relationship.getAttribute("TargetMode"))
                .doesNotContainIgnoringCase("external");

        Document document = parseSafeXml(entries.get("word/document.xml"));
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("document");
        assertThat(document.getElementsByTagNameNS("*", "altChunk").getLength()).isZero();
        assertThat(document.getElementsByTagNameNS("*", "object").getLength()).isZero();
        assertThat(document.getElementsByTagNameNS("*", "control").getLength()).isZero();
        assertThat(document.getDocumentElement().getTextContent())
                .contains("Alex Taylor revised CV");
    }

    private void assertSafeGeneratedPdf(byte[] content, String expectedFileName) throws Exception {
        assertThat(content)
                .startsWith("%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        try (RandomAccessReadBuffer source = new RandomAccessReadBuffer(content);
                PDDocument document = new PDFParser(source).parse(false)) {
            assertThat(document.isEncrypted()).isFalse();
            assertThat(document.getVersion()).isEqualTo(1.4f);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getDocument().getXrefTable()).hasSize(5);
            assertThat(document.getDocument().getHighestXRefObjectNumber()).isEqualTo(5L);
            assertThat(document.getDocument().getTrailer().keySet())
                    .doesNotContain(COSName.ENCRYPT);
            assertThat(document.getDocumentCatalog().getCOSObject().keySet())
                    .doesNotContain(
                            COSName.AA,
                            COSName.OPEN_ACTION,
                            COSName.ACRO_FORM,
                            COSName.NAMES);
            assertThat(document.getPage(0).getAnnotations()).isEmpty();
            assertThat(document.getPage(0).getCOSObject().keySet())
                    .doesNotContain(COSName.AA);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Job Seeker Copilot demo fixture", expectedFileName);
        }
    }

    private Document parseSafeXml(byte[] content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private String applicationScenarioUrl(String scenarioId, String userId) {
        return "http://localhost:8088/internal/system-data/v1/application-scenarios/"
                + scenarioId + "/owners/" + userId;
    }

    private String runtimeOwnerUrl(
            String baseUrl,
            String scenarioId,
            String identityKey,
            String userId) {
        return baseUrl
                + "/internal/system-data/v1/runtime-owners/"
                + scenarioId
                + "/identities/"
                + identityKey
                + "/owners/"
                + userId;
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

    private void expectVerificationResult(String url, String details) {
        server.expect(requestTo(url))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"status\":\"SUCCESS\",\"recordsAffected\":0,\"details\":"
                                + details
                                + "}",
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

}
