package com.jobseekercopilot.systemdata.controller;

import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.DatasetVersionSummary;
import com.jobseekercopilot.systemdata.service.DatasetStorageService;
import com.jobseekercopilot.systemdata.service.FixtureGuard;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/internal/datasets", "/internal/system-data/datasets"})
public class DatasetQueryController {
    private final DatasetStorageService storageService;
    private final FixtureGuard guard;

    public DatasetQueryController(DatasetStorageService storageService, FixtureGuard guard) {
        this.storageService = storageService;
        this.guard = guard;
    }

    @GetMapping
    ResponseEntity<List<String>> list() throws IOException {
        guard.requireEnabled();
        return ResponseEntity.ok(storageService.listDatasetIds());
    }

    @GetMapping("/{directoryName}")
    ResponseEntity<DatasetGenerationResult> inspect(@PathVariable String directoryName) {
        guard.requireEnabled();
        return ResponseEntity.ok(storageService.read(storageService.datasetVersionDirectory(directoryName, "1.0.0")));
    }

    @GetMapping("/{datasetId}/versions")
    ResponseEntity<List<DatasetVersionSummary>> versions(@PathVariable String datasetId) throws IOException {
        guard.requireEnabled();
        return ResponseEntity.ok(storageService.listVersions(datasetId));
    }

    @GetMapping("/{datasetId}/versions/{version}")
    ResponseEntity<DatasetGenerationResult> inspectVersion(@PathVariable String datasetId, @PathVariable String version) {
        guard.requireEnabled();
        return ResponseEntity.ok(storageService.read(storageService.datasetVersionDirectory(datasetId, version)));
    }
}
