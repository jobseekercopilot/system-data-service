package com.jobseekercopilot.systemdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.exception.GatewayUnavailableException;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.FixtureJobSearchResponse;
import com.jobseekercopilot.systemdata.model.FixtureLlmRequest;
import com.jobseekercopilot.systemdata.model.FixtureLlmResponse;
import com.jobseekercopilot.systemdata.model.FixturePostcodeResponse;
import com.jobseekercopilot.systemdata.model.FixturePlaceResponse;
import com.jobseekercopilot.systemdata.model.FixtureStripeRequest;
import com.jobseekercopilot.systemdata.model.FixtureStripeResponse;
import com.jobseekercopilot.systemdata.util.DeterministicIds;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FixtureService {
    private final SystemDataProperties properties;
    private final DatasetStorageService datasetStorageService;
    private final ObjectMapper objectMapper;
    private final GovernedFixtureValidator fixtureValidator;

    public FixtureService(SystemDataProperties properties, DatasetStorageService datasetStorageService,
                          ObjectMapper objectMapper, GovernedFixtureValidator fixtureValidator) {
        this.properties = properties;
        this.datasetStorageService = datasetStorageService;
        this.objectMapper = objectMapper;
        this.fixtureValidator = fixtureValidator;
    }

    public FixtureJobSearchResponse searchJobs(String datasetId, String datasetVersion, String scenario, String provider,
                                               String query, String location, Integer page, Integer pageSize,
                                               String sort, Integer salaryMin, Integer salaryMax, String remoteType) {
        String resolvedDataset = valueOrDefault(datasetId, properties.getFixtures().getDefaultDatasetId());
        String resolvedVersion = valueOrDefault(datasetVersion, properties.getFixtures().getDefaultDatasetVersion());
        String resolvedScenario = valueOrDefault(scenario, properties.getFixtures().getDefaultScenario());
        if ("PROVIDER_FAILURE".equalsIgnoreCase(resolvedScenario)) {
            throw new GatewayUnavailableException("Synthetic provider fixture is unavailable");
        }
        int resolvedPage = Math.max(0, page == null ? 0 : page);
        int resolvedPageSize = Math.min(100, Math.max(1, pageSize == null ? 10 : pageSize));
        List<DemoJob> providerJobs = readJobs(resolvedDataset, resolvedVersion).stream()
                .filter(job -> matchesProvider(job, provider))
                .toList();
        var candidates = providerJobs.stream();
        if (hasSearchFilters(query, location, salaryMin, salaryMax, remoteType)) {
            candidates = candidates
                    .filter(job -> matchesQuery(job, query))
                    .filter(job -> matchesLocation(job, location))
                    .filter(job -> matchesSalary(job, salaryMin, salaryMax))
                    .filter(job -> matchesRemote(job, remoteType));
        }
        List<DemoJob> matching = candidates
                .sorted(comparator(sort, query, location))
                .toList();
        int from = Math.min(matching.size(), resolvedPage * resolvedPageSize);
        int to = Math.min(matching.size(), from + resolvedPageSize);
        return new FixtureJobSearchResponse(resolvedDataset, resolvedVersion, resolvedScenario, provider, query, location,
                resolvedPage, resolvedPageSize, matching.size(), matching.subList(from, to));
    }

    public DemoJob job(String datasetId, String datasetVersion, String jobId) {
        return readJobs(valueOrDefault(datasetId, properties.getFixtures().getDefaultDatasetId()),
                valueOrDefault(datasetVersion, properties.getFixtures().getDefaultDatasetVersion())).stream()
                .filter(job -> job.id().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new DatasetGenerationException("Fixture job not found: " + jobId));
    }

    public FixturePostcodeResponse postcode(String postcode) {
        String clean = postcode == null ? "" : postcode.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return knownPostcodes().getOrDefault(clean,
                new FixturePostcodeResponse(postcode, "England", "South East", "Reading", 51.4543, -0.9781, false, "fallback"));
    }

    public List<FixturePlaceResponse> places(String query, Integer limit) {
        String requested = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.min(10, Math.max(1, limit == null ? 10 : limit));
        if (requested.length() < 2) {
            return List.of();
        }
        return knownPostcodes().values().stream()
                .filter(FixturePostcodeResponse::found)
                .filter(place -> contains(place.adminDistrict(), requested)
                        || contains(place.region(), requested)
                        || contains(place.postcode(), requested))
                .sorted(Comparator.comparing(FixturePostcodeResponse::adminDistrict))
                .limit(boundedLimit)
                .map(place -> new FixturePlaceResponse(
                        DeterministicIds.uuidString("fixture-place:" + place.postcode()),
                        place.adminDistrict() + ", " + place.region(),
                        place.postcode(),
                        place.region(),
                        place.adminDistrict(),
                        place.latitude(),
                        place.longitude()))
                .toList();
    }

    public FixtureLlmResponse llm(FixtureLlmRequest request) {
        String operation = valueOrDefault(request.operation(), "GENERIC_GENERATION").toUpperCase(Locale.ROOT);
        String key = StringUtils.hasText(request.fixtureKey()) ? request.fixtureKey() :
                valueOrDefault(request.scenario(), properties.getFixtures().getDefaultScenario()) + ":" + operation;
        FixtureLlmResponse captured = capturedLlmFixture(request, key);
        if (captured != null) {
            return withCurrentContractContent(operation, captured);
        }
        long input = 1_800L + Math.abs(key.hashCode() % 700);
        long output = operation.contains("COVER") ? 1_250L : operation.contains("CV") ? 1_600L : 900L;
        String content = fixtureContent(operation, key);
        return new FixtureLlmResponse("FIXTURE", "fixture-llm-demo-v1", content, input, output, input + output,
                "stop", "2026-07-10T09:00:00Z", key, true);
    }

    private FixtureLlmResponse withCurrentContractContent(
            String operation,
            FixtureLlmResponse captured
    ) {
        if (!"CV_COVER_LETTER_GENERATION".equalsIgnoreCase(operation)) {
            return captured;
        }
        return new FixtureLlmResponse(
                captured.provider(),
                captured.model(),
                combinedCvCoverLetterFixtureContent(),
                captured.inputTokens(),
                captured.outputTokens(),
                captured.totalTokens(),
                captured.finishReason(),
                captured.createdAt(),
                captured.fixtureKey(),
                captured.fixtureMode()
        );
    }

    private FixtureLlmResponse capturedLlmFixture(FixtureLlmRequest request, String key) {
        String resolvedDataset = valueOrDefault(request.datasetId(), properties.getFixtures().getDefaultDatasetId());
        String resolvedVersion = valueOrDefault(request.datasetVersion(), properties.getFixtures().getDefaultDatasetVersion());
        Path fixturePath = datasetStorageService.datasetVersionDirectory(resolvedDataset, resolvedVersion).resolve("llm-fixtures.json");
        if (!Files.exists(fixturePath)) {
            return null;
        }
        fixtureValidator.requireApproved(fixturePath.getParent());
        try {
            Map<String, FixtureLlmResponse> fixtures = objectMapper.readValue(
                    fixturePath.toFile(),
                    new TypeReference<Map<String, FixtureLlmResponse>>() {
                    });
            FixtureLlmResponse exact = fixtures.get(key);
            if (exact != null) {
                return exact;
            }
            String prefix = valueOrDefault(request.scenario(), properties.getFixtures().getDefaultScenario())
                    + ":" + valueOrDefault(request.operation(), "GENERIC_GENERATION").toUpperCase(Locale.ROOT) + ":";
            List<FixtureLlmResponse> operationMatches = fixtures.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .toList();
            return operationMatches.size() == 1 ? operationMatches.get(0) : null;
        } catch (Exception ex) {
            throw new DatasetGenerationException("Failed to read LLM fixture file: " + fixturePath, ex);
        }
    }

    public FixtureStripeResponse stripe(FixtureStripeRequest request) {
        String operation = valueOrDefault(request.operation(), "CHECKOUT_SESSION").toUpperCase(Locale.ROOT);
        String suffix = DeterministicIds.uuidString("stripe:" + operation + ":" + request.userId() + ":" + request.pricingPlanId()).substring(0, 8);
        return new FixtureStripeResponse(operation, "cs_test_demo_" + suffix, "pi_demo_" + suffix,
                "evt_demo_" + suffix, "https://checkout.stripe.test/fixture/cs_test_demo_" + suffix,
                operation.contains("FAIL") ? "failed" : "succeeded", true);
    }

    private List<DemoJob> readJobs(String datasetId, String datasetVersion) {
        Path directory = datasetStorageService.datasetVersionDirectory(datasetId, datasetVersion);
        if (!Files.exists(directory)) {
            throw new DatasetGenerationException("Fixture dataset does not exist: " + datasetId + ":" + datasetVersion);
        }
        fixtureValidator.requireApproved(directory);
        return datasetStorageService.read(directory).jobs().jobs();
    }

    private boolean matchesProvider(DemoJob job, String provider) {
        if (!StringUtils.hasText(provider)) {
            return true;
        }
        String requested = provider.toLowerCase(Locale.ROOT).replace("_", "-");
        String actual = job.sourceProvider() == null ? "" : job.sourceProvider().toLowerCase(Locale.ROOT);
        return actual.equals(requested) || actual.equals(requested + "-gateway") || actual.startsWith(requested + "-");
    }

    private boolean matchesQuery(DemoJob job, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(job.title(), q) || contains(job.description(), q) || contains(job.sourceQuery(), q)
                || job.skills().stream().anyMatch(skill -> skill.toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean matchesLocation(DemoJob job, String location) {
        if (!StringUtils.hasText(location)) {
            return true;
        }
        String loc = location.toLowerCase(Locale.ROOT);
        if (contains(job.locationName(), loc)
                || contains(job.region(), loc)
                || contains(job.postcode(), loc)
                || (StringUtils.hasText(job.locationName())
                        && loc.contains(job.locationName().toLowerCase(Locale.ROOT)))
                || loc.contains("remote") && contains(job.remoteType(), "remote")) {
            return true;
        }
        String compact = location.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return knownPostcodes().entrySet().stream()
                .filter(entry -> compact.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .anyMatch(postcode -> contains(job.locationName(), postcode.adminDistrict().toLowerCase(Locale.ROOT))
                        || contains(job.region(), postcode.region().toLowerCase(Locale.ROOT)));
    }

    private boolean matchesSalary(DemoJob job, Integer salaryMin, Integer salaryMax) {
        return (salaryMin == null || job.salaryMaximum() == null || job.salaryMaximum() >= salaryMin)
                && (salaryMax == null || job.salaryMinimum() == null || job.salaryMinimum() <= salaryMax);
    }

    private boolean matchesRemote(DemoJob job, String remoteType) {
        return !StringUtils.hasText(remoteType) || contains(job.remoteType(), remoteType.toLowerCase(Locale.ROOT));
    }

    private boolean hasSearchFilters(String query, String location, Integer salaryMin, Integer salaryMax,
                                     String remoteType) {
        return StringUtils.hasText(query)
                || StringUtils.hasText(location)
                || salaryMin != null
                || salaryMax != null
                || StringUtils.hasText(remoteType);
    }

    private Comparator<DemoJob> comparator(String sort, String query, String location) {
        Comparator<DemoJob> byRelevance = Comparator.comparingInt(job -> score(job, query, location));
        Comparator<DemoJob> bySalary = Comparator.comparing((DemoJob job) -> job.salaryMinimum() == null ? 0 : job.salaryMinimum()).reversed();
        Comparator<DemoJob> byDate = Comparator.comparing((DemoJob job) -> valueOrDefault(job.datePosted(), "")).reversed();
        Comparator<DemoJob> selected = "salary".equalsIgnoreCase(sort) ? bySalary : "date".equalsIgnoreCase(sort) ? byDate : byRelevance;
        return selected.thenComparing(DemoJob::id);
    }

    private int score(DemoJob job, String query, String location) {
        int score = 100;
        if (StringUtils.hasText(query) && contains(job.title(), query.toLowerCase(Locale.ROOT))) score -= 40;
        if (StringUtils.hasText(location) && contains(job.locationName(), location.toLowerCase(Locale.ROOT))) score -= 30;
        if (job.suitableForDemo()) score -= 10;
        return score;
    }

    private Map<String, FixturePostcodeResponse> knownPostcodes() {
        Map<String, FixturePostcodeResponse> postcodes = new LinkedHashMap<>();
        postcodes.put("RG11AA", new FixturePostcodeResponse("RG1 1AA", "England", "South East", "Reading", 51.4543, -0.9781, true, "fixture"));
        postcodes.put("B11AA", new FixturePostcodeResponse("B1 1AA", "England", "West Midlands", "Birmingham", 52.4797, -1.9027, true, "fixture"));
        postcodes.put("M11AE", new FixturePostcodeResponse("M1 1AE", "England", "North West", "Manchester", 53.4808, -2.2426, true, "fixture"));
        postcodes.put("LS11UR", new FixturePostcodeResponse("LS1 1UR", "England", "Yorkshire and The Humber", "Leeds", 53.8008, -1.5491, true, "fixture"));
        postcodes.put("BS11AA", new FixturePostcodeResponse("BS1 1AA", "England", "South West", "Bristol", 51.4545, -2.5879, true, "fixture"));
        postcodes.put("EC1A1BB", new FixturePostcodeResponse("EC1A 1BB", "England", "London", "City of London", 51.5202, -0.0978, true, "fixture"));
        return postcodes;
    }

    private String fixtureContent(String operation, String key) {
        if ("CV_COVER_LETTER_GENERATION".equalsIgnoreCase(operation)) {
            return combinedCvCoverLetterFixtureContent();
        }
        return """
                {
                  "fixtureMode": true,
                  "fixtureKey": "%s",
                  "operation": "%s",
                  "message": "Deterministic Job Seeker Copilot fixture response for E2E and demo runs.",
                  "content": "Alex Taylor is presented as a capable software developer with Java, Spring Boot, Angular and delivery experience tailored to the selected role."
                }
                """.formatted(key, operation);
    }

    private String combinedCvCoverLetterFixtureContent() {
        return """
                {
                  "cv": {
                    "title": "Tailored Java Software Developer CV",
                    "targetRole": "Java Software Developer",
                    "personalSummary": "Java software developer experienced in building reliable Spring Boot microservices and accessible Angular products. Combines pragmatic API design, automated testing and cloud delivery with a record of improving deployment speed and reducing escaped defects in collaborative Agile teams.",
                    "coreSkills": [],
                    "projects": [],
                    "qualifications": [],
                    "workHistory": []
                  },
                  "coverLetter": {
                    "title": "Java Software Developer Cover Letter",
                    "jobTitle": "Java Software Developer",
                    "companyName": "Northstar Digital Labs",
                    "greeting": "Dear Hiring Manager,",
                    "openingParagraph": "Please consider my application for this role.",
                    "bodyParagraphs": [
                      {"text":"My profile includes confirmed software-delivery experience.","disposition":"REWORDED","evidenceIds":["PROFILE.SKILL.1"]},
                      {"text":"That confirmed experience is relevant to this Java Software Developer role.","disposition":"REWORDED","evidenceIds":["PROFILE.SKILL.1","JOB.TITLE"]},
                      {"text":"I would bring that confirmed experience to the role.","disposition":"REWORDED","evidenceIds":["PROFILE.SKILL.1"]},
                      {"text":"I welcome the opportunity to discuss how that experience supports the Java Software Developer role.","disposition":"REWORDED","evidenceIds":["PROFILE.SKILL.1","JOB.TITLE"]}
                    ],
                    "closingParagraph": "Thank you for considering my application.",
                    "signOff": "Yours sincerely"
                  },
                  "generationNotes": {
                    "assumptionsMade": [],
                    "missingInformation": [],
                    "tailoringSummary": "Fixture-generated documents tailored to the governed synthetic Java vacancy."
                  },
                  "claims": [
                    {"claimId":"CLAIM-001","disposition":"SUPPORTED","evidenceIds":["JOB.TITLE"],"contentPaths":["/cv/targetRole"],"reviewText":""},
                    {"claimId":"CLAIM-002","disposition":"SUPPORTED","evidenceIds":["JOB.TITLE"],"contentPaths":["/coverLetter/jobTitle"],"reviewText":""},
                    {"claimId":"CLAIM-003","disposition":"SUPPORTED","evidenceIds":["JOB.COMPANY"],"contentPaths":["/coverLetter/companyName"],"reviewText":""}
                  ],
                  "canonicalApplicationClaims": {
                    "opening": {
                      "claimId":"CLAIM-9001","disposition":"SUPPORTED",
                      "generationIntentEvidenceId":"REQUEST.GENERATION_INTENT",
                      "jobTitleEvidenceId":"JOB.TITLE","companyEvidenceId":"JOB.COMPANY",
                      "contentPath":"/coverLetter/openingParagraph","reviewText":""
                    },
                    "closing": {
                      "claimId":"CLAIM-9002","disposition":"SUPPORTED",
                      "generationIntentEvidenceId":"REQUEST.GENERATION_INTENT",
                      "jobTitleEvidenceId":"JOB.TITLE","companyEvidenceId":"JOB.COMPANY",
                      "contentPath":"/coverLetter/closingParagraph","reviewText":""
                    }
                  },
                  "personalSummaryClaim": {
                    "claimId":"CLAIM-9003","disposition":"REWORDED",
                    "evidenceIds":["PROFILE.SKILL.1","JOB.TITLE"],
                    "contentPath":"/cv/personalSummary","reviewText":""
                  }
                }
                """;
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
