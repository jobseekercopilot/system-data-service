package com.jobseekercopilot.systemdata.config;

import com.jobseekercopilot.generated.adzunagateway.api.AdzunaJobsApi;
import com.jobseekercopilot.generated.jsearchgateway.api.JSearchJobsApi;
import com.jobseekercopilot.generated.postcodeiogateway.api.PostcodeApi;
import com.jobseekercopilot.generated.reedgateway.api.ReedJobsApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayApiConfig {

    @Bean
    AdzunaJobsApi adzunaJobsApi(SystemDataProperties properties) {
        var apiClient = new com.jobseekercopilot.generated.adzunagateway.client.ApiClient();
        apiClient.setBasePath(properties.getGateways().getAdzuna().getBaseUrl());
        return new AdzunaJobsApi(apiClient);
    }

    @Bean
    JSearchJobsApi jSearchJobsApi(SystemDataProperties properties) {
        var apiClient = new com.jobseekercopilot.generated.jsearchgateway.client.ApiClient();
        apiClient.setBasePath(properties.getGateways().getJsearch().getBaseUrl());
        return new JSearchJobsApi(apiClient);
    }

    @Bean
    ReedJobsApi reedJobsApi(SystemDataProperties properties) {
        var apiClient = new com.jobseekercopilot.generated.reedgateway.client.ApiClient();
        apiClient.setBasePath(properties.getGateways().getReed().getBaseUrl());
        return new ReedJobsApi(apiClient);
    }

    @Bean
    PostcodeApi postcodeApi(SystemDataProperties properties) {
        var apiClient = new com.jobseekercopilot.generated.postcodeiogateway.client.ApiClient();
        apiClient.setBasePath(properties.getGateways().getPostcodeIo().getBaseUrl());
        return new PostcodeApi(apiClient);
    }

}
