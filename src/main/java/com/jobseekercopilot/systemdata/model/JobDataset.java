package com.jobseekercopilot.systemdata.model;

import java.util.List;

public record JobDataset(String schemaVersion, List<DemoJob> jobs) {
}
