package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonaCatalogTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void catalogHasAllRequiredPersonasAndMeaningfulSizeVariation() {
        PersonaCatalog catalog = new PersonaCatalog(objectMapper);

        assertThat(catalog.list()).extracting(definition -> definition.personaId())
                .containsExactlyInAnyOrderElementsOf(PersonaCatalog.REQUIRED_PERSONAS);
        assertThat(skills(catalog, "minimal-profile")).hasSize(3);
        assertThat(skills(catalog, "typical-profile")).hasSize(10);
        assertThat(skills(catalog, "rich-profile")).hasSize(20);
        assertThat(skills(catalog, "very-rich-profile")).hasSize(60);
        assertThat(roles(catalog, "very-rich-profile")).hasSize(18);
        assertThat(roles(catalog, "career-changer")).extracting(role -> role.get("jobTitle"))
                .contains("Secondary School Teacher")
                .doesNotContain("Project Coordinator");
    }

    @Test
    void profileCopyInjectsDeterministicOwnerWithoutMutatingCanonicalData() {
        PersonaCatalog catalog = new PersonaCatalog(objectMapper);

        Map<String, Object> first = catalog.profile("typical-profile", "owner-one");
        Map<String, Object> second = catalog.profile("typical-profile", "owner-two");

        assertThat(first.get("userId")).isEqualTo("owner-one");
        assertThat(second.get("userId")).isEqualTo("owner-two");
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void rejectsIncompleteCatalog() {
        String incomplete = "[]";
        assertThatThrownBy(() -> new PersonaCatalog(objectMapper,
                new ByteArrayInputStream(incomplete.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Persona catalog failed validation");
    }

    @SuppressWarnings("unchecked")
    private List<String> skills(PersonaCatalog catalog, String personaId) {
        return (List<String>) catalog.profile(personaId, "owner").get("skills");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roles(PersonaCatalog catalog, String personaId) {
        return (List<Map<String, Object>>) catalog.profile(personaId, "owner").get("roles");
    }
}
