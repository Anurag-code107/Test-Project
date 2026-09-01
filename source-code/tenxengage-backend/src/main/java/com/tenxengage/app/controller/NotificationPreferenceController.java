package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.BulkUpdateUserPreferencesRequest;
import com.tenxengage.app.dto.request.UpdateUserNotificationPreferenceRequest;
import com.tenxengage.app.dto.request.UpdateUserNotificationSettingRequest;
import com.tenxengage.app.dto.response.UserNotificationPreferenceResponse;
import com.tenxengage.app.dto.response.UserNotificationSettingResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.NotificationPreferenceService;
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

@RestController
@RequestMapping("/api/v1/notification-preferences")
@Tag(name = "Notification Preferences", description = "User notification preference endpoints")
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    public NotificationPreferenceController(NotificationPreferenceService notificationPreferenceService) {
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @GetMapping("/global")
    @Operation(summary = "Get global notification setting for current user")
    @RequiresPermission("action.notifications.manage")
    public ResponseEntity<UserNotificationSettingResponse> getGlobalSetting() {
        return ResponseEntity.ok(notificationPreferenceService.getGlobalSetting());
    }

    @PutMapping("/global")
    @Operation(summary = "Update global notification setting for current user")
    @RequiresPermission("action.notifications.manage")
    public ResponseEntity<UserNotificationSettingResponse> updateGlobalSetting(
            @Valid @RequestBody UpdateUserNotificationSettingRequest request) {
        return ResponseEntity.ok(notificationPreferenceService.updateGlobalSetting(request));
    }

    @GetMapping
    @Operation(summary = "List per-type notification preferences for current user")
    @RequiresPermission("action.notifications.manage")
    public ResponseEntity<List<UserNotificationPreferenceResponse>> getPreferences() {
        return ResponseEntity.ok(notificationPreferenceService.getPreferences());
    }

    @PutMapping
    @Operation(summary = "Update a per-type notification preference for current user")
    @RequiresPermission("action.notifications.manage")
    public ResponseEntity<UserNotificationPreferenceResponse> updatePreference(
            @Valid @RequestBody UpdateUserNotificationPreferenceRequest request) {
        return ResponseEntity.ok(notificationPreferenceService.updatePreference(request));
    }

    @PutMapping("/bulk")
    @Operation(summary = "Bulk update per-type notification preferences for current user")
    @RequiresPermission("action.notifications.manage")
    public ResponseEntity<List<UserNotificationPreferenceResponse>> bulkUpdatePreferences(
            @Valid @RequestBody BulkUpdateUserPreferencesRequest request) {
        return ResponseEntity.ok(notificationPreferenceService.bulkUpdatePreferences(request));
    }
}
