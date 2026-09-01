package com.tenxengage.app.service.forecast;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.IncentiveAudienceRule;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.repository.ForecastIncentiveOutcomeRepository;
import com.tenxengage.app.repository.ForecastRegionDistributionRepository;
import com.tenxengage.app.repository.ForecastSalesAggregateRepository;
import com.tenxengage.app.repository.ForecastTrainingCorrelationRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for BUG-024: the preview forecast pipeline writes audience
 * rules with ruleType = "REGION" (name-valued), but the assembler used to filter
 * exclusively on ruleType = "LOCATION" (UUID-valued). These tests pin the
 * widened extractor behaviour so that re-introducing either side of the
 * mismatch fails fast.
 */
@ExtendWith(MockitoExtension.class)
class ForecastDataAssemblerTest {

    @Mock private ForecastSalesAggregateRepository salesAggregateRepo;
    @Mock private ForecastIncentiveOutcomeRepository incentiveOutcomeRepo;
    @Mock private ForecastRegionDistributionRepository regionDistributionRepo;
    @Mock private ForecastTrainingCorrelationRepository trainingCorrelationRepo;
    @Mock private LocationValueRepository locationValueRepo;
    @Mock private ForecastAccuracyService accuracyService;
    @Mock private JdbcTemplate jdbcTemplate;

