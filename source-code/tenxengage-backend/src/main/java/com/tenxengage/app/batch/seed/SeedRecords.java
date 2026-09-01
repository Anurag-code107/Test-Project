package com.tenxengage.app.batch.seed;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Record types used across the seed data generation pipeline.
 */
public final class SeedRecords {

    private SeedRecords() {}

    public record ProductRow(UUID id, String sku, String category) {}

    public record SellerRef(UUID userId, UUID partnerId) {}

    public record FiscalQuarter(int fyYear, String qLabel, LocalDate startDate, LocalDate endDate) {
        public String displayName() {
            return "FY" + fyYear + " " + qLabel;
        }

        public int calendarQuarter() {
            return switch (qLabel) {
                case "Q1" -> 1;
                case "Q2" -> 2;
                case "Q3" -> 3;
                case "Q4" -> 4;
                default -> 1;
            };
        }
    }

    public record IncentiveRef(
            UUID id, UUID requirementId, FiscalQuarter quarter,
            String targetRegion, List<String> eligibleSkus,
            BigDecimal minBookingAmount, List<String> eligibleSegments,
            List<PayoutConfigRef> payoutConfigs) {}

    public record IncentivePlan(
            UUID plannedId, FiscalQuarter quarter, String targetRegion,
            String focusCategory, List<String> selectedSkus, BigDecimal minBookingAmount,
            List<String> eligibleSegments, String name, String description, String status,
            double successScore, List<String> audienceRegions, List<String> audienceRoles,
            PayoutPlan cashPayout, PayoutPlan pointsPayout,
            BigDecimal cashBudget, BigDecimal pointsBudget,
            Map<String, BigDecimal> regionCashBudgets,
            Map<String, BigDecimal> regionPointsBudgets) {}

    public record PartnerSets(List<UUID> enrolledIds, List<UUID> nonEnrolledIds, List<UUID> allIds,
                              Map<UUID, Timestamp> creationDates) {}

    public record PayoutPlan(String payoutType, String against,
                             BigDecimal maxPerDeal, List<BandPlan> bands) {}

    public record BandPlan(BigDecimal minAmount, BigDecimal maxAmount, BigDecimal payoutValue) {}

    public record PayoutConfigRef(UUID configId, String currencyId, String payoutType,
                                  String against, BigDecimal maxPerDeal, List<BandPlan> bands) {}

    public record NonSalesIncentiveRef(UUID id, String type, FiscalQuarter quarter, String region) {}

    /** Pre-computed course completion for in-memory schedule (used before DB insert). */
    public record CourseCompletionRecord(UUID userId, UUID courseId, String productCategory,
                                         LocalDate completedAt, String source) {}

    public record UserCreationResult(List<SellerRef> sellers, Map<UUID, Timestamp> userCreationDates) {}

    /** Region + country assignment for a partner. */
    public record PartnerLocationRef(UUID regionValueId, String regionName,
                                     UUID countryValueId, String countryName) {}

    /**
     * Per-user (user_id → incentive_ids) skip-list for random completion seeding.
     * Populated by {@code IncentiveSeeder.createJourneyIncentives} for the current-
     * quarter AMERICAS Journeys so {@code CompletionSeeder} preserves BUG-019's
     * deterministic states: one Journey with zero completed stages, one with only
     * the first stage completed.
     */
    public record JourneyCompletionExclusions(Map<UUID, Set<UUID>> excludedByUser) {
        public static JourneyCompletionExclusions empty() {
            return new JourneyCompletionExclusions(Map.of());
        }

        public boolean isExcluded(UUID userId, UUID incentiveId) {
            Set<UUID> set = excludedByUser.get(userId);
            return set != null && set.contains(incentiveId);
        }
    }
}
