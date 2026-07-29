package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.JobDataset;
import com.jobseekercopilot.systemdata.model.SystemDataApplicationSeedRecord;
import com.jobseekercopilot.systemdata.util.ChecksumUtil;
import com.jobseekercopilot.systemdata.util.DeterministicIds;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DemoEnvironmentScenarioBuilder {
    private static final String SCENARIO_ID = "demo-ready-v1";
    private static final String USER_ID = DeterministicIds.uuidString(SCENARIO_ID + ":alex-taylor:user");
    private static final String EMAIL = "alex.taylor92@example.com";
    private static final ChecksumUtil CHECKSUM = new ChecksumUtil();

    public DemoEnvironmentScenario build(JobDataset dataset, Instant referenceDate) {
        List<String> warnings = new ArrayList<>();
        List<DemoJob> selectedJobs = selectJobs(dataset.jobs());
        if (selectedJobs.size() < 9) {
            warnings.add("Fewer than 9 high-quality jobs were available in the selected dataset.");
        }

        LocalDateTime ref = LocalDateTime.ofInstant(referenceDate, ZoneOffset.UTC);
        Map<String, Object> user = user(ref.minusDays(28));
        Map<String, Object> profile = profile();
        List<SystemDataApplicationSeedRecord> applications = applications(selectedJobs, ref);
        Map<String, Object> documents = documents(selectedJobs, applications, ref);
        Map<String, Object> payment = payment(ref);
        Map<String, Integer> byStatus = applications.stream()
                .collect(Collectors.toMap(
                        SystemDataApplicationSeedRecord::status,
                        app -> 1,
                        Integer::sum,
                        LinkedHashMap::new));

        return new DemoEnvironmentScenario(
                SCENARIO_ID,
                USER_ID,
                EMAIL,
                user,
                profile,
                payment,
                documents,
                applications,
                selectedJobs.stream().map(DemoJob::id).toList(),
                byStatus,
                warnings);
    }

    public String demoUserId() {
        return USER_ID;
    }

    private List<DemoJob> selectJobs(List<DemoJob> jobs) {
        return jobs.stream()
                .filter(DemoJob::suitableForDemo)
                .filter(job -> job.title() != null && job.companyName() != null)
                .filter(job -> job.description() != null && job.description().length() > 140)
                .filter(job -> job.salaryMinimum() == null || job.salaryMinimum() >= 20000)
                .filter(job -> !containsAny((job.title() + " " + job.description()).toLowerCase(Locale.ROOT),
                        "placement programme", "training programme", "fees apply", "no experience needed"))
                .sorted(Comparator.comparingInt(this::jobRank).thenComparing(DemoJob::id))
                .limit(9)
                .toList();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int jobRank(DemoJob job) {
        String text = ((job.title() == null ? "" : job.title()) + " " + (job.locationName() == null ? "" : job.locationName())).toLowerCase(Locale.ROOT);
        int rank = 100;
        if (text.contains("reading")) rank -= 40;
        if (text.contains("london")) rank -= 25;
        if (text.contains("java")) rank -= 20;
        if (text.contains("backend") || text.contains("back end")) rank -= 18;
        if (text.contains("software developer")) rank -= 15;
        if ("HYBRID".equalsIgnoreCase(job.remoteType())) rank -= 8;
        if (job.salaryMinimum() != null) rank -= 5;
        return rank;
    }

    private Map<String, Object> user(LocalDateTime createdAt) {
        return map(
                "scenarioId", SCENARIO_ID,
                "userId", USER_ID,
                "name", "Alex Taylor",
                "email", EMAIL,
                "password", "Password123!",
                "createdAt", createdAt.toString());
    }

    private Map<String, Object> profile() {
        return map(
                "userId", USER_ID,
                "skills", List.of("Java", "Spring Boot", "Angular", "TypeScript", "SQL", "PostgreSQL", "Git", "REST APIs", "Docker", "Microservices", "Problem Solving", "Teamwork"),
                "aspirations", map(
                        "targetRoles", List.of("Software Developer", "Backend Developer", "Java Developer"),
                        "targetWeeklyHours", "FULL_TIME"),
                "workPreferences", map(
                        "location", map("postcode", null, "region", "Berkshire", "adminDistrict", "Reading", "latitude", 51.4543, "longitude", -0.9781),
                        "commuteRange", 35),
                "qualifications", List.of(map(
                        "qualificationName", "BSc Computer Science",
                        "issuingBody", "University of Birmingham",
                        "status", "COMPLETED",
                        "grade", "2:1",
                        "dateAchieved", "2021-06",
                        "expectedCompletion", null)),
                "roles", List.of(
                        map("jobTitle", "Software Developer", "employer", "BrightTech Solutions", "status", "CURRENT", "startDate", "2021-07", "endDate", null,
                                "keyResponsibilities", "Developed Java and Spring Boot microservices for enterprise applications, collaborated with Angular developers and delivered REST APIs for cloud-based systems."),
                        map("jobTitle", "Software Engineering Intern", "employer", "CodeBridge Ltd", "status", "PREVIOUS_ROLE", "startDate", "2020-06", "endDate", "2020-08",
                                "keyResponsibilities", "Assisted with bug fixing, automated testing and frontend development in an Agile engineering team.")));
    }

    private List<SystemDataApplicationSeedRecord> applications(List<DemoJob> jobs, LocalDateTime ref) {
        String[] statuses = {"OFFER", "INTERVIEW", "APPLIED", "APPLIED", "DOCUMENTS_GENERATED", "DOCUMENTS_GENERATED", "UNSUCCESSFUL", "WITHDRAWN", "DOCUMENTS_GENERATED"};
        int[] createdOffsets = {21, 16, 12, 10, 14, 7, 19, 18, 24};
        List<SystemDataApplicationSeedRecord> applications = new ArrayList<>();
        for (int i = 0; i < jobs.size() && i < statuses.length; i++) {
            DemoJob job = jobs.get(i);
            String appId = DeterministicIds.uuidString(SCENARIO_ID + ":application:" + i + ":" + job.id());
            UUID cvDocumentId = DeterministicIds.uuid(SCENARIO_ID + ":document:cv:" + appId + ":v1");
            UUID coverLetterDocumentId =
                    DeterministicIds.uuid(SCENARIO_ID + ":document:cover:" + appId + ":v1");
            applications.add(new SystemDataApplicationSeedRecord(
                    UUID.fromString(appId),
                    job.id(),
                    job.id(),
                    job.sourceProvider(),
                    job.externalReference(),
                    job.title(),
                    job.companyName(),
                    job.locationName(),
                    cvDocumentId,
                    cvDocumentId,
                    1,
                    CHECKSUM.sha256(syntheticDocumentContent("CV", job)),
                    coverLetterDocumentId,
                    coverLetterDocumentId,
                    1,
                    CHECKSUM.sha256(syntheticDocumentContent("COVER_LETTER", job)),
                    statuses[i],
                    ref.minusDays(createdOffsets[i]).toString(),
                    ref.minusDays(Math.max(1, createdOffsets[i] - 4)).toString(),
                    appliedAt(statuses[i], ref.minusDays(Math.max(1, createdOffsets[i] - 2)))));
        }
        return applications;
    }

    private String appliedAt(String status, LocalDateTime value) {
        return switch (status) {
            case "APPLIED", "INTERVIEW", "OFFER", "UNSUCCESSFUL",
                    "ACCEPTED", "REJECTED_BY_USER", "WITHDRAWN" -> value.toString();
            default -> null;
        };
    }

    private Map<String, Object> documents(List<DemoJob> jobs, List<SystemDataApplicationSeedRecord> applications, LocalDateTime ref) {
        List<Map<String, Object>> docs = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (int i = 0; i < applications.size(); i++) {
            SystemDataApplicationSeedRecord app = applications.get(i);
            DemoJob job = jobs.get(i);
            addDocumentPair(docs, files, app, job, ref.minusDays(20 - i), 1, true);
        }
        if (!applications.isEmpty()) {
            SystemDataApplicationSeedRecord app = applications.get(0);
            DemoJob job = jobs.get(0);
            docs.stream()
                    .filter(document -> app.cvDocumentId().toString().equals(document.get("id")))
                    .findFirst()
                    .ifPresent(document -> document.put("active", false));
            String docId = DeterministicIds.uuidString(SCENARIO_ID + ":document:cv:" + app.id() + ":v2");
            docs.add(document(
                    docId,
                    app.cvDocumentFamilyId().toString(),
                    app,
                    job,
                    "CV",
                    "Tailored CV for " + job.companyName() + " - revised",
                    ref.minusDays(6),
                    2,
                    true,
                    "UPLOADED"));
            files.add(file(docId, "alex-taylor-" + slug(job.companyName()) + "-cv-revised.pdf", ref.minusDays(6), "USER_UPLOADED"));
        }
        return map("scenarioId", SCENARIO_ID, "userId", USER_ID, "documents", docs, "files", files);
    }

    private void addDocumentPair(List<Map<String, Object>> docs, List<Map<String, Object>> files, SystemDataApplicationSeedRecord app, DemoJob job, LocalDateTime createdAt, int version, boolean active) {
        String cvId = app.cvDocumentId().toString();
        String coverId = app.coverLetterDocumentId().toString();
        docs.add(document(
                cvId,
                app.cvDocumentFamilyId().toString(),
                app,
                job,
                "CV",
                "Tailored CV for " + job.companyName(),
                createdAt,
                version,
                active,
                "GENERATED"));
        docs.add(document(
                coverId,
                app.coverLetterDocumentFamilyId().toString(),
                app,
                job,
                "COVER_LETTER",
                "Cover letter for " + job.companyName(),
                createdAt.plusHours(1),
                version,
                active,
                "GENERATED"));
        files.add(file(cvId, "alex-taylor-" + slug(job.companyName()) + "-cv.pdf", createdAt, "GENERATED"));
        files.add(file(coverId, "alex-taylor-" + slug(job.companyName()) + "-cover-letter.pdf", createdAt.plusHours(1), "GENERATED"));
    }

    private Map<String, Object> document(
            String id,
            String documentFamilyId,
            SystemDataApplicationSeedRecord app,
            DemoJob job,
            String type,
            String title,
            LocalDateTime createdAt,
            int version,
            boolean active,
            String sourceType) {
        String content = syntheticDocumentContent(type, job);
        return map(
                "id", id,
                "userId", USER_ID,
                "jobId", job.id(),
                "applicationId", app.id().toString(),
                "documentFamilyId", documentFamilyId,
                "documentType", type,
                "title", title,
                "content", content,
                "version", version,
                "active", active,
                "lifecycleState", "APPROVED",
                "approvedAt", createdAt.toString(),
                "approvedBy", USER_ID,
                "contentSha256", CHECKSUM.sha256(content),
                "originalFilename", null,
                "sourceType", sourceType,
                "createdBy", sourceType.equals("GENERATED") ? "system-data-service" : "alex.taylor",
                "createdAt", createdAt.toString(),
                "updatedAt", createdAt.toString());
    }

    private Map<String, Object> file(String documentId, String fileName, LocalDateTime createdAt, String source) {
        return map(
                "id", DeterministicIds.uuidString(SCENARIO_ID + ":file:" + documentId + ":" + fileName),
                "generatedDocumentId", documentId,
                "fileType", "PDF",
                "fileName", fileName,
                "mimeType", "application/pdf",
                "source", source,
                "active", true,
                "fileContent", Base64.getEncoder().encodeToString(("%PDF-1.4\n% Job Seeker Copilot demo fixture\n" + fileName + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8)),
                "createdAt", createdAt.toString(),
                "updatedAt", createdAt.toString());
    }

    private String syntheticDocumentContent(String type, DemoJob job) {
        return type + " fixture for Alex Taylor applying to " + job.title() + " at " + job.companyName()
                + ". Highlights Java, Spring Boot, Angular, REST APIs, SQL, teamwork, and delivery experience.";
    }

    private Map<String, Object> payment(LocalDateTime ref) {
        UUID walletId = DeterministicIds.uuid(SCENARIO_ID + ":wallet");
        long purchased = 600_000;
        long spent = 62_000;
        long balance = purchased - spent;
        List<Map<String, Object>> transactions = List.of(
                transaction("purchase", walletId, "DEMO_PURCHASE", purchased, 0, purchased, "Demo AI Credit purchase: £15.99 package", "DEMO_PAYMENT", "demo-payment-001", ref.minusDays(23)),
                transaction("reservation-1", walletId, "RESERVATION", 40_000, purchased, 560_000, "AI Credit reservation for CV generation", "DOCUMENT_GENERATION", "demo-generation-001", ref.minusDays(22)),
                transaction("spend-1", walletId, "SPEND", 32_000, 560_000, 560_000, "CV generation completed", "DOCUMENT_GENERATION", "demo-generation-001", ref.minusDays(22).plusMinutes(4)),
                transaction("release-1", walletId, "RESERVATION_RELEASED", 8_000, 560_000, 568_000, "Released unused reserved AI Credit", "DOCUMENT_GENERATION", "demo-generation-001", ref.minusDays(22).plusMinutes(5)),
                transaction("reservation-2", walletId, "RESERVATION", 35_000, 568_000, 533_000, "AI Credit reservation for cover letter generation", "DOCUMENT_GENERATION", "demo-generation-002", ref.minusDays(3)),
                transaction("spend-2", walletId, "SPEND", 30_000, 533_000, 533_000, "Cover letter generation completed", "DOCUMENT_GENERATION", "demo-generation-002", ref.minusDays(3).plusMinutes(3)),
                transaction("release-2", walletId, "RESERVATION_RELEASED", 5_000, 533_000, balance, "Released unused reserved AI Credit", "DOCUMENT_GENERATION", "demo-generation-002", ref.minusDays(3).plusMinutes(4)));
        return map(
                "scenarioId", SCENARIO_ID,
                "userId", USER_ID,
                "wallet", map(
                        "id", walletId.toString(),
                        "userId", USER_ID,
                        "balanceTokens", balance,
                        "lifetimePurchasedTokens", purchased,
                        "lifetimeSpentTokens", spent,
                        "lifetimeRefundedTokens", 0,
                        "freeTrialGranted", false,
                        "createdAt", utcTimestamp(ref.minusDays(23)),
                        "updatedAt", utcTimestamp(ref.minusDays(3).plusMinutes(4))),
                "transactions", transactions,
                "reservations", List.of());
    }

    private Map<String, Object> transaction(String key, UUID walletId, String type, long amount, long before, long after, String description, String referenceType, String referenceId, LocalDateTime createdAt) {
        return map(
                "id", DeterministicIds.uuidString(SCENARIO_ID + ":transaction:" + key),
                "userId", USER_ID,
                "walletId", walletId.toString(),
                "transactionType", type,
                "tokenAmount", amount,
                "balanceDeltaTokens", Math.subtractExact(after, before),
                "balanceBefore", before,
                "balanceAfter", after,
                "operationId", DeterministicIds.uuidString(SCENARIO_ID + ":payment-operation:" + key),
                "description", description,
                "referenceType", referenceType,
                "referenceId", referenceId,
                "createdAt", utcTimestamp(createdAt));
    }

    private String utcTimestamp(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(entries[i].toString(), entries[i + 1]);
        }
        return map;
    }
}
