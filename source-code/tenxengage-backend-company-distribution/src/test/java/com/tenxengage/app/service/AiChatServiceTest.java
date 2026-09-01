package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.BuilderConfigResponse;
import com.tenxengage.app.dto.response.BuilderFieldConfigResponse;
import com.tenxengage.app.dto.response.BuilderSectionConfigResponse;
import com.tenxengage.app.dto.response.LocationFilterOptionsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the pure prompt-assembly helpers on AiChatService.
 * Asserts the LOCATION HIERARCHY block produced by the tenant's configured
 * levels matches what the copilot system prompt relies on — the block's
 * presence lets the AI dispatch the hierarchy-aware `locationSelections`
 * shape instead of the legacy flat `regions` enum (BUG-025).
 */
class AiChatServiceTest {

    @Test
    void formatLocationHierarchyBlock_returnsEmptyForNullOrEmptyLevels() {
        assertThat(AiChatService.formatLocationHierarchyBlock(null)).isEmpty();
        assertThat(
            AiChatService.formatLocationHierarchyBlock(
                new LocationFilterOptionsResponse(List.of())
            )
        ).isEmpty();
    }

    @Test
    void formatLocationHierarchyBlock_rendersSingleLevelHierarchy() {
        UUID regionLevelId = UUID.randomUUID();
        UUID americasId = UUID.randomUUID();
        UUID emearId = UUID.randomUUID();

        LocationFilterOptionsResponse options = new LocationFilterOptionsResponse(List.of(
            new LocationFilterOptionsResponse.LocationFilterLevel(
                regionLevelId,
                "Region",
                0,
                List.of(
                    new LocationFilterOptionsResponse.LocationFilterValue(
                        americasId, "AMERICAS", "AMER", null),
                    new LocationFilterOptionsResponse.LocationFilterValue(
                        emearId, "EMEAR", "EMEA", null)
                )
            )
        ));

        String block = AiChatService.formatLocationHierarchyBlock(options);

        assertThat(block).contains("=== LOCATION HIERARCHY ===");
        assertThat(block).contains("Level: **Region** (depth 0)");
        assertThat(block).contains("levelId: `" + regionLevelId + "`");
        // Each value carries its own valueId so the AI can key locationBudgets
        // by it (BUG-071 round 3). Top-level values have no parent valueId.
        assertThat(block).contains("- AMERICAS (valueId: `" + americasId + "`)");
        assertThat(block).contains("- EMEAR (valueId: `" + emearId + "`)");
        assertThat(block).contains("locationSelections");
        assertThat(block).contains("locationBudgets");
    }

    @Test
    void formatLocationHierarchyBlock_rendersMultiLevelHierarchyWithParentRefs() {
        UUID regionLevelId = UUID.randomUUID();
        UUID countryLevelId = UUID.randomUUID();
        UUID americasId = UUID.randomUUID();
        UUID canadaId = UUID.randomUUID();

        LocationFilterOptionsResponse options = new LocationFilterOptionsResponse(List.of(
            new LocationFilterOptionsResponse.LocationFilterLevel(
                regionLevelId,
                "Region",
                0,
                List.of(new LocationFilterOptionsResponse.LocationFilterValue(
                    americasId, "AMERICAS", "AMER", null))
            ),
            new LocationFilterOptionsResponse.LocationFilterLevel(
                countryLevelId,
                "Country",
                1,
                List.of(new LocationFilterOptionsResponse.LocationFilterValue(
                    canadaId, "Canada", "CA", americasId))
            )
        ));

        String block = AiChatService.formatLocationHierarchyBlock(options);

        assertThat(block).contains("Level: **Region** (depth 0)");
        assertThat(block).contains("Level: **Country** (depth 1)");
        // BUG-071 round 3: child values must surface BOTH their own valueId
        // (so locationBudgets can be keyed by it) and their parent valueId
        // (so the AI can navigate the tree).
        assertThat(block).contains(
            "- Canada (valueId: `" + canadaId + "`, parent valueId: `" + americasId + "`)");
    }

    @Test
    void formatLocationHierarchyBlock_rendersLevelsWithNoValuesAsPlaceholder() {
        UUID levelId = UUID.randomUUID();
        LocationFilterOptionsResponse options = new LocationFilterOptionsResponse(List.of(
            new LocationFilterOptionsResponse.LocationFilterLevel(
                levelId, "Region", 0, List.of())
        ));

        String block = AiChatService.formatLocationHierarchyBlock(options);

        assertThat(block).contains("Level: **Region**");
        assertThat(block).contains("(no values configured yet)");
    }

