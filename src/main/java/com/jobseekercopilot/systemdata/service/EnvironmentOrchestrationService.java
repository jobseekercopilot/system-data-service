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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EnvironmentOrchestrationService {
    private static final String DEFAULT_DATASET_ID = "uk-software-developer-demo";
    private static final String DEFAULT_DATASET_VERSION = "1.0.0";
    private static final Instant DEFAULT_REFERENCE_DATE = Instant.parse("2026-07-10T09:00:00Z");

    private final SystemDataProperties properties;
    private final DatasetStorageService datasetStorageService;
    private final EnvironmentManagementGuard guard;
    private final DemoEnvironmentScenarioBuilder scenarioBuilder;
    private final RestTemplate restTemplate = new RestTemplate();

    public EnvironmentOrchestrationService(
            SystemDataProperties properties,
            DatasetStorageService datasetStorageService,
            EnvironmentManagementGuard guard,
            DemoEnvironmentScenarioBuilder scenarioBuilder) {
        this.properties = properties;
        this.datasetStorageService = datasetStorageService;
        this.guard = guard;
        this.scenarioBuilder = scenarioBuilder;
    }

    public EnvironmentOperationResponse reset(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        String userId = scenarioBuilder.demoUserId();
        List<EnvironmentServiceResult> services = resetServices(scenarioId(scenario), userId);
        return response("reset", scenario, startedAt, services, summaryForReset(services), List.of());
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
        return response("seed", scenario, startedAt, services, summaryForSeed(demoScenario), demoScenario.warnings());
    }

    public EnvironmentOperationResponse resetAndSeed(EnvironmentOperationRequest request) {
        guard.requireEnabled();
        Instant startedAt = Instant.now();
        EnvironmentScenario scenario = scenario(request);
        List<EnvironmentServiceResult> services = new ArrayList<>();
        services.addAll(resetServices(scenarioId(scenario), scenarioBuilder.demoUserId()));
        List<String> warnings = new ArrayList<>();
        EnvironmentSummary summary = summaryForReset(services);
        if (scenario == EnvironmentScenario.DEMO_READY) {
            DemoEnvironmentScenario demoScenario = buildScenario(request);
            services.addAll(seedServices(demoScenario));
            warnings.addAll(demoScenario.warnings());
            summary = summaryForSeed(demoScenario);
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
                "defaultDatasetVersion", DEFAULT_DATASET_VERSION,
                "targetServices", properties.getEnvironmentManagement().getTargetServices());
    }

    private List<EnvironmentServiceResult> resetServices(String scenarioId, String userId) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        return List.of(
                delete("payment-service", targets.getPayment() + "/internal/system-data/scenario/" + scenarioId + "/payments/" + userId),
                delete("document-store-service", targets.getDocumentStore() + "/internal/system-data/scenario/" + scenarioId + "/documents/" + userId),
                delete("application-tracker-service", targets.getApplicationTracker() + "/internal/system-data/scenario/" + scenarioId + "/applications/" + userId),
                delete("user-profile-service", targets.getUserProfile() + "/internal/system-data/scenario/" + scenarioId + "/profiles/" + userId),
                delete("authentication-service", targets.getAuthentication() + "/internal/system-data/scenario/" + scenarioId + "/users/" + userId),
                EnvironmentServiceResult.skipped("cv-cover-letter-service", "No persistence discovered."),
                EnvironmentServiceResult.skipped("reporting-service", "No persistence discovered in current source."));
    }

    private List<EnvironmentServiceResult> seedServices(DemoEnvironmentScenario scenario) {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        return List.of(
                post("authentication-service", targets.getAuthentication() + "/internal/system-data/seed/user", scenario.user()),
                post("user-profile-service", targets.getUserProfile() + "/internal/system-data/seed/profiles/" + scenario.userId(), scenario.profile()),
                post("payment-service", targets.getPayment() + "/internal/system-data/seed/payments", scenario.payment()),
                post("document-store-service", targets.getDocumentStore() + "/internal/system-data/seed/documents", scenario.documents()),
                post("application-tracker-service", targets.getApplicationTracker() + "/internal/system-data/seed/applications", scenario.applications()),
                EnvironmentServiceResult.skipped("cv-cover-letter-service", "No persistent generation records discovered; document-store-service owns document records."),
                EnvironmentServiceResult.skipped("reporting-service", "No persistent activity repository discovered; E2E can verify aggregate state through this endpoint."));
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
        DatasetGenerationResult dataset = datasetStorageService.read(datasetStorageService.datasetVersionDirectory(datasetId, datasetVersion));
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
            return resultFrom(service, response.getBody());
        } catch (RestClientException exception) {
            return failure(service, "SEED", exception);
        }
    }

    private EnvironmentServiceResult delete(String service, String url) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
            return resultFrom(service, response.getBody());
        } catch (RestClientException exception) {
            return failure(service, "RESET", exception);
        }
    }

    private EnvironmentServiceResult get(String service, String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return resultFrom(service, response.getBody());
        } catch (RestClientException exception) {
            return failure(service, "VERIFY", exception);
        }
    }

    private EnvironmentServiceResult resultFrom(String fallbackService, Map body) {
        if (body == null) {
            return new EnvironmentServiceResult(fallbackService, "UNKNOWN", "SUCCESS", 0, Map.of(), List.of());
        }
        Object service = body.getOrDefault("service", fallbackService);
        Object operation = body.getOrDefault("operation", "UNKNOWN");
        Object status = body.getOrDefault("status", "SUCCESS");
        int recordsAffected = ((Number) body.getOrDefault("recordsAffected", 0)).intValue();
        Object details = body.get("details");
        Object warnings = body.get("warnings");
        return new EnvironmentServiceResult(
                service.toString(),
                operation.toString(),
                status.toString(),
                recordsAffected,
                details instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of(),
                warnings instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of());
    }

    private EnvironmentServiceResult failure(String service, String operation, RestClientException exception) {
        return new EnvironmentServiceResult(service, operation, "FAILED", 0, Map.of("error", exception.getMessage()), List.of(exception.getClass().getSimpleName()));
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
}
