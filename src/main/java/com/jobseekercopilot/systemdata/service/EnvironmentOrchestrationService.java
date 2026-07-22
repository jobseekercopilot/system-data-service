package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationResponse;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.model.EnvironmentServiceResult;
import com.jobseekercopilot.systemdata.model.EnvironmentSummary;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class EnvironmentOrchestrationService {
    private static final String DEFAULT_DATASET_ID = "uk-software-developer-demo";
    private static final String DEFAULT_DATASET_VERSION = "1.0.0";
    private static final Instant DEFAULT_REFERENCE_DATE = Instant.parse("2026-07-10T09:00:00Z");

    private final SystemDataProperties properties;
    private final DatasetStorageService datasetStorageService;
    private final EnvironmentManagementGuard guard;
    private final DemoEnvironmentScenarioBuilder scenarioBuilder;
    private final GovernedFixtureValidator fixtureValidator;
    private final RestTemplate restTemplate;

    public EnvironmentOrchestrationService(
            SystemDataProperties properties,
            DatasetStorageService datasetStorageService,
            EnvironmentManagementGuard guard,
            DemoEnvironmentScenarioBuilder scenarioBuilder,
            GovernedFixtureValidator fixtureValidator,
            @Qualifier("environmentManagementRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.datasetStorageService = datasetStorageService;
        this.guard = guard;
        this.scenarioBuilder = scenarioBuilder;
        this.fixtureValidator = fixtureValidator;
        this.restTemplate = restTemplate;
    }

    public EnvironmentOperationResponse reset(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        String userId = scenarioBuilder.demoUserId();
        List<EnvironmentServiceResult> services = resetServices(scenarioId(scenario), userId);
        List<String> warnings = hasFailed(services)
                ? List.of("Reset stopped after a downstream failure; retry is safe after the dependency recovers.")
                : List.of();
        return response("reset", scenario, startedAt, services, summaryForReset(services), warnings);
    }

    public EnvironmentOperationResponse seed(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        if (scenario != EnvironmentScenario.DEMO_READY) {
            return response("seed", scenario, startedAt, List.of(), summaryForReset(List.of()), List.of("EMPTY has no seed payload."));
        }
        DemoEnvironmentScenario demoScenario = buildScenario(request);
        List<EnvironmentServiceResult> services = seedServices(demoScenario);
        List<String> warnings = new ArrayList<>(demoScenario.warnings());
        if (hasFailed(services)) {
            warnings.add("Seed stopped after a downstream failure; retry with reset-and-seed after the dependency recovers.");
        }
        EnvironmentSummary summary = hasFailed(services) ? summaryForReset(services) : summaryForSeed(demoScenario);
        return response("seed", scenario, startedAt, services, summary, warnings);
    }

    public EnvironmentOperationResponse resetAndSeed(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        List<EnvironmentServiceResult> services = new ArrayList<>();
        services.addAll(resetServices(scenarioId(scenario), scenarioBuilder.demoUserId()));
        List<String> warnings = new ArrayList<>();
        EnvironmentSummary summary = summaryForReset(services);
        if (hasFailed(services)) {
            warnings.add("Seed phase skipped because reset did not complete; retry is safe after the dependency recovers.");
        } else if (scenario == EnvironmentScenario.DEMO_READY) {
            DemoEnvironmentScenario demoScenario = buildScenario(request);
            List<EnvironmentServiceResult> seedResults = seedServices(demoScenario);
            services.addAll(seedResults);
            warnings.addAll(demoScenario.warnings());
            if (hasFailed(seedResults)) {
                warnings.add("Seed stopped after a downstream failure; retry with reset-and-seed after the dependency recovers.");
            } else {
                summary = summaryForSeed(demoScenario);
            }
        }
        return response("reset-and-seed", scenario, startedAt, services, summary, warnings);
    }

    public EnvironmentOperationResponse verify(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        String userId = scenarioBuilder.demoUserId();
        List<EnvironmentServiceResult> services = List.of(
                get("authentication-service", properties.getEnvironmentManagement().getTargetServices().getAuthentication() + "/internal/system-data/verify/users/" + userId),
                get("user-profile-service", properties.getEnvironmentManagement().getTargetServices().getUserProfile() + "/internal/system-data/verify/profiles/" + userId),
                get("application-tracker-service", properties.getEnvironmentManagement().getTargetServices().getApplicationTracker() + "/internal/system-data/verify/applications/" + userId),
                get("document-store-service", properties.getEnvironmentManagement().getTargetServices().getDocumentStore() + "/internal/system-data/verify/documents/" + userId),
                get("payment-service", properties.getEnvironmentManagement().getTargetServices().getPayment() + "/internal/system-data/verify/payments/" + userId),
                EnvironmentServiceResult.skipped("cv-cover-letter-service", "Stateless orchestration service; generated documents are stored in document-store-service."),
                EnvironmentServiceResult.skipped("reporting-service", "Current implementation derives reporting from application/profile services and does not persist activity records."));
        return response("verify", scenario, startedAt, services, summaryFromVerification(services), List.of());
    }

    public Map<String, Object> status() {
        guard.requireEnabled();
        return Map.of(
                "status", "ENABLED",
                "activeEnvironment", guard.activeEnvironment(),
                "environmentManagementEnabled", true,
                "supportedScenarios", List.of(EnvironmentScenario.EMPTY, EnvironmentScenario.DEMO_READY),
                "defaultDatasetId", DEFAULT_DATASET_ID,
                "defaultDatasetVersion", DEFAULT_DATASET_VERSION);
    }

    private List<EnvironmentServiceResult> resetServices(String scenarioId, String userId) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        List<EnvironmentServiceResult> results = executeFailFast(List.of(
                operation("payment-service", () -> delete("payment-service", targets.getPayment() + "/internal/system-data/scenario/" + scenarioId + "/payments/" + userId)),
                operation("document-store-service", () -> delete("document-store-service", targets.getDocumentStore() + "/internal/system-data/scenario/" + scenarioId + "/documents/" + userId)),
                operation("application-tracker-service", () -> delete("application-tracker-service", targets.getApplicationTracker() + "/internal/system-data/scenario/" + scenarioId + "/applications/" + userId)),
                operation("user-profile-service", () -> delete("user-profile-service", targets.getUserProfile() + "/internal/system-data/scenario/" + scenarioId + "/profiles/" + userId)),
                operation("authentication-service", () -> delete("authentication-service", targets.getAuthentication() + "/internal/system-data/scenario/" + scenarioId + "/users/" + userId))));
        results.add(EnvironmentServiceResult.skipped("cv-cover-letter-service", "No persistence discovered."));
        results.add(EnvironmentServiceResult.skipped("reporting-service", "No persistence discovered in current source."));
        return results;
    }

    private List<EnvironmentServiceResult> seedServices(DemoEnvironmentScenario scenario) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        List<EnvironmentServiceResult> results = executeFailFast(List.of(
                operation("authentication-service", () -> post("authentication-service", targets.getAuthentication() + "/internal/system-data/seed/user", scenario.user())),
                operation("user-profile-service", () -> post("user-profile-service", targets.getUserProfile() + "/internal/system-data/seed/profiles/" + scenario.userId(), scenario.profile())),
                operation("payment-service", () -> post("payment-service", targets.getPayment() + "/internal/system-data/seed/payments", scenario.payment())),
                operation("document-store-service", () -> post("document-store-service", targets.getDocumentStore() + "/internal/system-data/seed/documents", scenario.documents())),
                operation("application-tracker-service", () -> post("application-tracker-service", targets.getApplicationTracker() + "/internal/system-data/seed/applications", scenario.applications()))));
        results.add(EnvironmentServiceResult.skipped("cv-cover-letter-service", "No persistent generation records discovered; document-store-service owns document records."));
        results.add(EnvironmentServiceResult.skipped("reporting-service", "No persistent activity repository discovered; E2E can verify aggregate state through this endpoint."));
        return results;
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

    private EnvironmentScenario scenario(EnvironmentOperationRequest request) {
        if (request == null || request.scenario() == null) {
            return EnvironmentScenario.EMPTY;
        }
        return request.scenario();
    }

    private String scenarioId(EnvironmentScenario scenario) {
        return scenario == EnvironmentScenario.DEMO_READY ? "demo-ready-v1" : "empty";
    }

    private DemoEnvironmentScenario buildScenario(EnvironmentOperationRequest request) {
        String datasetId = request == null || request.datasetId() == null || request.datasetId().isBlank() ? DEFAULT_DATASET_ID : request.datasetId();
        String datasetVersion = request == null || request.datasetVersion() == null || request.datasetVersion().isBlank() ? DEFAULT_DATASET_VERSION : request.datasetVersion();
        Instant referenceDate = request == null || request.referenceDate() == null ? DEFAULT_REFERENCE_DATE : request.referenceDate();
        Path datasetDirectory = datasetStorageService.datasetVersionDirectory(datasetId, datasetVersion);
        fixtureValidator.requireApproved(datasetDirectory);
        DatasetGenerationResult dataset = datasetStorageService.read(datasetDirectory);
        return scenarioBuilder.build(dataset.jobs(), referenceDate);
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

    private EnvironmentSummary summaryForReset(List<EnvironmentServiceResult> services) {
        return new EnvironmentSummary(0, 0, 0, 0, 0, 0, 0, 0L, Map.of());
    }

    private EnvironmentSummary summaryFromVerification(List<EnvironmentServiceResult> services) {
        int users = present(services, "authentication-service") ? 1 : 0;
        int profiles = present(services, "user-profile-service") ? 1 : 0;
        int applications = intDetail(services, "application-tracker-service", "applications");
        int documents = intDetail(services, "document-store-service", "documents");
        int versions = intDetail(services, "document-store-service", "documentVersions");
        int ledger = intDetail(services, "payment-service", "ledgerEntries");
        Long balance = longDetail(services, "payment-service", "balanceTokens");
        return new EnvironmentSummary(users, profiles, applications, documents, versions, ledger, 0, balance, applicationsByStatus(services));
    }

    private boolean present(List<EnvironmentServiceResult> services, String serviceName) {
        return services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .findFirst()
                .map(result -> Boolean.TRUE.equals(result.details().get("exists")) || Boolean.TRUE.equals(result.details().get("walletExists")))
                .orElse(false);
    }

    private int intDetail(List<EnvironmentServiceResult> services, String serviceName, String key) {
        return services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .findFirst()
                .map(result -> result.details().get(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(0);
    }

    private Long longDetail(List<EnvironmentServiceResult> services, String serviceName, String key) {
        return services.stream()
                .filter(result -> serviceName.equals(result.service()))
                .findFirst()
                .map(result -> result.details().get(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .orElse(0L);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> applicationsByStatus(List<EnvironmentServiceResult> services) {
        Object byStatus = services.stream()
                .filter(result -> "application-tracker-service".equals(result.service()))
                .findFirst()
                .map(result -> result.details().get("byStatus"))
                .orElse(null);
        if (!(byStatus instanceof Map<?, ?> rawStatuses)) {
            return Map.of();
        }
        Map<String, Integer> statuses = new LinkedHashMap<>();
        rawStatuses.forEach((key, value) -> {
            if (value instanceof Number number) {
                statuses.put(key.toString(), number.intValue());
            }
        });
        return statuses;
    }

    private record DownstreamOperation(String service, Supplier<EnvironmentServiceResult> action) {
    }
}
