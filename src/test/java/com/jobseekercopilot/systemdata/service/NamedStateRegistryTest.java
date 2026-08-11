package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NamedStateRegistryTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void catalogIsCompleteVersionedSyntheticAndDeterministic() {
        NamedStateRegistry first = new NamedStateRegistry(objectMapper);
        NamedStateRegistry second = new NamedStateRegistry(objectMapper);

        assertThat(first.list()).hasSize(EnvironmentScenario.values().length).isEqualTo(second.list());
        assertThat(first.require(EnvironmentScenario.CROSS_USER_SECURITY).identities())
                .extracting(identity -> identity.userId("cross-user-security-v1"))
                .containsExactly(
                        "07137a30-692e-3342-8a96-cd3eca5ad037",
                        "b0bab5d2-b55b-34e1-a20a-65a31d2cf53b");
        assertThat(first.require(EnvironmentScenario.PROVIDER_FAILURE).providerBehaviour())
                .isEqualTo("ALL_UNAVAILABLE");
        assertThat(first.require(EnvironmentScenario.REAL_WORLD_PERSONAS).version()).isEqualTo("1.1.0");
        assertThat(first.require(EnvironmentScenario.REAL_WORLD_PERSONAS).identities())
                .allSatisfy(identity -> {
                    assertThat(identity.resetComponents()).containsExactlyInAnyOrder(
                            "PAYMENT", "DOCUMENTS", "APPLICATIONS", "USER_PROFILE", "AUTHENTICATION");
                    assertThat(identity.seedComponents()).containsExactlyInAnyOrder(
                            "AUTHENTICATION", "USER_PROFILE");
                });
    }

    @Test
    void catalogBytesMatchTheReviewedGoldenChecksum() throws Exception {
        String catalog = new ClassPathResource("scenarios/named-states.json")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(new ChecksumUtil().sha256(catalog))
                .isEqualTo("803b28338646ac9aed631538c6bcd2ca1dcdccf390538d7338370d1945a05723");
    }

    @Test
    void rejectsIncompleteOrUnsafeCatalogs() {
        String unsafe = """
                [{"scenarioId":"unsafe-v1","version":"1.0.0","scenario":"EMPTY",
                  "purpose":"unsafe","providerBehaviour":"FIXTURE",
                  "datasetId":"demo","datasetVersion":"1.0.0","referenceDate":"2026-07-10T09:00:00Z",
                  "identities":[{"key":"person","email":"person@real.example.org","displayName":"Person",
                    "resetComponents":["AUTHENTICATION"],"seedComponents":["AUTHENTICATION"]}],"expected":{}}]
                """;

        assertThatThrownBy(() -> new NamedStateRegistry(objectMapper,
                new ByteArrayInputStream(unsafe.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Named-state catalog failed validation");
    }
}
