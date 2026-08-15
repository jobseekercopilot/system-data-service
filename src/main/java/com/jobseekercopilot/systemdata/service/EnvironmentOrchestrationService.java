package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationResponse;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.model.EnvironmentServiceResult;
import com.jobseekercopilot.systemdata.model.EnvironmentSummary;
import com.jobseekercopilot.systemdata.model.NamedStateDefinition;
import com.jobseekercopilot.systemdata.model.NamedStateIdentity;
import com.jobseekercopilot.systemdata.model.SystemDataApplicationSeedRequest;
import com.jobseekercopilot.systemdata.util.DeterministicIds;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class EnvironmentOrchestrationService {
    private static final String DEFAULT_DATASET_ID = "uk-software-developer-demo";
    private static final String DEFAULT_DATASET_VERSION = "1.1.0";

    private final SystemDataProperties properties;
    private final DatasetStorageService datasetStorageService;
    private final EnvironmentManagementGuard guard;
    private final DemoEnvironmentScenarioBuilder scenarioBuilder;
    private final GovernedFixtureValidator fixtureValidator;
    private final NamedStateRegistry stateRegistry;
    private final PersonaCatalog personaCatalog;
    private final RestTemplate restTemplate;

    public EnvironmentOrchestrationService(
            SystemDataProperties properties,
            DatasetStorageService datasetStorageService,
            EnvironmentManagementGuard guard,
            DemoEnvironmentScenarioBuilder scenarioBuilder,
            GovernedFixtureValidator fixtureValidator,
            NamedStateRegistry stateRegistry,
            PersonaCatalog personaCatalog,
            @Qualifier("environmentManagementRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.datasetStorageService = datasetStorageService;
        this.guard = guard;
        this.scenarioBuilder = scenarioBuilder;
        this.fixtureValidator = fixtureValidator;
        this.stateRegistry = stateRegistry;
        this.personaCatalog = personaCatalog;
        this.restTemplate = restTemplate;
    }

    public EnvironmentOperationResponse reset(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        NamedStateDefinition definition = definition(request);
        EnvironmentScenario scenario = definition.scenario();
        List<EnvironmentServiceResult> services = resetServices(definition);
        List<String> warnings = hasFailed(services)
                ? List.of("Reset stopped after a downstream failure; retry is safe after the dependency recovers.")
                : List.of();
        return response("reset", scenario, startedAt, services, summaryForReset(services), warnings);
    }

    public EnvironmentOperationResponse seed(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        NamedStateDefinition definition = definition(request);
        EnvironmentScenario scenario = definition.scenario();
        SeedResult seedResult = seedServices(definition, request);
        List<EnvironmentServiceResult> services = seedResult.services();
        List<String> warnings = new ArrayList<>(seedResult.warnings());
        if (hasFailed(services)) {
            warnings.add("Seed stopped after a downstream failure; retry with reset-and-seed after the dependency recovers.");
        }
        EnvironmentSummary summary = hasFailed(services) ? summaryForReset(services) : summaryForDefinition(definition, seedResult.demoScenario());
        return response("seed", scenario, startedAt, services, summary, warnings);
    }

    public EnvironmentOperationResponse resetAndSeed(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        NamedStateDefinition definition = definition(request);
        EnvironmentScenario scenario = definition.scenario();
        List<EnvironmentServiceResult> services = new ArrayList<>();
        services.addAll(resetServices(definition));
        List<String> warnings = new ArrayList<>();
        EnvironmentSummary summary = summaryForReset(services);
        if (hasFailed(services)) {
            warnings.add("Seed phase skipped because reset did not complete; retry is safe after the dependency recovers.");
        } else {
            SeedResult seedResult = seedServices(definition, request);
            services.addAll(seedResult.services());
            warnings.addAll(seedResult.warnings());
            if (hasFailed(seedResult.services())) {
                warnings.add("Seed stopped after a downstream failure; retry with reset-and-seed after the dependency recovers.");
            } else {
                summary = summaryForDefinition(definition, seedResult.demoScenario());
            }
        }
        return response("reset-and-seed", scenario, startedAt, services, summary, warnings);
    }

    public EnvironmentOperationResponse verify(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        NamedStateDefinition definition = definition(request);
        EnvironmentScenario scenario = definition.scenario();
        List<EnvironmentServiceResult> services = new ArrayList<>(verifyServices(definition));
        EnvironmentSummary summary = summaryFromVerification(services);
        boolean expected = matchesExpected(definition, summary);
        services.add(new EnvironmentServiceResult(
                "named-state-catalog",
                "VERIFY",
                expected ? "SUCCESS" : "FAILED",
                0,
                Map.of("expected", definition.expected(), "actual", summaryMap(summary)),
                expected ? List.of() : List.of("Prepared state does not match its named-state definition.")));
        return response("verify", scenario, startedAt, services, summary, List.of());
    }

    public EnvironmentOperationResponse prepare(EnvironmentOperationRequest request) {
        EnvironmentOperationResponse result = resetAndSeed(request);
        return new EnvironmentOperationResponse(result.operationId(), result.scenario(), result.status(),
                result.startedAt(), result.completedAt(), result.services(), result.summary(), result.warnings());
    }

    public List<NamedStateDefinition> listStates() {
        guard.requireEnabled();
        return stateRegistry.list();
    }

    public NamedStateDefinition describe(EnvironmentScenario scenario) {
        guard.requireEnabled();
        return stateRegistry.require(scenario);
    }

    public Map<String, Object> status() {
        guard.requireEnabled();
        return Map.of(
                "status", "ENABLED",
                "activeEnvironment", guard.activeEnvironment(),
                "environmentManagementEnabled", true,
                "supportedScenarios", stateRegistry.list().stream().map(NamedStateDefinition::scenario).toList(),
                "defaultDatasetId", DEFAULT_DATASET_ID,
                "defaultDatasetVersion", DEFAULT_DATASET_VERSION);
    }

    private List<EnvironmentServiceResult> resetServices(NamedStateDefinition definition) {
        List<DownstreamOperation> operations = new ArrayList<>();
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            if (definition.scenario() == EnvironmentScenario.REGISTRATION_CLEAN) {
                userId = resolveRuntimeRegistrationUserId(identity, userId);
                if (userId == null) {
                    return List.of(failure("authentication-service", "RESOLVE"));
                }
            }
            addResetOperations(operations, definition.scenarioId(), identity, userId);
        }
        return executeFailFast(operations);
    }

    private String resolveRuntimeRegistrationUserId(NamedStateIdentity identity, String deterministicUserId) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        String encodedEmail = URLEncoder.encode(identity.email(), StandardCharsets.UTF_8);
        try {
            URI resolveUrl = URI.create(
                    targets.getAuthentication() + "/internal/system-data/resolve/users?email=" + encodedEmail);
            ResponseEntity<Map> response = restTemplate.getForEntity(resolveUrl, Map.class);
            Map body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || !"SUCCESS".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                return null;
            }
            Object detailsValue = body.get("details");
            if (!(detailsValue instanceof Map<?, ?> details)
                    || !Boolean.TRUE.equals(details.get("exists"))) {
                return deterministicUserId;
            }
            Object runtimeUserId = details.get("userId");
            return runtimeUserId instanceof String value && value.matches("[a-f0-9-]{36}")
                    ? value
                    : null;
        } catch (RestClientException exception) {
            return null;
        }
    }

    private void addResetOperations(
            List<DownstreamOperation> operations,
            String scenarioId,
            NamedStateIdentity identity,
            String userId) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        for (String component : List.of(
                "APPLICATIONS",
                "DOCUMENTS",
                "PAYMENT",
                "USER_PROFILE",
                "AUTHENTICATION")) {
            if (!identity.resetComponents().contains(component)) {
                continue;
            }
            switch (component) {
                case "APPLICATIONS" -> operations.add(operation("application-tracker-service", () -> delete("application-tracker-service", runtimeOwnerUrl(targets.getApplicationTracker(), scenarioId, identity.key(), userId))));
                case "DOCUMENTS" -> operations.add(operation("document-store-service", () -> delete("document-store-service", runtimeOwnerUrl(targets.getDocumentStore(), scenarioId, identity.key(), userId))));
                case "PAYMENT" -> {
                    if ("payment-acceptance-v1".equals(scenarioId)) {
                        operations.add(operation("stripe-gateway", () -> delete(
                                "stripe-gateway",
                                targets.getStripeGateway()
                                        + "/internal/fixtures/v2/stripe/owners/" + userId)));
                    }
                    operations.add(operation("payment-service", () -> delete(
                            "payment-service",
                            runtimeOwnerUrl(targets.getPayment(), scenarioId, identity.key(), userId))));
                }
                case "USER_PROFILE" -> operations.add(operation("user-profile-service", () -> delete("user-profile-service", targets.getUserProfile() + "/internal/system-data/scenario/" + scenarioId + "/profiles/" + userId)));
                case "AUTHENTICATION" -> operations.add(operation("authentication-service", () -> delete("authentication-service", targets.getAuthentication() + "/internal/system-data/scenario/" + scenarioId + "/users/" + userId)));
                default -> throw new IllegalStateException("Unsupported named-state component");
            }
        }
    }

    private SeedResult seedServices(NamedStateDefinition definition, EnvironmentOperationRequest request) {
        if (definition.identities().isEmpty()) {
            return new SeedResult(List.of(), null, List.of());
        }
        if (definition.scenario() == EnvironmentScenario.DEMO_READY) {
            DemoEnvironmentScenario scenario = buildScenario(request, definition);
            return new SeedResult(seedDemoServices(scenario), scenario, scenario.warnings());
        }
        List<DownstreamOperation> operations = new ArrayList<>();
        for (NamedStateIdentity identity : definition.identities()) {
            addSyntheticIdentitySeedOperations(operations, definition.scenarioId(), identity);
        }
        return new SeedResult(executeFailFast(operations), null, List.of());
    }

    private List<EnvironmentServiceResult> seedDemoServices(DemoEnvironmentScenario scenario) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        return executeFailFast(List.of(
                operation("authentication-service", () -> post("authentication-service", targets.getAuthentication() + "/internal/system-data/seed/user", scenario.user())),
                operation("user-profile-service", () -> post("user-profile-service", targets.getUserProfile() + "/internal/system-data/seed/profiles/" + scenario.userId(), scenario.profile())),
                operation("payment-service", () -> post("payment-service", targets.getPayment() + "/internal/system-data/seed/payments", scenario.payment())),
                operation("document-store-service", () -> post("document-store-service", targets.getDocumentStore() + "/internal/system-data/seed/documents", scenario.documents())),
                operation("application-tracker-service", () -> post(
                        "application-tracker-service",
                        targets.getApplicationTracker() + "/internal/system-data/v1/application-scenarios",
                        new SystemDataApplicationSeedRequest(
                                SystemDataApplicationSeedRequest.SCHEMA_VERSION,
                                scenario.scenarioId(),
                                UUID.fromString(scenario.userId()),
                                scenario.applications())))));
    }

    private void addSyntheticIdentitySeedOperations(List<DownstreamOperation> operations, String scenarioId,
                                                     NamedStateIdentity identity) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        String userId = identity.userId(scenarioId);
        Map<String, Object> user = map(
                "scenarioId", scenarioId, "userId", userId, "name", identity.displayName(),
                "email", identity.email(), "password", "PublicTestPassword123!", "syntheticIdentity", true);
        Map<String, Object> profile = scenarioId.equals("real-world-personas-v2")
                ? personaCatalog.profile(identity.key(), userId)
                : map(
                        "userId", userId, "skills", List.of("Java", "Testing"),
                        "aspirations", map("targetRoles", List.of("Software Developer"), "targetWeeklyHours", "FULL_TIME"),
                        "workPreferences", map("location", map("postcode", "RG1 1AA", "region", "South East", "adminDistrict", "Reading"), "commuteRange", 25),
                        "qualifications", List.of(), "roles", List.of());
        for (String component : identity.seedComponents()) {
            switch (component) {
                case "AUTHENTICATION" -> operations.add(operation("authentication-service", () -> post("authentication-service", targets.getAuthentication() + "/internal/system-data/seed/user", user)));
                case "USER_PROFILE" -> operations.add(operation("user-profile-service", () -> post("user-profile-service", targets.getUserProfile() + "/internal/system-data/seed/profiles/" + userId, profile)));
                case "PAYMENT" -> operations.add(operation("payment-service", () -> post(
                        "payment-service",
                        targets.getPayment()
                                + "/internal/system-data/v2/runtime-owners/"
                                + scenarioId + "/identities/" + identity.key()
                                + "/owners/" + userId + "/document-credit-wallet",
                        Map.of())));
                default -> throw new IllegalStateException("Non-demo named state cannot seed persistent application data");
            }
        }
    }

    private List<EnvironmentServiceResult> executeFailFast(List<DownstreamOperation> operations) {
        List<EnvironmentServiceResult> results = new ArrayList<>();
        boolean failed = false;
        for (DownstreamOperation operation : operations) {
            if (failed) {
                results.add(EnvironmentServiceResult.skipped(operation.service(), "Skipped after an earlier downstream failure."));
                continue;
            }
            EnvironmentServiceResult result = operation.action().get();
            results.add(result);
            failed = !"SUCCESS".equals(result.status());
        }
        return results;
    }

    private DownstreamOperation operation(String service, Supplier<EnvironmentServiceResult> action) {
        return new DownstreamOperation(service, action);
    }

    private boolean hasFailed(List<EnvironmentServiceResult> services) {
        return services.stream().anyMatch(result -> "FAILED".equals(result.status()));
    }

    private NamedStateDefinition definition(EnvironmentOperationRequest request) {
        return stateRegistry.require(scenario(request));
    }

    private EnvironmentScenario scenario(EnvironmentOperationRequest request) {
        if (request == null || request.scenario() == null) {
            return EnvironmentScenario.EMPTY;
        }
        return request.scenario();
    }

    private DemoEnvironmentScenario buildScenario(EnvironmentOperationRequest request, NamedStateDefinition definition) {
        String datasetId = request == null || request.datasetId() == null || request.datasetId().isBlank()
                ? definition.datasetId() : request.datasetId();
        String datasetVersion = request == null || request.datasetVersion() == null || request.datasetVersion().isBlank()
                ? definition.datasetVersion() : request.datasetVersion();
        Instant referenceDate = request == null || request.referenceDate() == null
                ? definition.referenceDate() : request.referenceDate();
        Path datasetDirectory = datasetStorageService.datasetVersionDirectory(datasetId, datasetVersion);
        fixtureValidator.requireApproved(datasetDirectory);
        DatasetGenerationResult dataset = datasetStorageService.read(datasetDirectory);
        return scenarioBuilder.build(dataset.jobs(), referenceDate);
    }

    private List<EnvironmentServiceResult> verifyServices(NamedStateDefinition definition) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        List<DownstreamOperation> operations = new ArrayList<>();
        for (NamedStateIdentity identity : definition.identities()) {
            String userId = identity.userId(definition.scenarioId());
            for (String component : List.of("AUTHENTICATION", "USER_PROFILE", "APPLICATIONS", "DOCUMENTS", "PAYMENT")) {
                if (!identity.resetComponents().contains(component)) {
                    continue;
                }
                switch (component) {
                    case "AUTHENTICATION" -> operations.add(operation("authentication-service", () -> get("authentication-service", targets.getAuthentication() + "/internal/system-data/verify/users/" + userId)));
                    case "USER_PROFILE" -> operations.add(operation("user-profile-service", () -> get("user-profile-service", targets.getUserProfile() + "/internal/system-data/verify/profiles/" + userId)));
                    case "APPLICATIONS" -> operations.add(operation("application-tracker-service", () -> get("application-tracker-service", runtimeOwnerUrl(targets.getApplicationTracker(), definition.scenarioId(), identity.key(), userId))));
                    case "DOCUMENTS" -> operations.add(operation("document-store-service", () -> get("document-store-service", runtimeOwnerUrl(targets.getDocumentStore(), definition.scenarioId(), identity.key(), userId))));
                    case "PAYMENT" -> {
                        operations.add(operation("payment-service", () -> get(
                                "payment-service",
                                runtimeOwnerUrl(targets.getPayment(), definition.scenarioId(), identity.key(), userId))));
                        if (definition.scenario() == EnvironmentScenario.PAYMENT_ACCEPTANCE) {
                            operations.add(operation("stripe-gateway", () -> get(
                                    "stripe-gateway",
                                    targets.getStripeGateway()
                                            + "/internal/fixtures/v2/stripe/owners/" + userId)));
                        }
                    }
                    default -> throw new IllegalStateException("Unsupported named-state component");
                }
            }
        }
        return executeFailFast(operations);
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

    private EnvironmentOperationResponse response(String operation, EnvironmentScenario scenario, Instant startedAt, List<EnvironmentServiceResult> services, EnvironmentSummary summary, List<String> warnings) {
        boolean failed = services.stream().anyMatch(result -> "FAILED".equals(result.status()));
        return new EnvironmentOperationResponse(
                DeterministicIds.uuidString("environment:" + operation + ":" + scenario + ":" + startedAt),
                scenario,
                failed ? "FAILED" : "SUCCESS",
                startedAt,
                Instant.now(),
                services,
                summary,
                warnings);
    }

    private EnvironmentServiceResult post(String service, String url, Object body) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
            return resultFrom(service, "SEED", response);
        } catch (RestClientException exception) {
            return failure(service, "SEED");
        }
    }

    public Map<String, Object> emitFixturePaymentEvent(
            String providerSessionId, Map<String, Object> request) {
        guard.requireEnabled();
        if (providerSessionId == null
                || !providerSessionId.matches("cs_fixture_[a-f0-9]{32}")
                || request == null
                || request.size() != 1
                || !(request.get("event") instanceof String event)
                || !("COMPLETED".equals(event) || "EXPIRED".equals(event))) {
            throw new IllegalArgumentException("Invalid fixture payment event request");
        }
        String url = properties.getEnvironmentManagement().getTargetServices()
                .getStripeGateway()
                + "/internal/fixtures/v2/stripe/checkout-sessions/"
                + providerSessionId + "/events";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {
                throw new IllegalStateException("Fixture payment settlement failed");
            }
            return new LinkedHashMap<>((Map<String, Object>) response.getBody());
        } catch (RestClientException exception) {
            throw new IllegalStateException("Fixture payment settlement failed", exception);
        }
    }

    private EnvironmentServiceResult delete(String service, String url) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
            return resultFrom(service, "RESET", response);
        } catch (RestClientException exception) {
            return failure(service, "RESET");
        }
    }

    private EnvironmentServiceResult get(String service, String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return resultFrom(service, "VERIFY", response);
        } catch (RestClientException exception) {
            return failure(service, "VERIFY");
        }
    }

    private EnvironmentServiceResult resultFrom(String service, String operation, ResponseEntity<Map> response) {
        Map body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            return failure(service, operation);
        }
        try {
            if (!"SUCCESS".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                return failure(service, operation);
            }
            Object affected = body.getOrDefault("recordsAffected", 0);
            if (!(affected instanceof Number recordsAffected)) {
                return failure(service, operation);
            }
            Object details = body.get("details");
            Object warnings = body.get("warnings");
            return new EnvironmentServiceResult(
                    service,
                    operation,
                    "SUCCESS",
                    recordsAffected.intValue(),
                    details instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of(),
                    warnings instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of());
        } catch (RuntimeException exception) {
            return failure(service, operation);
        }
    }

    private EnvironmentServiceResult failure(String service, String operation) {
        return new EnvironmentServiceResult(service, operation, "FAILED", 0, Map.of(),
                List.of("Downstream operation failed; no response details are exposed."));
    }

    private EnvironmentSummary summaryForSeed(DemoEnvironmentScenario scenario) {
        Map<String, Object> payment = scenario.payment();
        Map<String, Object> wallet = (Map<String, Object>) payment.get("wallet");
        List<?> transactions = (List<?>) payment.get("transactions");
        Map<String, Object> documents = scenario.documents();
        List<?> documentList = (List<?>) documents.get("documents");
        return new EnvironmentSummary(
                1,
                1,
                scenario.applications().size(),
                documentList.size(),
                documentList.size(),
                transactions.size(),
                0,
                ((Number) wallet.get("balanceTokens")).longValue(),
                scenario.applicationsByStatus());
    }

    private EnvironmentSummary summaryForDefinition(NamedStateDefinition definition, DemoEnvironmentScenario demo) {
        if (demo != null) {
            return summaryForSeed(demo);
        }
        return new EnvironmentSummary(
                expectedInt(definition, "users"),
                expectedInt(definition, "profiles"),
                expectedInt(definition, "applications"),
                expectedInt(definition, "documents"),
                expectedInt(definition, "documentVersions"),
                expectedInt(definition, "ledgerEntries"),
                0,
                0L,
                Map.of());
    }

    private int expectedInt(NamedStateDefinition definition, String key) {
        Object value = definition.expected().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean matchesExpected(NamedStateDefinition definition, EnvironmentSummary summary) {
        Map<String, Integer> actual = Map.of(
                "users", summary.users(),
                "profiles", summary.profiles(),
                "applications", summary.applications(),
                "documents", summary.documents(),
                "documentVersions", summary.documentVersions(),
                "ledgerEntries", summary.creditLedgerEntries());
        return actual.entrySet().stream().allMatch(entry -> {
            Object expected = definition.expected().get(entry.getKey());
            return !(expected instanceof Number number) || number.intValue() == entry.getValue();
        });
    }

    private Map<String, Object> summaryMap(EnvironmentSummary summary) {
        return map(
                "users", summary.users(),
                "profiles", summary.profiles(),
                "applications", summary.applications(),
                "documents", summary.documents(),
                "documentVersions", summary.documentVersions(),
                "ledgerEntries", summary.creditLedgerEntries());
    }

    private EnvironmentSummary summaryForReset(List<EnvironmentServiceResult> services) {
        return new EnvironmentSummary(0, 0, 0, 0, 0, 0, 0, 0L, Map.of());
    }

    private EnvironmentSummary summaryFromVerification(List<EnvironmentServiceResult> services) {
        int users = countPresent(services, "authentication-service", "exists");
        int profiles = countPresent(services, "user-profile-service", "exists");
        int applications = intDetail(services, "application-tracker-service", "applications");
        int documents = intDetail(services, "document-store-service", "documents");
        int versions = intDetail(services, "document-store-service", "documentVersions");
        int ledger = intDetail(services, "payment-service", "ledgerEntries");
        Long balance = longDetail(services, "payment-service", "balanceTokens");
        return new EnvironmentSummary(users, profiles, applications, documents, versions, ledger, 0, balance, applicationsByStatus(services));
    }

    private int countPresent(List<EnvironmentServiceResult> services, String serviceName, String key) {
        return (int) services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .filter(result -> Boolean.TRUE.equals(result.details().get(key))
                        || Boolean.TRUE.equals(result.details().get("walletExists")))
                .count();
    }

    private int intDetail(List<EnvironmentServiceResult> services, String serviceName, String key) {
        return services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .map(result -> result.details().get(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .sum();
    }

    private Long longDetail(List<EnvironmentServiceResult> services, String serviceName, String key) {
        return services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .map(result -> result.details().get(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .sum();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> applicationsByStatus(List<EnvironmentServiceResult> services) {
        Map<String, Integer> statuses = new LinkedHashMap<>();
        services.stream()
                .filter(result -> "application-tracker-service".equals(result.service()))
                .map(result -> result.details().get("byStatus"))
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .forEach(rawStatuses -> rawStatuses.forEach((key, value) -> {
                    if (value instanceof Number number) {
                        statuses.merge(
                                key.toString(), number.intValue(), Integer::sum);
                    }
                }));
        return statuses;
    }

    private record DownstreamOperation(String service, Supplier<EnvironmentServiceResult> action) {
    }

    private record SeedResult(
            List<EnvironmentServiceResult> services,
            DemoEnvironmentScenario demoScenario,
            List<String> warnings) {
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }
}
