package com.jobseekercopilot.systemdata.normaliser;

import com.jobseekercopilot.generated.jsearchgateway.model.JSearchJob;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JSearchJobNormaliser {
    private final TextSanitiser textSanitiser;
    private final DatasetIdGenerator idGenerator;
    private final DemoJobQualityAssessor qualityAssessor;

    public JSearchJobNormaliser(TextSanitiser textSanitiser, DatasetIdGenerator idGenerator, DemoJobQualityAssessor qualityAssessor) {
        this.textSanitiser = textSanitiser;
        this.idGenerator = idGenerator;
        this.qualityAssessor = qualityAssessor;
    }

    public DemoJob normalise(JSearchJob job, Instant retrievedAt) {
        return normalise(job, retrievedAt, null, null);
    }

    public DemoJob normalise(JSearchJob job, Instant retrievedAt, String sourceQuery, String sourceLocation) {
        String title = textSanitiser.clean(job.getTitle());
        String company = textSanitiser.clean(job.getCompanyName());
        String location = textSanitiser.clean(job.getLocationDisplayName());
        String description = textSanitiser.clean(job.getDescription());
        var metadata = DemoJob.metadata();
        metadata.put("publisher", job.getPublisher());
        metadata.put("directApply", job.getDirectApply());
        metadata.put("applyOptions", job.getApplyOptions());

        return new DemoJob(
                idGenerator.stableJobId("jsearch-gateway", job.getExternalJobId(), title, company, location),
                job.getExternalJobId(),
                "jsearch-gateway",
                title,
                company,
                company,
                location,
                null,
                textSanitiser.clean(job.getState()),
                job.getLatitude() == null ? null : job.getLatitude().doubleValue(),
                job.getLongitude() == null ? null : job.getLongitude().doubleValue(),
                job.getSalaryMinimum(),
                job.getSalaryMaximum(),
                job.getSalaryCurrency() == null ? "GBP" : job.getSalaryCurrency(),
                job.getSalaryPeriod(),
                textSanitiser.clean(job.getEmploymentType()),
                null,
                Boolean.TRUE.equals(job.getRemote()) ? "REMOTE" : inferRemoteType(location, description),
                null,
                null,
                inferSeniority(title),
                description,
                truncate(description),
                List.of(),
                List.of(),
                List.of(),
                job.getPostedAt(),
                job.getExpiresAt(),
                job.getPrimaryApplyUrl(),
                retrievedAt,
                sourceQuery,
                sourceLocation,
                metadata,
                DemoJob.metadata(),
                isUk(job.getCountry()) && qualityAssessor.suitable(title, company, location, description,
                        job.getSalaryMinimum(), job.getSalaryMaximum(), job.getSalaryPeriod(), job.getPostedAt()),
                qualityAssessor.qualityScore(title, company, location, description, job.getSalaryMinimum(), job.getSalaryMaximum(),
                        job.getSalaryPeriod(), job.getPostedAt(), job.getLatitude() == null ? null : job.getLatitude().doubleValue(),
                        job.getLongitude() == null ? null : job.getLongitude().doubleValue()));
    }

    private boolean isUk(String country) {
        return country == null || "gb".equalsIgnoreCase(country) || "uk".equalsIgnoreCase(country)
                || "united kingdom".equalsIgnoreCase(country);
    }

    private String inferRemoteType(String location, String description) {
        String combined = ((location == null ? "" : location) + " " + (description == null ? "" : description)).toLowerCase();
        if (combined.contains("hybrid")) return "HYBRID";
        if (combined.contains("remote")) return "REMOTE";
        return "ONSITE";
    }

    private String inferSeniority(String title) {
        if (title == null) return "UNSPECIFIED";
        String value = title.toLowerCase();
        if (value.contains("junior") || value.contains("graduate")) return "JUNIOR";
        if (value.contains("senior") || value.contains("lead") || value.contains("principal")) return "SENIOR";
        return "UNSPECIFIED";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 220) return value;
        return value.substring(0, 217) + "...";
    }

}
