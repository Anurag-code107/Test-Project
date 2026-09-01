package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;
import com.tenxengage.app.batch.seed.SeedRecords.JourneyCompletionExclusions;
import com.tenxengage.app.batch.seed.SeedRecords.NonSalesIncentiveRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

/**
 * Guards the product invariant asserted in BUG-019: every Journey seeded by
 * {@link IncentiveSeeder#createJourneyIncentives} must only link stage incentives
 * whose region matches the Journey's own LOCATION audience rule. A drift here
 * reproduces the seed scenario behind BUG-019 (AMERICAS-parent Journey with
 * APJ/EMEAR stages), which surfaces in the UI as stage cards painting the parent's
 * data instead of the stage's.
 */
@ExtendWith(MockitoExtension.class)
class JourneyStageRegionIntegrityTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void createJourneyIncentives_neverLinksStagesFromADifferentRegion() {
        IncentiveSeeder seeder = spy(new IncentiveSeeder(jdbc));
        // BUG-020: bypass the client_roles DB lookup that insertRoleAudienceRules now
        // performs. The role UUIDs themselves don't matter to this test — it asserts
        // journey/stage region integrity, not audience rule content.
        doReturn(Map.of(
                SeedConstants.ROLE_COMPANY_ADMIN, UUID.randomUUID(),
                SeedConstants.ROLE_PARTNER_SELLER, UUID.randomUUID()
        )).when(seeder).loadRoleIdsByName(any());

        UUID clientId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID regionLevelId = UUID.randomUUID();
        Map<String, UUID> regionValueIds = new HashMap<>();
        for (String r : SeedConstants.REGIONS) {
            regionValueIds.put(r, UUID.randomUUID());
        }

