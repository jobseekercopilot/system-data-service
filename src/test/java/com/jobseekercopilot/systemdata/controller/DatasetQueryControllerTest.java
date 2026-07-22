package com.jobseekercopilot.systemdata.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.service.DatasetPathPolicy;
import com.jobseekercopilot.systemdata.service.DatasetStorageService;
import com.jobseekercopilot.systemdata.service.FixtureGuard;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatasetQueryControllerTest {
    @Test
    void rejectsQueriesWhenFixturesAreDisabled() {
        var fixture = fixture(false);

        assertThatThrownBy(fixture.controller()::list)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsTraversalBeforeReadingAConfiguredRepository() {
        var fixture = fixture(true);

        assertThatThrownBy(() -> fixture.controller().inspectVersion("../outside", "1.0.0"))
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessage("Dataset path is invalid");
    }

    private ControllerFixture fixture(boolean enabled) {
        var properties = new SystemDataProperties();
        properties.getFixtures().setEnabled(enabled);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        var pathPolicy = new DatasetPathPolicy(new SemanticVersionValidator());
        var storage = new DatasetStorageService(
                new ObjectMapper().findAndRegisterModules(), properties, pathPolicy);
        return new ControllerFixture(new DatasetQueryController(
                storage, new FixtureGuard(properties, environment)));
    }

    private record ControllerFixture(DatasetQueryController controller) {
    }
}
