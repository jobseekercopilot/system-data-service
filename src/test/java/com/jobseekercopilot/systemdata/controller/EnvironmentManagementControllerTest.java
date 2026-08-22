package com.jobseekercopilot.systemdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationResponse;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.service.DemoEnvironmentScenarioBuilder;
import com.jobseekercopilot.systemdata.service.EnvironmentOrchestrationService;
import com.jobseekercopilot.systemdata.service.InternalCallerGuard;
import com.jobseekercopilot.systemdata.service.NamedStateRegistry;
import com.jobseekercopilot.systemdata.service.PersonaCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

class EnvironmentManagementControllerTest {
    private static final String VALID_KEY = "local-system-data-key-32-characters";

    @Test
    void wrongCallerCannotReachAnyEnvironmentOperation() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService();
        EnvironmentManagementController controller = controller(orchestration);

        assertUnauthorized(() -> controller.reset("wrong", null));
        assertUnauthorized(() -> controller.seed(null, request()));
        assertUnauthorized(() -> controller.resetAndSeed("wrong", request()));
        assertUnauthorized(() -> controller.prepare("wrong", request()));
        assertUnauthorized(() -> controller.states("wrong"));
        assertUnauthorized(() -> controller.describe("wrong", EnvironmentScenario.DEMO_READY));
        assertUnauthorized(() -> controller.status(null));
        assertUnauthorized(() -> controller.fixturePaymentEvent(
                "wrong", "cs_fixture_00000000000000000000000000000000",
                Map.of("event", "COMPLETED")));
        assertUnauthorized(() -> controller.verify("wrong", EnvironmentScenario.DEMO_READY));
        assertThat(orchestration.invocations).isZero();
    }

    @Test
    void authorizedCallerCanReachTheOperationBoundary() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService();
        EnvironmentManagementController controller = controller(orchestration);

        controller.reset(VALID_KEY, request());

        assertThat(orchestration.invocations).isOne();
    }

    @Test
    void authorizedCallerCanDiscoverDescribeAndPrepareStates() {
        RecordingOrchestrationService orchestration = new RecordingOrchestrationService();
        EnvironmentManagementController controller = controller(orchestration);

        controller.states(VALID_KEY);
        controller.describe(VALID_KEY, EnvironmentScenario.LOGIN_SESSION);
        controller.prepare(VALID_KEY, new EnvironmentOperationRequest(
                EnvironmentScenario.LOGIN_SESSION, null, null, null));
        controller.fixturePaymentEvent(
                VALID_KEY,
                "cs_fixture_00000000000000000000000000000000",
                Map.of("event", "COMPLETED"));

        assertThat(orchestration.invocations).isEqualTo(4);
    }

    private EnvironmentManagementController controller(RecordingOrchestrationService orchestration) {
        SystemDataProperties properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setCallerKey(VALID_KEY);
        return new EnvironmentManagementController(orchestration, new InternalCallerGuard(properties));
    }

    private EnvironmentOperationRequest request() {
        return new EnvironmentOperationRequest(EnvironmentScenario.DEMO_READY, null, null, null);
    }

    private void assertUnauthorized(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    private static final class RecordingOrchestrationService extends EnvironmentOrchestrationService {
        private int invocations;

        private RecordingOrchestrationService() {
            super(null, null, null, new DemoEnvironmentScenarioBuilder(), null,
                    new NamedStateRegistry(new ObjectMapper().findAndRegisterModules()),
                    new PersonaCatalog(new ObjectMapper().findAndRegisterModules()),
                    new RestTemplate());
        }

        @Override
        public EnvironmentOperationResponse reset(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public EnvironmentOperationResponse seed(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public EnvironmentOperationResponse resetAndSeed(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public EnvironmentOperationResponse prepare(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public java.util.List<com.jobseekercopilot.systemdata.model.NamedStateDefinition> listStates() {
            invocations++;
            return java.util.List.of();
        }

        @Override
        public com.jobseekercopilot.systemdata.model.NamedStateDefinition describe(EnvironmentScenario scenario) {
            invocations++;
            return new NamedStateRegistry(new ObjectMapper().findAndRegisterModules()).require(scenario);
        }

        @Override
        public EnvironmentOperationResponse verify(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public Map<String, Object> status() {
            invocations++;
            return Map.of();
        }

        @Override
        public Map<String, Object> emitFixturePaymentEvent(
                String providerSessionId, Map<String, Object> request) {
            invocations++;
            return Map.of("status", "FORWARDED");
        }
    }
}
