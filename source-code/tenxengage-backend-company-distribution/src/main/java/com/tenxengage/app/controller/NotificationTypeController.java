package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.NotificationTypeResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.NotificationTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-types")
@Tag(name = "Notification Types", description = "Notification type catalog endpoints")
public class NotificationTypeController {

    private final NotificationTypeService notificationTypeService;

    public NotificationTypeController(NotificationTypeService notificationTypeService) {
        this.notificationTypeService = notificationTypeService;
    }

    @GetMapping
    @Operation(summary = "List all notification types")
    @RequiresPermission("action.notifications.view")
    public ResponseEntity<List<NotificationTypeResponse>> getAllTypes() {
        return ResponseEntity.ok(notificationTypeService.getAllTypes());
    }

    @GetMapping("/by-category")
    @Operation(summary = "List notification types by category")
    @RequiresPermission("action.notifications.view")
    public ResponseEntity<List<NotificationTypeResponse>> getTypesByCategory(
            @Parameter(description = "Category name (e.g., INCENTIVE, BUDGET, CLAIMS)")
            @RequestParam String category) {
        return ResponseEntity.ok(notificationTypeService.getTypesByCategory(category));
    }
}
