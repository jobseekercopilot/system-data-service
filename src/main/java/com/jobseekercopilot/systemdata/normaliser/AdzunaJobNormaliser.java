package com.jobseekercopilot.systemdata.normaliser;

import com.jobseekercopilot.generated.adzunagateway.model.AdzunaJob;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdzunaJobNormaliser {
    private final TextSanitiser textSanitiser;
    private final DatasetIdGenerator idGenerator;
    private final DemoJobQualityAssessor qualityAssessor;

    public AdzunaJobNormaliser(TextSanitiser textSanitiser, DatasetIdGenerator idGenerator, DemoJobQualityAssessor qualityAssessor) {
        this.textSanitiser = textSanitiser;
        this.idGenerator = idGenerator;
        this.qualityAssessor = qualityAssessor;
    }

    public DemoJob normalise(AdzunaJob job, Instant retrievedAt) {
        return normalise(job, retrievedAt, null, null);
    }

    public DemoJob normalise(AdzunaJob job, Instant retrievedAt, String sourceQuery, String sourceLocation) {
        String title = textSanitiser.clean(job.getTitle());
        String company = textSanitiser.clean(job.getCompanyName());
        String location = textSanitiser.clean(job.getLocationDisplayName());
        String description = textSanitiser.clean(job.getDescription());
        var metadata = DemoJob.metadata();
        metadata.put("salaryPredicted", job.getSalaryPredicted());
        metadata.put("locationAreas", job.getLocationAreas());

        return new DemoJob(
                idGenerator.stableJobId("adzuna-gateway", job.getExternalJobId(), title, company, location),
                job.getExternalJobId(),
                "adzuna-gateway",
                title,
                company,
                company,
                location,
                null,
                null,
                job.getLatitude() == null ? null : job.getLatitude().doubleValue(),
                job.getLongitude() == null ? null : job.getLongitude().doubleValue(),
                job.getSalaryMinimum(),
                job.getSalaryMaximum(),
                "GBP",
                "YEAR",
                textSanitiser.clean(job.getEmploymentType()),
                null,
                inferRemoteType(location, description),
                textSanitiser.clean(job.getContractType()),
                textSanitiser.clean(job.getCategory()),
                inferSeniority(title),
                description,
                truncate(description),
                List.of(),
                List.of(),
                List.of(),
                job.getPostedAt(),
                null,
                job.getRedirectUrl(),
                retrievedAt,
                sourceQuery,
                sourceLocation,
                metadata,
                DemoJob.metadata(),
                qualityAssessor.suitable(title, company, location, description, job.getSalaryMinimum(), job.getSalaryMaximum(), "YEAR", job.getPostedAt()),
                qualityAssessor.qualityScore(title, company, location, description, job.getSalaryMinimum(), job.getSalaryMaximum(), "YEAR",
                        job.getPostedAt(), job.getLatitude() == null ? null : job.getLatitude().doubleValue(),
                        job.getLongitude() == null ? null : job.getLongitude().doubleValue()));
    }

    private String inferRemoteType(String location, String description) {
        String combined = ((location == null ? "" : location) + " " + (description == null ? "" : description)).toLowerCase();
        if (combined.contains("hybrid")) {
            return "HYBRID";
        }
        if (combined.contains("remote")) {
            return "REMOTE";
        }
        return "ONSITE";
    }

    private String inferSeniority(String title) {
        if (title == null) {
            return "UNSPECIFIED";
        }
        String value = title.toLowerCase();
        if (value.contains("junior") || value.contains("graduate")) {
            return "JUNIOR";
        }
        if (value.contains("senior") || value.contains("lead") || value.contains("principal")) {
            return "SENIOR";
        }
        return "UNSPECIFIED";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 220) {
            return value;
        }
        return value.substring(0, 217) + "...";
    }

}
