package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.response.ProfileFieldResponse;
import com.tenxengage.app.entity.DataObject;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.FieldDataType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.DataObjectRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ProfileFieldService {

    private static final Logger log = LoggerFactory.getLogger(ProfileFieldService.class);
    private static final String PARTNER_USER_DATA_NAME = "Partner User Data";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Field names that map to dedicated User entity columns.
     * Used for both resolving values and writing updates.
     */
    private static final Map<String, Function<User, String>> KNOWN_FIELD_RESOLVERS = Map.of(
        "Partner ID", u -> u.getPartnerCompany() != null
            ? String.valueOf(u.getPartnerCompany().getExternalPartnerId()) : null,
        "First Name", u -> u.getFirstName(),
        "Last Name", u -> u.getLastName(),
        "Email", u -> u.getEmail()
    );

    /**
     * Known fields that are always read-only regardless of editableByUser flag.
     */
    private static final Set<String> IMMUTABLE_KNOWN_FIELDS = Set.of(
        "Partner ID", "Email"
    );

    private final DataObjectRepository dataObjectRepository;
    private final UserRepository userRepository;
    private final TenantValidator tenantValidator;

    public ProfileFieldService(DataObjectRepository dataObjectRepository,
                                UserRepository userRepository,
                                TenantValidator tenantValidator) {
        this.dataObjectRepository = dataObjectRepository;
        this.userRepository = userRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public List<ProfileFieldResponse> getProfileFields() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        User user = userRepository.findByIdAndClientId(userId, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        boolean isExternal = user.getPartnerCompanyId() != null;

        if (isExternal) {
            return getExternalProfileFields(user, clientId);
        }
        return getInternalProfileFields(user);
    }

    /**
     * External users (Partner Admin, Partner Seller): profile fields driven
     * by the Partner User Data data object configuration.
     */
    private List<ProfileFieldResponse> getExternalProfileFields(User user, UUID clientId) {
        DataObject partnerUserData = dataObjectRepository
            .findByClientIdAndName(clientId, PARTNER_USER_DATA_NAME)
            .orElse(null);

        if (partnerUserData != null) {
            Map<String, String> metadata = parseMetadata(user.getMetadata());
            List<ProfileFieldResponse> fields = partnerUserData.getFields().stream()
                .filter(DataObjectField::isVisibleOnProfile)
                .sorted(Comparator.comparingInt(DataObjectField::getSortOrder))
                .map(field -> toProfileFieldResponse(field, user, metadata))
                .toList();

            if (!fields.isEmpty()) {
                return fields;
            }
        }

        // Fallback for external users when no data object fields are marked visible
        String partnerName = user.getPartnerCompany() != null
            ? user.getPartnerCompany().getName() : null;

        return List.of(
            new ProfileFieldResponse(null, "Partner Name",
                FieldDataType.TEXT, partnerName, false, 0, null),
            new ProfileFieldResponse(null, "First Name",
                FieldDataType.TEXT, user.getFirstName(), true, 1, null),
            new ProfileFieldResponse(null, "Last Name",
                FieldDataType.TEXT, user.getLastName(), true, 2, null),
            new ProfileFieldResponse(null, "Email",
                FieldDataType.TEXT, user.getEmail(), false, 3, null)
        );
    }

    /**
     * Internal users (Client Admin, Activity Approver): simple static fields
     * not driven by any data object.
     */
    private List<ProfileFieldResponse> getInternalProfileFields(User user) {
        return List.of(
            new ProfileFieldResponse(null, "First Name",
                FieldDataType.TEXT, user.getFirstName(), true, 0, null),
            new ProfileFieldResponse(null, "Last Name",
                FieldDataType.TEXT, user.getLastName(), true, 1, null),
            new ProfileFieldResponse(null, "Email",
                FieldDataType.TEXT, user.getEmail(), false, 2, null)
        );
    }

    @Transactional
    public void updateProfileFields(Map<String, String> customFields) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        User user = userRepository.findByIdAndClientId(userId, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        boolean isExternal = user.getPartnerCompanyId() != null;

        if (isExternal) {
            updateExternalProfileFields(user, clientId, customFields);
        } else {
            updateInternalProfileFields(user, customFields);
        }

        userRepository.save(user);
        log.info("Profile fields updated for userId={}", userId);
    }

    /**
     * External users: validate against data object field config (editableByUser flag).
     */
    private void updateExternalProfileFields(User user, UUID clientId,
                                              Map<String, String> customFields) {
        DataObject partnerUserData = dataObjectRepository
            .findByClientIdAndName(clientId, PARTNER_USER_DATA_NAME)
            .orElseThrow(() -> new ResourceNotFoundException("DataObject", "name", PARTNER_USER_DATA_NAME));

        Map<String, String> metadata = parseMetadata(user.getMetadata());
        boolean metadataChanged = false;

        for (Map.Entry<String, String> entry : customFields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            DataObjectField fieldDef = partnerUserData.getFields().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElse(null);

            if (fieldDef == null) {
                continue;
            }

            if (!fieldDef.isEditableByUser()) {
                throw new AccessDeniedException("Field '" + fieldName + "' is not editable");
            }

            if (IMMUTABLE_KNOWN_FIELDS.contains(fieldName)) {
                throw new AccessDeniedException("Field '" + fieldName + "' cannot be modified");
            }

            if (KNOWN_FIELD_RESOLVERS.containsKey(fieldName)) {
                writeKnownField(user, fieldName, value);
            } else {
                metadata.put(fieldName, value);
                metadataChanged = true;
            }
        }

        if (metadataChanged) {
            try {
                user.setMetadata(MAPPER.writeValueAsString(metadata));
            } catch (Exception e) {
                log.error("Failed to serialize user metadata", e);
            }
        }
    }

    /**
     * Internal users: only First Name and Last Name are editable.
     */
    private void updateInternalProfileFields(User user, Map<String, String> customFields) {
        for (Map.Entry<String, String> entry : customFields.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            switch (fieldName) {
                case "First Name" -> user.setFirstName(value);
                case "Last Name" -> user.setLastName(value);
                default -> throw new AccessDeniedException("Field '" + fieldName + "' is not editable");
            }
        }
    }

    private ProfileFieldResponse toProfileFieldResponse(DataObjectField field, User user,
                                                         Map<String, String> metadata) {
        String value;
        Function<User, String> resolver = KNOWN_FIELD_RESOLVERS.get(field.getName());
        if (resolver != null) {
            value = resolver.apply(user);
        } else {
            value = metadata.get(field.getName());
        }

        List<String> sampleValues = parseSampleValues(field.getSampleValues());

        return new ProfileFieldResponse(
            field.getId(),
            field.getName(),
            field.getDataType(),
            value,
            field.isEditableByUser() && !IMMUTABLE_KNOWN_FIELDS.contains(field.getName()),
            field.getSortOrder(),
            sampleValues
        );
    }

    private void writeKnownField(User user, String fieldName, String value) {
        switch (fieldName) {
            case "First Name" -> user.setFirstName(value);
            case "Last Name" -> user.setLastName(value);
            case "Email" -> user.setEmail(value);
            default -> log.warn("Attempted to write immutable known field: {}", fieldName);
        }
    }

    private Map<String, String> parseMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new java.util.HashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse user metadata, returning empty map", e);
            return new java.util.HashMap<>();
        }
    }

    private List<String> parseSampleValues(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
