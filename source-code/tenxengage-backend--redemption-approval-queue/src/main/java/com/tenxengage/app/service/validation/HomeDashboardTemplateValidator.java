package com.tenxengage.app.service.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.model.HomeDashboardLayoutPayload;
import com.tenxengage.app.dto.model.HomeDashboardRowPayload;
import com.tenxengage.app.dto.model.HomeDashboardSlotPayload;
import com.tenxengage.app.entity.enums.HomeDashboardRowLayout;
import com.tenxengage.app.entity.enums.HomeDashboardWidget;
import com.tenxengage.app.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class HomeDashboardTemplateValidator {

    private static final Set<String> ALLOWED_ROLE_TYPES = Set.of("INTERNAL", "EXTERNAL");

    private final ObjectMapper objectMapper;

    public HomeDashboardTemplateValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateRoleType(String roleType) {
        if (roleType == null || !ALLOWED_ROLE_TYPES.contains(roleType)) {
            throw new BusinessRuleException("roleType must be INTERNAL or EXTERNAL, got: " + roleType);
        }
    }

    public HomeDashboardLayoutPayload parseAndValidate(String layoutJson, String templateRoleType) {
        validateRoleType(templateRoleType);
        HomeDashboardLayoutPayload payload;
        try {
            payload = objectMapper.readValue(layoutJson, HomeDashboardLayoutPayload.class);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Invalid layout JSON: " + e.getOriginalMessage());
        }
        validate(payload, templateRoleType);
        return payload;
    }

    public void validate(HomeDashboardLayoutPayload payload, String templateRoleType) {
        validateRoleType(templateRoleType);
        if (payload == null || payload.rows() == null || payload.rows().isEmpty()) {
            throw new BusinessRuleException("Template layout must contain at least one row");
        }
        for (int r = 0; r < payload.rows().size(); r++) {
            final int rowIdx = r;
            HomeDashboardRowPayload row = payload.rows().get(rowIdx);
            if (row == null) {
                throw new BusinessRuleException("Row " + rowIdx + " is null");
            }
            HomeDashboardRowLayout layout = HomeDashboardRowLayout.fromKey(row.layout())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Row " + rowIdx + " has unknown layout: " + row.layout()));
            int expected = layout.getSlotCount();
            int actual = row.slots() == null ? 0 : row.slots().size();
            if (actual != expected) {
                throw new BusinessRuleException(
                        "Row " + rowIdx + " (layout '" + row.layout() + "') expects "
                                + expected + " slot(s), got " + actual);
            }
            for (int s = 0; s < row.slots().size(); s++) {
                final int slotIdx = s;
                HomeDashboardSlotPayload slot = row.slots().get(slotIdx);
                if (slot == null || slot.widgetKey() == null || slot.widgetKey().isBlank()) {
                    throw new BusinessRuleException("Row " + rowIdx + ", slot " + slotIdx + ": missing widgetKey");
                }
                HomeDashboardWidget widget = HomeDashboardWidget.fromKey(slot.widgetKey())
                        .orElseThrow(() -> new BusinessRuleException(
                                "Row " + rowIdx + ", slot " + slotIdx + ": unknown widget '" + slot.widgetKey() + "'"));
                if (!widget.supportsRoleType(templateRoleType)) {
                    throw new BusinessRuleException(
                            "Row " + rowIdx + ", slot " + slotIdx + ": widget '" + slot.widgetKey()
                                    + "' does not support role type " + templateRoleType);
                }
            }
        }
    }
}
