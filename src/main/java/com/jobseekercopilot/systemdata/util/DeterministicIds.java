package com.jobseekercopilot.systemdata.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DeterministicIds {
    private static final String NAMESPACE = "job-seeker-copilot:system-data:";

    private DeterministicIds() {
    }

    public static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes((NAMESPACE + key).getBytes(StandardCharsets.UTF_8));
    }

    public static String uuidString(String key) {
        return uuid(key).toString();
    }
}
