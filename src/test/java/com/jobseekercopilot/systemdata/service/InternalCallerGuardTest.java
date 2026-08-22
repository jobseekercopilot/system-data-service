package com.jobseekercopilot.systemdata.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalCallerGuardTest {
    private static final String VALID_KEY = "local-system-data-key-32-characters";

    @Test
    void acceptsExactConfiguredCallerKey() {
        InternalCallerGuard guard = guardWith(VALID_KEY);

        assertThatCode(() -> guard.requireAuthorized(VALID_KEY)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingWrongAndUnconfiguredCallerKeysWithoutEchoingThem() {
        for (CallerCase callerCase : new CallerCase[] {
                new CallerCase(VALID_KEY, null),
                new CallerCase(VALID_KEY, "wrong-system-data-key-32-characters"),
                new CallerCase(null, VALID_KEY),
                new CallerCase("too-short", "too-short")}) {
            assertThatThrownBy(() -> guardWith(callerCase.configured()).requireAuthorized(callerCase.presented()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("401 UNAUTHORIZED")
                    .hasMessageNotContaining(VALID_KEY)
                    .hasMessageNotContaining("wrong-system-data-key");
        }
    }

    private InternalCallerGuard guardWith(String key) {
        SystemDataProperties properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setCallerKey(key);
        return new InternalCallerGuard(properties);
    }

    private record CallerCase(String configured, String presented) {
    }
}
