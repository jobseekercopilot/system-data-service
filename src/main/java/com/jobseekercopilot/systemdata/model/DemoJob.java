package com.jobseekercopilot.systemdata.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DemoJob(
        String id,
        String externalReference,
        String sourceProvider,
        String title,
        String companyName,
        String companyDisplayName,
        String locationName,
        String postcode,
        String region,
        Double latitude,
        Double longitude,
        Integer salaryMinimum,
        Integer salaryMaximum,
        String salaryCurrency,
        String salaryPeriod,
        String employmentType,
        String workingPattern,
        String remoteType,
        String contractType,
        String category,
        String seniority,
        String description,
        String shortDescription,
        List<String> skills,
        List<String> qualifications,
        List<String> benefits,
        String datePosted,
        String closingDate,
        String sourceUrl,
        Instant sourceRetrievedAt,
        String sourceQuery,
        String sourceLocation,
        Map<String, Object> sourceMetadata,
        Map<String, Object> generatedMetadata,
        boolean suitableForDemo,
        double qualityScore) {

    public DemoJob withId(String newId) {
        return new DemoJob(newId, externalReference, sourceProvider, title, companyName, companyDisplayName, locationName,
                postcode, region, latitude, longitude, salaryMinimum, salaryMaximum, salaryCurrency, salaryPeriod,
                employmentType, workingPattern, remoteType, contractType, category, seniority, description,
                shortDescription, skills, qualifications, benefits, datePosted, closingDate, sourceUrl,
                sourceRetrievedAt, sourceQuery, sourceLocation, sourceMetadata, generatedMetadata, suitableForDemo, qualityScore);
    }

    public static Map<String, Object> metadata() {
        return new LinkedHashMap<>();
    }
}