    private ForecastDataAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ForecastDataAssembler(
                salesAggregateRepo,
                incentiveOutcomeRepo,
                regionDistributionRepo,
                trainingCorrelationRepo,
                locationValueRepo,
                new SimilarityScorer(),
                accuracyService,
                jdbcTemplate
        );
    }

    @Test
    void extractLocationValueNames_returnsRegionNames_forRegionTypedRules() {
        Incentive incentive = incentiveWithRules(
                regionRule("AMERICAS"),
                regionRule("EMEAR"),
                regionRule("APJ")
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).containsExactly("AMERICAS", "EMEAR", "APJ");
    }

    @Test
    void extractLocationValueNames_returnsLocationValueIds_forLocationTypedRules() {
        String americasUuid = UUID.randomUUID().toString();
        String emearUuid = UUID.randomUUID().toString();
        Incentive incentive = incentiveWithRules(
                locationRule(americasUuid),
                locationRule(emearUuid)
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        // LOCATION path preserves legacy behaviour: returns UUID strings, resolved
        // to display names elsewhere via LocationValueRepository.
        assertThat(names).containsExactly(americasUuid, emearUuid);
    }

    @Test
    void extractLocationValueNames_prefersRegionOverLocation_whenBothArePresent() {
        // Defensive: if a caller accidentally writes both rule types, REGION
        // names win. This matches the preview path's expected shape and avoids
        // double-counting regions through the Claude context.
        Incentive incentive = incentiveWithRules(
                regionRule("AMERICAS"),
                locationRule(UUID.randomUUID().toString())
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).containsExactly("AMERICAS");
    }

    @Test
    void extractLocationValueNames_returnsEmpty_whenNoRegionOrLocationRulesPresent() {
        Incentive incentive = incentiveWithRules(
                partnerTypeRule("Reseller"),
                partnerTypeRule("Distributor")
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).isEmpty();
    }

    @Test
    void extractLocationValueNames_returnsEmpty_whenAudienceRulesIsNull() {
        Incentive incentive = new Incentive();
        incentive.setAudienceRules(null);

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).isEmpty();
    }

    @Test
    void extractLocationValueNames_dedupesRepeatedRegionNames() {
        Incentive incentive = incentiveWithRules(
                regionRule("AMERICAS"),
                regionRule("AMERICAS"),
                regionRule("EMEAR")
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).containsExactly("AMERICAS", "EMEAR");
    }

    @Test
    void extractLocationValueNames_skipsRegionRulesWithBlankRuleValue() {
        Incentive incentive = incentiveWithRules(
                regionRule("AMERICAS"),
                regionRule(""),
                regionRule("   "),
                regionRule("EMEAR")
        );

        List<String> names = assembler.extractLocationValueNames(incentive);

        assertThat(names).containsExactly("AMERICAS", "EMEAR");
    }

    // ── Hierarchy-aware target resolution ──────────────────────────────────────

    @Test
    void resolveTargetLocationIds_returnsSelectedUuidAtItsOwnDepth() {
        // Saved-incentive shape: ruleValue is a LocationValue UUID at any depth.
        // With per-depth aggregates available, the resolver returns the selected
        // node itself — no longer collapsed to its depth-0 ancestor.
        UUID clientId = UUID.randomUUID();
        UUID californiaId = UUID.randomUUID();

        Incentive incentive = new Incentive();
        incentive.setClientId(clientId);
        incentive.setAudienceRules(new ArrayList<>(List.of(
                locationRule(californiaId.toString())
        )));

        List<UUID> result = assembler.resolveTargetLocationIds(incentive);

        assertThat(result).containsExactly(californiaId);
        // No ancestor walk roundtrip — the selected UUID is used directly.
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void resolveTargetLocationIds_resolvesLevelKeyedNameToSelectedDepth() {
        // Preview shape: rule has ruleType=LOCATION, ruleValue=name (not UUID),
        // and locationLevel set to a stub carrying the level UUID. The resolver
        // looks up the LocationValue via (level, name) and returns it directly.
        UUID clientId = UUID.randomUUID();
        UUID stateLevelId = UUID.randomUUID();
        UUID californiaId = UUID.randomUUID();

        IncentiveAudienceRule rule = new IncentiveAudienceRule();
        rule.setRuleType("LOCATION");
        rule.setRuleValue("California");
        LocationLevel stubLevel = new LocationLevel();
        stubLevel.setId(stateLevelId);
        rule.setLocationLevel(stubLevel);

        Incentive incentive = new Incentive();
        incentive.setClientId(clientId);
        incentive.setAudienceRules(new ArrayList<>(List.of(rule)));

        org.mockito.Mockito.when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql != null && sql.contains("level_id = ?") && sql.contains("UPPER(lv.name) = ?")),
                org.mockito.ArgumentMatchers.eq(UUID.class),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of(californiaId));

        List<UUID> result = assembler.resolveTargetLocationIds(incentive);

        assertThat(result).containsExactly(californiaId);
    }

    @Test
    void resolveTargetLocationIds_returnsEmpty_whenNoRules() {
        Incentive incentive = new Incentive();
        incentive.setClientId(UUID.randomUUID());
        incentive.setAudienceRules(new ArrayList<>());

        List<UUID> result = assembler.resolveTargetLocationIds(incentive);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void resolveTargetLocationIds_returnsEmpty_whenClientIdMissing() {
        Incentive incentive = new Incentive();
        incentive.setClientId(null);
        incentive.setAudienceRules(new ArrayList<>(List.of(
                locationRule(UUID.randomUUID().toString())
        )));

        List<UUID> result = assembler.resolveTargetLocationIds(incentive);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void buildTargetLocationsByLevel_groupsRulesByLevelName_forSavedIncentiveShape() {
        // Saved-incentive shape: locationLevel is a fully-loaded LocationLevel
        // with `name` set, ruleValue is a LocationValue UUID. The assembler
        // must look up the LocationValue name via repo and group by level.
        UUID clientId = UUID.randomUUID();
        UUID californiaId = UUID.randomUUID();
        UUID usId = UUID.randomUUID();

        LocationLevel countryLevel = new LocationLevel();
        countryLevel.setId(UUID.randomUUID());
        countryLevel.setName("Country");
        LocationLevel stateLevel = new LocationLevel();
        stateLevel.setId(UUID.randomUUID());
        stateLevel.setName("State");

        IncentiveAudienceRule countryRule = new IncentiveAudienceRule();
        countryRule.setRuleType("LOCATION");
        countryRule.setRuleValue(usId.toString());
        countryRule.setLocationLevel(countryLevel);

        IncentiveAudienceRule stateRule = new IncentiveAudienceRule();
        stateRule.setRuleType("LOCATION");
        stateRule.setRuleValue(californiaId.toString());
        stateRule.setLocationLevel(stateLevel);

        Incentive incentive = new Incentive();
        incentive.setClientId(clientId);
        incentive.setAudienceRules(new ArrayList<>(List.of(countryRule, stateRule)));

        com.tenxengage.app.entity.LocationValue californiaValue = new com.tenxengage.app.entity.LocationValue();
        californiaValue.setId(californiaId);
        californiaValue.setName("California");
        com.tenxengage.app.entity.LocationValue usValue = new com.tenxengage.app.entity.LocationValue();
        usValue.setId(usId);
        usValue.setName("United States");
        org.mockito.Mockito.when(locationValueRepo.findByIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(usValue, californiaValue));

        java.util.Map<String, List<String>> result = assembler.buildTargetLocationsByLevel(incentive);

        assertThat(result)
                .containsEntry("Country", List.of("United States"))
                .containsEntry("State", List.of("California"));
    }

    @Test
    void buildTargetLocationsByLevel_fetchesLevelNamesForPreviewStubLevels() {
        // Preview shape: locationLevel is a stub with only `id` set; the
        // assembler must hit the DB to resolve the level name.
        UUID clientId = UUID.randomUUID();
        UUID stateLevelId = UUID.randomUUID();

        LocationLevel stubLevel = new LocationLevel();
        stubLevel.setId(stateLevelId);

        IncentiveAudienceRule rule = new IncentiveAudienceRule();
        rule.setRuleType("LOCATION");
        rule.setRuleValue("California");
        rule.setLocationLevel(stubLevel);

        Incentive incentive = new Incentive();
        incentive.setClientId(clientId);
        incentive.setAudienceRules(new ArrayList<>(List.of(rule)));

        // The level-name lookup is `query(sql, RowMapper, clientId, levelId)` —
        // varargs spread, so Mockito sees four separate arguments. Match each
        // one explicitly. Cast the stubbed return through the raw `query`
        // overload to avoid the generic-inference dance.
        org.mockito.Mockito.when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.argThat(
                        (String sql) -> sql != null && sql.contains("FROM location_levels")),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<java.util.Map.Entry<UUID, String>>>any(),
                org.mockito.ArgumentMatchers.eq(clientId),
                org.mockito.ArgumentMatchers.eq(stateLevelId)
        )).thenReturn(List.of(java.util.Map.entry(stateLevelId, "State")));

        java.util.Map<String, List<String>> result = assembler.buildTargetLocationsByLevel(incentive);

        assertThat(result).containsEntry("State", List.of("California"));
    }

    @Test
    void buildTargetLocationsByLevel_groupsRegionRulesUnderRegionKey() {
        // Legacy shape: REGION rules with no level. Default key is "Region".
        Incentive incentive = new Incentive();
        incentive.setClientId(UUID.randomUUID());
        incentive.setAudienceRules(new ArrayList<>(List.of(
                regionRule("Americas"),
                regionRule("EMEA")
        )));

        java.util.Map<String, List<String>> result = assembler.buildTargetLocationsByLevel(incentive);

        assertThat(result).containsEntry("Region", List.of("Americas", "EMEA"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Incentive incentiveWithRules(IncentiveAudienceRule... rules) {
        Incentive incentive = new Incentive();
        incentive.setAudienceRules(new ArrayList<>(List.of(rules)));
        return incentive;
    }

    private IncentiveAudienceRule regionRule(String regionName) {
        IncentiveAudienceRule rule = new IncentiveAudienceRule();
        rule.setRuleType("REGION");
        rule.setRuleValue(regionName);
        return rule;
    }

    private IncentiveAudienceRule locationRule(String locationValueUuid) {
        IncentiveAudienceRule rule = new IncentiveAudienceRule();
        rule.setRuleType("LOCATION");
        rule.setRuleValue(locationValueUuid);
        // LOCATION-typed rules are only considered when a locationLevel is set —
        // the extractor filters on r.getLocationLevel() != null.
        LocationLevel level = new LocationLevel();
        rule.setLocationLevel(level);
        return rule;
    }

    private IncentiveAudienceRule partnerTypeRule(String partnerType) {
        IncentiveAudienceRule rule = new IncentiveAudienceRule();
        rule.setRuleType("PARTNER_TYPE");
        rule.setRuleValue(partnerType);
        return rule;
    }
}
