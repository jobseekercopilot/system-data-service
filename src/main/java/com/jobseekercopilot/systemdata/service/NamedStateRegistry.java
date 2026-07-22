package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.model.NamedStateDefinition;
import com.jobseekercopilot.systemdata.model.NamedStateIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NamedStateRegistry {
    private static final Pattern VERSION = Pattern.compile("[1-9][0-9]*\\.[0-9]+\\.[0-9]+");
    private static final Set<String> COMPONENTS = Set.of(
            "AUTHENTICATION", "USER_PROFILE", "PAYMENT", "DOCUMENTS", "APPLICATIONS");
    private final Map<EnvironmentScenario, NamedStateDefinition> definitions;

    @Autowired
    public NamedStateRegistry(ObjectMapper objectMapper) {
        this(objectMapper, resource());
    }

    NamedStateRegistry(ObjectMapper objectMapper, InputStream input) {
        try (input) {
            List<NamedStateDefinition> loaded = objectMapper.readValue(
                    input, new TypeReference<List<NamedStateDefinition>>() { });
            definitions = validate(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Named-state catalog is unavailable", exception);
        }
    }

    public List<NamedStateDefinition> list() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(definition -> definition.scenario().name()))
                .toList();
    }

    public NamedStateDefinition require(EnvironmentScenario scenario) {
        NamedStateDefinition definition = definitions.get(scenario);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported named state");
        }
        return definition;
    }

    private Map<EnvironmentScenario, NamedStateDefinition> validate(List<NamedStateDefinition> loaded) {
        if (loaded == null || loaded.size() != EnvironmentScenario.values().length) {
            throw invalid();
        }
        Map<EnvironmentScenario, NamedStateDefinition> result = new EnumMap<>(EnvironmentScenario.class);
        Set<String> scenarioIds = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (NamedStateDefinition definition : loaded) {
            require(definition != null && definition.scenario() != null);
            require(definition.scenarioId() != null && definition.scenarioId().matches("[a-z0-9-]+-v[1-9][0-9]*|empty-v1"));
            require(VERSION.matcher(definition.version()).matches());
            require(definition.purpose() != null && !definition.purpose().isBlank());
            require(Set.of("FIXTURE", "ALL_UNAVAILABLE").contains(definition.providerBehaviour()));
            require(definition.datasetId() != null && definition.datasetVersion() != null && definition.referenceDate() != null);
            require(definition.identities() != null && definition.expected() != null);
            require(scenarioIds.add(definition.scenarioId()) && result.put(definition.scenario(), definition) == null);
            for (NamedStateIdentity identity : definition.identities()) {
                require(identity.key() != null && identity.key().matches("[a-z0-9-]+"));
                require(identity.email() != null && identity.email().endsWith("@example.com") && emails.add(identity.email()));
                require(identity.displayName() != null && !identity.displayName().isBlank());
                require(identity.resetComponents() != null && COMPONENTS.containsAll(identity.resetComponents()));
                require(identity.seedComponents() != null && COMPONENTS.containsAll(identity.seedComponents()));
                require(identity.resetComponents().containsAll(identity.seedComponents()));
            }
        }
        return Map.copyOf(result);
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private IllegalStateException invalid() {
        return new IllegalStateException("Named-state catalog failed validation");
    }

    private static InputStream resource() {
        try {
            return new ClassPathResource("scenarios/named-states.json").getInputStream();
        } catch (IOException exception) {
            throw new IllegalStateException("Named-state catalog is unavailable", exception);
        }
    }
}
