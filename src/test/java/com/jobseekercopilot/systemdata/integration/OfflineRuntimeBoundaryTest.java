package com.jobseekercopilot.systemdata.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.systemdata.SystemDataServiceApplication;
import com.jobseekercopilot.systemdata.service.DatasetGenerationService;
import com.jobseekercopilot.systemdata.service.JobDatasetService;
import com.jobseekercopilot.systemdata.service.LiveAcquisitionRunner;
import com.jobseekercopilot.systemdata.service.LocationDatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OfflineRuntimeBoundaryTest {
    @Test
    void testRuntimeContainsNoLiveAcquisitionOrGatewayClientBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            context.register(SystemDataServiceApplication.class);
            context.refresh();

            assertThat(context.getBeansOfType(DatasetGenerationService.class)).isEmpty();
            assertThat(context.getBeansOfType(JobDatasetService.class)).isEmpty();
            assertThat(context.getBeansOfType(LocationDatasetService.class)).isEmpty();
            assertThat(context.getBeansOfType(LiveAcquisitionRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(AdzunaJobsApi.class)).isEmpty();
        }
    }
}
