package com.jobseekercopilot.systemdata.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SemanticVersionValidatorTest {
    private final SemanticVersionValidator validator = new SemanticVersionValidator();

    @Test
    void acceptsStandardSemanticVersions() {
        assertThat(validator.isValid("1.0.0")).isTrue();
        assertThat(validator.isValid("1.2.3-beta")).isTrue();
        assertThat(validator.isValid("1.2.3+build.4")).isTrue();
    }

    @Test
    void rejectsNonSemanticVersions() {
        assertThat(validator.isValid("1")).isFalse();
        assertThat(validator.isValid("v1.0.0")).isFalse();
        assertThatThrownBy(() -> validator.requireValid("latest"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
