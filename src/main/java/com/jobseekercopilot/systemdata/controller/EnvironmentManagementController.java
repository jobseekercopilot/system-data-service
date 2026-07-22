package com.jobseekercopilot.systemdata.controller;

import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationResponse;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.service.EnvironmentOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/environments")
public class EnvironmentManagementController {
    private final EnvironmentOrchestrationService orchestrationService;

    public EnvironmentManagementController(EnvironmentOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/reset")
    public ResponseEntity<EnvironmentOperationResponse> reset(@RequestBody(required = false) EnvironmentOperationRequest request) {
        return ResponseEntity.ok(orchestrationService.reset(request));
    }

    @PostMapping("/seed")
    public ResponseEntity<EnvironmentOperationResponse> seed(@RequestBody EnvironmentOperationRequest request) {
        return ResponseEntity.ok(orchestrationService.seed(request));
    }

    @PostMapping("/reset-and-seed")
    public ResponseEntity<EnvironmentOperationResponse> resetAndSeed(@RequestBody EnvironmentOperationRequest request) {
        return ResponseEntity.ok(orchestrationService.resetAndSeed(request));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(orchestrationService.status());
    }

    @GetMapping("/verify")
    public ResponseEntity<EnvironmentOperationResponse> verify(@RequestParam(defaultValue = "DEMO_READY") EnvironmentScenario scenario) {
        return ResponseEntity.ok(orchestrationService.verify(new EnvironmentOperationRequest(scenario, null, null, null)));
    }
}
