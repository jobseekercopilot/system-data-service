package com.jobseekercopilot.systemdata.normaliser;

import com.jobseekercopilot.generated.reedgateway.model.ReedJobDto;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.service.DemoJobQualityAssessor;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReedJobNormaliser {
    private final TextSanitiser textSanitiser;
    private final DatasetIdGenerator idGenerator;
    private final DemoJobQualityAssessor qualityAssessor;

    public ReedJobNormaliser(TextSanitiser textSanitiser, DatasetIdGenerator idGenerator, DemoJobQualityAssessor qualityAssessor) {
        this.textSanitiser = textSanitiser;
        this.idGenerator = idGenerator;
        this.qualityAssessor = qualityAssessor;
    }

    public DemoJob normalise(ReedJobDto job, Instant retrievedAt) {
        return normalise(job, retrievedAt, null, null);
    }

    public DemoJob normalise(ReedJobDto job, Instant retrievedAt, String sourceQuery, String sourceLocation) {
        String title = textSanitiser.clean(job.getJobTitle());
        String company = textSanitiser.clean(job.getEmployerName());
        String location = textSanitiser.clean(job.getLocationName());
        String description = textSanitiser.clean(job.getJobDescription());
        Integer salaryMinimum = parseInteger(job.getMinimumSalary());
        Integer salaryMaximum = parseInteger(job.getMaximumSalary());

        return new DemoJob(
                idGenerator.stableJobId("reed-gateway", job.getJobId(), title, company, location),
                job.getJobId(),
                "reed-gateway",
                title,
                company,
                company,
                location,
                null,
                null,
                null,
                null,
                salaryMinimum,
                salaryMaximum,
                job.getCurrency() == null ? "GBP" : job.getCurrency(),
                "YEAR",
                textSanitiser.clean(job.getEmploymentType()),
                null,
                inferRemoteType(location, description),
                null,
                null,
                inferSeniority(title),
                description,
                truncate(description),
                List.of(),
                List.of(),
                List.of(),
                job.getDate(),
                null,
                job.getJobUrl(),
                retrievedAt,
                sourceQuery,
                sourceLocation,
                DemoJob.metadata(),
                DemoJob.metadata(),
                qualityAssessor.suitable(title, company, location, description, salaryMinimum, salaryMaximum, "YEAR", job.getDate()),
                qualityAssessor.qualityScore(title, company, location, description, salaryMinimum, salaryMaximum, "YEAR", job.getDate(), null, null));
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replace(",", "").replaceAll("[^0-9.]", "");
        if (digits.isBlank()) return null;
        return (int) Math.round(Double.parseDouble(digits));
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
