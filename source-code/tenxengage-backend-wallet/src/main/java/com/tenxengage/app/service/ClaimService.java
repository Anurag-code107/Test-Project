package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.UnclaimRequest;
import com.tenxengage.app.dto.response.ClaimDetailResponse;
import com.tenxengage.app.dto.response.ClaimResponse;
import com.tenxengage.app.dto.response.ClaimSummaryResponse;
import com.tenxengage.app.dto.response.EligibleIncentiveResponse;
import com.tenxengage.app.dto.response.IneligibleIncentiveResponse;
import com.tenxengage.app.dto.response.RewardBreakdownResponse;
import com.tenxengage.app.entity.BudgetUtilization;
import com.tenxengage.app.entity.ClaimAction;
import com.tenxengage.app.entity.EligibilityPayout;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.LocationBudgetAllocation;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PoEligibilityMapping;
import com.tenxengage.app.entity.PurchaseOrder;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.BudgetMode;
import com.tenxengage.app.entity.enums.ClaimStatus;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.ClaimActionRepository;
import com.tenxengage.app.repository.CurrencyRepository;
import com.tenxengage.app.repository.IncentiveRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PoEligibilityMappingRepository;
import com.tenxengage.app.repository.PurchaseOrderRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimActionRepository claimActionRepository;
    private final PoEligibilityMappingRepository eligibilityMappingRepository;
    private final IncentiveRepository incentiveRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final RewardTransactionRepository rewardTransactionRepository;
    private final RewardBalanceService rewardBalanceService;
    private final BudgetUtilizationRepository budgetUtilizationRepository;
    private final CurrencyService currencyService;
    private final TenantValidator tenantValidator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationEventProducer notificationEventProducer;
    private final ParticipantEligibilityChecker eligibilityChecker;
    private final PartnerCompanyRepository partnerCompanyRepository;

    public ClaimService(ClaimActionRepository claimActionRepository,
                        PoEligibilityMappingRepository eligibilityMappingRepository,
                        IncentiveRepository incentiveRepository,
                        PurchaseOrderRepository purchaseOrderRepository,
                        UserRepository userRepository,
                        RewardTransactionRepository rewardTransactionRepository,
                        RewardBalanceService rewardBalanceService,
                        BudgetUtilizationRepository budgetUtilizationRepository,
                        CurrencyService currencyService,
                        TenantValidator tenantValidator,
                        JdbcTemplate jdbcTemplate,
                        ObjectMapper objectMapper,
                        NotificationEventProducer notificationEventProducer,
                        ParticipantEligibilityChecker eligibilityChecker,
                        PartnerCompanyRepository partnerCompanyRepository) {
        this.claimActionRepository = claimActionRepository;
        this.eligibilityMappingRepository = eligibilityMappingRepository;
        this.incentiveRepository = incentiveRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.userRepository = userRepository;
        this.rewardTransactionRepository = rewardTransactionRepository;
        this.rewardBalanceService = rewardBalanceService;
        this.budgetUtilizationRepository = budgetUtilizationRepository;
        this.currencyService = currencyService;
        this.tenantValidator = tenantValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationEventProducer = notificationEventProducer;
        this.eligibilityChecker = eligibilityChecker;
        this.partnerCompanyRepository = partnerCompanyRepository;
    }

    // -- List claimable POs (role-aware) ----------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getClaims(ClaimStatus statusFilter, String search,
                                          LocalDate startDate, LocalDate endDate,
                                          String region, UUID partnerCompanyId,
                                          UUID userId, Pageable pageable) {
        var userDetails = tenantValidator.getCurrentUserDetails();
        UUID clientId = userDetails.getClientId();
        UUID currentUserId = userDetails.getUserId();
        boolean isAdmin = userDetails.isTenxAdmin()
            || userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT_ADMIN"));

        UUID pcId = isAdmin ? partnerCompanyId : userDetails.getPartnerCompanyId();

        // BUG-021: for non-admin callers, filter the elig-aggregate columns by the
        // incentives this user is audience-eligible for. Reuses the same audience logic
        // buildClaimDetail applies, so list and detail endpoints agree on eligibleIncentiveCount,
        // totalMonetaryReward, primaryIncentiveName, and rewardBreakdown.
        boolean applyAudienceFilter = !isAdmin && userDetails.getPartnerCompanyId() != null;
        String audiencePassIdsLiteral = applyAudienceFilter
            ? buildAudiencePassIdsLiteral(clientId, userDetails)
            : null;
        String audienceFilterOuter = applyAudienceFilter
            ? "AND pem.incentive_id = ANY(?::uuid[]) " : "";
        String audienceFilterTotalPayout = applyAudienceFilter
            ? "AND pem2.incentive_id = ANY(?::uuid[]) " : "";
        String audienceFilterByCurrency = applyAudienceFilter
            ? "AND pem3.incentive_id = ANY(?::uuid[]) " : "";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT po.id AS po_id, po.order_number, po.order_date, po.total_amount, ");
        sql.append("po.metadata->>'Customer Name' AS customer_name, po.partner_company_id, po.created_at, po.updated_at, ");
        sql.append("pc.name AS partner_name, ");
        sql.append("region.name AS partner_region, ");
        sql.append("elig.eligible_count, elig.total_payout, elig.incentive_names, ");
        sql.append("elig.primary_incentive_name, elig.payout_by_currency, ");
        sql.append("ca_agg.claimer_count, ca_agg.claimer_data, ");
        if (isAdmin) {
            sql.append("CASE WHEN ca_agg.claimer_count > 0 THEN 'CLAIMED' ELSE 'UNCLAIMED' END AS status ");
        } else {
            sql.append("CASE WHEN ca_user.id IS NOT NULL THEN 'CLAIMED' ELSE 'UNCLAIMED' END AS status ");
        }
        sql.append("FROM purchase_orders po ");
        sql.append("JOIN partner_companies pc ON pc.id = po.partner_company_id ");
        sql.append("LEFT JOIN LATERAL ( ");
        sql.append("  SELECT lv.name ");
        sql.append("  FROM partner_company_locations pcl ");
        sql.append("  JOIN location_values lv ON lv.id = pcl.location_value_id ");
        sql.append("  JOIN location_levels ll ON ll.id = lv.level_id ");
        sql.append("  WHERE pcl.partner_company_id = pc.id AND ll.depth = 0 ");
        sql.append("  LIMIT 1 ");
        sql.append(") region ON true ");
        sql.append("LEFT JOIN LATERAL ( ");
        sql.append("  SELECT ");
        sql.append("    COUNT(DISTINCT pem.id) FILTER (WHERE pem.eligible = true) ");
        sql.append("      AS eligible_count, ");
        sql.append("    COALESCE(( ");
        sql.append("      SELECT SUM(ep2.payout_amount) FROM eligibility_payouts ep2 ");
        sql.append("      JOIN po_eligibility_mappings pem2 ON pem2.id = ep2.eligibility_mapping_id ");
        sql.append("      WHERE pem2.purchase_order_id = po.id AND pem2.client_id = ? ");
        sql.append("        AND pem2.eligible = true ");
        sql.append(audienceFilterTotalPayout);
        sql.append("    ), 0) AS total_payout, ");
        sql.append("    ARRAY_AGG(DISTINCT i.name) FILTER (WHERE pem.eligible = true) ");
        sql.append("      AS incentive_names, ");
        sql.append("    (ARRAY_AGG(DISTINCT i.name) FILTER (WHERE pem.eligible = true))[1] ");
        sql.append("      AS primary_incentive_name, ");
        sql.append("    COALESCE(( ");
        sql.append("      SELECT jsonb_object_agg(sub.currency_id, sub.total) ");
        sql.append("      FROM ( ");
        sql.append("        SELECT ep3.currency_id, SUM(ep3.payout_amount) AS total ");
        sql.append("        FROM eligibility_payouts ep3 ");
        sql.append("        JOIN po_eligibility_mappings pem3 ");
        sql.append("          ON pem3.id = ep3.eligibility_mapping_id ");
        sql.append("        WHERE pem3.purchase_order_id = po.id AND pem3.client_id = ? ");
        sql.append("          AND pem3.eligible = true ");
        sql.append(audienceFilterByCurrency);
        sql.append("        GROUP BY ep3.currency_id ");
        sql.append("      ) sub ");
        sql.append("    ), '{}') AS payout_by_currency ");
        sql.append("  FROM po_eligibility_mappings pem ");
        sql.append("  LEFT JOIN incentives i ON i.id = pem.incentive_id ");
        sql.append("  WHERE pem.purchase_order_id = po.id AND pem.client_id = ? ");
        sql.append(audienceFilterOuter);
        sql.append(") elig ON true ");
        sql.append("LEFT JOIN LATERAL ( ");
        sql.append("  SELECT COUNT(*) AS claimer_count, ");
        sql.append("    jsonb_agg(jsonb_build_object( ");
        sql.append("      'userId', ca2.user_id, ");
        sql.append("      'name', COALESCE(u.first_name || ' ' || u.last_name, 'Unknown'), ");
        sql.append("      'claimedAt', ca2.claimed_at ");
        sql.append("    )) AS claimer_data ");
        sql.append("  FROM claim_actions ca2 ");
        sql.append("  LEFT JOIN users u ON u.id = ca2.user_id ");
        sql.append("  WHERE ca2.purchase_order_id = po.id AND ca2.client_id = ? ");
        sql.append(") ca_agg ON true ");
        if (!isAdmin) {
            sql.append("LEFT JOIN claim_actions ca_user ON ca_user.purchase_order_id = po.id ");
            sql.append("  AND ca_user.client_id = ? AND ca_user.user_id = ? ");
        }
        sql.append("WHERE po.client_id = ? ");
        sql.append("AND EXISTS (SELECT 1 FROM po_eligibility_mappings pem_exists WHERE pem_exists.purchase_order_id = po.id AND pem_exists.client_id = po.client_id AND pem_exists.eligible = true) ");

        List<Object> params = new ArrayList<>();
        params.add(clientId);   // elig: total_payout subquery
        if (applyAudienceFilter) {
            params.add(audiencePassIdsLiteral); // elig: total_payout audience filter
        }
        params.add(clientId);   // elig: payout_by_currency subquery
        if (applyAudienceFilter) {
            params.add(audiencePassIdsLiteral); // elig: payout_by_currency audience filter
        }
        params.add(clientId);   // elig: outer WHERE
        if (applyAudienceFilter) {
            params.add(audiencePassIdsLiteral); // elig: outer audience filter
        }
        params.add(clientId);   // ca_agg subquery
        if (!isAdmin) {
            params.add(clientId);   // ca_user join
            params.add(currentUserId); // ca_user join
        }
        params.add(clientId);   // main WHERE

        if (pcId != null) {
            sql.append("AND po.partner_company_id = ? ");
            params.add(pcId);
        }
        if (statusFilter != null) {
            if (isAdmin) {
                if (statusFilter == ClaimStatus.CLAIMED) {
                    sql.append("AND ca_agg.claimer_count > 0 ");
                } else {
                    sql.append("AND ca_agg.claimer_count = 0 ");
                }
            } else {
                if (statusFilter == ClaimStatus.CLAIMED) {
                    sql.append("AND ca_user.id IS NOT NULL ");
                } else {
                    sql.append("AND ca_user.id IS NULL ");
                }
            }
        }
        if (startDate != null) {
            sql.append("AND po.order_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND po.order_date <= ? ");
            params.add(Date.valueOf(endDate));
        }
        if (region != null && !region.isBlank() && !"GLOBAL".equalsIgnoreCase(region)) {
            sql.append("AND pc.id IN (SELECT partner_company_id FROM partner_company_locations WHERE location_value_id = ?::uuid) ");
            params.add(region);
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (po.order_number ILIKE ? OR pc.name ILIKE ? OR po.metadata->>'Customer Name' ILIKE ?) ");
            String like = "%" + search + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (userId != null) {
            sql.append("AND EXISTS (SELECT 1 FROM claim_actions ca_filter ");
            sql.append("WHERE ca_filter.purchase_order_id = po.id AND ca_filter.user_id = ?) ");
            params.add(userId);
        }

        // Count query
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") sub";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Order and paginate
        sql.append("ORDER BY po.order_date DESC, po.created_at DESC ");
        sql.append("LIMIT ? OFFSET ? ");
        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());

        Set<String> monetaryCodes = currencyService.getMonetaryCodes(clientId);
        List<ClaimResponse> rows = jdbcTemplate.query(
            sql.toString(), (rs, rowNum) -> mapClaimResponse(rs, monetaryCodes), params.toArray());

        return new PageImpl<>(rows, pageable, total);
    }

    // -- Claim detail -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ClaimDetailResponse getClaimDetail(UUID purchaseOrderId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PurchaseOrder po = purchaseOrderRepository.findByIdAndClientId(purchaseOrderId, clientId)
            .orElseThrow(() -> new EntityNotFoundException("PO not found: " + purchaseOrderId));

        var userDetails = tenantValidator.getCurrentUserDetails();
        boolean isAdmin = userDetails.isTenxAdmin()
            || userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT_ADMIN"));
        if (!isAdmin) {
            tenantValidator.validatePartnerCompanyAccess(po.getPartnerCompanyId());
        }

        return buildClaimDetail(po, clientId);
    }

    // -- Claim a deal -----------------------------------------------------------------------------

    @Transactional
    public ClaimDetailResponse claimDeal(UUID purchaseOrderId) {
        var userDetails = tenantValidator.getCurrentUserDetails();
        UUID clientId = userDetails.getClientId();
        UUID currentUserId = userDetails.getUserId();

        PurchaseOrder po = purchaseOrderRepository.findByIdAndClientId(purchaseOrderId, clientId)
            .orElseThrow(() -> new EntityNotFoundException("PO not found"));
        tenantValidator.validatePartnerCompanyAccess(po.getPartnerCompanyId());

        // Lock to prevent concurrent claims on same PO
        claimActionRepository.findByClientIdAndPurchaseOrderIdForUpdate(clientId, purchaseOrderId);

        // Check if already claimed by this user
        if (claimActionRepository.existsByClientIdAndPurchaseOrderIdAndUserId(
                clientId, purchaseOrderId, currentUserId)) {
            throw new IllegalStateException("Already claimed by this user");
        }

        // Check max claimers from the first eligible incentive
        int maxClaimers = getMaxClaimersForPO(clientId, purchaseOrderId);
        long currentClaimers = claimActionRepository.countByClientIdAndPurchaseOrderId(
            clientId, purchaseOrderId);
        if (currentClaimers >= maxClaimers) {
            throw new IllegalStateException("Maximum claimers reached for this deal");
        }

        // Check max per partner
        checkMaxPerPartner(clientId, po.getPartnerCompanyId(), purchaseOrderId);

        // Create claim action
        User currentUser = userRepository.findById(currentUserId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        ClaimAction action = ClaimAction.builder()
            .clientId(clientId)
            .purchaseOrderId(purchaseOrderId)
            .userId(currentUserId)
            .claimedAt(Instant.now())
            .build();
        action = claimActionRepository.save(action);

        // Award rewards for each eligible mapping
        List<PoEligibilityMapping> eligibleMappings = eligibilityMappingRepository
            .findByPurchaseOrderIdAndEligible(purchaseOrderId, true);

        // ROLE audience rules are keyed on ClientRole.id (BUG-020). Capture both the id
        // (canonical match) and the display name (transitional fallback for any legacy or
        // externally-written row still holding a display-name string) and pass both down
        // to checkRoleEligibility.
        UUID userRoleId = currentUser.getClientRole() != null
            ? currentUser.getClientRole().getId() : null;
        String userRoleName = currentUser.getClientRole() != null
            ? currentUser.getClientRole().getName() : null;

        // Batch-load all incentives to avoid N+1 queries
        Set<UUID> incentiveIds = eligibleMappings.stream()
            .map(PoEligibilityMapping::getIncentiveId)
            .collect(Collectors.toSet());
        Map<UUID, Incentive> incentiveMap = incentiveRepository.findAllById(incentiveIds).stream()
            .collect(Collectors.toMap(Incentive::getId, Function.identity()));

        for (PoEligibilityMapping mapping : eligibleMappings) {
            Incentive incentive = incentiveMap.get(mapping.getIncentiveId());
            if (incentive == null) {
                continue;
            }

            // Check role eligibility at claim time
            boolean roleAllowed = checkRoleEligibility(incentive, userRoleId, userRoleName);

            List<EligibilityPayout> payouts = mapping.getPayouts();
            if (!roleAllowed) {
                // Create zero-amount audit records
                for (EligibilityPayout ep : payouts) {
                    createRewardTransaction(action, currentUserId, clientId, incentive,
                        ep.getCurrencyId(), BigDecimal.ZERO, BigDecimal.ZERO, false);
                }
                continue;
            }

            // Award per-currency payouts
            for (EligibilityPayout ep : payouts) {
                awardPayout(action, currentUserId, clientId, incentive, po, ep);
            }
        }

        log.info("User {} claimed PO {} (order: {})",
            currentUserId, purchaseOrderId, po.getOrderNumber());

        // Notify claiming user
        notificationEventProducer.publish(new NotificationEvent(
            "DEAL_CLAIMED", clientId,
            "Deal Claimed: " + po.getOrderNumber(),
            "You have successfully claimed deal " + po.getOrderNumber() + ".",
            "CLAIM", purchaseOrderId, currentUserId,
            List.of(currentUserId), null));

        // Notify PARTNER_ADMIN of team member claim
        if (po.getPartnerCompanyId() != null) {
            notificationEventProducer.publish(new NotificationEvent(
                "TEAM_DEAL_CLAIMED", clientId,
                "Team Deal Claimed: " + po.getOrderNumber(),
                currentUser.getFirstName() + " " + currentUser.getLastName()
                    + " claimed deal " + po.getOrderNumber() + ".",
                "CLAIM", purchaseOrderId, currentUserId, null, null));
        }

        return buildClaimDetail(po, clientId);
    }

    // -- Unclaim a deal (Admin only) --------------------------------------------------------------

    @Transactional
    public ClaimDetailResponse unclaimDeal(UUID purchaseOrderId, UnclaimRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        if (!tenantValidator.isTenxAdmin()) {
            var userDetails = tenantValidator.getCurrentUserDetails();
            boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT_ADMIN"));
            if (!isAdmin) {
                throw new AccessDeniedException("Only admins can unclaim");
            }
        }

        PurchaseOrder po = purchaseOrderRepository.findByIdAndClientId(purchaseOrderId, clientId)
            .orElseThrow(() -> new EntityNotFoundException("PO not found: " + purchaseOrderId));

        // Find all claim actions for this PO
        List<ClaimAction> actions = claimActionRepository
            .findByClientIdAndPurchaseOrderId(clientId, purchaseOrderId);

        if (actions.isEmpty()) {
            throw new IllegalStateException("No claims exist for this deal");
        }

        // Reverse all reward transactions for every claimer
        Set<String> monetaryCodes = currencyService.getMonetaryCodes(clientId);
        for (ClaimAction action : actions) {
            List<RewardTransaction> transactions = rewardTransactionRepository
                .findByClaimActionId(action.getId());

            for (RewardTransaction tx : transactions) {
                rewardBalanceService.debit(
                    tx.getClientId(), tx.getUserId(), tx.getCurrencyId(), tx.getAmountAwarded());

                // Reverse budget utilization
                if (monetaryCodes.contains(tx.getCurrencyId())) {
                    // Resolve the location value for this specific budget
                    IncentiveBudget txBudget = incentiveRepository.findById(tx.getIncentiveId())
                        .map(inc -> inc.getBudgets().stream()
                            .filter(b -> b.getCurrencyId().equals(tx.getCurrencyId()))
                            .findFirst().orElse(null))
                        .orElse(null);
                    UUID locValId = resolvePartnerLocationValueForBudget(po, txBudget);
                    reverseBudgetUtilization(tx.getIncentiveId(), tx.getCurrencyId(),
                        locValId, tx.getAmountAwarded());
                }
            }

            rewardTransactionRepository.deleteByClaimActionId(action.getId());
        }

        // Collect affected user IDs before deleting
        List<UUID> affectedUserIds = actions.stream()
            .map(ClaimAction::getUserId)
            .distinct()
            .toList();

        // Delete all claim actions
        claimActionRepository.deleteAll(actions);

        log.info("Admin unclaimed all claims for PO {} with comment: {}",
            purchaseOrderId, request.comment());

        // Notify affected users about claim reversal
        notificationEventProducer.publish(new NotificationEvent(
            "CLAIM_REVERSED", clientId,
            "Claim Reversed: " + po.getOrderNumber(),
            "Your claim on deal " + po.getOrderNumber() + " has been reversed."
                + (request.comment() != null ? " Reason: " + request.comment() : ""),
            "CLAIM", purchaseOrderId, tenantValidator.getCurrentUserId(),
            affectedUserIds, null));

        return buildClaimDetail(po, clientId);
    }

    // -- Claim summary ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ClaimSummaryResponse getClaimSummary(ClaimStatus status, LocalDate startDate,
                                                 LocalDate endDate, String region) {
        var userDetails = tenantValidator.getCurrentUserDetails();
        UUID clientId = userDetails.getClientId();
        UUID currentUserId = userDetails.getUserId();
        boolean isAdmin = userDetails.isTenxAdmin()
            || userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT_ADMIN"));
        boolean isPartnerAdmin = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_PARTNER_ADMIN"));

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  COALESCE(SUM(rt.amount_awarded), 0) AS total_earnings, ");
        sql.append("  rt.currency_id ");
        sql.append("FROM reward_transactions rt ");
        sql.append("JOIN claim_actions ca ON ca.id = rt.claim_action_id ");
        sql.append("JOIN purchase_orders po ON po.id = ca.purchase_order_id ");
        sql.append("WHERE rt.client_id = ? ");

        List<Object> params = new ArrayList<>();
        params.add(clientId);

        if (!isAdmin) {
            if (isPartnerAdmin) {
                sql.append("AND po.partner_company_id = ? ");
                params.add(userDetails.getPartnerCompanyId());
            } else {
                sql.append("AND rt.user_id = ? ");
                params.add(currentUserId);
            }
        }
        if (startDate != null) {
            sql.append("AND po.order_date >= ? ");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append("AND po.order_date <= ? ");
            params.add(Date.valueOf(endDate));
        }
        if (region != null && !region.isBlank() && !"GLOBAL".equalsIgnoreCase(region)) {
            sql.append("AND po.partner_company_id IN (");
            sql.append("  SELECT partner_company_id FROM partner_company_locations ");
            sql.append("  WHERE location_value_id = ?::uuid");
            sql.append(") ");
            params.add(region);
        }

        sql.append("GROUP BY rt.currency_id");

        List<Map<String, Object>> earningsRows = jdbcTemplate.queryForList(
            sql.toString(), params.toArray());

        BigDecimal totalEarnings = BigDecimal.ZERO;
        Map<String, String> currencyBreakdown = new LinkedHashMap<>();
        for (Map<String, Object> row : earningsRows) {
            String currencyId = (String) row.get("currency_id");
            BigDecimal amount = (BigDecimal) row.get("total_earnings");
            totalEarnings = totalEarnings.add(amount);
            currencyBreakdown.put(currencyId, amount.stripTrailingZeros().toPlainString());
        }

        // Count claimed/unclaimed POs
        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT ");
        countSql.append("  COUNT(DISTINCT po.id) FILTER (WHERE ca.id IS NOT NULL) AS claimed, ");
        countSql.append("  COUNT(DISTINCT po.id) FILTER (WHERE ca.id IS NULL) AS unclaimed ");
        countSql.append("FROM purchase_orders po ");
        countSql.append("JOIN po_eligibility_mappings pem_s ON pem_s.purchase_order_id = po.id AND pem_s.client_id = po.client_id AND pem_s.eligible = true ");
        countSql.append("LEFT JOIN claim_actions ca ON ca.purchase_order_id = po.id ");
        countSql.append("  AND ca.client_id = po.client_id ");

        if (!isAdmin) {
            if (isPartnerAdmin) {
                countSql.append("  AND ca.user_id IN (");
                countSql.append("    SELECT u.id FROM users u ");
                countSql.append("    WHERE u.partner_company_id = ?");
                countSql.append("  ) ");
            } else {
                countSql.append("  AND ca.user_id = ? ");
            }
        }

        countSql.append("WHERE po.client_id = ? ");
        List<Object> countParams = new ArrayList<>();

        if (!isAdmin) {
            if (isPartnerAdmin) {
                countParams.add(userDetails.getPartnerCompanyId());
            } else {
                countParams.add(currentUserId);
            }
        }
        countParams.add(clientId);

        if (!isAdmin) {
            countSql.append("AND po.partner_company_id = ? ");
            countParams.add(userDetails.getPartnerCompanyId());
        }

        if (startDate != null) {
            countSql.append("AND po.order_date >= ? ");
            countParams.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            countSql.append("AND po.order_date <= ? ");
            countParams.add(Date.valueOf(endDate));
        }
        if (region != null && !region.isBlank() && !"GLOBAL".equalsIgnoreCase(region)) {
            countSql.append("AND po.partner_company_id IN (");
            countSql.append("  SELECT partner_company_id FROM partner_company_locations ");
            countSql.append("  WHERE location_value_id = ?::uuid");
            countSql.append(") ");
            countParams.add(region);
        }

        Map<String, Object> countRow = jdbcTemplate.queryForMap(
            countSql.toString(), countParams.toArray());
        long claimed = ((Number) countRow.get("claimed")).longValue();
        long unclaimed = ((Number) countRow.get("unclaimed")).longValue();

        return new ClaimSummaryResponse(
            totalEarnings.stripTrailingZeros().toPlainString(),
            currencyBreakdown,
            claimed,
            unclaimed
        );
    }

    // == Private helpers ==========================================================================

    // BUG-021: compute the set of active SALES incentive IDs the current (non-admin, partner)
    // user passes audience for — same logic buildClaimDetail applies when filtering
    // eligibleIncentives/ineligibleIncentives. Result is bound into getClaims' SQL as a
    // PostgreSQL uuid[] literal so the list endpoint's aggregates match the detail endpoint's.
    private String buildAudiencePassIdsLiteral(UUID clientId,
            com.tenxengage.app.security.CustomUserDetails userDetails) {
        User currentUser = userRepository.findById(userDetails.getUserId()).orElse(null);
        PartnerCompany pc = partnerCompanyRepository
            .findByIdAndClientId(userDetails.getPartnerCompanyId(), clientId)
            .orElse(null);

        Map<UUID, Set<UUID>> userLocationsByLevel = pc != null
            ? ParticipantEligibilityChecker.buildLocationMap(pc.getLocationAssignments()) : Map.of();
        String externalPartnerId = pc != null ? pc.getExternalPartnerId() : null;
        UUID userRoleId = (currentUser != null && currentUser.getClientRole() != null)
            ? currentUser.getClientRole().getId() : null;
        String userRoleName = (currentUser != null && currentUser.getClientRole() != null)
            ? currentUser.getClientRole().getName() : null;
        Map<String, String> partnerMetadata = parsePartnerMetadata(
            pc != null ? pc.getMetadata() : null);
        String userPartnerType = partnerMetadata.get("Partner Type");

        Page<Incentive> activeSales = incentiveRepository.searchByClientId(
            clientId, IncentiveType.SALES, IncentiveStatus.ACTIVE, null, Pageable.unpaged());

        StringBuilder literal = new StringBuilder("{");
        boolean first = true;
        for (Incentive inc : activeSales.getContent()) {
            if (eligibilityChecker.matchesUserEligibility(
                    inc, userLocationsByLevel, userRoleId, userRoleName,
                    userPartnerType, externalPartnerId, partnerMetadata)) {
                if (!first) literal.append(",");
                literal.append(inc.getId().toString());
                first = false;
            }
        }
        literal.append("}");
        return literal.toString();
    }

    private ClaimResponse mapClaimResponse(ResultSet rs, Set<String> monetaryCodes) throws SQLException {
        UUID poId = rs.getObject("po_id", UUID.class);
        String orderNumber = rs.getString("order_number");
        Date orderDateSql = rs.getDate("order_date");
        LocalDate orderDate = orderDateSql != null ? orderDateSql.toLocalDate() : null;
        BigDecimal totalAmount = rs.getBigDecimal("total_amount");
        String partnerName = rs.getString("partner_name");
        UUID partnerCompanyId = rs.getObject("partner_company_id", UUID.class);
        String partnerRegion = rs.getString("partner_region");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : null;
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : null;

        int eligibleCount = rs.getInt("eligible_count");
        BigDecimal totalPayout = rs.getBigDecimal("total_payout");
        if (totalPayout == null) {
            totalPayout = BigDecimal.ZERO;
        }
        String primaryIncentiveName = rs.getString("primary_incentive_name");
        String statusStr = rs.getString("status");
        ClaimStatus status = ClaimStatus.valueOf(statusStr);

        // Parse incentive names from SQL array
        List<String> incentiveNames = new ArrayList<>();
        java.sql.Array namesArray = rs.getArray("incentive_names");
        if (namesArray != null) {
            String[] arr = (String[]) namesArray.getArray();
            if (arr != null) {
                for (String n : arr) {
                    if (n != null) {
                        incentiveNames.add(n);
                    }
                }
            }
        }

        // Parse payout_by_currency JSONB into reward breakdown
        String payoutJson = rs.getString("payout_by_currency");
        Map<String, BigDecimal> payoutByCurrency = new LinkedHashMap<>();
        if (payoutJson != null && !payoutJson.isEmpty() && !payoutJson.equals("{}")) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(
                    payoutJson, new TypeReference<Map<String, Object>>() {});
                parsed.forEach((k, v) -> payoutByCurrency.put(k,
                    new BigDecimal(v.toString())));
            } catch (Exception e) {
                log.warn("Failed to parse payout_by_currency JSON: {}", payoutJson, e);
            }
        }
        RewardBreakdownResponse breakdown = buildBreakdownFromMap(payoutByCurrency, monetaryCodes);

        BigDecimal monetaryTotal = breakdown.monetary().values().stream()
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Parse claimers from JSONB
        List<ClaimResponse.ClaimerInfo> claimers = new ArrayList<>();
        String claimerJson = rs.getString("claimer_data");
        if (claimerJson != null && !claimerJson.equals("null")) {
            try {
                List<Map<String, Object>> claimerList = objectMapper.readValue(
                    claimerJson, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> c : claimerList) {
                    UUID claimerUserId = UUID.fromString((String) c.get("userId"));
                    String claimerName = (String) c.get("name");
                    Instant claimedAt = c.get("claimedAt") != null
                        ? Instant.parse(c.get("claimedAt").toString())
                        : null;
                    claimers.add(new ClaimResponse.ClaimerInfo(
                        claimerUserId, claimerName, claimedAt));
                }
            } catch (Exception e) {
                log.warn("Failed to parse claimer_data JSON: {}", claimerJson, e);
            }
        }

        return new ClaimResponse(
            poId,
            orderNumber,
            orderDate,
            status,
            partnerName,
            partnerCompanyId,
            partnerRegion,
            totalAmount != null ? totalAmount.stripTrailingZeros() : BigDecimal.ZERO,
            monetaryTotal.stripTrailingZeros(),
            breakdown,
            claimers,
            eligibleCount,
            primaryIncentiveName,
            incentiveNames,
            createdAt,
            updatedAt
        );
    }

    private RewardBreakdownResponse buildBreakdownFromMap(Map<String, BigDecimal> byCurrency,
                                                             Set<String> monetaryCodes) {
        Map<String, String> monetary = new LinkedHashMap<>();
        Map<String, String> nonMonetary = new LinkedHashMap<>();
        byCurrency.forEach((currencyId, amount) -> {
            if (monetaryCodes.contains(currencyId)) {
                monetary.put(currencyId, amount.stripTrailingZeros().toPlainString());
            } else {
                nonMonetary.put(currencyId, amount.stripTrailingZeros().toPlainString());
            }
        });
        return new RewardBreakdownResponse(monetary, nonMonetary);
    }

    private ClaimDetailResponse buildClaimDetail(PurchaseOrder po, UUID clientId) {
        Set<String> monetaryCodes = currencyService.getMonetaryCodes(clientId);

        String partnerName = po.getPartnerCompany() != null
            ? po.getPartnerCompany().getName() : "Unknown Partner";

        // Load claim actions (claimers)
        List<ClaimAction> actions = claimActionRepository
            .findByClientIdAndPurchaseOrderId(clientId, po.getId());
        List<ClaimResponse.ClaimerInfo> claimers = actions.stream()
            .map(a -> new ClaimResponse.ClaimerInfo(
                a.getUserId(),
                a.getUser() != null
                    ? a.getUser().getFirstName() + " " + a.getUser().getLastName()
                    : resolveUserName(a.getUserId()),
                a.getClaimedAt()))
            .toList();

        // Determine status for current user
        UUID currentUserId = tenantValidator.getCurrentUserDetails().getUserId();
        boolean userHasClaimed = actions.stream()
            .anyMatch(a -> a.getUserId().equals(currentUserId));
        ClaimStatus status = userHasClaimed ? ClaimStatus.CLAIMED : ClaimStatus.UNCLAIMED;

        // Load eligibility mappings with payouts
        List<PoEligibilityMapping> mappings = eligibilityMappingRepository
            .findByPurchaseOrderIdWithPayouts(po.getId());

        // Load all reward transactions for this PO
        List<RewardTransaction> allTransactions = rewardTransactionRepository
            .findByClientIdAndPurchaseOrderId(clientId, po.getId());

        // Build eligible incentives
        List<EligibleIncentiveResponse> eligibleIncentives = new ArrayList<>();
        for (PoEligibilityMapping mapping : mappings) {
            if (!Boolean.TRUE.equals(mapping.getEligible())) {
                continue;
            }

            Incentive incentive = incentiveRepository.findById(mapping.getIncentiveId())
                .orElse(null);
            String incentiveName = incentive != null ? incentive.getName() : "Unknown";

            List<RewardTransaction> incentiveTxs = allTransactions.stream()
                .filter(t -> t.getIncentiveId().equals(mapping.getIncentiveId()))
                .toList();

            // Fallback for unclaimed: use eligibility payout amounts
            Map<String, BigDecimal> fallback = null;
            if (incentiveTxs.isEmpty()) {
                fallback = new LinkedHashMap<>();
                for (EligibilityPayout ep : mapping.getPayouts()) {
                    fallback.merge(ep.getCurrencyId(), ep.getPayoutAmount(), BigDecimal::add);
                }
            }

            RewardBreakdownResponse breakdown = RewardBreakdownResponse.from(
                incentiveTxs, fallback, monetaryCodes);
            BigDecimal computedTotal = breakdown.monetary().values().stream()
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            eligibleIncentives.add(new EligibleIncentiveResponse(
                mapping.getIncentiveId(), incentiveName, breakdown,
                computedTotal.stripTrailingZeros()));
        }

        // Build ineligible incentives
        List<IneligibleIncentiveResponse> ineligibleIncentives = new ArrayList<>();
        for (PoEligibilityMapping mapping : mappings) {
            if (Boolean.TRUE.equals(mapping.getEligible())) {
                continue;
            }

            Incentive incentive = incentiveRepository.findById(mapping.getIncentiveId())
                .orElse(null);
            String incentiveName = incentive != null ? incentive.getName() : "Unknown";
            String reason = mapping.getIneligibilityReason() != null
                ? mapping.getIneligibilityReason()
                : "Eligibility rules not met";

            ineligibleIncentives.add(new IneligibleIncentiveResponse(
                mapping.getIncentiveId(), incentiveName, reason));
        }

        // Also add active SALES incentives with no mapping at all
        Set<UUID> mappedIncentiveIds = mappings.stream()
            .map(PoEligibilityMapping::getIncentiveId)
            .collect(Collectors.toSet());
        Page<Incentive> activeSales = incentiveRepository.searchByClientId(
            clientId, IncentiveType.SALES, IncentiveStatus.ACTIVE, null,
            Pageable.unpaged());
        for (Incentive inc : activeSales.getContent()) {
            if (!mappedIncentiveIds.contains(inc.getId())) {
                ineligibleIncentives.add(new IneligibleIncentiveResponse(
                    inc.getId(), inc.getName(), "Not evaluated for this purchase order"));
            }
        }

        // Filter eligible/ineligible lists by participant eligibility for non-admin users
        var userDetails = tenantValidator.getCurrentUserDetails();
        boolean isAdmin = userDetails.isTenxAdmin()
            || userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT_ADMIN"))
            || userDetails.getPartnerCompanyId() == null;

        if (!isAdmin && userDetails.getPartnerCompanyId() != null) {
            User currentUser = userRepository.findById(userDetails.getUserId()).orElse(null);
            PartnerCompany pc = partnerCompanyRepository
                .findByIdAndClientId(userDetails.getPartnerCompanyId(), clientId)
                .orElse(null);

            Map<UUID, Set<UUID>> userLocationsByLevel = pc != null
                ? ParticipantEligibilityChecker.buildLocationMap(pc.getLocationAssignments()) : Map.of();
            String externalPartnerId = pc != null ? pc.getExternalPartnerId() : null;
            UUID userRoleIdForFilter = (currentUser != null && currentUser.getClientRole() != null)
                ? currentUser.getClientRole().getId() : null;
            String userRoleNameForFilter = (currentUser != null && currentUser.getClientRole() != null)
                ? currentUser.getClientRole().getName() : null;
            Map<String, String> partnerMetadata = parsePartnerMetadata(
                pc != null ? pc.getMetadata() : null);
            String userPartnerType = partnerMetadata.get("Partner Type");

            // Collect incentive IDs that pass eligibility
            Set<UUID> passIds = new HashSet<>();
            for (Incentive inc : activeSales.getContent()) {
                if (eligibilityChecker.matchesUserEligibility(
                        inc, userLocationsByLevel, userRoleIdForFilter, userRoleNameForFilter,
                        userPartnerType, externalPartnerId, partnerMetadata)) {
                    passIds.add(inc.getId());
                }
            }
            // Also check mapped incentives
            for (PoEligibilityMapping mapping : mappings) {
                Incentive inc = incentiveRepository.findById(mapping.getIncentiveId())
                    .orElse(null);
                if (inc != null && eligibilityChecker.matchesUserEligibility(
                        inc, userLocationsByLevel, userRoleIdForFilter, userRoleNameForFilter,
                        userPartnerType, externalPartnerId, partnerMetadata)) {
                    passIds.add(inc.getId());
                }
            }

            eligibleIncentives = eligibleIncentives.stream()
                .filter(e -> passIds.contains(e.incentiveId()))
                .collect(Collectors.toCollection(ArrayList::new));
            ineligibleIncentives = ineligibleIncentives.stream()
                .filter(e -> passIds.contains(e.incentiveId()))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        // Max claimers
        int maxClaimers = getMaxClaimersForPO(clientId, po.getId());

        // Overall reward breakdown (all eligible payouts as fallback when unclaimed)
        Map<String, BigDecimal> overallFallback = null;
        if (allTransactions.isEmpty()) {
            overallFallback = new LinkedHashMap<>();
            for (PoEligibilityMapping m : mappings) {
                if (Boolean.TRUE.equals(m.getEligible())) {
                    for (EligibilityPayout ep : m.getPayouts()) {
                        overallFallback.merge(
                            ep.getCurrencyId(), ep.getPayoutAmount(), BigDecimal::add);
                    }
                }
            }
        }
        RewardBreakdownResponse overallBreakdown = RewardBreakdownResponse.from(
            allTransactions, overallFallback, monetaryCodes);

        BigDecimal monetaryTotal = overallBreakdown.monetary().values().stream()
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Derive region name from partner's top-level location assignment
        String regionName = null;
        if (po.getPartnerCompany() != null && po.getPartnerCompany().getLocationAssignments() != null) {
            regionName = po.getPartnerCompany().getLocationAssignments().stream()
                .filter(pcl -> pcl.getLocationValue().getLevel().getDepth() == 0)
                .map(pcl -> pcl.getLocationValue().getName())
                .findFirst().orElse(null);
        }

        return new ClaimDetailResponse(
            po.getId(),
            po.getOrderNumber(),
            po.getOrderDate(),
            status,
            partnerName,
            po.getPartnerCompanyId(),
            regionName,
            extractMetadataValue(po.getMetadata(), "Customer Name"),
            po.getTotalAmount().stripTrailingZeros(),
            monetaryTotal.stripTrailingZeros(),
            overallBreakdown,
            claimers,
            maxClaimers,
            eligibleIncentives,
            ineligibleIncentives,
            null, // adminComment not stored on PO; could be added later
            po.getCreatedAt(),
            po.getUpdatedAt()
        );
    }

    private String extractMetadataValue(String metadataJson, String key) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson)) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(metadataJson,
                new TypeReference<Map<String, Object>>() {});
            Object val = map.get(key);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parsePartnerMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(metadataJson,
                new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new LinkedHashMap<>();
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

    // BUG-020: ROLE rules now store ClientRole.id as a UUID string in rule_value,
    // matching the LOCATION rule pattern. Primary path parses rule_value as UUID and
    // compares to userRoleId. Transitional fallback: if a rule_value can't be parsed
    // (e.g. an external writer still emitting display names), log a WARN and compare
    // case-insensitively against userRoleName. Remove the fallback after one clean
    // release cycle with zero WARN hits.
    private boolean checkRoleEligibility(Incentive incentive, UUID userRoleId, String userRoleName) {
        List<IncentiveAudienceRule> roleRules = incentive.getAudienceRules().stream()
            .filter(r -> "ROLE".equals(r.getRuleType()))
            .toList();
        if (roleRules.isEmpty()) {
            return true;
        }
        if (userRoleId == null && userRoleName == null) {
            return false;
        }
        for (IncentiveAudienceRule rule : roleRules) {
            String rv = rule.getRuleValue();
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

    private void awardPayout(ClaimAction action, UUID userId, UUID clientId,
                              Incentive incentive, PurchaseOrder po, EligibilityPayout ep) {
        BigDecimal potential = ep.getPayoutAmount();
        BigDecimal awarded = potential;
        boolean capped = false;

        // Find the budget for this incentive + currency
        List<IncentiveBudget> budgets = incentive.getBudgets().stream()
            .filter(b -> b.getCurrencyId().equals(ep.getCurrencyId()))
            .toList();

        if (!budgets.isEmpty()) {
            IncentiveBudget budget = budgets.get(0);
            UUID locationValueId = resolvePartnerLocationValueForBudget(po, budget);

            if (budget.getBudgetMode() == BudgetMode.PER_LOCATION && locationValueId != null) {
                awarded = applyBudgetCap(incentive.getId(), ep.getCurrencyId(),
                    locationValueId, budget, awarded);
            } else {
                awarded = applyBudgetCap(incentive.getId(), ep.getCurrencyId(),
                    null, budget, awarded);
            }
            if (awarded.compareTo(potential) < 0) {
                capped = true;
            }

            // Check budget utilization thresholds for notifications
            checkBudgetThresholds(clientId, incentive, budget, locationValueId);
        }

        // Check max per user
        if (incentive.getMaxPerUser() != null) {
            BigDecimal userTotal = getUserTotalForIncentive(
                clientId, userId, incentive.getId(), ep.getCurrencyId());
            BigDecimal userRemaining = incentive.getMaxPerUser().subtract(userTotal);
            if (awarded.compareTo(userRemaining) > 0) {
                awarded = userRemaining.max(BigDecimal.ZERO);
                capped = true;
            }
        }

        createRewardTransaction(action, userId, clientId, incentive,
            ep.getCurrencyId(), potential, awarded, capped);

        rewardBalanceService.credit(clientId, userId, ep.getCurrencyId(), awarded);

        // Notify user of reward earned
        if (awarded.compareTo(BigDecimal.ZERO) > 0) {
            notificationEventProducer.publish(new NotificationEvent(
                "REWARD_EARNED", clientId,
                "Reward Earned: " + awarded + " " + ep.getCurrencyId(),
                "You earned " + awarded + " " + ep.getCurrencyId()
                    + " from incentive '" + incentive.getName() + "'.",
                "INCENTIVE", incentive.getId(), null,
                List.of(userId), null));

            if (capped) {
                notificationEventProducer.publish(new NotificationEvent(
                    "REWARD_BUDGET_CAPPED", clientId,
                    "Reward Reduced by Budget Cap",
                    "Your reward for '" + incentive.getName() + "' was reduced from "
                        + potential + " to " + awarded + " due to budget constraints.",
                    "INCENTIVE", incentive.getId(), null,
                    List.of(userId), null));
            }
        }
    }

    private void checkBudgetThresholds(UUID clientId, Incentive incentive,
                                        IncentiveBudget budget, UUID locationValueId) {
        BigDecimal totalBudget = budget.getTotalBudget();
        if (locationValueId != null && budget.getLocationAllocations() != null) {
            totalBudget = budget.getLocationAllocations().stream()
                .filter(a -> a.getLocationValue().getId().equals(locationValueId))
                .findFirst()
                .map(LocationBudgetAllocation::getAmount)
                .orElse(totalBudget);
        }
        if (totalBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Get current utilization
        BigDecimal utilized;
        if (locationValueId != null) {
            utilized = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdForUpdate(incentive.getId(), budget.getCurrencyId(), locationValueId)
                .map(BudgetUtilization::getUtilized)
                .orElse(BigDecimal.ZERO);
        } else {
            utilized = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentive.getId(), budget.getCurrencyId())
                .map(BudgetUtilization::getUtilized)
                .orElse(BigDecimal.ZERO);
        }

        BigDecimal utilizationPct = utilized.multiply(BigDecimal.valueOf(100))
            .divide(totalBudget, 2, java.math.RoundingMode.HALF_UP);

        if (utilized.compareTo(totalBudget) >= 0) {
            notificationEventProducer.publish(new NotificationEvent(
                "BUDGET_EXHAUSTED", clientId,
                "Budget Exhausted: " + incentive.getName(),
                "The budget for incentive '" + incentive.getName() + "' has been fully exhausted.",
                "INCENTIVE", incentive.getId(), null, null,
                Map.of("incentiveId", incentive.getId().toString())));
        } else if (utilizationPct.compareTo(BigDecimal.valueOf(80)) >= 0) {
            notificationEventProducer.publish(new NotificationEvent(
                "BUDGET_THRESHOLD_WARNING", clientId,
                "Budget Running Low: " + incentive.getName(),
                "The budget for incentive '" + incentive.getName() + "' has reached "
                    + utilizationPct + "% utilization.",
                "INCENTIVE", incentive.getId(), null, null,
                Map.of("incentiveId", incentive.getId().toString())));
        }
    }

    private BigDecimal applyBudgetCap(UUID incentiveId, String currencyId, UUID locationValueId,
                                       IncentiveBudget budget, BigDecimal amount) {
        BudgetUtilization util;
        if (locationValueId != null) {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdForUpdate(incentiveId, currencyId, locationValueId)
                .orElseGet(() -> budgetUtilizationRepository.save(BudgetUtilization.builder()
                    .incentiveId(incentiveId).currencyId(currencyId).locationValueId(locationValueId)
                    .utilized(BigDecimal.ZERO).build()));
        } else {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentiveId, currencyId)
                .orElseGet(() -> budgetUtilizationRepository.save(BudgetUtilization.builder()
                    .incentiveId(incentiveId).currencyId(currencyId)
                    .utilized(BigDecimal.ZERO).build()));
        }

        BigDecimal totalBudget = budget.getTotalBudget();
        if (locationValueId != null && budget.getLocationAllocations() != null) {
            totalBudget = budget.getLocationAllocations().stream()
                .filter(a -> a.getLocationValue().getId().equals(locationValueId))
                .findFirst()
                .map(LocationBudgetAllocation::getAmount)
                .orElse(totalBudget);
        }

        BigDecimal remaining = totalBudget.subtract(util.getUtilized());
        BigDecimal awarded = amount.compareTo(remaining) > 0
            ? remaining.max(BigDecimal.ZERO) : amount;

        util.setUtilized(util.getUtilized().add(awarded));
        budgetUtilizationRepository.save(util);

        return awarded;
    }

    private void reverseBudgetUtilization(UUID incentiveId, String currencyId,
                                           UUID locationValueId, BigDecimal amount) {
        BudgetUtilization util;
        if (locationValueId != null) {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueId(incentiveId, currencyId, locationValueId)
                .orElse(null);
        } else {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNull(incentiveId, currencyId)
                .orElse(null);
        }
        if (util != null) {
            util.setUtilized(util.getUtilized().subtract(amount).max(BigDecimal.ZERO));
            budgetUtilizationRepository.save(util);
        }
    }

    private void createRewardTransaction(ClaimAction action, UUID userId, UUID clientId,
                                          Incentive incentive, String currencyId,
                                          BigDecimal potential, BigDecimal awarded,
                                          boolean capped) {
        RewardTransaction transaction = RewardTransaction.builder()
            .clientId(clientId)
            .claimActionId(action.getId())
            .userId(userId)
            .incentiveId(incentive.getId())
            .currencyId(currencyId)
            .amountPotential(potential)
            .amountAwarded(awarded)
            .budgetCapped(capped)
            .build();
        rewardTransactionRepository.save(transaction);
    }

    private BigDecimal getUserTotalForIncentive(UUID clientId, UUID userId,
                                                 UUID incentiveId, String currencyId) {
        return rewardTransactionRepository.sumAwardedByUserAndIncentiveAndCurrency(
            clientId, userId, incentiveId, currencyId);
    }

    private int getMaxClaimersForPO(UUID clientId, UUID purchaseOrderId) {
        List<PoEligibilityMapping> eligible = eligibilityMappingRepository
            .findByPurchaseOrderIdAndEligible(purchaseOrderId, true);
        if (!eligible.isEmpty()) {
            Incentive incentive = incentiveRepository
                .findById(eligible.get(0).getIncentiveId()).orElse(null);
            if (incentive != null && incentive.getMaxClaimersPerDeal() != null) {
                return incentive.getMaxClaimersPerDeal();
            }
        }
        return 1;
    }

    /**
     * Resolves the partner company's location value ID at the budget's designated location level.
     */
    private UUID resolvePartnerLocationValueForBudget(PurchaseOrder po, IncentiveBudget budget) {
        if (po.getPartnerCompany() == null) return null;
        return ParticipantEligibilityChecker.resolveLocationValueForBudget(
            po.getPartnerCompany().getLocationAssignments(), budget);
    }

    private String resolveUserName(UUID userId) {
        return userRepository.findById(userId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("Unknown");
    }

    private void checkMaxPerPartner(UUID clientId, UUID partnerCompanyId, UUID purchaseOrderId) {
        List<PoEligibilityMapping> eligible = eligibilityMappingRepository
            .findByPurchaseOrderIdAndEligible(purchaseOrderId, true);
        Set<UUID> incentiveIds = eligible.stream()
            .map(PoEligibilityMapping::getIncentiveId)
            .collect(Collectors.toSet());
        Map<UUID, Incentive> incentiveMap = incentiveRepository.findAllById(incentiveIds).stream()
            .collect(Collectors.toMap(Incentive::getId, Function.identity()));
        for (PoEligibilityMapping mapping : eligible) {
            Incentive incentive = incentiveMap.get(mapping.getIncentiveId());
            if (incentive == null || incentive.getMaxPerPartner() == null) {
                continue;
            }
            BigDecimal partnerTotal = rewardTransactionRepository
                .sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyId(
                    clientId, incentive.getId(), partnerCompanyId);
            if (partnerTotal.compareTo(incentive.getMaxPerPartner()) >= 0) {
                throw new IllegalStateException(
                    "Partner has reached the maximum reward limit for incentive: " + incentive.getName());
            }
        }
    }
}
