package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ParticipantEligibilityChecker {

    private static final Logger log = LoggerFactory.getLogger(ParticipantEligibilityChecker.class);

    // Defensive cap for walking LocationValue.parent chains. Real hierarchies are
    // single-digit deep (Region > Country > State > City); this only kicks in if
    // seed data ever introduces a cycle, in which case we log and bail rather than
    // hang the request thread.
    static final int MAX_HIERARCHY_DEPTH = 32;

    private final ObjectMapper objectMapper;

    public ParticipantEligibilityChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a location map from a partner company's location assignments, expanded
     * up the hierarchy: each assignment contributes an entry not only at its own level
     * but at every ancestor level reachable via {@link LocationValue#getParent()}.
     *
     * <p>BUG-061: a partner assigned only at {@code Country:USA} must implicitly satisfy
     * a {@code Region:AMERICAS} rule, since USA is a descendant of AMERICAS in the
     * hierarchy. Without ancestor expansion, the eligibility check finds no Region-level
     * assignment and silently denies the partner.
     *
     * <p>Map keys are level IDs; values are sets of location value IDs the partner
     * either explicitly belongs to or implicitly belongs to via descent.
     */
    public static Map<UUID, Set<UUID>> buildLocationMap(List<PartnerCompanyLocation> assignments) {
        if (assignments == null || assignments.isEmpty()) return Map.of();
        Map<UUID, Set<UUID>> byLevel = new HashMap<>();
        for (PartnerCompanyLocation pcl : assignments) {
            LocationValue value = pcl.getLocationValue();
            int hops = 0;
            while (value != null && hops < MAX_HIERARCHY_DEPTH) {
                if (value.getLevel() == null) break;
                byLevel.computeIfAbsent(value.getLevel().getId(), k -> new HashSet<>())
                    .add(value.getId());
                value = value.getParent();
                hops++;
            }
            if (hops == MAX_HIERARCHY_DEPTH && value != null) {
                log.warn("BUG-061 hierarchy walk hit MAX_HIERARCHY_DEPTH for assignment {}; "
                    + "possible cycle in location_values.parent_id chain.", pcl.getId());
            }
        }
        return byLevel;
    }

    /**
     * Checks if a user matches the participant eligibility criteria of an incentive.
     * Used for:
     * - Filtering which incentives a partner user can see
     * - Filtering which eligible/ineligible incentives appear in claims
     * - Validating eligibility in completion jobs
     *
     * <p>ROLE rules are matched on {@code ClientRole.id} (BUG-020). {@code userRoleName}
     * is only consulted by the transitional fallback below, which accepts rule values
     * that aren't UUID-shaped — belt-and-suspenders coverage for any external integration
     * or regressed seed path that emits a display-name string. The fallback is scheduled
     * for removal once telemetry confirms zero WARN hits for a release cycle.
     *
     * @param userLocationsByLevel map of levelId to set of location value IDs the user belongs to
     * @param userRoleId the user's ClientRole UUID (canonical key for ROLE rule match)
     * @param userRoleName the user's ClientRole display name (transitional fallback only)
     */
    public boolean matchesUserEligibility(Incentive incentive,
            Map<UUID, Set<UUID>> userLocationsByLevel,
            UUID userRoleId, String userRoleName,
            String userPartnerType, String externalPartnerId,
            Map<String, String> partnerMetadata) {

        // Group audience rules by type
        Map<String, List<IncentiveAudienceRule>> rulesByType = new HashMap<>();
        if (incentive.getAudienceRules() != null) {
            for (IncentiveAudienceRule rule : incentive.getAudienceRules()) {
                rulesByType.computeIfAbsent(rule.getRuleType(), k -> new ArrayList<>())
                    .add(rule);
            }
        }

        // 1. LOCATION check — dynamic multi-level location matching
        if (!matchesLocationRules(rulesByType.getOrDefault("LOCATION", List.of()), userLocationsByLevel)) {
            return false;
        }

        // 2. ROLE check — keyed on ClientRole.id with display-name fallback
        List<String> roleValues = rulesByType.getOrDefault("ROLE", List.of()).stream()
            .map(IncentiveAudienceRule::getRuleValue).toList();
        if (!matchesRoleRule(roleValues, userRoleId, userRoleName)) return false;

        // 3. PARTNER_TYPE check
        List<String> partnerTypeValues = rulesByType.getOrDefault("PARTNER_TYPE", List.of()).stream()
            .map(IncentiveAudienceRule::getRuleValue).toList();
        if (!matchesRule(partnerTypeValues, userPartnerType)) return false;

        // 4. Specific partners check
        if (!matchesSpecificPartners(incentive.getSpecificPartners(), externalPartnerId)) return false;

        // 5. Dynamic custom eligibility fields from builder config
        if (!matchesDynamicFields(incentive.getCustomFieldValues(), partnerMetadata)) return false;

        return true;
    }

    // matchesRoleRule: primary path parses rule_value as a UUID and compares to
    // userRoleId. Transitional fallback: if a rule_value doesn't parse as a UUID,
    // compare it as a display name (case-insensitive) against userRoleName, log a
    // WARN so the operator can spot unmigrated data, and proceed. Remove the
    // fallback in a follow-up PR after one clean release cycle.
    private boolean matchesRoleRule(List<String> ruleValues, UUID userRoleId, String userRoleName) {
        if (ruleValues == null || ruleValues.isEmpty()) return true;
        if (userRoleId == null && userRoleName == null) return false;
        for (String rv : ruleValues) {
            try {
                UUID ruleRoleId = UUID.fromString(rv);
                if (userRoleId != null && ruleRoleId.equals(userRoleId)) {
                    return true;
                }
            } catch (IllegalArgumentException notUuid) {
                log.warn("BUG-020 transitional fallback: ROLE audience rule_value '{}' is not a UUID; "
                    + "comparing to ClientRole.name '{}'. Check the seed/writer that emitted this row.",
                    rv, userRoleName);
                if (userRoleName != null && rv.equalsIgnoreCase(userRoleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks location-based eligibility rules. Rules are grouped by location level;
     * matching is OR both within a level (any value suffices) and across levels (any
     * populated level satisfying the rule admits the partner).
     *
     * <p>BUG-062: the previous AND-across-levels behavior silently rejected partners
     * whenever the admin selected a level deeper than the population was tagged at —
     * e.g. picking Region+Country+State+City against a partner population tagged only
     * at Region+Country yielded zero eligible partners with no UI signal. The hierarchy
     * is nested (State ⊂ Country ⊂ Region), so multi-level selections in the builder
     * read as inclusive targeting ("anyone in any of these places"), not as a stack of
     * independent constraints. Combined with BUG-061's ancestor walk in
     * {@link #buildLocationMap(List)}, this gives partners credit at every level
     * implied by their assignments — while still excluding partners with no matching
     * value at any level the admin selected.
     */
    private boolean matchesLocationRules(List<IncentiveAudienceRule> locationRules,
                                          Map<UUID, Set<UUID>> userLocationsByLevel) {
        if (locationRules.isEmpty()) return true; // No location restrictions

        // Group location rules by level
        Map<UUID, List<String>> rulesByLevel = new HashMap<>();
        for (IncentiveAudienceRule rule : locationRules) {
            if (rule.getLocationLevel() != null) {
                rulesByLevel.computeIfAbsent(rule.getLocationLevel().getId(), k -> new ArrayList<>())
                    .add(rule.getRuleValue());
            }
        }

        if (rulesByLevel.isEmpty()) return true; // Rules carry no usable level metadata

        // BUG-062: any populated level matching is sufficient (OR across levels).
        for (Map.Entry<UUID, List<String>> entry : rulesByLevel.entrySet()) {
            UUID levelId = entry.getKey();
            List<String> requiredValueIds = entry.getValue();
            Set<UUID> userValues = userLocationsByLevel != null
                ? userLocationsByLevel.getOrDefault(levelId, Set.of()) : Set.of();

            boolean anyMatch = requiredValueIds.stream()
                .anyMatch(rv -> {
                    try {
                        return userValues.contains(UUID.fromString(rv));
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                });

            if (anyMatch) return true;
        }
        return false;
    }

    /**
     * Resolves the location value ID for budget cap calculations.
     * Finds the partner company's assignment at the budget's designated location level —
     * either an exact-level assignment, or, failing that, the ancestor at that level
     * reachable from a deeper assignment via {@link LocationValue#getParent()}.
     *
     * <p>BUG-061: a partner assigned only at {@code Country:USA} for a {@code Region}-level
     * budget should roll up to USA's region (AMERICAS). Exact-level matches still win when
     * present, so this is purely additive: the descent path only fires when no direct
     * assignment exists at the budget level.
     *
     * @return the location value ID at the budget's level, or null if not resolvable
     */
    public static UUID resolveLocationValueForBudget(List<PartnerCompanyLocation> assignments,
                                                      IncentiveBudget budget) {
        if (assignments == null || budget == null || budget.getBudgetLocationLevel() == null) {
            return null;
        }
        UUID budgetLevelId = budget.getBudgetLocationLevel().getId();

        // Prefer an exact-level assignment when one exists.
        for (PartnerCompanyLocation pcl : assignments) {
            LocationValue value = pcl.getLocationValue();
            if (value != null && value.getLevel() != null
                && budgetLevelId.equals(value.getLevel().getId())) {
                return value.getId();
            }
        }

        // Fall back to an ancestor at the budget level reachable from any assignment.
        for (PartnerCompanyLocation pcl : assignments) {
            UUID ancestorId = ancestorIdAtLevel(pcl.getLocationValue(), budgetLevelId);
            if (ancestorId != null) return ancestorId;
        }
        return null;
    }

    private static UUID ancestorIdAtLevel(LocationValue start, UUID targetLevelId) {
        LocationValue current = start != null ? start.getParent() : null;
        int hops = 0;
        while (current != null && hops < MAX_HIERARCHY_DEPTH) {
            if (current.getLevel() != null && targetLevelId.equals(current.getLevel().getId())) {
                return current.getId();
            }
            current = current.getParent();
            hops++;
        }
        if (hops == MAX_HIERARCHY_DEPTH && current != null) {
            log.warn("BUG-061 ancestor walk hit MAX_HIERARCHY_DEPTH from value {}; "
                + "possible cycle in location_values.parent_id chain.",
                start != null ? start.getId() : null);
        }
        return null;
    }

    // matchesRule: if no rules exist, passes. If rules exist, userValue must match one (case-insensitive).
    private boolean matchesRule(List<String> ruleValues, String userValue) {
        if (ruleValues == null || ruleValues.isEmpty()) return true; // No restriction
        if (userValue == null) return false; // Has restriction but user has no value
        return ruleValues.stream().anyMatch(r -> r.equalsIgnoreCase(userValue));
    }

    // matchesSpecificPartners: if specificPartners text is null/empty, passes. Otherwise, externalPartnerId must be in
    // the list.
    private boolean matchesSpecificPartners(String specificPartners, String externalPartnerId) {
        if (specificPartners == null || specificPartners.isBlank()) return true;
        List<String> allowed = Arrays.stream(specificPartners.split("[,\n]"))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.isEmpty()) return true;
        if (externalPartnerId == null) return false;
        return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(externalPartnerId));
    }

    // matchesDynamicFields: compares incentive's customFieldValues JSON against partner metadata
    private boolean matchesDynamicFields(String customFieldValuesJson, Map<String, String> partnerMetadata) {
        if (customFieldValuesJson == null || customFieldValuesJson.isBlank()) return true;
        if (partnerMetadata == null || partnerMetadata.isEmpty()) return true;

        try {
            // Parse: {"country": ["USA", "Canada"], "tier": ["Gold"]}
            // OR: {"country": "USA", "tier": "Gold"} (single values)
            Map<String, Object> customValues = objectMapper.readValue(customFieldValuesJson,
                new TypeReference<Map<String, Object>>() {});

            for (Map.Entry<String, Object> entry : customValues.entrySet()) {
                String fieldKey = entry.getKey();
                Object expected = entry.getValue();
                if (expected == null) continue;

                String actual = partnerMetadata.get(fieldKey);
                if (actual == null) return false;

                if (expected instanceof List<?> list) {
                    boolean match = list.stream()
                        .filter(v -> v instanceof String)
                        .anyMatch(v -> ((String) v).equalsIgnoreCase(actual));
                    if (!match) return false;
                } else if (expected instanceof String str) {
                    if (!str.equalsIgnoreCase(actual)) return false;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse customFieldValues: {}", e.getMessage());
        }
        return true;
    }
}
