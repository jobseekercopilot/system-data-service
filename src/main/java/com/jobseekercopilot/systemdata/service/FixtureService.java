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
        List<DemoJob> matching = readJobs(resolvedDataset, resolvedVersion).stream()
                .filter(job -> matchesProvider(job, provider))
                .filter(job -> matchesQuery(job, query))
                .filter(job -> matchesLocation(job, location))
                .filter(job -> matchesSalary(job, salaryMin, salaryMax))
                .filter(job -> matchesRemote(job, remoteType))
                .sorted(comparator(sort, query, location))
                .toList();
        if (matching.isEmpty()) {
            matching = readJobs(resolvedDataset, resolvedVersion).stream()
                    .filter(job -> matchesProvider(job, provider))
                    .sorted(comparator(sort, query, location))
                    .toList();
        }
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

    public FixtureLlmResponse llm(FixtureLlmRequest request) {
        String operation = valueOrDefault(request.operation(), "GENERIC_GENERATION").toUpperCase(Locale.ROOT);
        String key = StringUtils.hasText(request.fixtureKey()) ? request.fixtureKey() :
                valueOrDefault(request.scenario(), properties.getFixtures().getDefaultScenario()) + ":" + operation;
        FixtureLlmResponse captured = capturedLlmFixture(request, key);
        if (captured != null) {
            return captured;
        }
        long input = 1_800L + Math.abs(key.hashCode() % 700);
        long output = operation.contains("COVER") ? 1_250L : operation.contains("CV") ? 1_600L : 900L;
        String content = fixtureContent(operation, key);
        return new FixtureLlmResponse("FIXTURE", "fixture-llm-demo-v1", content, input, output, input + output,
                "stop", "2026-07-10T09:00:00Z", key, true);
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
        return contains(job.locationName(), loc) || contains(job.region(), loc) || contains(job.postcode(), loc)
                || loc.contains("remote") && contains(job.remoteType(), "remote");
    }

    private boolean matchesSalary(DemoJob job, Integer salaryMin, Integer salaryMax) {
        return (salaryMin == null || job.salaryMaximum() == null || job.salaryMaximum() >= salaryMin)
                && (salaryMax == null || job.salaryMinimum() == null || job.salaryMinimum() <= salaryMax);
    }

    private boolean matchesRemote(DemoJob job, String remoteType) {
        return !StringUtils.hasText(remoteType) || contains(job.remoteType(), remoteType.toLowerCase(Locale.ROOT));
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
                    "title": "Alex Taylor - Tailored Software Developer CV",
                    "targetRole": "Software Developer",
                    "personalSummary": "Software developer with hands-on experience building Java, Spring Boot and Angular applications, with a practical track record of delivering reliable APIs, clean user interfaces and well-tested services. Brings strong communication, secure coding habits and an evidence-led approach to solving delivery problems.",
                    "coreSkills": [
                      {
                        "name": "Java and Spring Boot",
                        "evidence": "Built and maintained REST APIs, service integrations and backend workflows across portfolio and commercial projects."
                      },
                      {
                        "name": "Angular and TypeScript",
                        "evidence": "Created user-focused interfaces and connected them to backend services with clear state and error handling."
                      },
                      {
                        "name": "Testing and delivery discipline",
                        "evidence": "Uses automated tests, version control and incremental delivery practices to keep releases predictable."
                      }
                    ],
                    "qualifications": [
                      {
                        "qualificationName": "BSc Computer Science",
                        "issuingBody": "University of Birmingham",
                        "status": "Completed",
                        "grade": "2:1",
                        "dateAchieved": "2021",
                        "expectedCompletion": null
                      }
                    ],
                    "workHistory": [
                      {
                        "jobTitle": "Software Developer",
                        "employer": "BrightTech Solutions",
                        "startDate": "July 2021",
                        "endDate": "Present",
                        "responsibilities": [
                          "Developed Java and Spring Boot services for customer-facing applications.",
                          "Implemented Angular components and connected them to REST APIs.",
                          "Worked with Git, SQL databases and CI checks to support reliable delivery."
                        ],
                        "tailoredDescription": "Relevant experience for software developer roles requiring strong backend engineering, frontend collaboration and production-minded delivery."
                      },
                      {
                        "jobTitle": "Software Engineering Intern",
                        "employer": "CodeBridge Ltd",
                        "startDate": "June 2020",
                        "endDate": "August 2020",
                        "responsibilities": [
                          "Supported internal tooling using Java and TypeScript.",
                          "Contributed to API testing and documentation improvements.",
                          "Worked with senior engineers during agile delivery ceremonies."
                        ],
                        "tailoredDescription": "Early career experience that demonstrates practical engineering foundations and team collaboration."
                      }
                    ]
                  },
                  "coverLetter": {
                    "title": "Alex Taylor - Software Developer Cover Letter",
                    "jobTitle": "Software Developer",
                    "companyName": "Forward Role",
                    "greeting": "Dear Hiring Manager,",
                    "openingParagraph": "I am pleased to apply for the Software Developer role. My experience with Java, Spring Boot, Angular and REST API delivery aligns closely with the practical engineering skills described in the vacancy.",
                    "bodyParagraphs": [
                      "In my current role I have developed backend services, integrated user-facing features and worked with SQL-backed systems, giving me a solid understanding of how reliable applications are built and maintained.",
                      "I also bring a careful approach to testing, code review and incremental delivery. I enjoy working with product and engineering colleagues to turn requirements into clear, maintainable software.",
                      "The role appeals to me because it combines hands-on development with the chance to contribute to meaningful, well-engineered systems."
                    ],
                    "closingParagraph": "Thank you for considering my application. I would welcome the opportunity to discuss how my skills and experience could support your team.",
                    "signOff": "Yours sincerely,\\nAlex Taylor"
                  },
                  "generationNotes": {
                    "assumptionsMade": [
                      "Used the selected job details and Alex Taylor's seeded profile to tailor the documents.",
                      "Kept the tone professional and suitable for a UK software developer application."
                    ],
                    "missingInformation": [],
                    "tailoringSummary": "Emphasised Java, Spring Boot, Angular, REST APIs, testing and delivery experience for the selected software developer role."
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
