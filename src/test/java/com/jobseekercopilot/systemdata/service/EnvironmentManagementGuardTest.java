package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class EnvironmentManagementGuardTest {

    @Test
    void acceptsExplicitLocalProfileAndBoundedLoopbackTargets() {
        SystemDataProperties properties = enabledProperties();
        var guard = new EnvironmentManagementGuard(properties, environment("local"));

        assertThatCode(guard::requireEnabled).doesNotThrowAnyException();
    }

    @Test
    void acceptsExpectedDockerDnsTargets() {
        SystemDataProperties properties = enabledProperties();
        var targets = properties.getEnvironmentManagement().getTargetServices();
        targets.setAuthentication("http://authentication-service:8084");
        targets.setUserProfile("http://user-profile-service:8085");
        targets.setApplicationTracker("http://application-tracker-service:8088");
        targets.setDocumentStore("http://document-store-service:8089");
        targets.setPayment("http://payment-service:8099");

        assertThatCode(() -> new EnvironmentManagementGuard(properties, environment("test")).requireEnabled())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledDefaultProductionAndAmbiguousProfiles() {
        SystemDataProperties disabled = enabledProperties();
        disabled.getEnvironmentManagement().setEnabled(false);
        assertForbidden(new EnvironmentManagementGuard(disabled, environment("local")));

        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment()));
        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment("default")));
        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment("prod")));
        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment("production")));
        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment("local", "demo")));
        assertForbidden(new EnvironmentManagementGuard(enabledProperties(), environment("local", "prod")));
    }

    @Test
    void configurationCannotExpandTheHardCodedProfileBoundary() {
        SystemDataProperties properties = enabledProperties();
        properties.getEnvironmentManagement().setAllowedEnvironments(List.of("local", "default"));

        assertForbidden(new EnvironmentManagementGuard(properties, environment("local")));
    }

    @Test
    void rejectsExternalProductionLikeAndMalformedTargets() {
        for (String invalid : List.of(
                "https://authentication-service:8084",
                "http://authentication-service:443",
                "http://auth.production.example:8084",
                "http://10.0.0.10:8084",
                "http://user:password@localhost:8084",
                "http://localhost:8084/internal",
                "http://localhost:8084?target=prod",
                "http://localhost:8084#production",
                "jdbc:postgresql://localhost:5432/production",
                "not-a-url")) {
            SystemDataProperties properties = enabledProperties();
            properties.getEnvironmentManagement().getTargetServices().setAuthentication(invalid);
            assertForbidden(new EnvironmentManagementGuard(properties, environment("demo")));
        }
    }

    @Test
    void rejectsCrossWiredLocalServiceHostAndPort() {
        SystemDataProperties properties = enabledProperties();
        properties.getEnvironmentManagement().getTargetServices()
                .setAuthentication("http://payment-service:8099");

        assertForbidden(new EnvironmentManagementGuard(properties, environment("local")));
    }

    private SystemDataProperties enabledProperties() {
        SystemDataProperties properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setEnabled(true);
        return properties;
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private void assertForbidden(EnvironmentManagementGuard guard) {
        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN")
                .hasMessageContaining("unavailable");
    }
}
