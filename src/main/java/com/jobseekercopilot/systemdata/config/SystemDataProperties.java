package com.jobseekercopilot.systemdata.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "system-data")
public class SystemDataProperties {
    private Path repositoryDirectory = Path.of("./fixtures/datasets");
    private Path outputDirectory = Path.of("./generated-datasets");
    private Generation generation = new Generation();
    private Enrichment enrichment = new Enrichment();
    private Gateways gateways = new Gateways();
    private EnvironmentManagement environmentManagement = new EnvironmentManagement();
    private Fixtures fixtures = new Fixtures();
    private LiveAcquisition liveAcquisition = new LiveAcquisition();

    public Path getRepositoryDirectory() {
        return repositoryDirectory;
    }

    public void setRepositoryDirectory(Path repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public Enrichment getEnrichment() {
        return enrichment;
    }

    public void setEnrichment(Enrichment enrichment) {
        this.enrichment = enrichment;
    }

    public Gateways getGateways() {
        return gateways;
    }

    public void setGateways(Gateways gateways) {
        this.gateways = gateways;
    }

    public EnvironmentManagement getEnvironmentManagement() {
        return environmentManagement;
    }

    public void setEnvironmentManagement(EnvironmentManagement environmentManagement) {
        this.environmentManagement = environmentManagement;
    }

    public Fixtures getFixtures() {
        return fixtures;
    }

    public void setFixtures(Fixtures fixtures) {
        this.fixtures = fixtures;
    }

    public LiveAcquisition getLiveAcquisition() { return liveAcquisition; }
    public void setLiveAcquisition(LiveAcquisition liveAcquisition) { this.liveAcquisition = liveAcquisition; }

    public static class Generation {
        private String defaultDatasetId = "uk-software-developer-demo";
        private String defaultName = "UK Software Developer Demo";
        private String defaultVersion = "1.0.0";
        private int maximumJobsPerProvider = 20;
        private boolean failIfAllProvidersFail = true;
        private boolean allowPartialProviderFailure = true;
        private List<String> queries = new ArrayList<>(List.of(
                "Software Developer",
                "Java Developer",
                "Backend Developer",
                "Full Stack Developer",
                "Junior Software Developer",
                "Software Engineer",
                "Angular Developer",
                "Spring Boot Developer"));
        private List<String> locations = new ArrayList<>(List.of(
                "London", "Reading", "Birmingham", "Manchester", "Leeds", "Bristol", "Remote"));
        private List<String> postcodes = new ArrayList<>();

        public String getDefaultDatasetId() { return defaultDatasetId; }
        public void setDefaultDatasetId(String defaultDatasetId) { this.defaultDatasetId = defaultDatasetId; }
        public String getDefaultName() { return defaultName; }
        public void setDefaultName(String defaultName) { this.defaultName = defaultName; }
        public String getDefaultVersion() { return defaultVersion; }
        public void setDefaultVersion(String defaultVersion) { this.defaultVersion = defaultVersion; }
        public int getMaximumJobsPerProvider() { return maximumJobsPerProvider; }
        public void setMaximumJobsPerProvider(int maximumJobsPerProvider) { this.maximumJobsPerProvider = maximumJobsPerProvider; }
        public boolean isFailIfAllProvidersFail() { return failIfAllProvidersFail; }
        public void setFailIfAllProvidersFail(boolean failIfAllProvidersFail) { this.failIfAllProvidersFail = failIfAllProvidersFail; }
        public boolean isAllowPartialProviderFailure() { return allowPartialProviderFailure; }
        public void setAllowPartialProviderFailure(boolean allowPartialProviderFailure) { this.allowPartialProviderFailure = allowPartialProviderFailure; }
        public List<String> getQueries() { return queries; }
        public void setQueries(List<String> queries) { this.queries = queries; }
        public List<String> getLocations() { return locations; }
        public void setLocations(List<String> locations) { this.locations = locations; }
        public List<String> getPostcodes() { return postcodes; }
        public void setPostcodes(List<String> postcodes) { this.postcodes = postcodes; }
    }

    public static class Enrichment {
        private boolean enabled;
        private int maxRetries = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Gateways {
        private Gateway adzuna = new Gateway();
        private Gateway jsearch = new Gateway();
        private Gateway reed = new Gateway();
        private Gateway postcodeIo = new Gateway();
        private Gateway llm = new Gateway();

        public Gateway getAdzuna() { return adzuna; }
        public void setAdzuna(Gateway adzuna) { this.adzuna = adzuna; }
        public Gateway getJsearch() { return jsearch; }
        public void setJsearch(Gateway jsearch) { this.jsearch = jsearch; }
        public Gateway getReed() { return reed; }
        public void setReed(Gateway reed) { this.reed = reed; }
        public Gateway getPostcodeIo() { return postcodeIo; }
        public void setPostcodeIo(Gateway postcodeIo) { this.postcodeIo = postcodeIo; }
        public Gateway getLlm() { return llm; }
        public void setLlm(Gateway llm) { this.llm = llm; }
    }

    public static class Gateway {
        private String baseUrl;
        private boolean enabled = false;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class EnvironmentManagement {
        private boolean enabled = false;
        private List<String> allowedEnvironments = new ArrayList<>(List.of("local", "test", "demo"));
        private String callerKey;
        private String downstreamEnvironmentDataToken;
        private TargetServices targetServices = new TargetServices();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedEnvironments() { return allowedEnvironments; }
        public void setAllowedEnvironments(List<String> allowedEnvironments) { this.allowedEnvironments = allowedEnvironments; }
        public String getCallerKey() { return callerKey; }
        public void setCallerKey(String callerKey) { this.callerKey = callerKey; }
        public String getDownstreamEnvironmentDataToken() { return downstreamEnvironmentDataToken; }
        public void setDownstreamEnvironmentDataToken(String value) { downstreamEnvironmentDataToken = value; }
        public TargetServices getTargetServices() { return targetServices; }
        public void setTargetServices(TargetServices targetServices) { this.targetServices = targetServices; }
    }

    public static class TargetServices {
        private String authentication = "http://localhost:8084";
        private String userProfile = "http://localhost:8085";
        private String applicationTracker = "http://localhost:8088";
        private String documentStore = "http://localhost:8089";
        private String payment = "http://localhost:8099";

        public String getAuthentication() { return authentication; }
        public void setAuthentication(String authentication) { this.authentication = authentication; }
        public String getUserProfile() { return userProfile; }
        public void setUserProfile(String userProfile) { this.userProfile = userProfile; }
        public String getApplicationTracker() { return applicationTracker; }
        public void setApplicationTracker(String applicationTracker) { this.applicationTracker = applicationTracker; }
        public String getDocumentStore() { return documentStore; }
        public void setDocumentStore(String documentStore) { this.documentStore = documentStore; }
        public String getPayment() { return payment; }
        public void setPayment(String payment) { this.payment = payment; }
    }

    public static class Fixtures {
        private boolean enabled = false;
        private List<String> allowedEnvironments = new ArrayList<>(List.of("local", "test", "demo", "default"));
        private String defaultDatasetId = "uk-software-developer-demo";
        private String defaultDatasetVersion = "1.0.0";
        private String defaultScenario = "DEMO_READY";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedEnvironments() { return allowedEnvironments; }
        public void setAllowedEnvironments(List<String> allowedEnvironments) { this.allowedEnvironments = allowedEnvironments; }
        public String getDefaultDatasetId() { return defaultDatasetId; }
        public void setDefaultDatasetId(String defaultDatasetId) { this.defaultDatasetId = defaultDatasetId; }
        public String getDefaultDatasetVersion() { return defaultDatasetVersion; }
        public void setDefaultDatasetVersion(String defaultDatasetVersion) { this.defaultDatasetVersion = defaultDatasetVersion; }
        public String getDefaultScenario() { return defaultScenario; }
        public void setDefaultScenario(String defaultScenario) { this.defaultScenario = defaultScenario; }
    }

    public static class LiveAcquisition {
        private boolean enabled;
        private boolean execute;
        private String operatorConfirmation;
        private String termsApprovalReference;
        private String provenanceReviewer;
        private int maximumOutputRecords = 120;
        private List<String> approvedProviders = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isExecute() { return execute; }
        public void setExecute(boolean execute) { this.execute = execute; }
        public String getOperatorConfirmation() { return operatorConfirmation; }
        public void setOperatorConfirmation(String operatorConfirmation) { this.operatorConfirmation = operatorConfirmation; }
        public String getTermsApprovalReference() { return termsApprovalReference; }
        public void setTermsApprovalReference(String termsApprovalReference) { this.termsApprovalReference = termsApprovalReference; }
        public String getProvenanceReviewer() { return provenanceReviewer; }
        public void setProvenanceReviewer(String provenanceReviewer) { this.provenanceReviewer = provenanceReviewer; }
        public int getMaximumOutputRecords() { return maximumOutputRecords; }
        public void setMaximumOutputRecords(int maximumOutputRecords) { this.maximumOutputRecords = maximumOutputRecords; }
        public List<String> getApprovedProviders() { return approvedProviders; }
        public void setApprovedProviders(List<String> approvedProviders) { this.approvedProviders = approvedProviders; }
    }
}
