package com.tenxengage.app.service.connector;

import com.tenxengage.app.dto.response.TestConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

/**
 * Snowflake connector using JDBC with three authentication modes.
 *
 * Auth types:
 * - LOGIN: username + password + url + role
 * - PASSKEY: username + password + privateKey + privateKeyPassword (optional) + url
 * - OAUTH: authorizationCode + clientId + clientSecret + tokenRequestUrl + redirectUrl + url
 *
 * Test connection runs SELECT CURRENT_VERSION() to verify connectivity.
 */
@Component
public class SnowflakeConnectorClient implements ConnectorClient {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeConnectorClient.class);

    @Override
    public TestConnectionResponse testConnection(Map<String, String> config, String authType) {
        String url = config.get("url");
        if (url == null || url.isBlank()) {
            return new TestConnectionResponse(false, "Missing required field: url");
        }

        // Normalize URL to JDBC format
        String jdbcUrl = url.startsWith("jdbc:snowflake://") ? url : "jdbc:snowflake://" + url;

        Properties props = new Properties();
        String effectiveAuthType = authType != null ? authType.toUpperCase() : "LOGIN";

        switch (effectiveAuthType) {
            case "LOGIN" -> {
                String username = config.get("username");
                String password = config.get("password");
                if (username == null || password == null) {
                    return new TestConnectionResponse(false, "Missing required fields: username, password");
                }
                props.put("user", username);
                props.put("password", password);
                String role = config.get("role");
                if (role != null && !role.isBlank()) {
                    props.put("role", role);
                }
            }
            case "PASSKEY" -> {
                String username = config.get("username");
                String privateKey = config.get("privateKey");
                if (username == null || privateKey == null) {
                    return new TestConnectionResponse(false, "Missing required fields: username, privateKey");
                }
                props.put("user", username);
                props.put("privateKey", privateKey);
                String pkPassword = config.get("privateKeyPassword");
                if (pkPassword != null && !pkPassword.isBlank()) {
                    props.put("privateKeyPassword", pkPassword);
                }
                props.put("authenticator", "snowflake_jwt");
            }
            case "OAUTH" -> {
                String token = config.get("authorizationCode");
                if (token == null || token.isBlank()) {
                    return new TestConnectionResponse(false, "Missing required field: authorizationCode (token)");
                }
                props.put("authenticator", "oauth");
                props.put("token", token);
            }
            default -> {
                return new TestConnectionResponse(false, "Unknown auth type: " + authType);
            }
        }

        props.put("loginTimeout", "30");
        props.put("networkTimeout", "30");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, props);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            try (ResultSet rs = stmt.executeQuery("SELECT CURRENT_VERSION()")) {

            String version = rs.next() ? rs.getString(1) : "unknown";
            return new TestConnectionResponse(true,
                    "Connected to Snowflake (version " + version + ")");
            }
        } catch (Exception e) {
            log.warn("Snowflake connection test failed", e);
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage());
        }
    }
}