        // Generate many quarters with cross-region candidate refs. The seeder has a
        // random 50% gate per quarter, so feeding ~12 quarters ensures multiple
        // Journeys are produced regardless of the seed.
        List<FiscalQuarter> quarters = new ArrayList<>();
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        LocalDate start = LocalDate.of(2023, 2, 1);
        for (int q = 0; q < 12; q++) {
            FiscalQuarter fq = new FiscalQuarter(
                    2024 + (q / 4), "Q" + ((q % 4) + 1),
                    start.plusMonths(q * 3L),
                    start.plusMonths(q * 3L + 3).minusDays(1));
            quarters.add(fq);
            for (String region : SeedConstants.REGIONS) {
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", fq, region));
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "ACTIVITY", fq, region));
            }
        }

        Random random = new Random(42L);
        seeder.createJourneyIncentives(clientId, adminUserId, quarters, refs,
                regionLevelId, regionValueIds,
                UUID.randomUUID(), Map.of(), Map.of(), random);

        // Collect all jdbc.update(...) invocations after the fact — avoids the
        // Mockito varargs-stubbing pitfalls that bit earlier iterations of this test.
        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();

        Map<UUID, UUID> journeyRegionLocationValueId = new HashMap<>();
        Map<UUID, List<UUID>> journeyStageLinkedIds = new HashMap<>();
        for (Invocation inv : invocations) {
            if (!"update".equals(inv.getMethod().getName())) continue;
            Object[] rawArgs = inv.getArguments();
            if (rawArgs.length < 2 || !(rawArgs[0] instanceof String)) continue;
            String sql = (String) rawArgs[0];
            // Mockito may deliver the varargs either as a single Object[] in rawArgs[1]
            // (newer versions) or flattened into rawArgs from index 1 onward. Normalise
            // so userArgs[0] is always the first INSERT positional value.
            Object[] userArgs;
            if (rawArgs.length == 2 && rawArgs[1] instanceof Object[]) {
                userArgs = (Object[]) rawArgs[1];
            } else {
                userArgs = Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
            }

            if (sql.startsWith("INSERT INTO journey_stages")) {
                // journey_stages columns: id(0), incentive_id(1), linked_incentive_id(2), ...
                UUID journeyId = (UUID) userArgs[1];
                UUID linkedId = (UUID) userArgs[2];
                journeyStageLinkedIds.computeIfAbsent(journeyId, k -> new ArrayList<>()).add(linkedId);
            } else if (sql.startsWith("INSERT INTO incentive_audience_rules")
                    && userArgs.length >= 4 && "LOCATION".equals(userArgs[2])) {
                // incentive_audience_rules LOCATION columns: id(0), incentive_id(1),
                // rule_type="LOCATION"(2), rule_value=locationValueId-as-string(3), ...
                UUID journeyId = (UUID) userArgs[1];
                UUID locationValueId = UUID.fromString((String) userArgs[3]);
                journeyRegionLocationValueId.put(journeyId, locationValueId);
            }
        }

        Map<UUID, String> refIdToRegion = new HashMap<>();
        for (NonSalesIncentiveRef ref : refs) {
            refIdToRegion.put(ref.id(), ref.region());
        }
        Map<UUID, String> locationValueIdToRegion = new HashMap<>();
        regionValueIds.forEach((region, id) -> locationValueIdToRegion.put(id, region));

        assertThat(journeyStageLinkedIds)
                .as("Seed must produce at least one Journey for this test to be meaningful "
                        + "(captured %s jdbc.update calls)", invocations.size())
                .isNotEmpty();

        journeyStageLinkedIds.forEach((journeyId, linkedIds) -> {
            UUID journeyLocationValueId = journeyRegionLocationValueId.get(journeyId);
            assertThat(journeyLocationValueId)
                    .as("Every seeded Journey must have a LOCATION audience rule (BUG-019)")
                    .isNotNull();
            String journeyRegion = locationValueIdToRegion.get(journeyLocationValueId);

            for (UUID linkedId : linkedIds) {
                String stageRegion = refIdToRegion.get(linkedId);
                assertThat(stageRegion)
                        .as("Journey %s is in region %s; stage %s must share the region, "
                                + "but is in %s — this would reproduce BUG-019 in the UI.",
                                journeyId, journeyRegion, linkedId, stageRegion)
                        .isEqualTo(journeyRegion);
            }
        });
    }

    /**
     * Verification support for BUG-019 in the running app: the current quarter must
     * produce TWO AMERICAS Journeys (not just one), and the second variant must
     * pre-complete its first stage for each seeded AMERICAS partner user.
     */
    @Test
    void createJourneyIncentives_currentQuarterAmericas_producesTwoJourneysWithFirstStageCompletionForPartners() {
        IncentiveSeeder seeder = spy(new IncentiveSeeder(jdbc));
        // BUG-020: bypass the client_roles DB lookup that insertRoleAudienceRules now
        // performs. The role UUIDs themselves don't matter to this test — it asserts
        // journey/stage region integrity, not audience rule content.
        doReturn(Map.of(
                SeedConstants.ROLE_COMPANY_ADMIN, UUID.randomUUID(),
                SeedConstants.ROLE_PARTNER_SELLER, UUID.randomUUID()
        )).when(seeder).loadRoleIdsByName(any());

        UUID clientId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID regionLevelId = UUID.randomUUID();
        Map<String, UUID> regionValueIds = new HashMap<>();
        for (String r : SeedConstants.REGIONS) {
            regionValueIds.put(r, UUID.randomUUID());
        }
        UUID americasLocationValueId = regionValueIds.get("AMERICAS");

        // Stub the AMERICAS-partner lookup so the seeder has a non-empty target set
        // for the completion insert.
        UUID sellerId = UUID.randomUUID();
        UUID partnerAdminId = UUID.randomUUID();
        doReturn(List.of(sellerId, partnerAdminId))
                .when(jdbc).queryForList(anyString(), eq(UUID.class),
                        any(), any(), any());

        // Minimal quarter set where the LAST quarter (the seeder's "current") has
        // AMERICAS eligible refs, so the two-variant branch fires.
        List<FiscalQuarter> quarters = new ArrayList<>();
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 2, 1);
        for (int q = 0; q < 3; q++) {
            FiscalQuarter fq = new FiscalQuarter(
                    2026, "Q" + (q + 1),
                    start.plusMonths(q * 3L),
                    start.plusMonths(q * 3L + 3).minusDays(1));
            quarters.add(fq);
            for (String region : SeedConstants.REGIONS) {
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", fq, region));
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "ACTIVITY", fq, region));
            }
        }
        FiscalQuarter current = quarters.get(quarters.size() - 1);
        // Match the production invariant: AMERICAS current quarter has ≥3 non-sales refs
        // so the two AMERICAS Journeys can pick disjoint first stages (see IncentiveSeeder
        // training/activity bumps).
        refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", current, "AMERICAS"));

        Random random = new Random(42L);
        seeder.createJourneyIncentives(clientId, adminUserId, quarters, refs,
                regionLevelId, regionValueIds,
                UUID.randomUUID(), Map.of(), Map.of(), random);

        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();

        // Capture: which Journey IDs are AMERICAS + belong to the current quarter,
        // what their first-stage (sort_order=0) linked incentive id is, and which
        // (incentive_id, user_id) pairs got a user_incentive_completions insert.
        Set<UUID> americasJourneyIdsForCurrentQuarter = new HashSet<>();
        Map<UUID, UUID> firstStageLinkedIdByJourney = new HashMap<>();
        Set<String> completionIncentiveUserPairs = new HashSet<>();

        for (Invocation inv : invocations) {
            if (!"update".equals(inv.getMethod().getName())) continue;
            Object[] rawArgs = inv.getArguments();
            if (rawArgs.length < 2 || !(rawArgs[0] instanceof String)) continue;
            String sql = (String) rawArgs[0];
            Object[] userArgs = (rawArgs.length == 2 && rawArgs[1] instanceof Object[])
                    ? (Object[]) rawArgs[1]
                    : Arrays.copyOfRange(rawArgs, 1, rawArgs.length);

            if (sql.startsWith("INSERT INTO incentives ")) {
                // incentives columns: id(0), name(1), description(2), incentive_type(3),
                // status(4), client_id(5), ...
                UUID id = (UUID) userArgs[0];
                String name = (String) userArgs[1];
                String type = (String) userArgs[3];
                if ("JOURNEY".equals(type)
                        && name.contains(current.displayName())
                        && name.contains("AMERICAS")) {
                    americasJourneyIdsForCurrentQuarter.add(id);
                }
            } else if (sql.startsWith("INSERT INTO journey_stages")) {
                UUID journeyId = (UUID) userArgs[1];
                UUID linkedId = (UUID) userArgs[2];
                int sortOrder = (int) userArgs[3];
                if (sortOrder == 0) {
                    firstStageLinkedIdByJourney.put(journeyId, linkedId);
                }
            } else if (sql.startsWith("INSERT INTO user_incentive_completions")) {
                // columns: id(0), client_id(1), incentive_id(2), user_id(3), ...
                UUID incentiveId = (UUID) userArgs[2];
                UUID userId = (UUID) userArgs[3];
                completionIncentiveUserPairs.add(incentiveId + ":" + userId);
            }
        }

        assertThat(americasJourneyIdsForCurrentQuarter)
                .as("Current quarter must seed TWO AMERICAS Journeys for BUG-019 verification")
                .hasSizeGreaterThanOrEqualTo(2);

        // Exactly one completion INSERT per seeded AMERICAS partner user — i.e.
        // variant 1 fired once and inserted one row per target user.
        assertThat(completionIncentiveUserPairs)
                .as("Variant 1 must insert exactly one completion row per AMERICAS partner user")
                .hasSize(2);

        // Both completions reference the SAME incentive id (variant 1's first stage).
        Set<UUID> completedIncentiveIds = completionIncentiveUserPairs.stream()
                .map(s -> UUID.fromString(s.split(":")[0]))
                .collect(Collectors.toSet());
        assertThat(completedIncentiveIds)
                .as("Both partner-user completions must reference the same first-stage incentive id")
                .hasSize(1);
        UUID completedIncentiveId = completedIncentiveIds.iterator().next();

        // The completed incentive id must be the first stage of at least one of the
        // current-quarter AMERICAS Journeys.
        Set<UUID> firstStagesOfCurrentAmericasJourneys = americasJourneyIdsForCurrentQuarter.stream()
                .map(firstStageLinkedIdByJourney::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        assertThat(firstStagesOfCurrentAmericasJourneys)
                .as("Completed incentive must be one of the current-quarter AMERICAS Journeys' first stages")
                .contains(completedIncentiveId);

        // Both target partner users must have received a completion row.
        assertThat(completionIncentiveUserPairs).contains(
                completedIncentiveId + ":" + sellerId,
                completedIncentiveId + ":" + partnerAdminId);
    }

    /**
     * Verifies the {@link JourneyCompletionExclusions} returned by
     * {@code createJourneyIncentives} skips exactly the stages needed to preserve the
     * two BUG-019 current-quarter AMERICAS Journey states:
     * <ul>
     *   <li>variant 0 (the "not started" Journey, the first AMERICAS current-quarter
     *       journey emitted) — ALL stage incentive ids are excluded for each seeded
     *       AMERICAS partner user, plus the Journey incentive itself;</li>
     *   <li>variant 1 (the "in progress" Journey, the second emitted) — every stage
     *       EXCEPT the first is excluded, plus the Journey incentive itself. The first
     *       stage's own completion is enforced separately via an explicit INSERT, so it
     *       doesn't need exclusion.</li>
     * </ul>
     * AMERICAS current-quarter now emits 3-5 journeys in total; journeys beyond the
     * first two are ordinary and must NOT appear in the exclusion set.
     */
    @Test
    void createJourneyIncentives_exclusionsCoverAmericasJourneyStagesForPartnerUsers() {
        IncentiveSeeder seeder = spy(new IncentiveSeeder(jdbc));
        // BUG-020: bypass the client_roles DB lookup that insertRoleAudienceRules now
        // performs. The role UUIDs themselves don't matter to this test — it asserts
        // journey/stage region integrity, not audience rule content.
        doReturn(Map.of(
                SeedConstants.ROLE_COMPANY_ADMIN, UUID.randomUUID(),
                SeedConstants.ROLE_PARTNER_SELLER, UUID.randomUUID()
        )).when(seeder).loadRoleIdsByName(any());

        UUID clientId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID regionLevelId = UUID.randomUUID();
        Map<String, UUID> regionValueIds = new HashMap<>();
        for (String r : SeedConstants.REGIONS) {
            regionValueIds.put(r, UUID.randomUUID());
        }

        UUID sellerId = UUID.randomUUID();
        UUID partnerAdminId = UUID.randomUUID();
        doReturn(List.of(sellerId, partnerAdminId))
                .when(jdbc).queryForList(anyString(), eq(UUID.class),
                        any(), any(), any());

        List<FiscalQuarter> quarters = new ArrayList<>();
        List<NonSalesIncentiveRef> refs = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 2, 1);
        for (int q = 0; q < 3; q++) {
            FiscalQuarter fq = new FiscalQuarter(
                    2026, "Q" + (q + 1),
                    start.plusMonths(q * 3L),
                    start.plusMonths(q * 3L + 3).minusDays(1));
            quarters.add(fq);
            for (String region : SeedConstants.REGIONS) {
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", fq, region));
                refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "ACTIVITY", fq, region));
            }
        }
        FiscalQuarter current = quarters.get(quarters.size() - 1);
        // Match the production invariant: AMERICAS current quarter has ≥3 non-sales refs
        // so the two AMERICAS Journeys can pick disjoint first stages (see IncentiveSeeder
        // training/activity bumps).
        refs.add(new NonSalesIncentiveRef(UUID.randomUUID(), "TRAINING", current, "AMERICAS"));

        Random random = new Random(42L);
        JourneyCompletionExclusions exclusions = seeder.createJourneyIncentives(
                clientId, adminUserId, quarters, refs,
                regionLevelId, regionValueIds,
                UUID.randomUUID(), Map.of(), Map.of(), random);

        // Reconstruct from the mock: which current-quarter AMERICAS Journey id maps to
        // which ordered stage list, and which of those is variant 1 (the one whose first
        // stage got a user_incentive_completions insert).
        Collection<Invocation> invocations = mockingDetails(jdbc).getInvocations();
        Set<UUID> americasCurrentQuarterJourneyIds = new HashSet<>();
        Map<UUID, Map<Integer, UUID>> stagesByJourneyBySortOrder = new HashMap<>();
        Set<UUID> completedIncentiveIds = new HashSet<>();

        for (Invocation inv : invocations) {
            if (!"update".equals(inv.getMethod().getName())) continue;
            Object[] rawArgs = inv.getArguments();
            if (rawArgs.length < 2 || !(rawArgs[0] instanceof String)) continue;
            String sql = (String) rawArgs[0];
            Object[] userArgs = (rawArgs.length == 2 && rawArgs[1] instanceof Object[])
                    ? (Object[]) rawArgs[1]
                    : Arrays.copyOfRange(rawArgs, 1, rawArgs.length);

            if (sql.startsWith("INSERT INTO incentives ")) {
                UUID id = (UUID) userArgs[0];
                String name = (String) userArgs[1];
                String type = (String) userArgs[3];
                if ("JOURNEY".equals(type)
                        && name.contains(current.displayName())
                        && name.contains("AMERICAS")) {
                    americasCurrentQuarterJourneyIds.add(id);
                }
            } else if (sql.startsWith("INSERT INTO journey_stages")) {
                UUID journeyId = (UUID) userArgs[1];
                UUID linkedId = (UUID) userArgs[2];
                int sortOrder = (int) userArgs[3];
                stagesByJourneyBySortOrder
                        .computeIfAbsent(journeyId, k -> new HashMap<>())
                        .put(sortOrder, linkedId);
            } else if (sql.startsWith("INSERT INTO user_incentive_completions")) {
                completedIncentiveIds.add((UUID) userArgs[2]);
            }
        }

        assertThat(americasCurrentQuarterJourneyIds)
                .as("Setup precondition: AMERICAS current quarter emits 3-5 journeys; "
                        + "the first two are BUG-019 variants 0 and 1")
                .hasSizeGreaterThanOrEqualTo(2);

        Map<UUID, Set<UUID>> excludedByUser = exclusions.excludedByUser();
        assertThat(excludedByUser.keySet())
                .as("Exclusions must key off both seeded AMERICAS partner users")
                .containsExactlyInAnyOrder(sellerId, partnerAdminId);

        // Variant 1 is the AMERICAS current-quarter journey whose id IS in the
        // exclusion set AND whose sort_order=0 stage was pre-completed (present in
        // completedIncentiveIds). The exclusion-membership check is essential: with
        // 3-5 journeys drawing from the same ~3-ref pool, an ordinary journey can
        // coincidentally share a first stage with variant 1; only variant 1's jid
        // lives in the exclusion set. Variant 0 is the other excluded journey.
        Set<UUID> anyExcluded = excludedByUser.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());

        UUID variantOneJourneyId = null;
        UUID variantOneFirstStageId = null;
        for (UUID jid : americasCurrentQuarterJourneyIds) {
            if (!anyExcluded.contains(jid)) continue;
            UUID firstStage = stagesByJourneyBySortOrder
                    .getOrDefault(jid, Map.of()).get(0);
            if (firstStage != null && completedIncentiveIds.contains(firstStage)) {
                variantOneJourneyId = jid;
                variantOneFirstStageId = firstStage;
                break;
            }
        }
        assertThat(variantOneJourneyId)
                .as("One of the AMERICAS Journeys must be variant 1 (jid in exclusion set "
                        + "AND first stage pre-completed)")
                .isNotNull();

        UUID variantZeroJourneyId = null;
        for (UUID jid : americasCurrentQuarterJourneyIds) {
            if (jid.equals(variantOneJourneyId)) continue;
            if (anyExcluded.contains(jid)) {
                variantZeroJourneyId = jid;
                break;
            }
        }
        assertThat(variantZeroJourneyId)
                .as("One of the AMERICAS Journeys must be variant 0 (Journey id present "
                        + "in the exclusion set, stages all excluded)")
                .isNotNull();

        Set<UUID> ordinaryJourneyIds = new HashSet<>(americasCurrentQuarterJourneyIds);
        ordinaryJourneyIds.remove(variantOneJourneyId);
        ordinaryJourneyIds.remove(variantZeroJourneyId);

        for (UUID userId : List.of(sellerId, partnerAdminId)) {
            Set<UUID> excluded = excludedByUser.get(userId);
            assertThat(excluded).as("Exclusion set for %s", userId).isNotNull();

            // Variant 1: Journey id + every stage except the first is excluded; first
            // stage is NOT excluded (its completion is handled explicitly, not randomly).
            assertThat(excluded)
                    .as("Variant 1 Journey id must be excluded for %s", userId)
                    .contains(variantOneJourneyId);
            assertThat(excluded)
                    .as("Variant 1's first stage must NOT be in exclusion set for %s "
                            + "(its completion is enforced by explicit INSERT, not by exclusion)", userId)
                    .doesNotContain(variantOneFirstStageId);
            Map<Integer, UUID> variantOneStages = stagesByJourneyBySortOrder.get(variantOneJourneyId);
            for (Map.Entry<Integer, UUID> e : variantOneStages.entrySet()) {
                if (e.getKey() == 0) continue;
                assertThat(excluded)
                        .as("Variant 1 non-first stage (sort_order=%s) must be excluded for %s",
                                e.getKey(), userId)
                        .contains(e.getValue());
            }

            // Variant 0: Journey id + EVERY stage is excluded.
            assertThat(excluded)
                    .as("Variant 0 Journey id must be excluded for %s", userId)
                    .contains(variantZeroJourneyId);
            Map<Integer, UUID> vZeroStages = stagesByJourneyBySortOrder.get(variantZeroJourneyId);
            for (UUID stageIncentiveId : vZeroStages.values()) {
                assertThat(excluded)
                        .as("Every variant 0 stage must be excluded for %s "
                                + "(the Journey must stay at zero completions)", userId)
                        .contains(stageIncentiveId);
            }

            // Ordinary AMERICAS current-quarter journeys (3rd+ emitted) must NOT be in
            // the exclusion set — they follow the normal completion flow.
            for (UUID ordinaryJid : ordinaryJourneyIds) {
                assertThat(excluded)
                        .as("Ordinary AMERICAS current-quarter Journey id must NOT be "
                                + "excluded for %s (only variants 0/1 are special)", userId)
                        .doesNotContain(ordinaryJid);
            }
        }
    }
}
