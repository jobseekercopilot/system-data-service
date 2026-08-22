package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.util.TextSanitiser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DatasetSanitisationService {
    private final TextSanitiser textSanitiser;

    public DatasetSanitisationService(TextSanitiser textSanitiser) {
        this.textSanitiser = textSanitiser;
    }

    public List<DemoJob> sanitiseJobs(List<DemoJob> jobs) {
        return jobs.stream()
                .filter(job -> textSanitiser.usable(job.title()))
                .filter(job -> textSanitiser.usable(job.description()))
                .filter(DemoJob::suitableForDemo)
                .toList();
    }
}
