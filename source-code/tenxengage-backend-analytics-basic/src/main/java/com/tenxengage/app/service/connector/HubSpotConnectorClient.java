package com.tenxengage.app.service.connector;

import com.tenxengage.app.dto.response.TestConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HubSpot connector using API key (Bearer token) authentication.
 *
 * Required config: apiKey, portalId
 *
 * Test connection by calling the HubSpot Account Info API to verify the key is valid
 * and the portal ID matches.
 */
@Component
public class HubSpotConnectorClient implements ConnectorClient {

    private static final Logger log = LoggerFactory.getLogger(HubSpotConnectorClient.class);
    private static final String HUBSPOT_API_BASE = "https://api.hubapi.com";

    private final RestClient restClient;

    public HubSpotConnectorClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public TestConnectionResponse testConnection(Map<String, String> config, String authType) {
        String apiKey = config.get("apiKey");
        String portalId = config.get("portalId");

        if (apiKey == null || apiKey.isBlank()) {
            return new TestConnectionResponse(false, "Missing required field: apiKey");
        }
        if (portalId == null || portalId.isBlank()) {
            return new TestConnectionResponse(false, "Missing required field: portalId");
        }

        try {
            // Verify API key by calling the account info endpoint
            Map<?, ?> response = restClient.get()
                    .uri(HUBSPOT_API_BASE + "/account-info/v3/details")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return new TestConnectionResponse(false, "No response from HubSpot API");
            }

            // Verify portal ID matches
            Object responsePortalId = response.get("portalId");
            if (responsePortalId != null && !String.valueOf(responsePortalId).equals(portalId)) {
                return new TestConnectionResponse(false,
                        "Portal ID mismatch: expected " + portalId + " but got " + responsePortalId);
            }

            return new TestConnectionResponse(true,
                    "Connected to HubSpot (Portal ID: " + portalId + ")");
        } catch (Exception e) {
            log.warn("HubSpot connection test failed", e);
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage());
        }
    }
}
