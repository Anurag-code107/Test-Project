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
 * Microsoft Dynamics 365 connector using OAuth2 Client Credentials flow via Azure AD.
 *
 * Required config: clientId, clientSecret, oauthAuthority, resource
 * - oauthAuthority: Azure AD token endpoint (e.g. https://login.microsoftonline.com/{tenantId})
 * - resource: Dynamics 365 instance URL (e.g. https://org.crm.dynamics.com)
 *
 * Flow:
 * 1. POST to {oauthAuthority}/oauth2/v2.0/token with client_credentials grant
 * 2. Use the access_token to call the Dynamics 365 Web API
 * 3. Test by fetching {resource}/api/data/v9.2/ (WhoAmI or API root)
 */
@Component
public class DynamicsConnectorClient implements ConnectorClient {

    private static final Logger log = LoggerFactory.getLogger(DynamicsConnectorClient.class);

    private final RestClient restClient;

    public DynamicsConnectorClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public TestConnectionResponse testConnection(Map<String, String> config, String authType) {
        String clientId = config.get("clientId");
        String clientSecret = config.get("clientSecret");
        String oauthAuthority = config.get("oauthAuthority");
        String resource = config.get("resource");

        if (clientId == null || clientSecret == null || oauthAuthority == null || resource == null) {
            return new TestConnectionResponse(false,
                    "Missing required fields: clientId, clientSecret, oauthAuthority, resource");
        }

        try {
            // Step 1: Obtain access token via Azure AD client_credentials
            String tokenUrl = oauthAuthority.endsWith("/")
                    ? oauthAuthority + "oauth2/v2.0/token"
                    : oauthAuthority + "/oauth2/v2.0/token";

            // Dynamics uses {resource}/.default as scope in v2.0 endpoint
            String scope = resource.endsWith("/") ? resource + ".default" : resource + "/.default";

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("scope", scope);

            Map<?, ?> tokenResponse = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
                return new TestConnectionResponse(false, "Authentication failed: no access token received");
            }

            String accessToken = (String) tokenResponse.get("access_token");

            // Step 2: Test API access with WhoAmI
            String apiBase = resource.endsWith("/") ? resource : resource + "/";
            restClient.get()
                    .uri(apiBase + "api/data/v9.2/WhoAmI")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("OData-MaxVersion", "4.0")
                    .header("OData-Version", "4.0")
                    .retrieve()
                    .body(String.class);

            return new TestConnectionResponse(true, "Connected to Dynamics 365 at " + resource);
        } catch (Exception e) {
            log.warn("Dynamics 365 connection test failed", e);
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage());
        }
    }
}