    /**
     * BUG-045: the AI Copilot was blind to admin-defined custom audience fields,
     * so it never filled `audience.dynamicFields` and silently completed Step 3
     * with mandatory custom fields blank. The CUSTOM BUILDER FIELDS block is the
     * counterpart to LOCATION HIERARCHY — it tells the AI which custom keys exist
     * for the current incentive type, which are mandatory, and how to dispatch
     * them via the `dynamicFields` slot in UPDATE_AUDIENCE.
     */
    @Test
    void formatBuilderConfigBlock_returnsEmptyForNullOrSectionless() {
        assertThat(AiChatService.formatBuilderConfigBlock(null, "audience")).isEmpty();
        assertThat(AiChatService.formatBuilderConfigBlock(
            new BuilderConfigResponse("SALES", List.of()), "audience"
        )).isEmpty();
    }

    @Test
    void formatBuilderConfigBlock_returnsEmptyWhenSectionKeyDoesNotMatch() {
        BuilderConfigResponse config = new BuilderConfigResponse("SALES", List.of(
            section("audience", List.of(field("campaignSegment", "Campaign Segment", false)))
        ));

        assertThat(AiChatService.formatBuilderConfigBlock(config, "criteria")).isEmpty();
    }

    @Test
    void formatBuilderConfigBlock_returnsEmptyWhenAllFieldsAreSystem() {
        BuilderConfigResponse config = new BuilderConfigResponse("SALES", List.of(
            section("audience", List.of(systemField("regions", "Regions")))
        ));

        assertThat(AiChatService.formatBuilderConfigBlock(config, "audience")).isEmpty();
    }

    @Test
    void formatBuilderConfigBlock_rendersCustomFieldsWithMandatoryAndHelperText() {
        BuilderFieldConfigResponse mandatory = new BuilderFieldConfigResponse(
            UUID.randomUUID(),
            "campaignSegment",
            "Campaign Segment",
            "TEXT",
            "Internal segment label used for reporting",
            true,   // isMandatory
            false,  // isSystem
            false,  // isEligibility
            null, null, null,
            null, null,
            false,
            0
        );
        BuilderFieldConfigResponse optional = new BuilderFieldConfigResponse(
            UUID.randomUUID(),
            "internalNote",
            "Internal Note",
            "TEXTAREA",
            null,
            false,  // isMandatory
            false,  // isSystem
            false,  // isEligibility
            null, null, null,
            null, null,
            false,
            1
        );
        BuilderConfigResponse config = new BuilderConfigResponse("SALES", List.of(
            section("audience", List.of(mandatory, optional))
        ));

        String block = AiChatService.formatBuilderConfigBlock(config, "audience");

        assertThat(block).contains("=== CUSTOM BUILDER FIELDS (audience) ===");
        assertThat(block).contains("**Campaign Segment**");
        assertThat(block).contains("fieldKey: `campaignSegment`");
        assertThat(block).contains("mandatory: true");
        assertThat(block).contains("Internal segment label used for reporting");
        assertThat(block).contains("**Internal Note**");
        assertThat(block).contains("fieldKey: `internalNote`");
        assertThat(block).contains("mandatory: false");
        assertThat(block).contains("dynamicFields");
    }

    @Test
    void formatBuilderConfigBlock_rendersDataObjectSourceForLookupFields() {
        BuilderFieldConfigResponse lookup = new BuilderFieldConfigResponse(
            UUID.randomUUID(),
            "productLine",
            "Product Line",
            "SINGLE_SELECT",
            null,
            false, false, false,
            UUID.randomUUID(),
            "productLine",
            "Sales Data",
            "DATA_OBJECT",
            null,
            false,
            0
        );
        BuilderConfigResponse config = new BuilderConfigResponse("SALES", List.of(
            section("audience", List.of(lookup))
        ));

        String block = AiChatService.formatBuilderConfigBlock(config, "audience");

        assertThat(block).contains("valuesFrom: Sales Data.productLine");
        assertThat(block).contains("valueSource: DATA_OBJECT");
    }

    @Test
    void formatBuilderConfigBlock_skipsSystemFieldsAndKeepsCustomOnes() {
        BuilderConfigResponse config = new BuilderConfigResponse("SALES", List.of(
            section("audience", List.of(
                systemField("regions", "Regions"),
                systemField("userRoles", "User Roles"),
                field("campaignSegment", "Campaign Segment", true)
            ))
        ));

        String block = AiChatService.formatBuilderConfigBlock(config, "audience");

        assertThat(block).contains("**Campaign Segment**");
        assertThat(block).doesNotContain("**Regions**");
        assertThat(block).doesNotContain("**User Roles**");
    }

    private static BuilderSectionConfigResponse section(
        String sectionKey,
        List<BuilderFieldConfigResponse> fields
    ) {
        return new BuilderSectionConfigResponse(
            UUID.randomUUID(),
            "SALES",
            sectionKey,
            sectionKey,
            null,
            0,
            false,
            true,
            fields
        );
    }

