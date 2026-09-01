import { describe, it, expect } from "vitest";
import { initialBuilderState } from "@/types/builder-state.types";
import {
  buildCreateRequest,
  UnresolvedLocationNameError,
} from "@/utils/builderRequestMapper";
import type { BuilderState } from "@/types/builder-state.types";
import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

const REGION_LEVEL_ID = "a49a60f0-5116-4f29-8971-7d71911105ab";
const COUNTRY_LEVEL_ID = "de9e4d29-699f-4f98-a2aa-3d56c7844bd0";

function stateWithLocations(): BuilderState {
  return {
    ...initialBuilderState,
    basics: { ...initialBuilderState.basics, name: "Test", incentiveType: "SALES" },
    audience: {
      ...initialBuilderState.audience,
      userRoles: ["role-uuid"],
      locationSelections: {
        [REGION_LEVEL_ID]: ["AMERICAS", "EMEAR"],
        [COUNTRY_LEVEL_ID]: ["United States"],
      },
    },
  };
}

describe("buildCreateRequest — BUG-079 audience rules (UUID-on-the-wire)", () => {
  it("emits LOCATION-typed rules with locationLevelId + UUID ruleValue per selection", () => {
    // BUG-079: ruleValue carries a LocationValue UUID (looked up from the loaded
    // hierarchy); locationValueName is no longer on the wire.
    const req = buildCreateRequest(stateWithLocations(), HIERARCHY);
    const locationRules = (req.audienceRules ?? []).filter(
      (r) => r.ruleType === "LOCATION",
    );

    expect(locationRules).toHaveLength(3);

    const americas = locationRules.find((r) => r.ruleValue === "r-am");
    expect(americas).toBeDefined();
    expect(americas?.locationLevelId).toBe(REGION_LEVEL_ID);

    const emear = locationRules.find((r) => r.ruleValue === "r-em");
    expect(emear).toBeDefined();
    expect(emear?.locationLevelId).toBe(REGION_LEVEL_ID);

    const us = locationRules.find((r) => r.ruleValue === "c-us");
    expect(us).toBeDefined();
    expect(us?.locationLevelId).toBe(COUNTRY_LEVEL_ID);

    // Sanity: no rule should still be carrying a name in ruleValue.
    expect(locationRules.map((r) => r.ruleValue)).not.toContain("AMERICAS");
    expect(locationRules.map((r) => r.ruleValue)).not.toContain("United States");
  });

  it("emits no LOCATION rules when no locations are selected", () => {
    const state: BuilderState = {
      ...initialBuilderState,
      basics: { ...initialBuilderState.basics, name: "Test", incentiveType: "SALES" },
      audience: { ...initialBuilderState.audience, userRoles: ["role-uuid"] },
    };
    const req = buildCreateRequest(state, HIERARCHY);
    const locationRules = (req.audienceRules ?? []).filter(
      (r) => r.ruleType === "LOCATION",
    );
    expect(locationRules).toHaveLength(0);
  });

  it("never emits REGION-typed rules (BUG-034 cutover)", () => {
    const req = buildCreateRequest(stateWithLocations(), HIERARCHY);
    const regionRules = (req.audienceRules ?? []).filter(
      // The TS type no longer permits "REGION" in the union; cast for the runtime check.
      (r) => (r.ruleType as string) === "REGION",
    );
    expect(regionRules).toHaveLength(0);
  });

  it("preserves ROLE and PARTNER_TYPE rules unchanged", () => {
    const state: BuilderState = {
      ...initialBuilderState,
      basics: { ...initialBuilderState.basics, name: "Test", incentiveType: "SALES" },
      audience: {
        ...initialBuilderState.audience,
        userRoles: ["role-uuid-1"],
        partnerTypes: ["Reseller"],
      },
    };
    const req = buildCreateRequest(state, HIERARCHY);
    const roleRule = (req.audienceRules ?? []).find((r) => r.ruleType === "ROLE");
    const partnerRule = (req.audienceRules ?? []).find(
      (r) => r.ruleType === "PARTNER_TYPE",
    );
    expect(roleRule?.ruleValue).toBe("role-uuid-1");
    expect(roleRule?.locationLevelId).toBeUndefined();
    expect(partnerRule?.ruleValue).toBe("Reseller");
    expect(partnerRule?.locationLevelId).toBeUndefined();
  });

  it("resolves names case-insensitively (whitespace + casing tolerated)", () => {
    // Case drift via Excel paste must still resolve so the user isn't blocked
    // by trivial formatting differences. Disambiguation against UUIDs happens
    // after lookup so the persisted reference is exact.
    const state: BuilderState = {
      ...initialBuilderState,
      basics: { ...initialBuilderState.basics, name: "Test", incentiveType: "SALES" },
      audience: {
        ...initialBuilderState.audience,
        userRoles: ["role-uuid"],
        locationSelections: {
          [REGION_LEVEL_ID]: ["  americas  "],
          [COUNTRY_LEVEL_ID]: ["united STATES"],
        },
      },
    };
    const req = buildCreateRequest(state, HIERARCHY);
    const locationRules = (req.audienceRules ?? []).filter(
      (r) => r.ruleType === "LOCATION",
    );
    expect(locationRules.map((r) => r.ruleValue).sort()).toEqual(["c-us", "r-am"]);
  });

  it("throws UnresolvedLocationNameError when a name doesn't exist in the hierarchy", () => {
    // BUG-079 Q1 backstop: rename mid-session, Copilot hallucination, or a stale
    // draft can leave state with a name that no longer maps to a UUID. The
    // mapper must NOT silently drop — surfacing the error lets the UI prompt
    // re-pick instead of saving an incentive with fewer rules than the user picked.
    const state: BuilderState = {
      ...initialBuilderState,
      basics: { ...initialBuilderState.basics, name: "Test", incentiveType: "SALES" },
      audience: {
        ...initialBuilderState.audience,
        userRoles: ["role-uuid"],
        locationSelections: {
          [REGION_LEVEL_ID]: ["AMERICAS", "ATLANTIS"], // ATLANTIS doesn't exist
        },
      },
    };
    expect(() => buildCreateRequest(state, HIERARCHY)).toThrow(
      UnresolvedLocationNameError,
    );
    try {
      buildCreateRequest(state, HIERARCHY);
    } catch (e) {
      expect(e).toBeInstanceOf(UnresolvedLocationNameError);
      const err = e as UnresolvedLocationNameError;
      expect(err.unresolvedByLevel[REGION_LEVEL_ID]).toEqual(["ATLANTIS"]);
      expect(err.message).toContain("ATLANTIS");
    }
  });

  it("throws UnresolvedLocationNameError when no hierarchy is provided but locations are selected", () => {
    // No hierarchy → empty index → every name fails to resolve. Better to fail
    // loud than silently produce an empty audience-rule list.
    expect(() => buildCreateRequest(stateWithLocations())).toThrow(
      UnresolvedLocationNameError,
    );
  });
});

