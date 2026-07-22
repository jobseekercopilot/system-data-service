package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.model.DatasetGenerationRequest;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Profile("live-acquisition")
public class LiveAcquisitionGuard {
    static final String CONFIRMATION = "I UNDERSTAND LIVE PROVIDERS WILL BE CALLED";
    private static final Set<String> FORBIDDEN_PROFILES = Set.of(
            "default", "test", "demo", "e2e", "ci", "prod", "production");
    private static final Set<String> PROVIDERS = Set.of("ADZUNA", "JSEARCH", "REED");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private final SystemDataProperties properties;
    private final Environment environment;
    private final DatasetPathPolicy pathPolicy;

    public LiveAcquisitionGuard(SystemDataProperties properties, Environment environment, DatasetPathPolicy pathPolicy) {
        this.properties = properties;
        this.environment = environment;
        this.pathPolicy = pathPolicy;
    }

    public void requireAllowed(DatasetGenerationRequest request) {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        var acquisition = properties.getLiveAcquisition();
        require(profiles.contains("live-acquisition") && profiles.stream().noneMatch(FORBIDDEN_PROFILES::contains));
        require(acquisition.isEnabled() && acquisition.isExecute());
        require(CONFIRMATION.equals(acquisition.getOperatorConfirmation()));
        require(present(acquisition.getTermsApprovalReference()) && present(acquisition.getProvenanceReviewer()));
        require(acquisition.getMaximumOutputRecords() > 0 && acquisition.getMaximumOutputRecords() <= 120);
        require(!samePath(properties.getRepositoryDirectory(), properties.getOutputDirectory()));
        require(request == null || !Boolean.TRUE.equals(request.overwrite()));
        require(request == null || !Boolean.TRUE.equals(request.includeLlmEnrichment()));
        String datasetId = request == null || !present(request.datasetId())
                ? properties.getGeneration().getDefaultDatasetId() : request.datasetId();
        String version = request == null || !present(request.version())
                ? properties.getGeneration().getDefaultVersion() : request.version();
        pathPolicy.datasetVersion(properties.getOutputDirectory(), datasetId, version);

        require(acquisition.getApprovedProviders() != null);
        require(acquisition.getApprovedProviders().stream().allMatch(this::present));
        Set<String> approved = acquisition.getApprovedProviders().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        require(!approved.isEmpty() && PROVIDERS.containsAll(approved));
        List<String> requestedValues = request == null ? null : request.providers();
        require(requestedValues == null || requestedValues.stream().allMatch(this::present));
        Set<String> requested = requestedValues == null || requestedValues.isEmpty() ? approved
                : requestedValues.stream().map(value -> value.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        require(approved.containsAll(requested));
        require(requested.stream().allMatch(this::enabled));
        requested.forEach(this::requireGatewayTarget);
        if (properties.getGateways().getPostcodeIo().isEnabled()) {
            requireGatewayTarget("POSTCODE_IO");
        }
        int queryCount = size(request == null ? null : request.queries(), properties.getGeneration().getQueries());
        int locationCount = size(request == null ? null : request.locations(), properties.getGeneration().getLocations());
        require(queryCount <= 10 && locationCount <= 10);
        require((long) queryCount * locationCount * requested.size() <= 120);
        int requestedMaximum = request != null && request.maximumResultsPerProvider() != null
                ? request.maximumResultsPerProvider() : properties.getGeneration().getMaximumJobsPerProvider();
        require(requestedMaximum > 0 && requestedMaximum <= 40);
    }

    private int size(List<String> requested, List<String> defaults) {
        List<String> values = requested == null || requested.isEmpty() ? defaults : requested;
        require(values != null && values.stream().allMatch(this::present));
        return values.size();
    }

    private boolean enabled(String provider) {
        return switch (provider) {
            case "ADZUNA" -> properties.getGateways().getAdzuna().isEnabled();
            case "JSEARCH" -> properties.getGateways().getJsearch().isEnabled();
            case "REED" -> properties.getGateways().getReed().isEnabled();
            default -> false;
        };
    }

    private void requireGatewayTarget(String provider) {
        SystemDataProperties.Gateway gateway;
        String dockerHost;
        int expectedPort;
        switch (provider) {
            case "ADZUNA" -> {
                gateway = properties.getGateways().getAdzuna();
                dockerHost = "adzuna-gateway";
                expectedPort = 8101;
            }
            case "JSEARCH" -> {
                gateway = properties.getGateways().getJsearch();
                dockerHost = "jsearch-gateway";
                expectedPort = 8102;
            }
            case "REED" -> {
                gateway = properties.getGateways().getReed();
                dockerHost = "reed-gateway";
                expectedPort = 8087;
            }
            case "POSTCODE_IO" -> {
                gateway = properties.getGateways().getPostcodeIo();
                dockerHost = "postcode-io-gateway";
                expectedPort = 8082;
            }
            default -> {
                require(false);
                return;
            }
        }
        require(present(gateway.getBaseUrl()));
        try {
            URI uri = URI.create(gateway.getBaseUrl());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean safeHost = LOOPBACK_HOSTS.contains(host) || dockerHost.equals(host);
            require("http".equalsIgnoreCase(uri.getScheme())
                    && safeHost
                    && uri.getPort() == expectedPort
                    && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    && uri.getQuery() == null
                    && uri.getFragment() == null);
        } catch (IllegalArgumentException ex) {
            require(false);
        }
    }

    private boolean samePath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new DatasetGenerationException("Live acquisition is not authorized");
        }
    }
}
