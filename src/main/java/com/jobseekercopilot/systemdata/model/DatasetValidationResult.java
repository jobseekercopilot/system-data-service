package com.jobseekercopilot.systemdata.model;

import java.util.List;

public record DatasetValidationResult(boolean valid, List<String> warnings) {
}
