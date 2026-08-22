package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EnvironmentManagementGuard {
    private static final Set<String> TRUSTED_PROFILES = Set.of("local", "test", "demo");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private final SystemDataProperties properties;
    private final Environment environment;

    public EnvironmentManagementGuard(SystemDataProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public void requireEnabled() {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!properties.getEnvironmentManagement().isEnabled()) {
            forbidden();
        }
        if (activeProfiles.size() != 1 || !TRUSTED_PROFILES.containsAll(activeProfiles)) {
            forbidden();
        }
        if (properties.getEnvironmentManagement().getAllowedEnvironments() == null) {
            forbidden();
        }
        Set<String> configuredAllowed = properties.getEnvironmentManagement().getAllowedEnvironments().stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (configuredAllowed.isEmpty()
                || !TRUSTED_PROFILES.containsAll(configuredAllowed)
                || !configuredAllowed.containsAll(activeProfiles)) {
            forbidden();
        }
        validateTargets();
    }

    public String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 1 ? profiles[0] : "unavailable";
    }

    private void validateTargets() {
        var targets = properties.getEnvironmentManagement().getTargetServices();
        validateTarget(targets.getAuthentication(), new TargetBoundary("authentication-service", 8084));
        validateTarget(targets.getUserProfile(), new TargetBoundary("user-profile-service", 8085));
        validateTarget(targets.getApplicationTracker(), new TargetBoundary("application-tracker-service", 8088));
        validateTarget(targets.getDocumentStore(), new TargetBoundary("document-store-service", 8089));
        validateTarget(targets.getPayment(), new TargetBoundary("payment-service", 8099));
        validateTarget(targets.getStripeGateway(), new TargetBoundary("stripe-gateway", 8100));
    }

    private void validateTarget(String value, TargetBoundary boundary) {
        try {
            URI uri = new URI(value);
            String host = normaliseHost(uri.getHost());
            boolean trustedHost = LOOPBACK_HOSTS.contains(host) || boundary.dockerHost().equals(host);
            boolean cleanBaseUrl = "http".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
            if (!cleanBaseUrl || !trustedHost || uri.getPort() != boundary.port()) {
                forbidden();
            }
        } catch (NullPointerException | URISyntaxException exception) {
            forbidden();
        }
    }

    private String normaliseHost(String host) {
        if (host == null) {
            return "";
        }
        String normalised = host.toLowerCase(Locale.ROOT);
        return normalised.startsWith("[") && normalised.endsWith("]")
                ? normalised.substring(1, normalised.length() - 1)
                : normalised;
    }

    private void forbidden() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Environment management is unavailable");
    }

    private record TargetBoundary(String dockerHost, int port) {
    }
}
