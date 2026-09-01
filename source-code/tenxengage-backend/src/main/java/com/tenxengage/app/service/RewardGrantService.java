package com.tenxengage.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.entity.BudgetUtilization;
import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveBudget;
import com.tenxengage.app.entity.LocationBudgetAllocation;
import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.enums.BudgetMode;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.repository.BudgetUtilizationRepository;
import com.tenxengage.app.repository.RewardTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared reward granting service used by all completion jobs and ClaimService.
 * Extracts the common reward granting logic (budget cap, per-user cap, per-partner cap,
 * transaction creation, balance credit, and notifications) into a single reusable service.
 */
@Service
public class RewardGrantService {

    private static final Logger log = LoggerFactory.getLogger(RewardGrantService.class);

    private final RewardTransactionRepository rewardTransactionRepository;
    private final WalletService walletService;
    private final BudgetUtilizationRepository budgetUtilizationRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final ObjectMapper objectMapper;

    public RewardGrantService(RewardTransactionRepository rewardTransactionRepository,
                              WalletService walletService,
                              BudgetUtilizationRepository budgetUtilizationRepository,
                              NotificationEventProducer notificationEventProducer,
                              ObjectMapper objectMapper) {
        this.rewardTransactionRepository = rewardTransactionRepository;
        this.walletService = walletService;
        this.budgetUtilizationRepository = budgetUtilizationRepository;
        this.notificationEventProducer = notificationEventProducer;
        this.objectMapper = objectMapper;
    }

    // -- Inner records -------------------------------------------------------------------------------

    public record RewardGrantRequest(
        UUID clientId,
        UUID userId,
        UUID incentiveId,
        UUID claimActionId,       // null for non-sales completions
        UUID completionId,        // null for sales claims
        String currencyId,
        BigDecimal amount,
        UUID locationValueId,     // resolved from partner company at budget's location level
        UUID partnerCompanyId     // for per-partner cap
    ) {}

    public record RewardGrantResult(
        BigDecimal amountPotential,
        BigDecimal amountAwarded,
        boolean budgetCapped
    ) {}

    // -- Core method ---------------------------------------------------------------------------------

