package com.jobseekercopilot.systemdata.service;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.adzunagateway.model.AdzunaSearchRequest;
import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.jsearchgateway.model.JSearchSearchRequest;
import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import com.jobseekercopilot.systemdata.config.SystemDataProperties;
import com.jobseekercopilot.systemdata.exception.DatasetGenerationException;
import com.jobseekercopilot.systemdata.model.DatasetSource;
import com.jobseekercopilot.systemdata.model.DemoJob;
import com.jobseekercopilot.systemdata.normaliser.AdzunaJobNormaliser;
import com.jobseekercopilot.systemdata.normaliser.JSearchJobNormaliser;
import com.jobseekercopilot.systemdata.normaliser.ReedJobNormaliser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClientException;

@Service
@Profile("live-acquisition")
public class JobDatasetService {
    private static final Logger log = LoggerFactory.getLogger(JobDatasetService.class);

    private final SystemDataProperties properties;
    private final AdzunaJobsApi adzunaJobsApi;
    private final JSearchJobsApi jSearchJobsApi;
    private final ReedJobsApi reedJobsApi;
    private final AdzunaJobNormaliser adzunaJobNormaliser;
    private final JSearchJobNormaliser jSearchJobNormaliser;
    private final ReedJobNormaliser reedJobNormaliser;

    public JobDatasetService(SystemDataProperties properties, AdzunaJobsApi adzunaJobsApi, JSearchJobsApi jSearchJobsApi,
                             ReedJobsApi reedJobsApi, AdzunaJobNormaliser adzunaJobNormaliser,
                             JSearchJobNormaliser jSearchJobNormaliser, ReedJobNormaliser reedJobNormaliser) {
        this.properties = properties;
        this.adzunaJobsApi = adzunaJobsApi;
        this.jSearchJobsApi = jSearchJobsApi;
        this.reedJobsApi = reedJobsApi;
        this.adzunaJobNormaliser = adzunaJobNormaliser;
        this.jSearchJobNormaliser = jSearchJobNormaliser;
        this.reedJobNormaliser = reedJobNormaliser;
    }

    public ProviderJobs gather(List<String> queries, List<String> locations, List<String> providers, int maximumResultsPerProvider) {
        List<DemoJob> jobs = new ArrayList<>();
        List<DatasetSource> sources = new ArrayList<>();
        Map<String, Integer> rawResultCounts = new LinkedHashMap<>();
        Map<String, String> providerStatuses = new LinkedHashMap<>();
        Set<String> selectedProviders = selectedProviders(providers);
        int successfulProviders = 0;
        if (selectedProviders.contains("ADZUNA") && properties.getGateways().getAdzuna().isEnabled()) {
            successfulProviders += gatherAdzuna(queries, locations, maximumResultsPerProvider, jobs, sources, rawResultCounts, providerStatuses) ? 1 : 0;
        }
        if (selectedProviders.contains("JSEARCH") && properties.getGateways().getJsearch().isEnabled()) {
            successfulProviders += gatherJSearch(queries, locations, jobs, sources, rawResultCounts, providerStatuses) ? 1 : 0;
        }
        if (selectedProviders.contains("REED") && properties.getGateways().getReed().isEnabled()) {
            successfulProviders += gatherReed(queries, locations, maximumResultsPerProvider, jobs, sources, rawResultCounts, providerStatuses) ? 1 : 0;
        }
        if (successfulProviders == 0 && properties.getGeneration().isFailIfAllProvidersFail()) {
            throw new DatasetGenerationException("All job providers failed or were disabled.");
        }
        return new ProviderJobs(jobs, sources, rawResultCounts, providerStatuses, List.copyOf(selectedProviders));
    }

    private boolean gatherAdzuna(List<String> queries, List<String> locations, int maximumResultsPerProvider,
                                 List<DemoJob> jobs, List<DatasetSource> sources, Map<String, Integer> rawResultCounts,
                                 Map<String, String> providerStatuses) {
        boolean success = false;
        int rawCount = 0;
        for (String query : queries) {
            for (String location : locations) {
                Instant retrievedAt = Instant.now();
                try {
                    AdzunaSearchRequest request = new AdzunaSearchRequest()
                            .targetRole(query)
                            .location(location)
                            .page(1)
                            .resultsPerPage(maximumResultsPerProvider);
                    var response = adzunaJobsApi.search(request);
                    if (response != null && response.getJobs() != null) {
                        rawCount += response.getJobs().size();
                        response.getJobs().forEach(job -> jobs.add(adzunaJobNormaliser.normalise(job, retrievedAt, query, location)));
                    }
                    sources.add(new DatasetSource("adzuna-gateway", query, location, retrievedAt, "SUCCESS", null));
                    providerStatuses.put("ADZUNA", "SUCCESS");
                    success = true;
                } catch (RestClientException ex) {
                    log.warn("Live acquisition provider call failed provider=ADZUNA");
                    sources.add(new DatasetSource("adzuna-gateway", query, location, retrievedAt, "FAILED", "Gateway request failed"));
                    providerStatuses.put("ADZUNA", "FAILED");
                    if (!properties.getGeneration().isAllowPartialProviderFailure()) {
                        throw ex;
                    }
                }
            }
        }
        if (success && providerStatuses.getOrDefault("ADZUNA", "").startsWith("FAILED")) {
            providerStatuses.put("ADZUNA", "PARTIAL_SUCCESS: collected " + rawCount + " records before a later request failed");
        }
        rawResultCounts.put("ADZUNA", rawCount);
        return success;
    }