// -----------------------------------------------------------------------------
// Per-level budget allocation (Step 4)
// -----------------------------------------------------------------------------

const STATE_LEVEL_ID = "11111111-1111-1111-1111-111111111111";
const REGION_LVL = {
  id: REGION_LEVEL_ID,
  name: "Region",
  depth: 0,
  valueCount: 0,
  useInBuilder: true,
  useInFilters: true,
  isRequired: true,
};
const COUNTRY_LVL = {
  id: COUNTRY_LEVEL_ID,
  name: "Country",
  depth: 1,
  valueCount: 0,
  useInBuilder: true,
  useInFilters: true,
  isRequired: false,
};
const STATE_LVL = {
  id: STATE_LEVEL_ID,
  name: "State",
  depth: 2,
  valueCount: 0,
  useInBuilder: true,
  useInFilters: true,
  isRequired: false,
};
function loc(
  id: string,
  name: string,
  levelId: string,
  parentId: string | null,
  children: LocationValueResponse[] = [],
): LocationValueResponse {
  return {
    id,
    name,
    code: null,
    levelName: "",
    levelId,
    parentId,
    children,
  };
}
const HIERARCHY: LocationHierarchyResponse = {
  levels: [REGION_LVL, COUNTRY_LVL, STATE_LVL],
  tree: [
    loc("r-am", "AMERICAS", REGION_LVL.id, null, [
      loc("c-us", "United States", COUNTRY_LVL.id, "r-am", [
        loc("s-ca", "California", STATE_LVL.id, "c-us"),
      ]),
    ]),
    loc("r-em", "EMEAR", REGION_LVL.id, null, []),
  ],
};

