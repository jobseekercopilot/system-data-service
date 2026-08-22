package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.model.DatasetGenerationRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LiveAcquisitionGuardTest {
    private static final DatasetGenerationRequest REQUEST = new DatasetGenerationRequest(
            "safe-dataset", "Safe dataset", "1.0.0", "test", List.of("Developer"),
            List.of("London"), List.of(), List.of("ADZUNA"), 10, false, false);

    @Test
    void permitsOnlyFullyConfirmedBoundedLocalGatewayAcquisition() {
        var fixture = validFixture();
        assertThatCode(() -> fixture.guard().requireAllowed(REQUEST)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingIntentAndForbiddenProfiles() {
        assertRejected(fixture -> fixture.properties().getLiveAcquisition().setExecute(false));
        assertRejected(fixture -> fixture.properties().getLiveAcquisition().setOperatorConfirmation("yes"));
        assertRejected(fixture -> fixture.properties().getLiveAcquisition().setTermsApprovalReference(""));
        assertRejected(fixture -> fixture.properties().getLiveAcquisition().setProvenanceReviewer(""));
        assertRejected(fixture -> fixture.environment().setActiveProfiles("live-acquisition", "e2e"));
        assertRejected(fixture -> fixture.environment().setActiveProfiles("live-acquisition", "production"));
    }

    @Test
    void rejectsUnsafeTargetsProvidersAndOutput() {
        assertRejected(fixture -> fixture.properties().getGateways().getAdzuna()
                .setBaseUrl("https://api.adzuna.example/jobs?credential=placeholder"));
        assertRejected(fixture -> fixture.properties().getGateways().getAdzuna()
                .setBaseUrl("http://localhost:9999"));
        assertRejected(fixture -> fixture.properties().getLiveAcquisition()
                .setApprovedProviders(List.of("UNKNOWN")));
        assertRejected(fixture -> fixture.properties().getGateways().getAdzuna().setEnabled(false));
        assertRejected(fixture -> fixture.properties().setOutputDirectory(
                fixture.properties().getRepositoryDirectory()));
    }

    @Test
    void rejectsUnboundedOrUnsafeGenerationOptions() {
        assertRejected(fixture -> fixture.properties().getLiveAcquisition().setMaximumOutputRecords(121));
        assertRequestRejected(new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0", "test", List.of("Developer"), List.of("London"),
                List.of(), List.of("ADZUNA"), 41, false, false));
        assertRequestRejected(new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0", "test", List.of("Developer"), List.of("London"),
                List.of(), List.of("ADZUNA"), 10, true, false));
        assertRequestRejected(new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0", "test", List.of("Developer"), List.of("London"),
                List.of(), List.of("ADZUNA"), 10, false, true));
        assertRequestRejected(new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0", "test",
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                List.of("London"), List.of(), List.of("ADZUNA"), 10, false, false));
        GuardFixture excessiveCalls = validFixture();
        excessiveCalls.properties().getLiveAcquisition().setApprovedProviders(List.of("ADZUNA", "JSEARCH"));
        excessiveCalls.properties().getGateways().getJsearch().setEnabled(true);
        excessiveCalls.properties().getGateways().getJsearch().setBaseUrl("http://localhost:8102");
        DatasetGenerationRequest excessiveCallRequest = new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0", "test",
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
                List.of(), List.of("ADZUNA", "JSEARCH"), 10, false, false);
        assertThatThrownBy(() -> excessiveCalls.guard().requireAllowed(excessiveCallRequest))
                .isInstanceOf(DatasetGenerationException.class);
        assertRequestRejected(new DatasetGenerationRequest(
                "../outside", "Safe", "1.0.0", "test", List.of("Developer"), List.of("London"),
                List.of(), List.of("ADZUNA"), 10, false, false));
        assertRequestRejected(new DatasetGenerationRequest(
                "safe-dataset", "Safe", "1.0.0/../../outside", "test", List.of("Developer"),
                List.of("London"), List.of(), List.of("ADZUNA"), 10, false, false));
    }

    private void assertRejected(Consumer<GuardFixture> mutation) {
        GuardFixture fixture = validFixture();
        mutation.accept(fixture);
        assertThatThrownBy(() -> fixture.guard().requireAllowed(REQUEST))
                .isInstanceOf(DatasetGenerationException.class)
                .hasMessage("Live acquisition is not authorized");
    }

    private void assertRequestRejected(DatasetGenerationRequest request) {
        GuardFixture fixture = validFixture();
        assertThatThrownBy(() -> fixture.guard().requireAllowed(request))
                .isInstanceOf(DatasetGenerationException.class);
    }

    private GuardFixture validFixture() {
        var properties = new SystemDataProperties();
        properties.setRepositoryDirectory(Path.of("fixtures/datasets"));
        properties.setOutputDirectory(Path.of("quarantined-acquisitions"));
        properties.getGateways().getAdzuna().setEnabled(true);
        properties.getGateways().getAdzuna().setBaseUrl("http://localhost:8101");
        properties.getLiveAcquisition().setEnabled(true);
        properties.getLiveAcquisition().setExecute(true);
        properties.getLiveAcquisition().setOperatorConfirmation(LiveAcquisitionGuard.CONFIRMATION);
        properties.getLiveAcquisition().setTermsApprovalReference("TEST-APPROVAL");
        properties.getLiveAcquisition().setProvenanceReviewer("test-reviewer");
        properties.getLiveAcquisition().setApprovedProviders(List.of("ADZUNA"));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("live-acquisition");
        var pathPolicy = new DatasetPathPolicy(new com.jobseekercopilot.systemdata.util.SemanticVersionValidator());
        return new GuardFixture(properties, environment,
                new LiveAcquisitionGuard(properties, environment, pathPolicy));
    }

    private record GuardFixture(SystemDataProperties properties, MockEnvironment environment,
                                LiveAcquisitionGuard guard) {
    }
}
