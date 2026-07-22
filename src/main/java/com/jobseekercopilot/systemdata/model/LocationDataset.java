package com.jobseekercopilot.systemdata.model;

import java.util.List;

public record LocationDataset(String schemaVersion, List<DemoLocation> locations) {
}
