package com.tenxengage.app.service.connector;

import com.tenxengage.app.dto.response.TestConnectionResponse;

import java.util.Map;

/**
 * Interface for external data source connector clients.
 * Each implementation handles authentication and connection testing for a specific connector type.
 */
public interface ConnectorClient {

    /**
     * Test the connection using the provided configuration.
     * @param config decrypted credential map
     * @param authType optional sub-auth type (e.g. Snowflake: LOGIN, PASSKEY, OAUTH)
     * @return result indicating success or failure with a message
     */
    TestConnectionResponse testConnection(Map<String, String> config, String authType);
}
