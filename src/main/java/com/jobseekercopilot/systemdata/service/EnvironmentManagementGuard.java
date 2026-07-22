package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EnvironmentManagementGuard {
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
        if (activeProfiles.isEmpty()) {
            activeProfiles = Set.of("default");
        }
        if (activeProfiles.contains("prod") || activeProfiles.contains("production")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Environment management is forbidden in production");
        }
        Set<String> allowed = properties.getEnvironmentManagement().getAllowedEnvironments().stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!properties.getEnvironmentManagement().isEnabled() || activeProfiles.stream().noneMatch(allowed::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Environment management is disabled");
        }
    }

    public String activeEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
