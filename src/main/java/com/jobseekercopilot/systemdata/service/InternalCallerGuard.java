package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalCallerGuard {
    public static final String HEADER_NAME = "X-System-Data-Key";
    private static final int MINIMUM_KEY_LENGTH = 32;

    private final SystemDataProperties properties;

    public InternalCallerGuard(SystemDataProperties properties) {
        this.properties = properties;
    }

    public void requireAuthorized(String presentedKey) {
        String expectedKey = properties.getEnvironmentManagement().getCallerKey();
        if (expectedKey == null || expectedKey.length() < MINIMUM_KEY_LENGTH
                || presentedKey == null || presentedKey.isBlank()
                || !MessageDigest.isEqual(
                        expectedKey.getBytes(StandardCharsets.UTF_8),
                        presentedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal caller authentication failed");
        }
    }
}
