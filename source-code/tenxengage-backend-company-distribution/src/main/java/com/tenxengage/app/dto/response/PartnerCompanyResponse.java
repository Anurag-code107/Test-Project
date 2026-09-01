package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PartnerCompanyResponse(
    UUID id,
    String name,
    String externalPartnerId,
    List<LocationAssignmentResponse> locations,
    String partnerType,
    UUID clientId,
    String clientName,
    PartnerCompanyStatus status,
    String website,
    String contactEmail,
    String contactPhone,
    long activeUserCount,
    String metadata,
    Instant createdAt,
    Instant updatedAt,

    /**
     * The company's XTRM payout connection, or null if it has never been connected.
     *
     * <p>Populated on the detail and connect paths only. The list path leaves it null rather than issuing a
     * query per row.</p>
     */
    PartnerCompanyXtrmAccountResponse xtrmAccount
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record LocationAssignmentResponse(
        UUID locationValueId,
        String locationValueName,
        String locationLevelName,
        UUID locationLevelId
    ) {
        public static LocationAssignmentResponse from(PartnerCompanyLocation pcl) {
            return new LocationAssignmentResponse(
                pcl.getLocationValue().getId(),
                pcl.getLocationValue().getName(),
                pcl.getLocationValue().getLevel().getName(),
                pcl.getLocationValue().getLevel().getId()
            );
        }
    }

    public static PartnerCompanyResponse from(PartnerCompany pc, String clientName) {
        return from(pc, clientName, 0);
    }

    public static PartnerCompanyResponse from(PartnerCompany pc, String clientName, long activeUserCount) {
        return from(pc, clientName, activeUserCount, null);
    }

    public static PartnerCompanyResponse from(PartnerCompany pc, String clientName, long activeUserCount,
                                              PartnerCompanyXtrmAccountResponse xtrmAccount) {
        Map<String, Object> meta = parseMetadata(pc.getMetadata());
        List<LocationAssignmentResponse> locationResponses = List.of();
        if (pc.getLocationAssignments() != null) {
            try {
                locationResponses = pc.getLocationAssignments().stream()
                    .map(LocationAssignmentResponse::from)
                    .toList();
            } catch (Exception e) {
                // Lazy-loading may fail if session is closed; return empty list
            }
        }
        return new PartnerCompanyResponse(
            pc.getId(),
            pc.getName(),
            pc.getExternalPartnerId(),
            locationResponses,
            meta.get("Partner Type") != null ? String.valueOf(meta.get("Partner Type")) : null,
            pc.getClientId(),
            clientName,
            pc.getStatus(),
            pc.getWebsite(),
            meta.get("Contact Email") != null ? String.valueOf(meta.get("Contact Email")) : null,
            pc.getContactPhone(),
            activeUserCount,
            pc.getMetadata(),
            pc.getCreatedAt(),
            pc.getUpdatedAt(),
            xtrmAccount
        );
    }

    private static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
