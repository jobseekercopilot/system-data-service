package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetGenerationRequest;
import com.jobseekercopilot.systemdata.model.DatasetGenerationReport;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResponse;
import com.jobseekercopilot.systemdata.model.DatasetGenerationResult;
import com.jobseekercopilot.systemdata.model.DatasetManifest;
import com.jobseekercopilot.systemdata.model.DatasetSource;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.JobDataset;
import com.jobseekercopilot.systemdata.model.LocationDataset;
import com.jobseekercopilot.systemdata.util.SemanticVersionValidator;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DatasetGenerationService {
    private final SystemDataProperties properties;
    private final JobDatasetService jobDatasetService;
    private final LocationDatasetService locationDatasetService;
    private final DatasetSanitisationService sanitisationService;
    private final DatasetDeduplicationService deduplicationService;
    private final DatasetValidationService validationService;
    private final DatasetManifestService manifestService;
    private final DatasetStorageService storageService;
    private final SemanticVersionValidator semanticVersionValidator;
    private final DemoJobQualityAssessor qualityAssessor;

    public DatasetGenerationService(SystemDataProperties properties, JobDatasetService jobDatasetService,
                                    LocationDatasetService locationDatasetService,
                                    DatasetSanitisationService sanitisationService,
                                    DatasetDeduplicationService deduplicationService,
                                    DatasetValidationService validationService,
                                    DatasetManifestService manifestService,
                                    DatasetStorageService storageService,
                                    SemanticVersionValidator semanticVersionValidator,
                                    DemoJobQualityAssessor qualityAssessor) {
        this.properties = properties;
        this.jobDatasetService = jobDatasetService;
        this.locationDatasetService = locationDatasetService;
        this.sanitisationService = sanitisationService;
        this.deduplicationService = deduplicationService;
        this.validationService = validationService;
        this.manifestService = manifestService;
        this.storageService = storageService;
        this.semanticVersionValidator = semanticVersionValidator;
        this.qualityAssessor = qualityAssessor;
    }

    public DatasetGenerationResponse generateResponse(DatasetGenerationRequest request) {
        DatasetGenerationResult result = generate(request);
        return new DatasetGenerationResponse(
                result.manifest().datasetId(),
                result.manifest().version(),
                result.outputDirectory(),
                request != null && Boolean.TRUE.equals(request.overwrite()),
                storageService.lastBackupDirectory(),
                result.jobs().jobs().size(),
                result.locations().locations().size(),
                result.report().duplicatesRemoved(),
                result.report().warnings(),
                result.report().providerStatuses());
    }

    public DatasetGenerationResult generate(DatasetGenerationRequest request) {
        Instant startedAt = Instant.now();
        List<String> queries = useOrDefault(request == null ? null : request.queries(), properties.getGeneration().getQueries());
        List<String> locations = useOrDefault(request == null ? null : request.locations(), properties.getGeneration().getLocations());
        int maximumResultsPerProvider = request != null && request.maximumResultsPerProvider() != null
                ? request.maximumResultsPerProvider()
                : properties.getGeneration().getMaximumJobsPerProvider();
        String datasetId = valueOrDefault(request == null ? null : request.datasetId(), properties.getGeneration().getDefaultDatasetId());
        String version = valueOrDefault(request == null ? null : request.version(), properties.getGeneration().getDefaultVersion());
        semanticVersionValidator.requireValid(version);

        var providerJobs = jobDatasetService.gather(queries, locations, request == null ? null : request.providers(), maximumResultsPerProvider);
        int invalidSalariesDetected = (int) providerJobs.jobs().stream()
                .filter(job -> qualityAssessor.invalidSalary(job.salaryMinimum(), job.salaryMaximum(), job.salaryPeriod()))
                .count();
        int paidTrainingRecordsDetected = (int) providerJobs.jobs().stream()
                .filter(job -> qualityAssessor.paidTraining(job.title(), job.description()))
                .count();
        var sanitisedJobs = sanitisationService.sanitiseJobs(providerJobs.jobs());
        int invalidRecordsRemoved = providerJobs.jobs().size() - sanitisedJobs.size();
        var deduplication = deduplicationService.deduplicateJobsWithStats(sanitisedJobs);
        var jobs = curateForDemo(deduplication.jobs());
        List<String> postcodes = availablePostcodes(request == null ? null : request.postcodes(), jobs);
        var providerLocations = locationDatasetService.gather(postcodes, jobs);
        var validation = validationService.validate(jobs, providerLocations.locations());
        var sources = new ArrayList<DatasetSource>();
        sources.addAll(providerJobs.sources());
        sources.addAll(providerLocations.sources());

        DatasetManifest manifest = manifestService.create(
                datasetId,
                valueOrDefault(request == null ? null : request.name(), properties.getGeneration().getDefaultName()),
                version,
                valueOrDefault(request == null ? null : request.description(), "UK software development jobs for demos and automated tests"),
                queries,
                locations,
                sources,
                jobs,
                providerLocations.locations(),
                validation);
        JobDataset jobDataset = new JobDataset("1.0", jobs);
        LocationDataset locationDataset = new LocationDataset("1.0", providerLocations.locations());
        Instant finishedAt = Instant.now();
        var warnings = new ArrayList<String>(validation.warnings());
        providerJobs.providerStatuses().forEach((provider, status) -> {
            if (status.startsWith("FAILED")) {
                warnings.add(provider + " failed: " + status.substring("FAILED: ".length()));
            }
        });
        DatasetGenerationReport report = new DatasetGenerationReport(
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt).toMillis(),
                providerJobs.providersCalled(),
                providerJobs.providerStatuses(),
                providerJobs.rawResultCounts(),
                sanitisedJobs.size(),
                invalidRecordsRemoved,
                invalidRecordsRemoved,
                invalidSalariesDetected,
                paidTrainingRecordsDetected,
                deduplication.duplicatesRemoved(),
                jobs.size(),
                providerLocations.locations().size(),
                providerLocations.attemptedLookups(),
                providerLocations.succeededLookups(),
                warnings);
        boolean overwrite = request != null && Boolean.TRUE.equals(request.overwrite());
        Path outputDirectory = storageService.save(manifest, jobDataset, locationDataset, report, overwrite);
        return new DatasetGenerationResult(manifest, outputDirectory, jobDataset, locationDataset, report);
    }

    private List<String> availablePostcodes(List<String> requestedPostcodes, List<DemoJob> jobs) {
        var postcodes = new LinkedHashSet<String>();
        if (requestedPostcodes != null) {
            requestedPostcodes.stream().filter(value -> value != null && !value.isBlank()).forEach(postcodes::add);
        }
        jobs.stream()
                .map(DemoJob::postcode)
                .filter(value -> value != null && !value.isBlank())
                .forEach(postcodes::add);
        return List.copyOf(postcodes);
    }

    private List<DemoJob> curateForDemo(List<DemoJob> jobs) {
        int perProviderLimit = 40;
        int totalLimit = 120;
        Map<String, List<DemoJob>> byProvider = new LinkedHashMap<>();
        jobs.stream()
                .sorted(Comparator.comparingDouble(DemoJob::qualityScore).reversed()
                        .thenComparing(job -> valueOrDefault(job.datePosted(), ""), Comparator.reverseOrder())
                        .thenComparing(DemoJob::id))
                .forEach(job -> byProvider.computeIfAbsent(job.sourceProvider(), ignored -> new ArrayList<>()).add(job));

        var selected = new ArrayList<DemoJob>();
        byProvider.values().forEach(providerJobs -> providerJobs.stream().limit(perProviderLimit).forEach(selected::add));
        return selected.stream()
                .sorted(Comparator.comparingDouble(DemoJob::qualityScore).reversed()
                        .thenComparing(job -> valueOrDefault(job.datePosted(), ""), Comparator.reverseOrder())
                        .thenComparing(DemoJob::id))
                .limit(totalLimit)
                .toList();
    }

    private List<String> useOrDefault(List<String> values, List<String> fallback) {
        return values == null || values.isEmpty() ? fallback : values;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
