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
        assertUnauthorized(() -> controller.status(null));
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
            super(null, null, null, new DemoEnvironmentScenarioBuilder(), null, new RestTemplate());
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
        public EnvironmentOperationResponse verify(EnvironmentOperationRequest request) {
            invocations++;
            return null;
        }

        @Override
        public Map<String, Object> status() {
            invocations++;
            return Map.of();
        }
    }
}
