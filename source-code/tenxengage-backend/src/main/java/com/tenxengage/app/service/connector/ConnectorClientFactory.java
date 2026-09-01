package com.tenxengage.app.service.connector;

import com.tenxengage.app.entity.enums.ConnectorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConnectorClientFactory {

    private final SalesforceConnectorClient salesforceClient;
    private final DynamicsConnectorClient dynamicsClient;
    private final SnowflakeConnectorClient snowflakeClient;
    private final HubSpotConnectorClient hubSpotClient;

    public ConnectorClientFactory(SalesforceConnectorClient salesforceClient,
                                  DynamicsConnectorClient dynamicsClient,
                                  SnowflakeConnectorClient snowflakeClient,
                                  HubSpotConnectorClient hubSpotClient) {
        this.salesforceClient = salesforceClient;
        this.dynamicsClient = dynamicsClient;
        this.snowflakeClient = snowflakeClient;
        this.hubSpotClient = hubSpotClient;
    }

    public ConnectorClient getClient(ConnectorType type) {
        return switch (type) {
            case SALESFORCE -> salesforceClient;
            case MICROSOFT_DYNAMICS_365 -> dynamicsClient;
            case SNOWFLAKE -> snowflakeClient;
            case HUBSPOT -> hubSpotClient;
        };
    }
}
