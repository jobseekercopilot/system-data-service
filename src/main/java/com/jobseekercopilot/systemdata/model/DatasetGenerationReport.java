package com.jobseekercopilot.systemdata.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DatasetGenerationReport(
        Instant startedAt,
        Instant finishedAt,
        long durationMillis,
        List<String> providersCalled,
        Map<String, String> providerStatuses,
        Map<String, Integer> rawResultCounts,
        int normalisedResultCount,
        int invalidRecordsRemoved,
        int rejectedRecords,
        int invalidSalariesDetected,
        int paidTrainingRecordsDetected,
        int duplicatesRemoved,
        int finalJobCount,
        int locationRecordsGenerated,
        int postcodeLookupsAttempted,
        int postcodeLookupsSucceeded,
        List<String> warnings) {
}