    private boolean gatherJSearch(List<String> queries, List<String> locations, List<DemoJob> jobs, List<DatasetSource> sources,
                                  Map<String, Integer> rawResultCounts, Map<String, String> providerStatuses) {
        boolean success = false;
        int rawCount = 0;
        for (String query : queries) {
            for (String location : locations) {
                Instant retrievedAt = Instant.now();
                try {
                    JSearchSearchRequest request = new JSearchSearchRequest()
                            .targetRole(query)
                            .location(location)
                            .remoteOnly(location.toLowerCase().contains("remote"));
                    var response = jSearchJobsApi.search(request);
                    if (response != null && response.getJobs() != null) {
                        rawCount += response.getJobs().size();
                        response.getJobs().forEach(job -> jobs.add(jSearchJobNormaliser.normalise(job, retrievedAt, query, location)));
                    }
                    sources.add(new DatasetSource("jsearch-gateway", query, location, retrievedAt, "SUCCESS", null));
                    providerStatuses.put("JSEARCH", "SUCCESS");
                    success = true;
                } catch (RestClientException ex) {
                    log.warn("Live acquisition provider call failed provider=JSEARCH");
                    sources.add(new DatasetSource("jsearch-gateway", query, location, retrievedAt, "FAILED", "Gateway request failed"));
                    providerStatuses.put("JSEARCH", "FAILED");
                    if (!properties.getGeneration().isAllowPartialProviderFailure()) {
                        throw ex;
                    }
                }
            }
        }
        if (success && providerStatuses.getOrDefault("JSEARCH", "").startsWith("FAILED")) {
            providerStatuses.put("JSEARCH", "PARTIAL_SUCCESS: collected " + rawCount + " records before a later request failed");
        }
        rawResultCounts.put("JSEARCH", rawCount);
        return success;
    }

    private boolean gatherReed(List<String> queries, List<String> locations, int maximumResultsPerProvider,
                               List<DemoJob> jobs, List<DatasetSource> sources, Map<String, Integer> rawResultCounts,
                               Map<String, String> providerStatuses) {
        boolean success = false;
        int rawCount = 0;
        for (String query : queries) {
            for (String location : locations) {
                Instant retrievedAt = Instant.now();
                try {
                    var response = reedJobsApi.searchJobs(query, location, maximumResultsPerProvider);
                    if (response != null && response.getResults() != null) {
                        rawCount += response.getResults().size();
                        response.getResults().forEach(job -> jobs.add(reedJobNormaliser.normalise(job, retrievedAt, query, location)));
                    }
                    sources.add(new DatasetSource("reed-gateway", query, location, retrievedAt, "SUCCESS", null));
                    providerStatuses.put("REED", "SUCCESS");
                    success = true;
                } catch (RestClientException ex) {
                    log.warn("Live acquisition provider call failed provider=REED");
                    sources.add(new DatasetSource("reed-gateway", query, location, retrievedAt, "FAILED", "Gateway request failed"));
                    providerStatuses.put("REED", "FAILED");
                    if (!properties.getGeneration().isAllowPartialProviderFailure()) {
                        throw ex;
                    }
                }
            }
        }
        if (success && providerStatuses.getOrDefault("REED", "").startsWith("FAILED")) {
            providerStatuses.put("REED", "PARTIAL_SUCCESS: collected " + rawCount + " records before a later request failed");
        }
        rawResultCounts.put("REED", rawCount);
        return success;
    }

    private Set<String> selectedProviders(List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            return Set.of("ADZUNA", "JSEARCH", "REED");
        }
        var selected = new java.util.LinkedHashSet<String>();
        providers.forEach(provider -> selected.add(provider.toUpperCase(Locale.UK)));
        return selected;
    }

    public record ProviderJobs(List<DemoJob> jobs, List<DatasetSource> sources, Map<String, Integer> rawResultCounts,
                               Map<String, String> providerStatuses, List<String> providersCalled) {
    }
}
