package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    String user,
    String email,
    String userType,
    String company,
    String action,
    String actionDescription,
    String target,
    Instant date
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        String actionName = formatAction(auditLog.getAction().name());
        String resourceType = auditLog.getResourceType().name().toLowerCase().replace("_", " ");
        String description = auditLog.getDescription() != null
                ? auditLog.getDescription()
                : actionName + " " + resourceType;

        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorName() != null ? auditLog.getActorName() : auditLog.getActorType().name(),
                auditLog.getActorEmail(),
                auditLog.getUserType(),
                auditLog.getCompanyName(),
                actionName,
                description,
                auditLog.getResourceName(),
                auditLog.getCreatedAt()
        );
    }

    private static String formatAction(String enumName) {
        // LOGGED_IN → "Logged In", CREATED → "Created"
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
