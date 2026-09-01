package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.DealQualifierRequest;
import com.tenxengage.app.dto.response.CriterionResult;
import com.tenxengage.app.dto.response.DealQualifierResponse;
import com.tenxengage.app.dto.response.PartnerContextResponse;
import com.tenxengage.app.dto.response.PayoutBreakdown;
import com.tenxengage.app.dto.response.QualifiedIncentiveResult;
import com.tenxengage.app.entity.EligibilityRule;
import com.tenxengage.app.entity.EligibilityRuleGroup;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PayoutBand;
import com.tenxengage.app.entity.PayoutConfig;
import com.tenxengage.app.entity.SalesRequirement;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.EligibilityRuleType;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.entity.enums.PayoutType;
import com.tenxengage.app.entity.enums.RuleOperator;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DealQualifierService {

    private static final Logger log = LoggerFactory.getLogger(DealQualifierService.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("UTC"));

    private final IncentiveRepository incentiveRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final LocationValueRepository locationValueRepository;
    private final UserRepository userRepository;
    private final ParticipantEligibilityChecker eligibilityChecker;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TenantValidator tenantValidator;

    public DealQualifierService(IncentiveRepository incentiveRepository,
                                PartnerCompanyRepository partnerCompanyRepository,
                                LocationValueRepository locationValueRepository,
                                UserRepository userRepository,
                                ParticipantEligibilityChecker eligibilityChecker,
                                ObjectMapper objectMapper,
                                JdbcTemplate jdbcTemplate,
                                TenantValidator tenantValidator) {
        this.incentiveRepository = incentiveRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.locationValueRepository = locationValueRepository;
        this.userRepository = userRepository;
        this.eligibilityChecker = eligibilityChecker;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public PartnerContextResponse getPartnerContext() {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany company = resolvePartnerCompany();
        // Derive region name from top-level location assignment for display
        String regionName = company.getLocationAssignments().stream()
            .filter(pcl -> pcl.getLocationValue().getLevel().getDepth() == 0)
            .map(pcl -> pcl.getLocationValue().getName())
            .findFirst().orElse(null);
        return new PartnerContextResponse(regionName,
            extractMetadataValue(company.getMetadata(), "Partner Type"),
            loadCustomerSegmentOptions(clientId));
    }

    @Transactional(readOnly = true)
    public DealQualifierResponse evaluateDeal(DealQualifierRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        PartnerCompany company = resolvePartnerCompany();

        // Build location map for eligibility checks
        Map<UUID, Set<UUID>> userLocationsByLevel =
            ParticipantEligibilityChecker.buildLocationMap(company.getLocationAssignments());
        String region = company.getLocationAssignments().stream()
            .filter(pcl -> pcl.getLocationValue().getLevel().getDepth() == 0)
            .map(pcl -> pcl.getLocationValue().getName())
            .findFirst().orElse(null);
        Map<String, String> partnerMetadata = parseMetadata(company.getMetadata());
        String partnerType = partnerMetadata.get("Partner Type");
        String externalPartnerId = company.getExternalPartnerId();

        // Resolve the current user's ClientRole for ROLE audience-rule matching.
        UUID userRoleId = null;
        String userRoleName = null;
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getClientRole() != null) {
            userRoleId = user.getClientRole().getId();
            userRoleName = user.getClientRole().getName();
        }

        // Load active SALES incentives with full rule graph
        List<Incentive> allActiveIncentives = incentiveRepository
                .searchByClientId(clientId, IncentiveType.SALES, IncentiveStatus.ACTIVE, null,
                        Pageable.unpaged())
                .getContent();

        // Exclude incentives whose participant eligibility (location hierarchy, role,
        // partner type, specific-partner allowlist, custom fields) does not match the
        // current partner user. Same matcher used by IncentiveService.getIncentivesForPartner
        // and ClaimService — keeps Deal Qualifier results consistent with the incentives
        // the partner can otherwise see and claim against.
        UUID finalRoleId = userRoleId;
        String finalRoleName = userRoleName;
        Map<UUID, Set<UUID>> finalLocations = userLocationsByLevel;
        Map<String, String> finalMetadata = partnerMetadata;
        String finalExternalPartnerId = externalPartnerId;
        String finalPartnerType = partnerType;
        List<Incentive> activeIncentives = allActiveIncentives.stream()
                .filter(inc -> eligibilityChecker.matchesUserEligibility(
                        inc, finalLocations, finalRoleId, finalRoleName,
                        finalPartnerType, finalExternalPartnerId, finalMetadata))
                .toList();

        log.debug("Deal qualifier filtered {} active incentives down to {} eligible "
                + "for user {} (partnerCompany {})",
                allActiveIncentives.size(), activeIncentives.size(), userId, company.getId());

        if (activeIncentives.isEmpty()) {
            return new DealQualifierResponse(List.of(), region, partnerType);
        }

        Set<String> dealSkus = new HashSet<>(request.productSkus());

        // Pre-load product names/categories for human-readable hints
        Map<String, String[]> productLookup = loadProductLookup(clientId);

        // BUG-063: pre-resolve LOCATION rule value UUIDs to names so unmet criteria
        // can show the eligible set (e.g. "Eligible: Region: AMERICAS") rather than
        // bare UUIDs. One bulk lookup per request beats per-incentive round-trips.
        Map<UUID, String> locationValueNames = loadLocationValueNames(activeIncentives);

        List<QualifiedIncentiveResult> results = new ArrayList<>();

        for (Incentive incentive : activeIncentives) {
            List<CriterionResult> met = new ArrayList<>();
            List<CriterionResult> unmet = new ArrayList<>();

            // 1. Date range check
            evaluateDateRange(request.closeDate(), incentive, met, unmet);

            // 2. Region check
            evaluateRegion(region, userLocationsByLevel, incentive, locationValueNames, met, unmet);

            // 3. Partner type check
            evaluatePartnerType(partnerType, incentive, met, unmet);

            // 4. Specific partners check
            evaluateSpecificPartners(externalPartnerId, incentive, met, unmet);

            // 5. Sales requirements
            evaluateSalesRequirements(request.dealValue(), request.customerSegment(),
                    dealSkus, incentive.getSalesRequirements(), met, unmet, productLookup);

            int totalCriteria = met.size() + unmet.size();
            int matchPercentage = totalCriteria > 0
                    ? Math.round((float) met.size() / totalCriteria * 100) : 0;

            // Show ALL incentives — the Deal Qualifier is a motivational tool that shows
            // what's available and what changes would unlock eligibility

            // Calculate estimated payout from the first matching requirement's payout config
            PayoutResult payoutResult = calculateEstimatedPayout(
                    request.dealValue(), dealSkus, incentive.getSalesRequirements());

            results.add(new QualifiedIncentiveResult(
                    incentive.getId(),
                    incentive.getName(),
                    incentive.getDescription(),
                    incentive.getRewardMessage(),
                    incentive.getStartDate(),
                    incentive.getEndDate(),
                    matchPercentage,
                    payoutResult.amount(),
                    payoutResult.currencyId(),
                    payoutResult.payoutType(),
                    met,
                    unmet,
                    payoutResult.breakdown()
            ));
        }

        // Sort by match % desc, then estimated reward desc
        results.sort(Comparator
                .comparingInt(QualifiedIncentiveResult::matchPercentage).reversed()
                .thenComparing(r -> r.estimatedReward() != null ? r.estimatedReward() : BigDecimal.ZERO,
                        Comparator.reverseOrder()));

        log.info("Deal qualifier evaluated {} incentives, {} matches for client {}",
                activeIncentives.size(), results.size(), clientId);

        return new DealQualifierResponse(results, region, partnerType);
    }

    // ---- Participant-level checks ----

    private void evaluateDateRange(Instant closeDate, Incentive incentive,
                                   List<CriterionResult> met, List<CriterionResult> unmet) {
        Instant start = incentive.getStartDate();
        Instant end = incentive.getEndDate();

        String period = (start != null && end != null)
                ? DATE_FMT.format(start) + " – " + DATE_FMT.format(end)
                : "unknown";

        if (start != null && end != null
                && !closeDate.isBefore(start) && !closeDate.isAfter(end)) {
            met.add(new CriterionResult("DATE_RANGE",
                    "Close date falls within incentive period (" + period + ")", null));
        } else if (start != null && closeDate.isBefore(start)) {
            unmet.add(new CriterionResult("DATE_RANGE",
                    "Close date is before the incentive start date",
                    "This incentive runs " + period + ". Move your close date into this window."));
        } else if (end != null && closeDate.isAfter(end)) {
            unmet.add(new CriterionResult("DATE_RANGE",
                    "Close date is after the incentive end date",
                    "This incentive runs " + period + ". Move your close date into this window."));
        } else {
            met.add(new CriterionResult("DATE_RANGE",
                    "Close date falls within incentive period", null));
        }
    }

    /**
     * Evaluates LOCATION audience rules against the partner's expanded location map.
     *
     * <p>BUG-063: the prior implementation reported {@code "Location 'X' is eligible"}
     * for any non-null partner region whenever the incentive carried any LOCATION rule
     * at all — the rule's value set was never consulted. The check now mirrors
     * {@link ParticipantEligibilityChecker#matchesUserEligibility} location semantics:
     * group rules by level, OR-match required value UUIDs against the partner's
     * expanded location map (which already includes ancestors via
     * {@link ParticipantEligibilityChecker#buildLocationMap} per BUG-061), and emit
     * a met or unmet criterion that names the actual eligible set.
     *
     * <p>Hierarchy descent is automatic: a partner tagged at {@code Country:USA}
     * satisfies a {@code Region:AMERICAS} rule because {@code buildLocationMap} has
     * already expanded the assignment up to AMERICAS. OR-across-levels matches the
     * BUG-062 semantics enforced for participant eligibility.
     */
    static void evaluateRegion(String regionDisplay,
                               Map<UUID, Set<UUID>> userLocationsByLevel,
                               Incentive incentive,
                               Map<UUID, String> locationValueNames,
                               List<CriterionResult> met,
                               List<CriterionResult> unmet) {
        List<IncentiveAudienceRule> locationRules = incentive.getAudienceRules().stream()
                .filter(r -> "LOCATION".equalsIgnoreCase(r.getRuleType()))
                .toList();

        if (locationRules.isEmpty()) {
            met.add(new CriterionResult("LOCATION", "Open to all locations", null));
            return;
        }

        // Group rules by level. Rules without level metadata predate the multi-level
        // builder and cannot be membership-tested — fall through as "open to all"
        // rather than silently rejecting (matches the rulesByLevel.isEmpty() short-
        // circuit in ParticipantEligibilityChecker.matchesLocationRules).
        Map<UUID, List<UUID>> requiredByLevel = new HashMap<>();
        Map<UUID, String> levelNameById = new HashMap<>();
        for (IncentiveAudienceRule rule : locationRules) {
            if (rule.getLocationLevel() == null) continue;
            UUID levelId = rule.getLocationLevel().getId();
            levelNameById.putIfAbsent(levelId, rule.getLocationLevel().getName());
            try {
                UUID valueId = UUID.fromString(rule.getRuleValue());
                requiredByLevel.computeIfAbsent(levelId, k -> new ArrayList<>()).add(valueId);
            } catch (IllegalArgumentException ignored) {
                // Non-UUID rule value — same defensive skip the audience matcher uses.
            }
        }

        if (requiredByLevel.isEmpty()) {
            met.add(new CriterionResult("LOCATION", "Open to all locations", null));
            return;
        }

        if (userLocationsByLevel == null || userLocationsByLevel.isEmpty()) {
            unmet.add(new CriterionResult("LOCATION",
                    "Partner location could not be determined",
                    "Ensure your partner company has location assignments configured."));
            return;
        }

        String matchedLevelName = null;
        UUID matchedValueId = null;
        for (Map.Entry<UUID, List<UUID>> entry : requiredByLevel.entrySet()) {
            UUID levelId = entry.getKey();
            Set<UUID> userValues = userLocationsByLevel.getOrDefault(levelId, Set.of());
            for (UUID required : entry.getValue()) {
                if (userValues.contains(required)) {
                    matchedLevelName = levelNameById.get(levelId);
                    matchedValueId = required;
                    break;
                }
            }
            if (matchedLevelName != null) break;
        }

        if (matchedLevelName != null) {
            String valueName = locationValueNames.getOrDefault(matchedValueId,
                    regionDisplay != null ? regionDisplay : matchedValueId.toString());
            met.add(new CriterionResult("LOCATION",
                    matchedLevelName + " '" + valueName + "' is in the eligible audience",
                    null));
            return;
        }

        String hint = requiredByLevel.entrySet().stream()
                .map(entry -> {
                    String levelName = levelNameById.get(entry.getKey());
                    String values = entry.getValue().stream()
                            .map(id -> locationValueNames.getOrDefault(id, id.toString()))
                            .collect(Collectors.joining(", "));
                    return levelName + ": " + values;
                })
                .collect(Collectors.joining("; "));

        String partnerLocation = regionDisplay != null ? "'" + regionDisplay + "'" : "your current location";
        unmet.add(new CriterionResult("LOCATION",
                "Your location " + partnerLocation + " is not in the eligible audience",
                hint.isEmpty() ? null : "Eligible: " + hint));
    }

    private void evaluatePartnerType(String partnerType, Incentive incentive,
                                     List<CriterionResult> met, List<CriterionResult> unmet) {
        List<String> typeRules = incentive.getAudienceRules().stream()
                .filter(r -> "PARTNER_TYPE".equalsIgnoreCase(r.getRuleType()))
                .map(IncentiveAudienceRule::getRuleValue)
                .toList();

        if (typeRules.isEmpty()) {
            met.add(new CriterionResult("PARTNER_TYPE", "Open to all partner types", null));
            return;
        }

        boolean matches = typeRules.stream()
                .anyMatch(t -> t.equalsIgnoreCase(partnerType));

        if (matches) {
            met.add(new CriterionResult("PARTNER_TYPE",
                    "Partner type '" + partnerType + "' is eligible", null));
        } else {
            unmet.add(new CriterionResult("PARTNER_TYPE",
                    "Partner type '" + partnerType + "' is not eligible",
                    "This incentive targets: " + String.join(", ", typeRules)));
        }
    }

    private void evaluateSpecificPartners(String externalPartnerId, Incentive incentive,
                                          List<CriterionResult> met,
                                          List<CriterionResult> unmet) {
        String specificPartners = incentive.getSpecificPartners();
        if (specificPartners == null || specificPartners.isBlank()) {
            return; // No restriction
        }

        List<String> partners = parseStringList(specificPartners);
        if (partners.isEmpty()) {
            return;
        }

        if (externalPartnerId != null && partners.contains(externalPartnerId)) {
            met.add(new CriterionResult("SPECIFIC_PARTNERS",
                    "Your organization is in the eligible partners list", null));
        } else {
            unmet.add(new CriterionResult("SPECIFIC_PARTNERS",
                    "Your organization is not in the specific partners list",
                    "This incentive is restricted to specific partner organizations"));
        }
    }

    // ---- Sales requirement evaluation ----

    private void evaluateSalesRequirements(BigDecimal dealValue, String customerSegment,
                                           Set<String> dealSkus,
                                           List<SalesRequirement> requirements,
                                           List<CriterionResult> met,
                                           List<CriterionResult> unmet,
                                           Map<String, String[]> productLookup) {
        if (requirements == null || requirements.isEmpty()) {
            return;
        }

        for (SalesRequirement req : requirements) {
            List<EligibilityRuleGroup> groups = req.getEligibilityGroups();
            if (groups == null || groups.isEmpty()) {
                continue;
            }

            boolean anyGroupMatches = false;
            // Collect details from rules for human-readable descriptions
            List<String> productHints = new ArrayList<>();
            List<String> amountHints = new ArrayList<>();
            List<String> customerHints = new ArrayList<>();

            for (EligibilityRuleGroup group : groups) {
                boolean groupMatches = true;
                for (EligibilityRule rule : group.getRules()) {
                    boolean ruleResult = evaluateRule(dealValue, customerSegment, dealSkus, rule);
                    if (!ruleResult) {
                        groupMatches = false;
                        collectHints(rule, dealValue, customerSegment, dealSkus,
                                productHints, amountHints, customerHints, productLookup);
                    }
                }
                if (groupMatches) {
                    anyGroupMatches = true;
                    break;
                }
            }

            if (anyGroupMatches) {
                addMetCriteriaFromRequirement(req, dealValue, dealSkus, met);
            } else {
                addUnmetCriteriaFromRequirement(req, productHints, amountHints,
                        customerHints, unmet);
            }
        }
    }

    private void addMetCriteriaFromRequirement(SalesRequirement req, BigDecimal dealValue,
                                               Set<String> dealSkus,
                                               List<CriterionResult> met) {
        // Check which rule types were evaluated and add descriptive met criteria
        Set<EligibilityRuleType> ruleTypes = collectRuleTypes(req);

        if (ruleTypes.contains(EligibilityRuleType.PRODUCTS)) {
            met.add(new CriterionResult("PRODUCTS",
                    "Deal products match '" + req.getName() + "' requirements", null));
        }
        if (ruleTypes.contains(EligibilityRuleType.BOOKING_AMOUNT)) {
            met.add(new CriterionResult("BOOKING_AMOUNT",
                    "Deal value $" + dealValue.toPlainString()
                            + " meets '" + req.getName() + "' threshold", null));
        }
        if (ruleTypes.contains(EligibilityRuleType.CUSTOMER_TYPE)) {
            met.add(new CriterionResult("CUSTOMER_TYPE",
                    "Customer segment meets '" + req.getName() + "' requirements", null));
        }
    }

    private void addUnmetCriteriaFromRequirement(SalesRequirement req,
                                                  List<String> productHints,
                                                  List<String> amountHints,
                                                  List<String> customerHints,
                                                  List<CriterionResult> unmet) {
        if (!productHints.isEmpty()) {
            unmet.add(new CriterionResult("PRODUCTS",
                    "Products don't match '" + req.getName() + "' requirements",
                    String.join(", ", productHints)));
        }
        if (!amountHints.isEmpty()) {
            unmet.add(new CriterionResult("BOOKING_AMOUNT",
                    "Deal value doesn't meet '" + req.getName() + "' threshold",
                    String.join("; ", amountHints)));
        }
        if (!customerHints.isEmpty()) {
            unmet.add(new CriterionResult("CUSTOMER_TYPE",
                    "Customer segment doesn't match '" + req.getName() + "' requirements",
                    String.join("; ", customerHints)));
        }
        // If no specific hints, add a generic one
        if (productHints.isEmpty() && amountHints.isEmpty() && customerHints.isEmpty()) {
            unmet.add(new CriterionResult("REQUIREMENT",
                    "Requirement '" + req.getName() + "' not satisfied",
                    "Review the full eligibility criteria for this incentive"));
        }
    }

    private Set<EligibilityRuleType> collectRuleTypes(SalesRequirement req) {
        Set<EligibilityRuleType> types = new HashSet<>();
        for (EligibilityRuleGroup group : req.getEligibilityGroups()) {
            for (EligibilityRule rule : group.getRules()) {
                types.add(rule.getRuleType());
            }
        }
        return types;
    }

    private void collectHints(EligibilityRule rule, BigDecimal dealValue, String customerSegment,
                              Set<String> dealSkus,
                              List<String> productHints, List<String> amountHints,
                              List<String> customerHints,
                              Map<String, String[]> productLookup) {
        switch (rule.getRuleType()) {
            case PRODUCTS -> {
                List<String> required = parseStringList(rule.getSelectedProducts());
                // Resolve SKUs to human-readable names with categories
                List<String> namedProducts = required.stream()
                        .limit(4)
                        .map(sku -> {
                            String[] info = productLookup.get(sku);
                            return info != null ? info[0] + " (" + info[1] + ")" : sku;
                        })
                        .toList();
                // Collect unique categories from required products
                Set<String> requiredCategories = required.stream()
                        .map(sku -> {
                            String[] info = productLookup.get(sku);
                            return info != null ? info[1] : null;
                        })
                        .filter(c -> c != null)
                        .collect(Collectors.toSet());
                // Check if any deal products are in the same category
                Set<String> dealCategories = dealSkus.stream()
                        .map(sku -> {
                            String[] info = productLookup.get(sku);
                            return info != null ? info[1] : null;
                        })
                        .filter(c -> c != null)
                        .collect(Collectors.toSet());
                boolean hasCategoryOverlap = requiredCategories.stream()
                        .anyMatch(dealCategories::contains);

                String hint = "Required: " + String.join(", ", namedProducts);
                if (hasCategoryOverlap) {
                    hint += ". Your deal has products in the same category but not the specific required SKUs";
                } else if (!requiredCategories.isEmpty()) {
                    hint += ". Consider adding a " + String.join(" or ", requiredCategories) + " product";
                }
                productHints.add(hint);
            }
            case BOOKING_AMOUNT -> {
                if (rule.getValue() != null) {
                    try {
                        BigDecimal threshold = new BigDecimal(rule.getValue().trim());
                        if (dealValue.compareTo(threshold) < 0) {
                            BigDecimal gap = threshold.subtract(dealValue);
                            amountHints.add("Minimum deal value is $"
                                    + threshold.toPlainString()
                                    + ". Your deal is $" + dealValue.toPlainString()
                                    + " — increase by $" + gap.toPlainString() + " to qualify");
                        } else {
                            amountHints.add("Deal must be greater than $"
                                    + threshold.toPlainString());
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip
                    }
                }
            }
            case CUSTOMER_TYPE -> {
                if (rule.getValue() != null) {
                    String required = rule.getValue().trim();
                    customerHints.add("Required: " + required
                            + ". Your deal is " + customerSegment);
                }
            }
        }
    }

    // ---- Rule evaluation (adapted from TaggingEngineService) ----

    private boolean evaluateRule(BigDecimal dealValue, String customerSegment,
                                 Set<String> dealSkus, EligibilityRule rule) {
        return switch (rule.getRuleType()) {
            case PRODUCTS -> evaluateProductsRule(dealSkus, rule);
            case BOOKING_AMOUNT -> evaluateBookingAmountRule(dealValue, rule);
            case CUSTOMER_TYPE -> evaluateCustomerTypeRule(customerSegment, rule);
        };
    }

    private boolean evaluateProductsRule(Set<String> dealSkus, EligibilityRule rule) {
        String selectedProducts = rule.getSelectedProducts();
        if (selectedProducts == null || selectedProducts.isBlank()) {
            return false;
        }
        List<String> requiredSkus = parseStringList(selectedProducts);
        if (requiredSkus.isEmpty()) {
            return false;
        }
        for (String sku : requiredSkus) {
            if (dealSkus.contains(sku)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateBookingAmountRule(BigDecimal dealValue, EligibilityRule rule) {
        if (rule.getOperator() == null || rule.getValue() == null || dealValue == null) {
            return false;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(rule.getValue().trim());
        } catch (NumberFormatException e) {
            return false;
        }
        BigDecimal valueMax = null;
        if (rule.getValueMax() != null) {
            try {
                valueMax = new BigDecimal(rule.getValueMax().trim());
            } catch (NumberFormatException ignored) {
                // Skip
            }
        }
        return evaluateNumericCondition(dealValue, rule.getOperator(), value, valueMax);
    }

    private boolean evaluateCustomerTypeRule(String customerSegment, EligibilityRule rule) {
        if (rule.getOperator() == null || rule.getValue() == null || customerSegment == null) {
            return false;
        }
        return switch (rule.getOperator()) {
            case EQUALS -> customerSegment.equals(rule.getValue().trim());
            case IN -> {
                List<String> values = parseStringList(rule.getValue());
                yield values.contains(customerSegment);
            }
            case NOT_IN -> {
                List<String> values = parseStringList(rule.getValue());
                yield !values.contains(customerSegment);
            }
            default -> false;
        };
    }

    private boolean evaluateNumericCondition(BigDecimal actual, RuleOperator op,
                                             BigDecimal value, BigDecimal valueMax) {
        return switch (op) {
            case GREATER_THAN -> actual.compareTo(value) > 0;
            case GREATER_THAN_OR_EQUAL -> actual.compareTo(value) >= 0;
            case LESS_THAN -> actual.compareTo(value) < 0;
            case EQUALS -> actual.compareTo(value) == 0;
            case BETWEEN -> {
                if (valueMax == null) yield false;
                yield actual.compareTo(value) >= 0 && actual.compareTo(valueMax) <= 0;
            }
            default -> false;
        };
    }

    // ---- Payout calculation ----

    private record PayoutResult(BigDecimal amount, String currencyId, String payoutType,
                                PayoutBreakdown breakdown) {}

    private PayoutResult calculateEstimatedPayout(BigDecimal dealValue, Set<String> dealSkus,
                                                  List<SalesRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return new PayoutResult(BigDecimal.ZERO, "cash", null, null);
        }

        BigDecimal totalPayout = BigDecimal.ZERO;
        String primaryCurrency = "cash";
        String primaryPayoutType = null;
        PayoutBreakdown primaryBreakdown = null;

        for (SalesRequirement req : requirements) {
            for (PayoutConfig config : req.getPayouts()) {
                BigDecimal baseAmount = dealValue;

                // For ELIGIBLE_PRODUCTS, use the deal value as approximation
                // (we don't have line-item breakdowns for hypothetical deals)

                BigDecimal payout = calculatePayoutFromBands(config, baseAmount);
                if (payout == null) {
                    continue;
                }

                if (config.getMaxPerDeal() != null
                        && payout.compareTo(config.getMaxPerDeal()) > 0) {
                    payout = config.getMaxPerDeal();
                }

                // Build breakdown for the first (primary) payout config
                if (primaryBreakdown == null) {
                    primaryBreakdown = buildPayoutBreakdown(config, baseAmount);
                    primaryCurrency = config.getCurrencyId();
                    primaryPayoutType = config.getPayoutType() != null
                            ? config.getPayoutType().name() : null;
                }

                totalPayout = totalPayout.add(payout);
            }
        }

        return new PayoutResult(totalPayout, primaryCurrency, primaryPayoutType,
                primaryBreakdown);
    }

    private BigDecimal calculatePayoutFromBands(PayoutConfig config, BigDecimal baseAmount) {
        if (config.getBands().isEmpty() || baseAmount == null) {
            return null;
        }

        PayoutBand matchingBand = null;
        for (PayoutBand band : config.getBands()) {
            boolean aboveMin = baseAmount.compareTo(band.getMinAmount()) >= 0;
            boolean belowMax = band.getMaxAmount() == null
                    || baseAmount.compareTo(band.getMaxAmount()) <= 0;
            if (aboveMin && belowMax) {
                matchingBand = band;
                break;
            }
        }

        if (matchingBand == null) {
            return null;
        }

        if (config.getPayoutType() == PayoutType.FLAT) {
            return matchingBand.getPayoutValue();
        }

        return baseAmount.multiply(matchingBand.getPayoutValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private PayoutBreakdown buildPayoutBreakdown(PayoutConfig config, BigDecimal dealValue) {
        List<PayoutBand> bands = config.getBands().stream()
                .sorted(Comparator.comparing(PayoutBand::getMinAmount))
                .toList();

        PayoutBand currentBand = null;
        PayoutBand nextBand = null;

        for (int i = 0; i < bands.size(); i++) {
            PayoutBand band = bands.get(i);
            boolean aboveMin = dealValue.compareTo(band.getMinAmount()) >= 0;
            boolean belowMax = band.getMaxAmount() == null
                    || dealValue.compareTo(band.getMaxAmount()) <= 0;
            if (aboveMin && belowMax) {
                currentBand = band;
                if (i + 1 < bands.size()) {
                    nextBand = bands.get(i + 1);
                }
                break;
            }
        }

        if (currentBand == null) {
            // Deal value is below the lowest band — show first band as next tier
            if (!bands.isEmpty()) {
                PayoutBand first = bands.get(0);
                BigDecimal gap = first.getMinAmount().subtract(dealValue);
                return new PayoutBreakdown(
                        null, null, null, null,
                        first.getMinAmount(), first.getPayoutValue(),
                        gap.max(BigDecimal.ZERO), config.getMaxPerDeal());
            }
            return null;
        }

        BigDecimal gapToNext = null;
        BigDecimal nextMin = null;
        BigDecimal nextPayoutValue = null;

        if (nextBand != null) {
            nextMin = nextBand.getMinAmount();
            nextPayoutValue = nextBand.getPayoutValue();
            gapToNext = nextBand.getMinAmount().subtract(dealValue);
            if (gapToNext.compareTo(BigDecimal.ZERO) < 0) {
                gapToNext = BigDecimal.ZERO;
            }
        }

        return new PayoutBreakdown(
                currentBand.getMinAmount(),
                currentBand.getMaxAmount(),
                currentBand.getPayoutValue(),
                config.getPayoutType() != null ? config.getPayoutType().name() : null,
                nextMin, nextPayoutValue, gapToNext,
                config.getMaxPerDeal());
    }

    // ---- Utility ----

    /**
     * Bulk-resolve every LOCATION rule value across the active incentives to its
     * {@link com.tenxengage.app.entity.LocationValue#getName() display name} so that
     * unmet LOCATION criteria can show the eligible set instead of bare UUIDs.
     */
    private Map<UUID, String> loadLocationValueNames(List<Incentive> incentives) {
        List<UUID> ruleValueIds = incentives.stream()
                .flatMap(i -> i.getAudienceRules().stream())
                .filter(r -> "LOCATION".equalsIgnoreCase(r.getRuleType()))
                .map(IncentiveAudienceRule::getRuleValue)
                .filter(v -> v != null && !v.isBlank())
                .map(v -> {
                    try {
                        return UUID.fromString(v);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(v -> v != null)
                .distinct()
                .toList();
        if (ruleValueIds.isEmpty()) {
            return Map.of();
        }
        return locationValueRepository.findByIdIn(ruleValueIds).stream()
                .collect(Collectors.toMap(
                        com.tenxengage.app.entity.LocationValue::getId,
                        com.tenxengage.app.entity.LocationValue::getName,
                        (a, b) -> a));
    }

    /**
     * Load the Customer Segment dropdown options for the Deal Qualifier from the
     * tenant's "Sales Data" DataObject's "Customer Segment" DataObjectField
     * sampleValues. The field name is fixed by the seeded contract — see
     * tenxengage-contracts/models/data-object.md and V3 seed migration. Returns
     * an empty list if the tenant has not configured the field (which keeps the
     * frontend dropdown empty rather than 500-ing).
     */
    private List<String> loadCustomerSegmentOptions(UUID clientId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT dof.sample_values FROM data_object_fields dof "
                        + "JOIN data_objects d ON dof.data_object_id = d.id "
                        + "WHERE d.client_id = ? AND d.name = 'Sales Data' "
                        + "AND dof.name = 'Customer Segment' LIMIT 1",
                String.class, clientId);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rows.get(0), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse Sales Data 'Customer Segment' sampleValues for client {}: {}",
                    clientId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Load all products for the client as a sku → [name, category] lookup map.
     */
    private Map<String, String[]> loadProductLookup(UUID clientId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT sku, name, category FROM products WHERE client_id = ?", clientId);
        return rows.stream().collect(Collectors.toMap(
                r -> (String) r.get("sku"),
                r -> new String[]{(String) r.get("name"), (String) r.get("category")},
                (a, b) -> a));
    }

    private PartnerCompany resolvePartnerCompany() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID partnerCompanyId = tenantValidator.getCurrentPartnerCompanyId();
        return partnerCompanyRepository.findByIdAndClientId(partnerCompanyId, clientId)
                .orElseThrow(() -> new IllegalStateException(
                        "Partner company not found for current user"));
    }

    private List<String> parseStringList(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String cleaned = input.trim();
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.substring(1);
            if (cleaned.endsWith("]")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return Arrays.stream(cleaned.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Map<String, String> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(metadataJson,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse partner metadata: {}", e.getMessage());
            return Map.of();
        }
    }

    private String extractMetadataValue(String metadataJson, String key) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson)) return null;
        try {
            var map = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object val = map.get(key);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
