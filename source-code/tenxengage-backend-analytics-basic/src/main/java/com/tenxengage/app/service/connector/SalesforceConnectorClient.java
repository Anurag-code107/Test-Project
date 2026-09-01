package com.tenxengage.app.service.connector;

import com.tenxengage.app.dto.response.TestConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Salesforce connector using OAuth2 Client Credentials (Username-Password) flow.
 *
 * Required config: clientId, clientSecret, baseUrl
 * The baseUrl should be the Salesforce login URL (e.g. https://login.salesforce.com)
 *
 * Flow:
 * 1. POST to {baseUrl}/services/oauth2/token with grant_type=client_credentials
 * 2. On success, use the access_token to call the Salesforce REST API
 * 3. Test connection by fetching /services/data/ to verify API access
 */
@Component
public class SalesforceConnectorClient implements ConnectorClient {

    private static final Logger log = LoggerFactory.getLogger(SalesforceConnectorClient.class);

    private final RestClient restClient;

    public SalesforceConnectorClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public TestConnectionResponse testConnection(Map<String, String> config, String authType) {
        String clientId = config.get("clientId");
        String clientSecret = config.get("clientSecret");
        String baseUrl = config.get("baseUrl");

        if (clientId == null || clientSecret == null || baseUrl == null) {
            return new TestConnectionResponse(false, "Missing required fields: clientId, clientSecret, baseUrl");
        }

        try {
            // Step 1: Obtain access token via OAuth2 client_credentials flow
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);

            Map<?, ?> tokenResponse = restClient.post()
                    .uri(baseUrl + "/services/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                return new TestConnectionResponse(false, "Authentication failed: no access token received");
            }

            String accessToken = (String) tokenResponse.get("access_token");
            String instanceUrl = (String) tokenResponse.get("instance_url");

            // Step 2: Verify API access by fetching available API versions
            String apiUrl = (instanceUrl != null ? instanceUrl : baseUrl) + "/services/data/";
            restClient.get()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            return new TestConnectionResponse(true,
                    "Connected to Salesforce at " + (instanceUrl != null ? instanceUrl : baseUrl));
        } catch (Exception e) {
            log.warn("Salesforce connection test failed", e);
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage());
        }
    }
}
