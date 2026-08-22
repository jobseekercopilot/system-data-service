package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.model.DatasetValidationResult;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.DemoLocation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DatasetValidationService {

    public DatasetValidationResult validate(List<DemoJob> jobs, List<DemoLocation> locations) {
        List<String> warnings = new ArrayList<>();
        if (jobs.size() < 30) {
            warnings.add("Fewer than target 30 jobs were gathered.");
        }
        if (jobs.stream().map(DemoJob::companyName).filter(value -> value != null && !value.isBlank()).distinct().count() < 5) {
            warnings.add("Fewer than target 5 distinct employers were gathered.");
        }
        if (locations.size() < 5) {
            warnings.add("Fewer than target 5 locations were gathered.");
        }
        return new DatasetValidationResult(jobs.stream().allMatch(job -> job.title() != null && job.description() != null), warnings);
    }
}
