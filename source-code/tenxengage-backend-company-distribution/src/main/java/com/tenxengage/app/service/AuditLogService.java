package com.tenxengage.app.service;

import com.tenxengage.app.entity.AuditLog;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditActorType;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.repository.AuditLogRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetails;
import com.tenxengage.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordHash", "password_hash", "token", "secretKey",
            "secret_key", "accessToken", "refreshToken", "apiKey", "api_key"
    );

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(AuditAction action, AuditResourceType resourceType, UUID resourceId,
                         String resourceName, String description, Map<String, Object> metadata) {
        try {
            log(action, resourceType, resourceId, resourceName, description, metadata);
        } catch (Exception e) {
            log.error("Failed to write audit log asynchronously: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, AuditResourceType resourceType, UUID resourceId,
                    String resourceName, String description, Map<String, Object> metadata) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID clientId = TenantContext.getClientId();

        if (clientId == null && auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            clientId = userDetails.getClientId();
        }

        if (clientId == null) {
            log.warn("Cannot write audit log: no client context available for action={} resource={}", action, resourceType);
            return;
        }

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .clientId(clientId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .description(description)
                .metadata(sanitize(metadata));

        // Extract request ID from MDC
        String requestIdStr = MDC.get("requestId");
        if (requestIdStr != null) {
            try {
                builder.requestId(UUID.fromString(requestIdStr));
            } catch (IllegalArgumentException ignored) {
                // Not a valid UUID, skip
            }
        }

        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            builder.actorType(AuditActorType.USER)
                    .actorId(userDetails.getUserId())
                    .actorEmail(userDetails.getUsername())
                    .ipAddress(MDC.get("clientIp"));

            // Look up denormalized user info
            userRepository.findById(userDetails.getUserId()).ifPresent(user -> {
                builder.actorName(user.getFirstName() + " " + user.getLastName());
                builder.userType(resolveUserType(userDetails));
                if (user.getPartnerCompany() != null) {
                    builder.companyName(user.getPartnerCompany().getName());
                } else if (user.getPartnerCompanyId() != null) {
                    builder.companyName(null); // Partner company exists but not eagerly loaded
                }
            });
        } else {
            builder.actorType(AuditActorType.SYSTEM);
        }

        auditLogRepository.save(builder.build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWithActor(AuditAction action, AuditResourceType resourceType, UUID resourceId,
                             String resourceName, String description, UUID clientId,
                             String actorEmail, String actorName, String ipAddress) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .clientId(clientId)
                .actorType(AuditActorType.USER)
                .actorEmail(actorEmail)
                .actorName(actorName)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .description(description)
                .ipAddress(ipAddress);

        String requestIdStr = MDC.get("requestId");
        if (requestIdStr != null) {
            try {
                builder.requestId(UUID.fromString(requestIdStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Look up actor by email to get ID and user type
        userRepository.findByEmail(actorEmail).ifPresent(user -> {
            builder.actorId(user.getId());
            CustomUserDetails details = new CustomUserDetails(user);
            builder.userType(resolveUserType(details));
            if (user.getPartnerCompany() != null) {
                builder.companyName(user.getPartnerCompany().getName());
            }
        });

        auditLogRepository.save(builder.build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemEvent(AuditAction action, AuditResourceType resourceType, UUID resourceId,
                               String resourceName, String description, UUID clientId) {
        AuditLog auditLog = AuditLog.builder()
                .clientId(clientId)
                .actorType(AuditActorType.SYSTEM)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .description(description)
                .build();
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> query(UUID clientId, String userType, AuditAction action,
                                LocalDate dateFrom, LocalDate dateTo, String search,
                                int page, int pageSize) {
        Instant from = dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant to = dateTo != null ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        return auditLogRepository.findFiltered(
                clientId, userType, action, from, to, search,
                PageRequest.of(page, pageSize)
        );
    }

    private String resolveUserType(CustomUserDetails userDetails) {
        // Partner users have a partnerCompanyId; internal users do not
        if (userDetails.getPartnerCompanyId() != null) {
            return "Partner";
        }
        return "Internal";
    }

    private Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return metadata;
        }
        Map<String, Object> sanitized = new HashMap<>(metadata.size());
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (SENSITIVE_KEYS.contains(entry.getKey())) {
                sanitized.put(entry.getKey(), "***REDACTED***");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                sanitized.put(entry.getKey(), sanitize(nestedMap));
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }
}