    private static BuilderFieldConfigResponse field(
        String fieldKey,
        String displayName,
        boolean mandatory
    ) {
        return new BuilderFieldConfigResponse(
            UUID.randomUUID(),
            fieldKey,
            displayName,
            "TEXT",
            null,
            mandatory,
            false,
            false,
            null, null, null,
            null, null,
            false,
            0
        );
    }

    private static BuilderFieldConfigResponse systemField(String fieldKey, String displayName) {
        return new BuilderFieldConfigResponse(
            UUID.randomUUID(),
            fieldKey,
            displayName,
            "TEXT",
            null,
            true,
            true,   // isSystem = true
            false,
            null, null, null,
            null, null,
            false,
            0
        );
    }

    /**
     * BUG-071 regression: the static system prompt was missing instructions for the
     * `per-location` budget mode and the `locationBudgets` field, so the AI had no
     * way to recognize, explain, or update a per-level budget tree even though the
     * frontend serialized that data into `currentState`. This test loads the prompt
     * resource and asserts the per-location section is present — if a future edit
     * removes it, the LLM's awareness of the feature regresses immediately.
     */
    @Test
    void incentiveCopilotSystemPrompt_documentsPerLocationBudgetMode() throws Exception {
        String prompt = StreamUtils.copyToString(
            new ClassPathResource("prompts/incentive-copilot-system.txt").getInputStream(),
            StandardCharsets.UTF_8
        );

        // Action signature lists locationBudgets alongside the legacy budget fields.
        assertThat(prompt).contains("locationBudgets?");

        // budgetMode enumerates all three modes, including per-location.
        assertThat(prompt).contains("`\"per-location\"` when using locationBudgets");

        // Data-shape definition for locationBudgets is present and explains UUID-keying.
        assertThat(prompt).contains("locationBudgets: `{ \"cash\": { \"<locationValueId>\":");
        assertThat(prompt).contains("locationValueId (UUID)");

        // Worked example shows a per-location dispatch keyed by hierarchy UUIDs.
        assertThat(prompt).contains("Sales per-location mode");
        assertThat(prompt).contains("budgetMode: \"per-location\"");

        // Read-back instruction tells the AI to resolve UUIDs to names instead of
        // surfacing raw UUIDs to the user. The rule must apply to ALL user-facing
        // output — not just summarizing/auditing — since the Copilot was observed
        // pasting raw database UUIDs into diagnostic messages
        // ("Canada's parent valueId is b1306be4-..."), which is a data-leakage
        // concern even though those UUIDs aren't strictly sensitive.
        assertThat(prompt).contains("never expose UUIDs in user-facing text");
        assertThat(prompt).contains("LOCATION HIERARCHY");
        // The rule is explicit about the diagnostic / clarification path, not
        // just summarizing — that's what regressed in round 3 testing.
        assertThat(prompt).contains("not when describing why you couldn't do something");
        assertThat(prompt).contains("not when asking for clarification");

        // BUG-071 follow-up: the prompt must also instruct the AI that
        // (a) locationBudgets keys MUST be UUIDs (no name-keyed shortcuts) and
        // (b) globalBudgets[currencyId] must be set alongside locationBudgets in
        // per-location mode, since the Step 4 UI's per-currency total binds to
        // globalBudgets even in that mode.
        assertThat(prompt).contains("Keys MUST be UUIDs");
        assertThat(prompt).contains("Pair with `globalBudgets[currencyId]`");
        assertThat(prompt).contains("per-currency parent total");
    }

    /**
     * BUG-080 regression: the prompt must instruct the AI that adding a
     * deep-level location requires populating every ancestor level in
     * `locationSelections`, otherwise the leaf lands invisible behind the UI's
     * cascade filter until a matching ancestor is picked manually. A future
     * edit that drops the rule re-opens the bug, so guard the prompt here.
     */
    @Test
    void incentiveCopilotSystemPrompt_requiresAncestorChainForDeepLocations() throws Exception {
        String prompt = StreamUtils.copyToString(
            new ClassPathResource("prompts/incentive-copilot-system.txt").getInputStream(),
            StandardCharsets.UTF_8
        );

        // The MUST rule is present and names locationSelections explicitly.
        assertThat(prompt).contains("MUST — include the full ancestor chain");
        assertThat(prompt).contains("locationSelections");

        // The worked example shows a 4-level chain (Region → Country → State → City)
        // with all parent levels populated.
        assertThat(prompt).contains("Los Angeles");
        assertThat(prompt).contains("California");
        assertThat(prompt).contains("\"<region-level-id>\": [\"AMERICAS\"]");
        assertThat(prompt).contains("\"<city-level-id>\": [\"Los Angeles\"]");
    }
}
