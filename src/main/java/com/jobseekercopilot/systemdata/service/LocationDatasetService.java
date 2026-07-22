package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.model.DatasetSource;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.model.DemoLocation;
import com.jobseekercopilot.systemdata.normaliser.PostcodeNormaliser;
import com.jobseekercopilot.systemdata.util.DatasetIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class LocationDatasetService {
    private static final Logger log = LoggerFactory.getLogger(LocationDatasetService.class);
    private final SystemDataProperties properties;
    private final PostcodeApi postcodeApi;
    private final PostcodeNormaliser postcodeNormaliser;
    private final DatasetIdGenerator idGenerator;

    public LocationDatasetService(SystemDataProperties properties, PostcodeApi postcodeApi,
                                  PostcodeNormaliser postcodeNormaliser, DatasetIdGenerator idGenerator) {
        this.properties = properties;
        this.postcodeApi = postcodeApi;
        this.postcodeNormaliser = postcodeNormaliser;
        this.idGenerator = idGenerator;
    }

    public ProviderLocations gather(List<String> postcodes) {
        return gather(postcodes, List.of());
    }

    public ProviderLocations gather(List<String> postcodes, List<DemoJob> jobs) {
        var locations = new LinkedHashMap<String, DemoLocation>();
        var sources = new ArrayList<DatasetSource>();
        Map<String, DemoLocation> lookupCache = new LinkedHashMap<>();
        int attempted = 0;
        int succeeded = 0;
        if (!properties.getGateways().getPostcodeIo().isEnabled()) {
            deriveLocationsFromJobs(jobs, locations, sources);
            return new ProviderLocations(List.copyOf(locations.values()), sources, attempted, succeeded);
        }
        for (String postcode : new LinkedHashSet<>(postcodes)) {
            if (postcode == null || postcode.isBlank()) {
                continue;
            }
            attempted++;
            Instant retrievedAt = Instant.now();
            try {
                DemoLocation location = lookupCache.get(postcode);
                if (location == null) {
                    var response = postcodeApi.getLocationByPostcode(postcode);
                    if (response != null) {
                        location = postcodeNormaliser.normalise(response, retrievedAt);
                        lookupCache.put(postcode, location);
                    }
                }
                if (location != null) {
                    locations.putIfAbsent(location.id(), location);
                    succeeded++;
                }
                sources.add(new DatasetSource("postcode-io-gateway", postcode, postcode, retrievedAt, "SUCCESS", null));
            } catch (RestClientException ex) {
                log.warn("Postcode dataset gathering failed for postcode={}", postcode, ex);
                sources.add(new DatasetSource("postcode-io-gateway", postcode, postcode, retrievedAt, "FAILED", ex.getMessage()));
            }
        }
        deriveLocationsFromJobs(jobs, locations, sources);
        return new ProviderLocations(List.copyOf(locations.values()), sources, attempted, succeeded);
    }

    private void deriveLocationsFromJobs(List<DemoJob> jobs, Map<String, DemoLocation> locations, List<DatasetSource> sources) {
        var seen = new LinkedHashSet<String>();
        for (DemoJob job : jobs) {
            if (job.locationName() == null || job.locationName().isBlank()) {
                continue;
            }
            String placeName = canonicalPlace(job.locationName());
            if (placeName.isBlank()) {
                continue;
            }
            String key = placeName.toLowerCase(Locale.ROOT) + "|" + value(job.region());
            if (!seen.add(key)) {
                continue;
            }
            Instant retrievedAt = job.sourceRetrievedAt() == null ? Instant.now() : job.sourceRetrievedAt();
            DemoLocation location = new DemoLocation(
                    idGenerator.stableLocationId(job.postcode(), placeName),
                    job.postcode(),
                    postcodeDistrict(job.postcode()),
                    postcodeArea(job.postcode()),
                    placeName,
                    null,
                    null,
                    job.region(),
                    "United Kingdom",
                    job.latitude(),
                    job.longitude(),
                    job.sourceProvider(),
                    retrievedAt);
            locations.putIfAbsent(location.id(), location);
            sources.add(new DatasetSource("job-location-derivation", job.sourceQuery(), job.sourceLocation(), retrievedAt, "SUCCESS", job.sourceProvider()));
        }
    }

    private String canonicalPlace(String locationName) {
        String value = locationName == null ? "" : locationName.trim();
        value = value.replaceAll("(?i),\\s*UK$", "");
        value = value.replaceAll("(?i),\\s*United Kingdom$", "");
        value = value.replaceAll("(?i),\\s*England$", "");
        String[] parts = value.split(",");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    private String postcodeDistrict(String postcode) {
        if (postcode == null || postcode.isBlank()) {
            return null;
        }
        return postcode.trim().split("\\s+")[0].toUpperCase(Locale.ROOT);
    }

    private String postcodeArea(String postcode) {
        String district = postcodeDistrict(postcode);
        return district == null ? null : district.replaceAll("[0-9].*$", "");
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record ProviderLocations(List<DemoLocation> locations, List<DatasetSource> sources, int attemptedLookups,
                                    int succeededLookups) {
    }
}
