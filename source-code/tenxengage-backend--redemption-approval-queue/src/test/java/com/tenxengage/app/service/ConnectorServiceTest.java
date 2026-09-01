package com.tenxengage.app.service;

import com.tenxengage.app.entity.Connector;
import com.tenxengage.app.entity.enums.ConnectorType;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ConnectorRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.connector.ConnectorClientFactory;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock private ConnectorRepository connectorRepository;
    @Mock private ConnectorEncryptionService encryptionService;
    @Mock private ConnectorClientFactory connectorClientFactory;
    @Mock private TenantValidator tenantValidator;
    @Mock private NotificationEventProducer notificationEventProducer;

    @InjectMocks private ConnectorService connectorService;

    private UUID clientId;
    private UUID connectorId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        connectorId = UUID.randomUUID();
    }

    @Test
    void getConnector_throwsWhenNotFound() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(connectorRepository.findById(connectorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> connectorService.getConnector(connectorId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createConnector_throwsOnDuplicateName() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(connectorRepository.existsByClientIdAndName(clientId, "Existing")).thenReturn(true);

        assertThatThrownBy(() -> connectorService.createConnector(
                new com.tenxengage.app.dto.request.CreateConnectorRequest(
                        ConnectorType.SALESFORCE, "Existing", java.util.Map.of(), "OAUTH")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteConnector_removesEntity() {
        Connector connector = Connector.builder()
                .clientId(clientId).name("Test").connectorType(ConnectorType.SALESFORCE)
                .build();
        connector.setId(connectorId);

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(connectorRepository.findById(connectorId)).thenReturn(Optional.of(connector));

        connectorService.deleteConnector(connectorId);

        verify(connectorRepository).delete(connector);
    }
}
