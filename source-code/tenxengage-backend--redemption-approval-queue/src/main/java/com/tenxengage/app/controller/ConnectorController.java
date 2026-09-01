package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateConnectorRequest;
import com.tenxengage.app.dto.request.UpdateConnectorRequest;
import com.tenxengage.app.dto.response.ConnectorResponse;
import com.tenxengage.app.dto.response.TestConnectionResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.ConnectorService;
import com.tenxengage.app.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connectors")
@Tag(name = "Connectors", description = "External data source connector management")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    @Operation(summary = "List connectors for current tenant")
    @RequiresPermission("action.integrations.view")
    public ResponseEntity<List<ConnectorResponse>> getConnectors() {
        return ResponseEntity.ok(connectorService.getConnectors());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get connector by ID")
    @RequiresPermission("action.integrations.view")
    public ResponseEntity<ConnectorResponse> getConnector(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.getConnector(id));
    }

    @PostMapping
    @Operation(summary = "Create connector")
    @RequiresPermission("action.integrations.manage")
    @Audited(action = "Created", resourceType = "CONNECTOR", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<ConnectorResponse> createConnector(@Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectorService.createConnector(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update connector")
    @RequiresPermission("action.integrations.manage")
    @Audited(action = "Edited", resourceType = "CONNECTOR", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<ConnectorResponse> updateConnector(@PathVariable UUID id,
                                                             @Valid @RequestBody UpdateConnectorRequest request) {
        return ResponseEntity.ok(connectorService.updateConnector(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete connector")
    @RequiresPermission("action.integrations.manage")
    @Audited(action = "Deleted", resourceType = "CONNECTOR", resourceId = "#id.toString()")
    public ResponseEntity<Void> deleteConnector(@PathVariable UUID id) {
        connectorService.deleteConnector(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test connector connection")
    @RequiresPermission("action.integrations.test")
    public ResponseEntity<TestConnectionResponse> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(connectorService.testConnection(id));
    }
}
