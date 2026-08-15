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
        assertThat(first.list()).allSatisfy(definition -> {
            assertThat(definition.datasetId()).isEqualTo("uk-software-developer-demo");
            assertThat(definition.datasetVersion()).isEqualTo("1.1.0");
        });
        assertThat(first.require(EnvironmentScenario.CROSS_USER_SECURITY).identities())
                .extracting(identity -> identity.userId("cross-user-security-v1"))
                .containsExactly(
                        "07137a30-692e-3342-8a96-cd3eca5ad037",
                        "b0bab5d2-b55b-34e1-a20a-65a31d2cf53b");
        assertThat(first.require(EnvironmentScenario.PROVIDER_FAILURE).providerBehaviour())
                .isEqualTo("ALL_UNAVAILABLE");
        var paymentAcceptance = first.require(EnvironmentScenario.PAYMENT_ACCEPTANCE);
        assertThat(paymentAcceptance.scenarioId()).isEqualTo("payment-acceptance-v1");
        assertThat(paymentAcceptance.providerBehaviour()).isEqualTo("FIXTURE");
        assertThat(paymentAcceptance.identities()).singleElement().satisfies(identity -> {
            assertThat(identity.resetComponents()).containsExactly("PAYMENT", "AUTHENTICATION");
            assertThat(identity.seedComponents()).containsExactly("AUTHENTICATION", "PAYMENT");
        });
        assertThat(paymentAcceptance.expected()).containsEntry("ledgerEntries", 1);
        var realWorldPersonas = first.require(EnvironmentScenario.REAL_WORLD_PERSONAS);
        assertThat(realWorldPersonas.version()).isEqualTo("1.1.0");
        assertThat(realWorldPersonas.datasetId()).isEqualTo("uk-software-developer-demo");
        assertThat(realWorldPersonas.datasetVersion()).isEqualTo("1.1.0");
        assertThat(realWorldPersonas.identities())
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
                .isEqualTo("d32403901da606341ebb29e45d444b0283d977944374d25af6a061d30f08ce70");
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
