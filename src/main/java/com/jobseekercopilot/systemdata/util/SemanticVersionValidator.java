package com.jobseekercopilot.systemdata.util;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SemanticVersionValidator {
    private static final Pattern SEMVER = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:[-+][0-9A-Za-z.-]+)?$");

    public boolean isValid(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    public void requireValid(String version) {
        if (!isValid(version)) {
            throw new IllegalArgumentException("Dataset version must be a semantic version such as 1.0.0");
        }
    }
}
