package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateClientRequest;
import com.tenxengage.app.dto.request.SetFeatureOverrideRequest;
import com.tenxengage.app.dto.request.UpdateClientRequest;
import com.tenxengage.app.dto.response.ClientFeatureOverrideResponse;
import com.tenxengage.app.dto.response.ClientResponse;
import com.tenxengage.app.dto.response.ClientStatsResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client (tenant) management — TENX_ADMIN only")
@Validated
public class ClientController {

    private final ClientService clientService;
    private final FeatureFlagService featureFlagService;

    public ClientController(ClientService clientService, FeatureFlagService featureFlagService) {
        this.clientService = clientService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    @Operation(summary = "List clients", description = "Get a paginated list of all clients")
    @RequiresPermission("action.tenx.clients.view")
    public ResponseEntity<Page<ClientResponse>> getClients(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "Search by name or subdomain")
            @RequestParam(required = false) @Size(max = 255) String search) {
        Page<ClientResponse> clients = clientService.getClients(pageable, search);
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    @RequiresPermission("action.tenx.clients.view")
    public ResponseEntity<ClientResponse> getClientById(@PathVariable UUID id) {
        ClientResponse client = clientService.getClientById(id);
        return ResponseEntity.ok(client);
    }

    @PostMapping
    @Operation(summary = "Create client")
    @RequiresPermission("action.tenx.clients.manage")
    @Audited(action = "Created", resourceType = "CLIENT", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody CreateClientRequest request) {
        ClientResponse client = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update client")
    @RequiresPermission("action.tenx.clients.manage")
    @Audited(action = "Edited", resourceType = "CLIENT", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        ClientResponse client = clientService.updateClient(id, request);
        return ResponseEntity.ok(client);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete client")
    @RequiresPermission("action.tenx.clients.manage")
    @Audited(action = "Deleted", resourceType = "CLIENT", resourceId = "#id.toString()")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get client statistics")
    @RequiresPermission("action.tenx.clients.view")
    public ResponseEntity<ClientStatsResponse> getClientStats() {
        return ResponseEntity.ok(clientService.getClientStats());
    }

    @GetMapping("/{clientId}/feature-overrides")
    @Operation(summary = "Get feature overrides for a client")
    @RequiresPermission("action.tenx.features.view")
    public ResponseEntity<List<ClientFeatureOverrideResponse>> getClientFeatureOverrides(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(featureFlagService.getOverridesForClient(clientId));
    }

    @PutMapping("/{clientId}/feature-overrides")
    @Operation(summary = "Set feature overrides for a client")
    @RequiresPermission("action.tenx.features.manage")
    public ResponseEntity<List<ClientFeatureOverrideResponse>> setClientFeatureOverrides(
            @PathVariable UUID clientId,
            @Valid @RequestBody List<SetFeatureOverrideRequest> requests) {
        return ResponseEntity.ok(featureFlagService.setOverridesForClient(clientId, requests));
    }
}
