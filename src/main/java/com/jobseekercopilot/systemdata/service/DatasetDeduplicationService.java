package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.model.DemoJob;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DatasetDeduplicationService {

    public List<DemoJob> deduplicateJobs(List<DemoJob> jobs) {
        return deduplicateJobsWithStats(jobs).jobs();
    }

    public DeduplicationResult deduplicateJobsWithStats(List<DemoJob> jobs) {
        var seen = new LinkedHashMap<String, DemoJob>();
        for (DemoJob job : jobs) {
            String key = key(job);
            DemoJob existing = seen.get(key);
            if (existing == null || preferred(job, existing)) {
                seen.put(key, job);
            }
        }
        List<DemoJob> deterministic = seen.values().stream()
                .sorted(Comparator.comparing(DemoJob::id, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new DeduplicationResult(deterministic, jobs.size() - seen.size());
    }

    private boolean preferred(DemoJob candidate, DemoJob existing) {
        int completeness = Integer.compare(completenessScore(candidate), completenessScore(existing));
        if (completeness != 0) {
            return completeness > 0;
        }
        return Comparator.nullsLast(String::compareTo).compare(candidate.id(), existing.id()) < 0;
    }

    private String key(DemoJob job) {
        if (job.externalReference() != null && !job.externalReference().isBlank()) {
            return normalise(job.sourceProvider()) + "|" + normalise(job.externalReference());
        }
        if (job.sourceUrl() != null && !job.sourceUrl().isBlank()) {
            return "url|" + normalise(job.sourceUrl());
        }
        return String.join("|", normalise(job.title()), normalise(job.companyName()), normalise(job.locationName()));
    }

    private String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", "");
    }

    private int completenessScore(DemoJob job) {
        int score = 0;
        if (present(job.title())) score++;
        if (present(job.companyName())) score++;
        if (present(job.locationName())) score++;
        if (present(job.description())) score += job.description().length() > 120 ? 2 : 1;
        if (job.salaryMinimum() != null || job.salaryMaximum() != null) score++;
        if (present(job.sourceUrl())) score++;
        if (job.latitude() != null && job.longitude() != null) score++;
        if (present(job.remoteType())) score++;
        return score;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record DeduplicationResult(List<DemoJob> jobs, int duplicatesRemoved) {
    }
}