function stateForBudget(
  budgetMode: "global" | "per-location",
  globalBudgets: Record<string, string>,
  locationBudgets: Record<string, Record<string, string>> = {},
): BuilderState {
  return {
    ...initialBuilderState,
    basics: {
      ...initialBuilderState.basics,
      name: "Budget Test",
      incentiveType: "SALES",
    },
    audience: {
      ...initialBuilderState.audience,
      userRoles: ["role-uuid"],
      locationSelections: {
        [REGION_LEVEL_ID]: ["AMERICAS", "EMEAR"],
        [COUNTRY_LEVEL_ID]: ["United States"],
        [STATE_LEVEL_ID]: ["California"],
      },
    },
    budgetData: {
      ...initialBuilderState.budgetData,
      selectedCurrencies: ["cash"],
      budgetMode,
      globalBudgets,
      locationBudgets,
      budgetLocationLevelId: budgetMode === "per-location" ? REGION_LEVEL_ID : null,
    },
  };
}

describe("buildCreateRequest — per-level budget allocation", () => {
  it("global mode: emits one budget entry with no location allocations", () => {
    const state = stateForBudget("global", { cash: "50000" });
    const req = buildCreateRequest(state, HIERARCHY);

    expect(req.budgets).toHaveLength(1);
    expect(req.budgets![0]?.budgetMode).toBe("GLOBAL");
    expect(req.budgets![0]?.totalBudget).toBe("50000");
    expect(req.budgets![0]?.locationAllocations).toBeUndefined();
    expect(req.budgets![0]?.budgetLocationLevelId).toBeUndefined();
  });

  it("per-location mode: maps to PER_LOCATION on the wire (not GLOBAL)", () => {
    // BUG surfaced during exploration: the old mapper sent "GLOBAL" for
    // per-location mode, silently dropping mode + allocations.
    const state = stateForBudget("per-location", { cash: "100000" }, {
      cash: { "r-am": "60000", "r-em": "40000" },
    });
    const req = buildCreateRequest(state, HIERARCHY);
    expect(req.budgets![0]?.budgetMode).toBe("PER_LOCATION");
    expect(req.budgets![0]?.budgetLocationLevelId).toBe(REGION_LEVEL_ID);
  });

  it("per-location mode: serializes typed amounts at every depth", () => {
    const state = stateForBudget("per-location", { cash: "100000" }, {
      cash: {
        "r-am": "60000",
        "r-em": "40000",
        "c-us": "50000",
        "s-ca": "30000",
      },
    });
    const req = buildCreateRequest(state, HIERARCHY);
    const allocs = req.budgets![0]?.locationAllocations ?? [];
    const byId = Object.fromEntries(
      allocs.map((a) => [a.locationValueId, a.amount]),
    );
    expect(byId["r-am"]).toBe("60000");
    expect(byId["r-em"]).toBe("40000");
    expect(byId["c-us"]).toBe("50000");
    expect(byId["s-ca"]).toBe("30000");
  });

  it("per-location mode: auto-fills blank children with the residual at save time", () => {
    // Global=100k. Americas=60k typed, EMEAR blank → EMEAR auto-fills 40k.
    const state = stateForBudget("per-location", { cash: "100000" }, {
      cash: { "r-am": "60000" },
    });
    const req = buildCreateRequest(state, HIERARCHY);
    const allocs = req.budgets![0]?.locationAllocations ?? [];
    const byId = Object.fromEntries(
      allocs.map((a) => [a.locationValueId, a.amount]),
    );
    expect(byId["r-am"]).toBe("60000");
    expect(byId["r-em"]).toBe("40000");
  });

  it("per-location mode: drops nodes whose effective amount rounds to zero", () => {
    // Global=100k, Americas=100k typed → EMEAR effective=0, must not be sent.
    const state = stateForBudget("per-location", { cash: "100000" }, {
      cash: { "r-am": "100000" },
    });
    const req = buildCreateRequest(state, HIERARCHY);
    const allocIds = (req.budgets![0]?.locationAllocations ?? []).map(
      (a) => a.locationValueId,
    );
    expect(allocIds).toContain("r-am");
    expect(allocIds).not.toContain("r-em");
  });

  it("legacy 'per-region' internal mode is normalized to PER_LOCATION on the wire", () => {
    // Defensive — pre-2026-04-28 hydrated state may still carry the legacy alias.
    const state = stateForBudget("global", { cash: "100000" });
    state.budgetData.budgetMode = "per-region";
    state.budgetData.locationBudgets = { cash: { "r-am": "100000" } };
    state.budgetData.budgetLocationLevelId = REGION_LEVEL_ID;
    const req = buildCreateRequest(state, HIERARCHY);
    expect(req.budgets![0]?.budgetMode).toBe("PER_LOCATION");
  });
});
