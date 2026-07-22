package com.jobseekercopilot.systemdata.model;

import java.nio.file.Path;

public record DatasetGenerationResult(
        DatasetManifest manifest,
        Path outputDirectory,
        JobDataset jobs,
        LocationDataset locations,
        DatasetGenerationReport report) {
}
