package com.jobseekercopilot.systemdata.controller;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.FixtureJobSearchResponse;
import com.jobseekercopilot.systemdata.model.FixtureLlmRequest;
import com.jobseekercopilot.systemdata.model.FixtureLlmResponse;
import com.jobseekercopilot.systemdata.model.FixturePostcodeResponse;
import com.jobseekercopilot.systemdata.model.FixturePlaceResponse;
import com.jobseekercopilot.systemdata.model.FixtureStatusResponse;
import com.jobseekercopilot.systemdata.model.FixtureStripeRequest;
import com.jobseekercopilot.systemdata.model.FixtureStripeResponse;
import com.jobseekercopilot.systemdata.service.FixtureGuard;
import com.jobseekercopilot.systemdata.service.FixtureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/internal/fixtures")
public class FixtureController {
    private final FixtureGuard guard;
    private final FixtureService fixtureService;
    private final SystemDataProperties properties;

    public FixtureController(FixtureGuard guard, FixtureService fixtureService, SystemDataProperties properties) {
        this.guard = guard;
        this.fixtureService = fixtureService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public FixtureStatusResponse status() {
        guard.requireEnabled();
        return new FixtureStatusResponse(true, guard.activeEnvironment(), properties.getFixtures().getDefaultDatasetId(),
                properties.getFixtures().getDefaultDatasetVersion(), properties.getFixtures().getDefaultScenario(), false);
    }

    @GetMapping("/jobs/search")
    public FixtureJobSearchResponse searchJobs(
            @RequestParam(required = false) String datasetId,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer salaryMin,
            @RequestParam(required = false) Integer salaryMax,
            @RequestParam(required = false) String remoteType) {
        guard.requireEnabled();
        return fixtureService.searchJobs(datasetId, datasetVersion, scenario, provider, query, location, page, pageSize, sort, salaryMin, salaryMax, remoteType);
    }

    @GetMapping("/jobs/{jobId}")
    public DemoJob job(@PathVariable String jobId, @RequestParam(required = false) String datasetId,
                       @RequestParam(required = false) String datasetVersion) {
        guard.requireEnabled();
        return fixtureService.job(datasetId, datasetVersion, jobId);
    }

    @GetMapping("/postcodes/{postcode}")
    public FixturePostcodeResponse postcode(@PathVariable String postcode) {
        guard.requireEnabled();
        return fixtureService.postcode(postcode);
    }

    @GetMapping("/places")
    public List<FixturePlaceResponse> places(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "10") Integer limit) {
        guard.requireEnabled();
        return fixtureService.places(query, limit);
    }

    @PostMapping("/llm/respond")
    public FixtureLlmResponse llm(@RequestBody FixtureLlmRequest request) {
        guard.requireEnabled();
        return fixtureService.llm(request);
    }

    @PostMapping("/stripe/respond")
    public FixtureStripeResponse stripe(@RequestBody FixtureStripeRequest request) {
        guard.requireEnabled();
        return fixtureService.stripe(request);
    }
}
