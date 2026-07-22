package com.jobseekercopilot.systemdata.controller;

import com.jobseekercopilot.systemdata.model.EnvironmentOperationRequest;
import com.jobseekercopilot.systemdata.model.EnvironmentOperationResponse;
import com.jobseekercopilot.systemdata.model.EnvironmentScenario;
import com.jobseekercopilot.systemdata.service.EnvironmentOrchestrationService;
import com.jobseekercopilot.systemdata.service.InternalCallerGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/environments")
public class EnvironmentManagementController {
    private final EnvironmentOrchestrationService orchestrationService;
    private final InternalCallerGuard callerGuard;

    public EnvironmentManagementController(EnvironmentOrchestrationService orchestrationService,
                                           InternalCallerGuard callerGuard) {
        this.orchestrationService = orchestrationService;
        this.callerGuard = callerGuard;
    }

    @PostMapping("/reset")
    public ResponseEntity<EnvironmentOperationResponse> reset(
            @RequestHeader(name = InternalCallerGuard.HEADER_NAME, required = false) String callerKey,
            @RequestBody(required = false) EnvironmentOperationRequest request) {
        callerGuard.requireAuthorized(callerKey);
        return ResponseEntity.ok(orchestrationService.reset(request));
    }

    @PostMapping("/seed")
    public ResponseEntity<EnvironmentOperationResponse> seed(
            @RequestHeader(name = InternalCallerGuard.HEADER_NAME, required = false) String callerKey,
            @RequestBody EnvironmentOperationRequest request) {
        callerGuard.requireAuthorized(callerKey);
        return ResponseEntity.ok(orchestrationService.seed(request));
    }

    @PostMapping("/reset-and-seed")
    public ResponseEntity<EnvironmentOperationResponse> resetAndSeed(
            @RequestHeader(name = InternalCallerGuard.HEADER_NAME, required = false) String callerKey,
            @RequestBody EnvironmentOperationRequest request) {
        callerGuard.requireAuthorized(callerKey);
        return ResponseEntity.ok(orchestrationService.resetAndSeed(request));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader(name = InternalCallerGuard.HEADER_NAME, required = false) String callerKey) {
        callerGuard.requireAuthorized(callerKey);
        return ResponseEntity.ok(orchestrationService.status());
    }

    @GetMapping("/verify")
    public ResponseEntity<EnvironmentOperationResponse> verify(
            @RequestHeader(name = InternalCallerGuard.HEADER_NAME, required = false) String callerKey,
            @RequestParam(defaultValue = "DEMO_READY") EnvironmentScenario scenario) {
        callerGuard.requireAuthorized(callerKey);
        return ResponseEntity.ok(orchestrationService.verify(new EnvironmentOperationRequest(scenario, null, null, null)));
    }
}
