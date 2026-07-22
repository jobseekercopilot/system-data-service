package com.jobseekercopilot.systemdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("live-acquisition")
public class LiveAcquisitionRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LiveAcquisitionRunner.class);
    private final DatasetGenerationService generationService;

    public LiveAcquisitionRunner(DatasetGenerationService generationService) {
        this.generationService = generationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = generationService.generateResponse(null);
        log.info("Live acquisition completed datasetId={} version={} jobs={} locations={} status=PENDING_PROVENANCE_REVIEW",
                result.datasetId(), result.version(), result.jobCount(), result.locationCount());
    }
}
