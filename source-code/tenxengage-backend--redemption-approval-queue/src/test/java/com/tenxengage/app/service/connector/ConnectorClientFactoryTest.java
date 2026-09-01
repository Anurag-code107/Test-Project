package com.tenxengage.app.service.connector;

import com.tenxengage.app.entity.enums.ConnectorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConnectorClientFactoryTest {

    @Mock
    private SalesforceConnectorClient salesforceClient;

    @Mock
    private DynamicsConnectorClient dynamicsClient;

    @Mock
    private SnowflakeConnectorClient snowflakeClient;

    @Mock
    private HubSpotConnectorClient hubSpotClient;

    private ConnectorClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ConnectorClientFactory(salesforceClient, dynamicsClient, snowflakeClient, hubSpotClient);
    }

    @Test
    void getClient_salesforce_returnsSalesforceClient() {
        ConnectorClient client = factory.getClient(ConnectorType.SALESFORCE);
        assertThat(client).isSameAs(salesforceClient);
    }

    @Test
    void getClient_dynamics_returnsDynamicsClient() {
        ConnectorClient client = factory.getClient(ConnectorType.MICROSOFT_DYNAMICS_365);
        assertThat(client).isSameAs(dynamicsClient);
    }

    @Test
    void getClient_snowflake_returnsSnowflakeClient() {
        ConnectorClient client = factory.getClient(ConnectorType.SNOWFLAKE);
        assertThat(client).isSameAs(snowflakeClient);
    }

    @Test
    void getClient_hubspot_returnsHubSpotClient() {
        ConnectorClient client = factory.getClient(ConnectorType.HUBSPOT);
        assertThat(client).isSameAs(hubSpotClient);
    }
}
