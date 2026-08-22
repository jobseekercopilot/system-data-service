package com.jobseekercopilot.systemdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;

class DownstreamEnvironmentDataCredentialTest {
    private static final String CALLER_KEY = "inbound-system-data-caller-key-0001";
    private static final String DOWNSTREAM_TOKEN = "downstream-environment-data-token-0001";

    @Test
    void authenticatesEveryEnvironmentManagementRequestWithExactlyOneHeader() {
        SystemDataProperties properties = enabledProperties(DOWNSTREAM_TOKEN);
        var credential = new DownstreamEnvironmentDataCredential(properties);
        var client = new EnvironmentManagementHttpConfig().environmentManagementRestTemplate(credential);
        var server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("http://authentication-service:8084/internal/system-data/verify/users/test-user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(DownstreamEnvironmentDataCredential.HEADER_NAME, DOWNSTREAM_TOKEN))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        client.getForEntity(
                "http://authentication-service:8084/internal/system-data/verify/users/test-user",
                String.class);

        server.verify();
        assertThat(client.getInterceptors()).hasSize(1);
    }

    @Test
    void rejectsMissingWeakOrReusedCredentialsWithoutExposingValues() {
        for (String token : new String[] {null, "too-short", CALLER_KEY}) {
            var credential = new DownstreamEnvironmentDataCredential(enabledProperties(token));
            assertThatThrownBy(credential::requiredToken)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(CALLER_KEY)
                    .hasMessageNotContaining("too-short");
        }
    }

    @Test
    void disabledEnvironmentManagementDoesNotRequireAConfiguredTokenAtStartup() {
        SystemDataProperties properties = new SystemDataProperties();
        var credential = new DownstreamEnvironmentDataCredential(properties);

        credential.validateEnabledConfiguration();
        assertThatThrownBy(credential::requiredToken).isInstanceOf(IllegalStateException.class);
    }

    private SystemDataProperties enabledProperties(String token) {
        SystemDataProperties properties = new SystemDataProperties();
        properties.getEnvironmentManagement().setEnabled(true);
        properties.getEnvironmentManagement().setCallerKey(CALLER_KEY);
        properties.getEnvironmentManagement().setDownstreamEnvironmentDataToken(token);
        return properties;
    }
}
