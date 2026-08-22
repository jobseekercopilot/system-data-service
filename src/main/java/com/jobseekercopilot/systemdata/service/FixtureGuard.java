package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class FixtureGuard {
    private final SystemDataProperties properties;
    private final Environment environment;

    public FixtureGuard(SystemDataProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public void requireEnabled() {
        Set<String> activeProfiles = activeProfiles();
        if (activeProfiles.contains("prod") || activeProfiles.contains("production")) {
            throw new IllegalStateException("Fixture APIs are not available in production profiles.");
        }
        Set<String> allowedProfiles = properties.getFixtures().getAllowedEnvironments().stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!properties.getFixtures().isEnabled() || activeProfiles.stream().noneMatch(allowedProfiles::contains)) {
            throw new IllegalStateException("Fixture APIs are disabled for this environment.");
        }
    }

    public String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }

    private Set<String> activeProfiles() {
        Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return profiles.isEmpty() ? Set.of("default") : profiles;
    }
}
