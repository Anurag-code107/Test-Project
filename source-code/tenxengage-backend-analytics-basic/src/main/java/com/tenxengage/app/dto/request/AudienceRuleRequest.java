package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Inbound DTO for one audience-eligibility rule on an incentive.
 *
 * <p>Wire format (post-BUG-079):
 * <ul>
 *   <li>{@code ruleType} — LOCATION, ROLE, or PARTNER_TYPE. Legacy REGION/COUNTRY are rejected
 *       at the service layer (BUG-034 cutover).</li>
 *   <li>{@code ruleValue} — UUID for LOCATION (LocationValue.id, any depth) and ROLE
 *       (ClientRole.id); free-text label for PARTNER_TYPE.</li>
 *   <li>{@code locationLevelId} — required for LOCATION rules; disambiguates the depth in the
 *       tenant's hierarchy. Null for non-location rules.</li>
 * </ul>
 *
 * <p>BUG-079: the previous {@code locationValueName} field was removed. Names belong in
 * frontend state and Excel files; resolution to UUIDs happens at the frontend boundary
 * (mapper for picker selections, parser for uploads). The wire format is identity-stable
 * across renames because only the UUID crosses the boundary.
 */
public record AudienceRuleRequest(
    @NotBlank @Size(max = 20) String ruleType,
    @NotBlank @Size(max = 255) String ruleValue,
    UUID locationLevelId
) {
    /**
     * Back-compat constructor for ROLE / PARTNER_TYPE rules built in tests or by internal
     * callers that don't carry level metadata.
     */
    public AudienceRuleRequest(String ruleType, String ruleValue) {
        this(ruleType, ruleValue, null);
    }
}
