package com.jobseekercopilot.systemdata.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class DownstreamEnvironmentDataCredential {
    public static final String HEADER_NAME = "X-Environment-Data-Token";
    static final int MINIMUM_TOKEN_BYTES = 32;

    private final SystemDataProperties properties;

    public DownstreamEnvironmentDataCredential(SystemDataProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateEnabledConfiguration() {
        if (properties.getEnvironmentManagement().isEnabled()) {
            requiredToken();
        }
    }

    public String requiredToken() {
        String token = properties.getEnvironmentManagement().getDownstreamEnvironmentDataToken();
        if (token == null || token.isBlank()
                || token.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException("Downstream environment-data token must contain at least 32 bytes.");
        }
        String callerKey = properties.getEnvironmentManagement().getCallerKey();
        if (callerKey != null && MessageDigest.isEqual(
                callerKey.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Inbound caller key and downstream environment-data token must be distinct.");
        }
        return token;
    }
}
