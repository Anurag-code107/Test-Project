package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.UpdateClientNotificationConfigRequest;
import com.tenxengage.app.dto.request.UpdateNotificationRetentionRequest;
import com.tenxengage.app.dto.response.ClientNotificationConfigResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.NotificationConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notification-configs")
@Tag(name = "Notification Configs", description = "Client notification role configuration endpoints")
public class NotificationConfigController {

    private final NotificationConfigService notificationConfigService;

    public NotificationConfigController(NotificationConfigService notificationConfigService) {
        this.notificationConfigService = notificationConfigService;
    }

    @GetMapping
    @Operation(summary = "List all notification configs for the current client")
    @RequiresPermission("action.notifications.config")
    public ResponseEntity<List<ClientNotificationConfigResponse>> getConfigs() {
        return ResponseEntity.ok(notificationConfigService.getConfigs());
    }

    @PutMapping
    @Operation(summary = "Update a notification role config for the current client")
    @RequiresPermission("action.notifications.config")
    @Audited(action = "Edited", resourceType = "NOTIFICATION_CONFIG", description = "Updated notification config")
    public ResponseEntity<ClientNotificationConfigResponse> updateConfig(
            @Valid @RequestBody UpdateClientNotificationConfigRequest request) {
        return ResponseEntity.ok(notificationConfigService.updateConfig(request));
    }

    @GetMapping("/retention")
    @Operation(summary = "Get notification retention days for the current client")
    @RequiresPermission("action.notifications.config")
    public ResponseEntity<Map<String, Integer>> getRetention() {
        int days = notificationConfigService.getRetentionDays();
        return ResponseEntity.ok(Map.of("retentionDays", days));
    }

    @PutMapping("/retention")
    @Operation(summary = "Update notification retention days for the current client")
    @RequiresPermission("action.notifications.config")
    @Audited(action = "Edited", resourceType = "NOTIFICATION_CONFIG", description = "Updated notification retention")
    public ResponseEntity<Map<String, Integer>> updateRetention(
            @Valid @RequestBody UpdateNotificationRetentionRequest request) {
        int days = notificationConfigService.updateRetentionDays(request);
        return ResponseEntity.ok(Map.of("retentionDays", days));
    }
}
