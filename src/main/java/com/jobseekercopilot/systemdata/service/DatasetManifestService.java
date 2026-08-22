package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetManifest;
import com.jobseekercopilot.systemdata.model.DatasetSource;
import com.jobseekercopilot.systemdata.model.DatasetValidationResult;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.DemoLocation;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DatasetManifestService {
    private final ObjectMapper objectMapper;
    private final ChecksumUtil checksumUtil;
    private final SystemDataProperties properties;

    public DatasetManifestService(ObjectMapper objectMapper, ChecksumUtil checksumUtil, SystemDataProperties properties) {
        this.objectMapper = objectMapper;
        this.checksumUtil = checksumUtil;
        this.properties = properties;
    }

    public DatasetManifest create(String datasetId, String name, String version, String description,
                                  List<String> queries, List<String> searchLocations,
                                  List<DatasetSource> sources, List<DemoJob> jobs, List<DemoLocation> demoLocations,
                                  DatasetValidationResult validation) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("jobs", jobs.size());
        counts.put("locations", demoLocations.size());
        return new DatasetManifest(
                datasetId,
                name,
                version,
                "1.0",
                Instant.now(),
                description,
                validation.valid() ? "GENERATED" : "GENERATED_WITH_WARNINGS",
                sources.stream().map(DatasetSource::gateway).distinct().toList(),
                queries,
                searchLocations,
                sources,
                counts,
                checksum(jobs, demoLocations),
                validation,
                true,
                Map.of(
                        "queries", properties.getGeneration().getQueries(),
                        "locations", properties.getGeneration().getLocations(),
                        "maximumJobsPerProvider", properties.getGeneration().getMaximumJobsPerProvider()),
                validation.warnings(),
                null);
    }

    private String checksum(List<DemoJob> jobs, List<DemoLocation> locations) {
        try {
            return checksumUtil.sha256(objectMapper.writeValueAsString(Map.of("jobs", jobs, "locations", locations)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to checksum dataset", ex);
        }
    }
}
