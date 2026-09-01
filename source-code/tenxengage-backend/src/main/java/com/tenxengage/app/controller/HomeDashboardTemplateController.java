package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.response.HomeDashboardRowLayoutResponse;
import com.tenxengage.app.dto.response.HomeDashboardTemplateResponse;
import com.tenxengage.app.dto.response.HomeDashboardWidgetResponse;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.entity.enums.HomeDashboardRowLayout;
import com.tenxengage.app.entity.enums.HomeDashboardWidget;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.HomeDashboardTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Home Dashboard Templates", description = "Home dashboard template catalog and widget registry")
public class HomeDashboardTemplateController {

    private final HomeDashboardTemplateService templateService;
    private final TenantValidator tenantValidator;
    private final ObjectMapper objectMapper;

    public HomeDashboardTemplateController(HomeDashboardTemplateService templateService,
                                           TenantValidator tenantValidator,
                                           ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.tenantValidator = tenantValidator;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/home-dashboard-templates")
    @Operation(summary = "List home dashboard templates for the current tenant")
    @RequiresPermission("action.roles.view")
    public ResponseEntity<List<HomeDashboardTemplateResponse>> listTemplates(
            @RequestParam(required = false) String roleType) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<HomeDashboardTemplate> templates = (roleType != null && !roleType.isBlank())
                ? templateService.listForTenantAndRoleType(clientId, roleType)
                : templateService.listForTenant(clientId);
        return ResponseEntity.ok(templates.stream()
                .map(t -> HomeDashboardTemplateResponse.from(t, objectMapper))
                .toList());
    }

    @GetMapping("/home-dashboard-widgets")
    @Operation(summary = "List the widget catalog (global, not tenant-scoped)")
    public ResponseEntity<List<HomeDashboardWidgetResponse>> listWidgets() {
        return ResponseEntity.ok(Arrays.stream(HomeDashboardWidget.values())
                .map(HomeDashboardWidgetResponse::from)
                .toList());
    }

    @GetMapping("/home-dashboard-layouts")
    @Operation(summary = "List the supported row layouts (global, not tenant-scoped)")
    public ResponseEntity<List<HomeDashboardRowLayoutResponse>> listLayouts() {
        return ResponseEntity.ok(Arrays.stream(HomeDashboardRowLayout.values())
                .map(HomeDashboardRowLayoutResponse::from)
                .toList());
    }
}