    /**
     * Grants a reward for a given incentive, applying budget caps, per-partner caps,
     * and per-user caps. Creates the RewardTransaction, credits the user balance,
     * updates budget utilization, and sends notifications.
     *
     * @param request   the reward grant request containing user, incentive, currency, and amount info
     * @param incentive the incentive entity (with budgets eagerly loaded)
     * @return the result containing potential amount, awarded amount, and whether a cap was applied
     */
    @Transactional
    public RewardGrantResult grantReward(RewardGrantRequest request, Incentive incentive) {
        if (request.incentiveId() == null || request.userId() == null) {
            throw new IllegalArgumentException("incentiveId and userId must not be null for grant lock");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reward amount must be positive, got: " + request.amount());
        }

        if (request.completionId() != null) {
            Optional<RewardTransaction> existing = rewardTransactionRepository
                .findByCompletionIdAndCurrencyId(request.completionId(), request.currencyId());
            if (existing.isPresent()) {
                RewardTransaction t = existing.get();
                log.debug("Idempotent replay: completionId={}, currency={}", request.completionId(), request.currencyId());
                return new RewardGrantResult(t.getAmountPotential(), t.getAmountAwarded(), t.isBudgetCapped());
            }
        }
        if (request.claimActionId() != null) {
            Optional<RewardTransaction> existing = rewardTransactionRepository
                .findByClaimActionIdAndCurrencyId(request.claimActionId(), request.currencyId());
            if (existing.isPresent()) {
                RewardTransaction t = existing.get();
                log.debug("Idempotent replay: claimActionId={}, currency={}", request.claimActionId(), request.currencyId());
                return new RewardGrantResult(t.getAmountPotential(), t.getAmountAwarded(), t.isBudgetCapped());
            }
        }

        rewardTransactionRepository.acquireGrantLock(
            request.incentiveId() + ":" + request.userId());

        BigDecimal potential = request.amount();
        BigDecimal awarded = potential;
        boolean capped = false;

        // 1. Apply budget cap
        List<IncentiveBudget> budgets = incentive.getBudgets().stream()
            .filter(b -> b.getCurrencyId().equals(request.currencyId()))
            .toList();

        if (!budgets.isEmpty()) {
            IncentiveBudget budget = budgets.get(0);

            if (budget.getBudgetMode() == BudgetMode.PER_LOCATION && request.locationValueId() != null) {
                awarded = applyBudgetCap(request.incentiveId(), request.currencyId(),
                    request.locationValueId(), budget, awarded);
            } else {
                awarded = applyBudgetCap(request.incentiveId(), request.currencyId(),
                    null, budget, awarded);
            }
            if (awarded.compareTo(potential) < 0) {
                capped = true;
            }
        }

        // 2. Apply per-currency partner cap (with fallback to legacy single-value cap)
        if (request.partnerCompanyId() != null) {
            Map<String, BigDecimal> partnerCaps = parseAmountMap(incentive.getMaxPerPartnerByCurrency());
            BigDecimal partnerCap = partnerCaps.get(request.currencyId());

            if (partnerCap == null && incentive.getMaxPerPartner() != null) {
                partnerCap = incentive.getMaxPerPartner();
            }

            if (partnerCap != null) {
                rewardTransactionRepository.acquireGrantLock(
                    request.incentiveId() + ":" + request.partnerCompanyId());
                boolean isCurrencySpecificCap = partnerCaps.get(request.currencyId()) != null;
                BigDecimal partnerTotal;
                if (isCurrencySpecificCap) {
                    partnerTotal = Optional.ofNullable(rewardTransactionRepository
                        .sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyIdAndCurrencyId(
                            request.clientId(), request.incentiveId(),
                            request.partnerCompanyId(), request.currencyId()))
                        .orElse(BigDecimal.ZERO);
                } else {
                    partnerTotal = Optional.ofNullable(rewardTransactionRepository
                        .sumAwardedByClientIdAndIncentiveIdAndPartnerCompanyId(
                            request.clientId(), request.incentiveId(), request.partnerCompanyId()))
                        .orElse(BigDecimal.ZERO);
                }
                BigDecimal partnerRemaining = partnerCap.subtract(partnerTotal);
                if (awarded.compareTo(partnerRemaining) > 0) {
                    awarded = partnerRemaining.max(BigDecimal.ZERO);
                    capped = true;
                }
                log.debug("Partner cap check: cap={}, used={}, remaining={}, awarded={}",
                    partnerCap, partnerTotal, partnerRemaining, awarded);
            }
        }

        // 3. Apply per-currency user cap (with fallback to legacy single-value cap)
        Map<String, BigDecimal> userCaps = parseAmountMap(incentive.getMaxPerUserByCurrency());
        BigDecimal userCap = userCaps.get(request.currencyId());

        if (userCap == null && incentive.getMaxPerUser() != null) {
            userCap = incentive.getMaxPerUser();
        }

        if (userCap != null) {
            BigDecimal userTotal = Optional.ofNullable(rewardTransactionRepository
                .sumAwardedByUserAndIncentiveAndCurrency(
                    request.clientId(), request.userId(),
                    request.incentiveId(), request.currencyId()))
                .orElse(BigDecimal.ZERO);
            BigDecimal userRemaining = userCap.subtract(userTotal);
            if (awarded.compareTo(userRemaining) > 0) {
                awarded = userRemaining.max(BigDecimal.ZERO);
                capped = true;
            }
            log.debug("User cap check: cap={}, used={}, remaining={}, awarded={}",
                userCap, userTotal, userRemaining, awarded);
        }

        // 4. Create RewardTransaction
        RewardTransaction transaction = RewardTransaction.builder()
            .clientId(request.clientId())
            .claimActionId(request.claimActionId())
            .completionId(request.completionId())
            .userId(request.userId())
            .incentiveId(request.incentiveId())
            .currencyId(request.currencyId())
            .amountPotential(potential)
            .amountAwarded(awarded)
            .budgetCapped(capped)
            .build();
        rewardTransactionRepository.save(transaction);

        // 5. Credit balance — only when awarded > 0; zero means fully capped (no ledger write needed)
        if (awarded.compareTo(BigDecimal.ZERO) > 0) {
            walletService.creditInCurrentTx(request.clientId(), request.userId(),
                request.currencyId(), awarded, "INCENTIVE", transaction.getId(), null);
        }

        // 6. Update budget utilization (already done inside applyBudgetCap for the budget cap step;
        //    this is a no-op note -- utilization was updated during step 1)

        // 7. Send notifications if awarded > 0
        if (awarded.compareTo(BigDecimal.ZERO) > 0) {
            try {
                notificationEventProducer.publish(new NotificationEvent(
                    "REWARD_EARNED", request.clientId(),
                    "Reward Earned: " + awarded + " " + request.currencyId(),
                    "You earned " + awarded + " " + request.currencyId()
                        + " from incentive '" + incentive.getName() + "'.",
                    "INCENTIVE", request.incentiveId(), null,
                    List.of(request.userId()), null));

                if (capped) {
                    notificationEventProducer.publish(new NotificationEvent(
                        "REWARD_BUDGET_CAPPED", request.clientId(),
                        "Reward Reduced by Budget Cap",
                        "Your reward for '" + incentive.getName() + "' was reduced from "
                            + potential + " to " + awarded + " due to budget constraints.",
                        "INCENTIVE", request.incentiveId(), null,
                        List.of(request.userId()), null));
                }
            } catch (Exception e) {
                log.warn("step=notification_failed incentive={} user={}: {}",
                    request.incentiveId(), request.userId(), e.getMessage());
            }
        }

        log.info("Granted reward: incentive={}, user={}, currency={}, potential={}, awarded={}, capped={}",
            request.incentiveId(), request.userId(), request.currencyId(),
            potential, awarded, capped);

        return new RewardGrantResult(potential, awarded, capped);
    }

