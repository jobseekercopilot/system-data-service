package com.jobseekercopilot.systemdata.model;

import java.util.Map;

public record EnvironmentSummary(
        int users,
        int profiles,
        int applications,
        int documents,
        int documentVersions,
        int creditLedgerEntries,
        int activities,
        Long creditBalance,
        Map<String, Integer> applicationsByStatus) {
}
