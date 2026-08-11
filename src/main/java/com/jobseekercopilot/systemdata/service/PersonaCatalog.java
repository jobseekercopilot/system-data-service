package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.model.PersonaDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PersonaCatalog {
    public static final Set<String> REQUIRED_PERSONAS = Set.of(
            "minimal-profile",
            "typical-profile",
            "rich-profile",
            "very-rich-profile",
            "uploaded-cv-first",
            "manual-profile-first",
            "career-changer");

    private final ObjectMapper objectMapper;
    private final Map<String, PersonaDefinition> definitions;

    @Autowired
    public PersonaCatalog(ObjectMapper objectMapper) {
        this(objectMapper, resource());
    }

    PersonaCatalog(ObjectMapper objectMapper, InputStream input) {
        this.objectMapper = objectMapper;
        try (input) {
            List<PersonaDefinition> loaded = objectMapper.readValue(
                    input, new TypeReference<List<PersonaDefinition>>() { });
            this.definitions = validate(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Persona catalog is unavailable", exception);
        }
    }

    public List<PersonaDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public Map<String, Object> profile(String personaId, String userId) {
        PersonaDefinition definition = definitions.get(personaId);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported persona");
        }
        Map<String, Object> copy = objectMapper.convertValue(
                definition.profile(), new TypeReference<LinkedHashMap<String, Object>>() { });
        copy.put("userId", userId);
        return copy;
    }

    private Map<String, PersonaDefinition> validate(List<PersonaDefinition> loaded) {
        if (loaded == null || loaded.size() != REQUIRED_PERSONAS.size()) {
            throw invalid();
        }
        Map<String, PersonaDefinition> result = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        for (PersonaDefinition definition : loaded) {
            require(definition != null && REQUIRED_PERSONAS.contains(definition.personaId()));
            require(ids.add(definition.personaId()));
            require(text(definition.journeyStyle()) && text(definition.purpose()));
            require(definition.capabilities() != null && !definition.capabilities().isEmpty());
            require(definition.profile() != null);
            require(definition.profile().keySet().containsAll(List.of(
                    "skills", "aspirations", "workPreferences", "qualifications", "roles")));
            require(definition.profile().get("skills") instanceof List<?> skills && skills.size() <= 100);
            require(definition.profile().get("qualifications") instanceof List<?> qualifications
                    && qualifications.size() <= 50);
            require(definition.profile().get("roles") instanceof List<?> roles && roles.size() <= 50);
            result.put(definition.personaId(), definition);
        }
        require(ids.equals(REQUIRED_PERSONAS));
        return Map.copyOf(result);
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private IllegalStateException invalid() {
        return new IllegalStateException("Persona catalog failed validation");
    }

    private static InputStream resource() {
        try {
            return new ClassPathResource("personas/personas.json").getInputStream();
        } catch (IOException exception) {
            throw new IllegalStateException("Persona catalog is unavailable", exception);
        }
    }
}