    // -- Helper methods ------------------------------------------------------------------------------

    /**
     * Parses a JSON string containing currency-to-amount mappings.
     * Expected format: {"cash":"5000","points":"10000"}
     *
     * @param json the JSON string to parse (may be null or empty)
     * @return a map of currency ID to BigDecimal amount, or an empty map if input is null/empty/invalid
     */
    public Map<String, BigDecimal> parseAmountMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> raw = objectMapper.readValue(json,
                new TypeReference<Map<String, String>>() {});
            return raw.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> new BigDecimal(e.getValue())
                ));
        } catch (Exception e) {
            log.warn("Failed to parse amount map JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Applies the budget cap for a given incentive, currency, and optional location value.
     * Acquires a pessimistic write lock on the BudgetUtilization row, calculates remaining budget,
     * clamps the award amount, and updates utilization.
     */
    private BigDecimal applyBudgetCap(UUID incentiveId, String currencyId, UUID locationValueId,
                                       IncentiveBudget budget, BigDecimal amount) {
        budgetUtilizationRepository.ensureExists(incentiveId, currencyId, locationValueId);
        BudgetUtilization util;
        if (locationValueId != null) {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdForUpdate(incentiveId, currencyId, locationValueId)
                .orElseThrow(() -> new IllegalStateException(
                    "BudgetUtilization not found after ensureExists for incentive " + incentiveId));
        } else {
            util = budgetUtilizationRepository
                .findByIncentiveIdAndCurrencyIdAndLocationValueIdIsNullForUpdate(incentiveId, currencyId)
                .orElseThrow(() -> new IllegalStateException(
                    "BudgetUtilization not found after ensureExists for incentive " + incentiveId));
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

        log.debug("Budget cap applied: incentive={}, currency={}, locationValueId={}, "
            + "totalBudget={}, utilized={}, remaining={}, requested={}, awarded={}",
            incentiveId, currencyId, locationValueId, totalBudget,
            util.getUtilized(), remaining, amount, awarded);

        return awarded;
    }
}
