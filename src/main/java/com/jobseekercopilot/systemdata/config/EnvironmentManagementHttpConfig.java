package com.jobseekercopilot.systemdata.config;

import java.time.Duration;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class EnvironmentManagementHttpConfig {

    @Bean("environmentManagementRestTemplate")
    RestTemplate environmentManagementRestTemplate(DownstreamEnvironmentDataCredential credential) {
        var requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(
                    DownstreamEnvironmentDataCredential.HEADER_NAME,
                    credential.requiredToken());
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
