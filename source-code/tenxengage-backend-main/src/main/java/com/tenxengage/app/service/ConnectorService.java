package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateConnectorRequest;
import com.tenxengage.app.dto.request.UpdateConnectorRequest;
import com.tenxengage.app.dto.response.ConnectorResponse;
import com.tenxengage.app.dto.response.TestConnectionResponse;
import com.tenxengage.app.entity.Connector;
import com.tenxengage.app.entity.enums.ConnectorStatus;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.ConnectorRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.connector.ConnectorClient;
import com.tenxengage.app.service.connector.ConnectorClientFactory;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorService.class);

    private final ConnectorRepository connectorRepository;
    private final ConnectorEncryptionService encryptionService;
    private final ConnectorClientFactory connectorClientFactory;
    private final TenantValidator tenantValidator;
    private final NotificationEventProducer notificationEventProducer;

    public ConnectorService(ConnectorRepository connectorRepository,
                           ConnectorEncryptionService encryptionService,
                           ConnectorClientFactory connectorClientFactory,
                           TenantValidator tenantValidator,
                           NotificationEventProducer notificationEventProducer) {
        this.connectorRepository = connectorRepository;
        this.encryptionService = encryptionService;
        this.connectorClientFactory = connectorClientFactory;
        this.tenantValidator = tenantValidator;
        this.notificationEventProducer = notificationEventProducer;
    }

    @Transactional(readOnly = true)
    public List<ConnectorResponse> getConnectors() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return connectorRepository.findByClientIdOrderByName(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConnectorResponse getConnector(UUID id) {
        Connector connector = findByIdAndValidate(id);
        return toResponse(connector);
    }

    @Transactional
    public ConnectorResponse createConnector(CreateConnectorRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        if (connectorRepository.existsByClientIdAndName(clientId, request.name())) {
            throw new IllegalArgumentException("A connector with name '" + request.name() + "' already exists");
        }

        Connector connector = Connector.builder()
                .clientId(clientId)
                .connectorType(request.connectorType())
                .name(request.name())
                .config(encryptionService.encrypt(request.config()))
                .authType(request.authType())
                .build();

        connector = connectorRepository.save(connector);
        return toResponse(connector);
    }

    @Transactional
    public ConnectorResponse updateConnector(UUID id, UpdateConnectorRequest request) {
        Connector connector = findByIdAndValidate(id);

        if (request.name() != null) {
            connector.setName(request.name());
        }
        if (request.config() != null) {
            connector.setConfig(encryptionService.encrypt(request.config()));
            connector.setStatus(ConnectorStatus.DISCONNECTED);
        }
        if (request.authType() != null) {
            connector.setAuthType(request.authType());
        }

        connector = connectorRepository.save(connector);
        return toResponse(connector);
    }

    @Transactional
    public void deleteConnector(UUID id) {
        Connector connector = findByIdAndValidate(id);
        connectorRepository.delete(connector);
    }

    @Transactional
    public TestConnectionResponse testConnection(UUID id) {
        Connector connector = findByIdAndValidate(id);
        Map<String, String> config = encryptionService.decrypt(connector.getConfig());

        ConnectorClient client = connectorClientFactory.getClient(connector.getConnectorType());
        TestConnectionResponse result = client.testConnection(config, connector.getAuthType());

        connector.setStatus(result.success() ? ConnectorStatus.CONNECTED : ConnectorStatus.ERROR);
        connector.setLastSyncStatus(result.message());
        if (result.success()) {
            connector.setLastSyncAt(Instant.now());
        }
        connectorRepository.save(connector);

        log.info("Connection test for connector {} ({}): {}",
                connector.getName(), connector.getConnectorType(), result.success() ? "SUCCESS" : "FAILED");

        if (!result.success()) {
            UUID clientId = tenantValidator.getCurrentClientId();
            notificationEventProducer.publish(new NotificationEvent(
                "CONNECTOR_SYNC_FAILED", clientId,
                "Connector Failed: " + connector.getName(),
                "Connection test for '" + connector.getName() + "' failed: " + result.message(),
                "INTEGRATION", connector.getId(), tenantValidator.getCurrentUserId(), null, null));

            if (connector.getStatus() == ConnectorStatus.ERROR) {
                notificationEventProducer.publish(new NotificationEvent(
                    "CONNECTOR_DISCONNECTED", clientId,
                    "Connector Disconnected: " + connector.getName(),
                    "Connector '" + connector.getName() + "' has been disconnected due to errors.",
                    "INTEGRATION", connector.getId(), tenantValidator.getCurrentUserId(), null, null));
            }
        }

        return result;
    }

    private Connector findByIdAndValidate(UUID id) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Connector connector = connectorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Connector not found: " + id));
        if (!connector.getClientId().equals(clientId)) {
            throw new EntityNotFoundException("Connector not found: " + id);
        }
        return connector;
    }

    private ConnectorResponse toResponse(Connector connector) {
        Map<String, String> decrypted = encryptionService.decrypt(connector.getConfig());
        Map<String, String> masked = encryptionService.maskConfig(decrypted);
        return ConnectorResponse.from(connector, masked);
    }
}
