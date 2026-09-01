import { describe, it, expect } from "vitest";
import {
  flattenLocationValuesForAudience,
  flattenLocationValuesForBudget,
  translateAudiencePayload,
  translateBudgetPayload,
} from "@/hooks/useAiChat";

const regionLevel = { id: "lvl-region", name: "Region", depth: 0 };
const countryLevel = { id: "lvl-country", name: "Country", depth: 1 };

describe("translateAudiencePayload", () => {
  it("passes through a payload that already has locationSelections", () => {
    const payload = {
      locationSelections: { "lvl-region": ["AMERICAS"] },
      userRoles: ["Partner Seller"],
    };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toBe(payload);
  });

  it("passes through a payload with no legacy location fields", () => {
    const payload = { userRoles: ["Partner Seller"] };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toBe(payload);
  });

  it("passes through unchanged when builderLevels is empty (nothing to key against)", () => {
    const payload = { regions: ["AMERICAS"] };
    const result = translateAudiencePayload(payload, []);
    expect(result).toBe(payload);
  });

  it("maps legacy regions into locationSelections keyed by the top-level levelId", () => {
    const payload = { regions: ["AMERICAS", "EMEAR"], userRoles: ["Partner Seller"] };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toEqual({
      regions: ["AMERICAS", "EMEAR"],
      userRoles: ["Partner Seller"],
      locationSelections: { "lvl-region": ["AMERICAS", "EMEAR"] },
    });
  });

  it("maps legacy countries into locationSelections keyed by the second-level levelId", () => {
    const payload = { countries: ["United States", "Canada"] };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toEqual({
      countries: ["United States", "Canada"],
      locationSelections: { "lvl-country": ["United States", "Canada"] },
    });
  });

  it("maps both regions and countries in a single call", () => {
    const payload = {
      regions: ["AMERICAS"],
      countries: ["United States"],
    };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toEqual({
      regions: ["AMERICAS"],
      countries: ["United States"],
      locationSelections: {
        "lvl-region": ["AMERICAS"],
        "lvl-country": ["United States"],
      },
    });
  });

  it("skips empty legacy arrays", () => {
    const payload = { regions: [], countries: ["Canada"] };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toEqual({
      regions: [],
      countries: ["Canada"],
      locationSelections: { "lvl-country": ["Canada"] },
    });
  });

  it("drops countries if no second level is configured", () => {
    const payload = { countries: ["United States"] };
    const result = translateAudiencePayload(payload, [regionLevel]);
    // countries has nowhere to go — no locationSelections emitted
    expect(result).toBe(payload);
  });

  it("preserves other audience fields untouched (userRoles, partnerTypes, specificPartners)", () => {
    const payload = {
      regions: ["AMERICAS"],
      userRoles: ["Partner Seller"],
      partnerTypes: ["Reseller"],
      specificPartners: "Acme Corp",
    };
    const result = translateAudiencePayload(payload, [regionLevel, countryLevel]);
    expect(result).toMatchObject({
      userRoles: ["Partner Seller"],
      partnerTypes: ["Reseller"],
      specificPartners: "Acme Corp",
      locationSelections: { "lvl-region": ["AMERICAS"] },
    });
  });
});

// BUG-080: when the AI dispatches a deep-level locationSelections leaf without
// its ancestor chain, the cascade-filtered Step3Audience UI hides it until a
// matching parent is picked manually. The translator must back-fill ancestor
// names at every parent level UUID so the leaf is immediately visible.
describe("translateAudiencePayload — ancestor back-fill (BUG-080)", () => {
  // Realistic 4-level hierarchy: Region → Country → State → City
  const REGION_LVL = "lvl-region";
  const COUNTRY_LVL = "lvl-country";
  const STATE_LVL = "lvl-state";
  const CITY_LVL = "lvl-city";

  const builderLevels = [
    { id: REGION_LVL, name: "Region", depth: 0 },
    { id: COUNTRY_LVL, name: "Country", depth: 1 },
    { id: STATE_LVL, name: "State", depth: 2 },
    { id: CITY_LVL, name: "City", depth: 3 },
  ];

  const AMER_ID = "11111111-1111-1111-1111-111111111111";
  const APJ_ID = "22222222-2222-2222-2222-222222222222";
  const USA_ID = "33333333-3333-3333-3333-333333333333";
  const JPN_ID = "44444444-4444-4444-4444-444444444444";
  const CA_ID = "55555555-5555-5555-5555-555555555555";
  const LA_ID = "66666666-6666-6666-6666-666666666666";

  const VALUES = [
    { id: AMER_ID, name: "AMERICAS", levelId: REGION_LVL, parentId: null },
    { id: APJ_ID, name: "APJ", levelId: REGION_LVL, parentId: null },
    { id: USA_ID, name: "United States", levelId: COUNTRY_LVL, parentId: AMER_ID },
    { id: JPN_ID, name: "Japan", levelId: COUNTRY_LVL, parentId: APJ_ID },
    { id: CA_ID, name: "California", levelId: STATE_LVL, parentId: USA_ID },
    { id: LA_ID, name: "Los Angeles", levelId: CITY_LVL, parentId: CA_ID },
  ];

  it("back-fills the full ancestor chain when the AI sends a city leaf alone", () => {
    const payload = {
      locationSelections: { [CITY_LVL]: ["Los Angeles"] },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES) as {
      locationSelections: Record<string, string[]>;
    };
    expect(result.locationSelections).toEqual({
      [CITY_LVL]: ["Los Angeles"],
      [STATE_LVL]: ["California"],
      [COUNTRY_LVL]: ["United States"],
      [REGION_LVL]: ["AMERICAS"],
    });
  });

  it("preserves an existing top-level selection at a different region while adding the leaf's ancestor as well", () => {
    // User had APJ; AI adds Los Angeles. The AMERICAS ancestor must be added
    // ALONGSIDE APJ — the translator never silently drops user picks.
    const payload = {
      locationSelections: {
        [REGION_LVL]: ["APJ"],
        [CITY_LVL]: ["Los Angeles"],
      },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES) as {
      locationSelections: Record<string, string[]>;
    };
    expect(result.locationSelections[REGION_LVL]).toEqual(
      expect.arrayContaining(["APJ", "AMERICAS"]),
    );
    expect(result.locationSelections[REGION_LVL]).toHaveLength(2);
    expect(result.locationSelections[CITY_LVL]).toEqual(["Los Angeles"]);
    expect(result.locationSelections[STATE_LVL]).toEqual(["California"]);
    expect(result.locationSelections[COUNTRY_LVL]).toEqual(["United States"]);
  });

  it("does not duplicate an ancestor that the AI already included", () => {
    const payload = {
      locationSelections: {
        [REGION_LVL]: ["AMERICAS"],
        [CITY_LVL]: ["Los Angeles"],
      },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES) as {
      locationSelections: Record<string, string[]>;
    };
    expect(result.locationSelections[REGION_LVL]).toEqual(["AMERICAS"]);
  });

  it("matches names case-insensitively when looking up the leaf", () => {
    const payload = {
      locationSelections: { [CITY_LVL]: ["los angeles"] },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES) as {
      locationSelections: Record<string, string[]>;
    };
    expect(result.locationSelections[REGION_LVL]).toEqual(["AMERICAS"]);
    expect(result.locationSelections[STATE_LVL]).toEqual(["California"]);
  });

  it("returns the same payload reference when nothing needs back-filling", () => {
    // Top-level only, hierarchy provided — no ancestors to add.
    const payload = {
      locationSelections: { [REGION_LVL]: ["AMERICAS"] },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES);
    expect(result).toBe(payload);
  });

  it("leaves unknown leaf names alone instead of inventing ancestors", () => {
    const payload = {
      locationSelections: { [CITY_LVL]: ["Atlantis"] },
    };
    const result = translateAudiencePayload(payload, builderLevels, VALUES);
    // No back-fill possible — return same reference, payload unchanged.
    expect(result).toBe(payload);
  });

  it("back-fills the parent region when the legacy `countries` shape is used at a sub-level", () => {
    // Legacy shape: { countries: ["United States"] } maps to second-level key,
    // and the back-fill must still add the AMERICAS region above it.
    const payload = { countries: ["United States"] };
    const result = translateAudiencePayload(payload, builderLevels, VALUES) as {
      locationSelections: Record<string, string[]>;
    };
    expect(result.locationSelections[COUNTRY_LVL]).toEqual(["United States"]);
    expect(result.locationSelections[REGION_LVL]).toEqual(["AMERICAS"]);
  });
});

// BUG-082: the AI's prompt continues to speak in role display-name terms,
// but the wire format for ROLE audience rules is the role's UUID. The
// translator is the single resolve boundary: it flips known display names to
// their matching ClientRole.id, leaves already-id values as-is, and drops
// unknown names so hallucinated roles never reach the wire.
describe("translateAudiencePayload — userRoles UUID resolve (BUG-082)", () => {
  const PARTNER_ADMIN_ID = "aaaa1111-1111-1111-1111-111111111111";
  const PARTNER_SELLER_ID = "bbbb2222-2222-2222-2222-222222222222";

  const ROLES = [
    { id: PARTNER_ADMIN_ID, name: "Partner Admin" },
    { id: PARTNER_SELLER_ID, name: "Partner Seller" },
  ];

  it("translates AI-emitted display names to role-id UUIDs", () => {
    const payload = { userRoles: ["Partner Seller"] };
    const result = translateAudiencePayload(payload, [], [], ROLES) as {
      userRoles: string[];
    };
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID]);
  });

  it("translates a mix of names and existing UUIDs idempotently", () => {
    const payload = { userRoles: ["Partner Seller", PARTNER_ADMIN_ID] };
    const result = translateAudiencePayload(payload, [], [], ROLES) as {
      userRoles: string[];
    };
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID, PARTNER_ADMIN_ID]);
  });

  it("matches names case-insensitively (AI casing drift tolerated)", () => {
    const payload = { userRoles: ["partner seller", "  PARTNER ADMIN "] };
    const result = translateAudiencePayload(payload, [], [], ROLES) as {
      userRoles: string[];
    };
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID, PARTNER_ADMIN_ID]);
  });

  it("drops unknown role names rather than emitting them on the wire", () => {
    const payload = { userRoles: ["Account Executive", "Partner Seller"] };
    const result = translateAudiencePayload(payload, [], [], ROLES) as {
      userRoles: string[];
    };
    // "Account Executive" is not a real role at this tenant — drop, not pass through.
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID]);
  });

  it("dedupes role ids that resolve from both their name and their UUID in the same payload", () => {
    const payload = { userRoles: ["Partner Seller", PARTNER_SELLER_ID] };
    const result = translateAudiencePayload(payload, [], [], ROLES) as {
      userRoles: string[];
    };
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID]);
  });

  it("returns the same payload reference when userRoles is already in id form and order matches", () => {
    const payload = { userRoles: [PARTNER_ADMIN_ID, PARTNER_SELLER_ID] };
    const result = translateAudiencePayload(payload, [], [], ROLES);
    expect(result).toBe(payload);
  });

  it("returns the same payload reference when no availableRoles are supplied (no resolution possible)", () => {
    const payload = { userRoles: ["Partner Seller"] };
    const result = translateAudiencePayload(payload, [], [], []);
    expect(result).toBe(payload);
  });

  it("preserves an unrelated translator pass (locationSelections back-fill) alongside userRoles resolution", () => {
    const REGION_LVL = "lvl-region";
    const COUNTRY_LVL = "lvl-country";
    const builderLevels = [
      { id: REGION_LVL, name: "Region", depth: 0 },
      { id: COUNTRY_LVL, name: "Country", depth: 1 },
    ];
    const VALUES = [
      { id: "v-am", name: "AMERICAS", levelId: REGION_LVL, parentId: null },
      { id: "v-us", name: "United States", levelId: COUNTRY_LVL, parentId: "v-am" },
    ];
    const payload = {
      locationSelections: { [COUNTRY_LVL]: ["United States"] },
      userRoles: ["Partner Seller"],
    };
    const result = translateAudiencePayload(
      payload,
      builderLevels,
      VALUES,
      ROLES,
    ) as {
      locationSelections: Record<string, string[]>;
      userRoles: string[];
    };
    expect(result.userRoles).toEqual([PARTNER_SELLER_ID]);
    expect(result.locationSelections[REGION_LVL]).toEqual(["AMERICAS"]);
    expect(result.locationSelections[COUNTRY_LVL]).toEqual(["United States"]);
  });
});

describe("flattenLocationValuesForAudience", () => {
  it("walks a nested tree depth-first and emits id/name/levelId/parentId for every node", () => {
    const tree = [
      {
        id: "r1",
        name: "AMERICAS",
        levelId: "lvl-region",
        parentId: null,
        children: [
          {
            id: "c1",
            name: "United States",
            levelId: "lvl-country",
            parentId: "r1",
            children: [
              {
                id: "s1",
                name: "California",
                levelId: "lvl-state",
                parentId: "c1",
                children: [],
              },
            ],
          },
        ],
      },
    ];
    const flat = flattenLocationValuesForAudience(tree);
    expect(flat).toEqual([
      { id: "r1", name: "AMERICAS", levelId: "lvl-region", parentId: null },
      { id: "c1", name: "United States", levelId: "lvl-country", parentId: "r1" },
      { id: "s1", name: "California", levelId: "lvl-state", parentId: "c1" },
    ]);
  });

  it("handles empty input", () => {
    expect(flattenLocationValuesForAudience([])).toEqual([]);
  });
});

// BUG-071 follow-up: the AI's UPDATE_BUDGET dispatches need both a name→UUID
// rekey and an auto-derived per-currency parent total. Both happen on the
// frontend so the user sees their per-location budget actually fill in.
describe("translateBudgetPayload", () => {
  // Use real-shaped UUID strings — looksLikeUuid requires 36-char format, so
  // the test fixtures must match what the production hierarchy actually carries.
  const AMER_ID = "11111111-1111-1111-1111-111111111111";
  const EMEAR_ID = "22222222-2222-2222-2222-222222222222";
  const USA_ID = "33333333-3333-3333-3333-333333333333";
  const CANADA_ID = "44444444-4444-4444-4444-444444444444";
  const UK_ID = "55555555-5555-5555-5555-555555555555";

  const americas = { id: AMER_ID, name: "AMERICAS", parentId: null };
  const emear = { id: EMEAR_ID, name: "EMEAR", parentId: null };
  const usa = { id: USA_ID, name: "United States", parentId: AMER_ID };
  const canada = { id: CANADA_ID, name: "Canada", parentId: AMER_ID };
  const uk = { id: UK_ID, name: "United Kingdom", parentId: EMEAR_ID };
  const VALUES = [americas, emear, usa, canada, uk];

  it("passes through a payload with no locationBudgets", () => {
    const payload = {
      selectedCurrencies: ["cash"],
      globalBudgets: { cash: "500000" },
      budgetMode: "global",
    };
    const result = translateBudgetPayload(payload, VALUES);
    expect(result).toBe(payload);
  });

  it("passes through when all locationBudgets keys are already UUIDs and globalBudgets is set", () => {
    const payload = {
      selectedCurrencies: ["cash"],
      budgetMode: "per-location",
      globalBudgets: { cash: "200000" },
      locationBudgets: {
        cash: { [AMER_ID]: "200000", [USA_ID]: "100000" },
      },
    };
    const result = translateBudgetPayload(payload, VALUES);
    expect(result).toBe(payload);
  });

  it("re-keys name-shaped locationBudgets to UUID keys (case-insensitive)", () => {
    const payload = {
      selectedCurrencies: ["cash"],
      budgetMode: "per-location",
      globalBudgets: { cash: "200000" },
      locationBudgets: {
        cash: {
          AMERICAS: "200000",
          "united states": "100000",
          Canada: "100000",
        },
      },
    };
    const result = translateBudgetPayload(payload, VALUES) as {
      locationBudgets: Record<string, Record<string, string>>;
    };
    expect(result.locationBudgets).toEqual({
      cash: {
        [AMER_ID]: "200000",
        [USA_ID]: "100000",
        [CANADA_ID]: "100000",
      },
    });
  });

  it("drops unresolvable name keys silently rather than poisoning state", () => {
    const payload = {
      budgetMode: "per-location",
      locationBudgets: {
        cash: {
          AMERICAS: "200000",
          Atlantis: "999999", // not in the hierarchy
        },
      },
    };
    const result = translateBudgetPayload(payload, VALUES) as {
      locationBudgets: Record<string, Record<string, string>>;
    };
    expect(result.locationBudgets.cash).toEqual({ [AMER_ID]: "200000" });
  });

  it("derives globalBudgets[currencyId] from the sum of top-level entries when missing in per-location mode", () => {
    const payload = {
      selectedCurrencies: ["cash"],
      budgetMode: "per-location",
      // globalBudgets intentionally omitted — the regression case
      locationBudgets: {
        cash: {
          [AMER_ID]: "200000",
          [EMEAR_ID]: "150000",
          [USA_ID]: "100000", // child — must NOT contribute to globalBudgets sum
        },
      },
    };
    const result = translateBudgetPayload(payload, VALUES) as {
      globalBudgets: Record<string, string>;
    };
    expect(result.globalBudgets.cash).toBe("350000");
  });

  it("does NOT overwrite an existing globalBudgets[currencyId] the AI provided", () => {
    const payload = {
      budgetMode: "per-location",
      globalBudgets: { cash: "500000" }, // AI explicitly set a different parent total
      locationBudgets: {
        cash: { [AMER_ID]: "200000" },
      },
    };
    const result = translateBudgetPayload(payload, VALUES) as {
      globalBudgets: Record<string, string>;
    };
    // We trust what the AI provided rather than overwriting with our derived sum.
    expect(result.globalBudgets.cash).toBe("500000");
  });

  it("does NOT derive globalBudgets when budgetMode is not 'per-location'", () => {
    const payload = {
      budgetMode: "per-region",
      regionBudgets: { cash: { AMERICAS: "200000" } },
      locationBudgets: { cash: { [AMER_ID]: "200000" } },
    };
    const result = translateBudgetPayload(payload, VALUES);
    // budgetMode says per-region; globalBudgets stays untouched (not derived)
    expect((result as { globalBudgets?: Record<string, string> }).globalBudgets)
      .toBeUndefined();
  });

  it("treats a blank globalBudgets[currencyId] as missing and derives it", () => {
    const payload = {
      budgetMode: "per-location",
      globalBudgets: { cash: "   " }, // whitespace-only counts as missing
      locationBudgets: {
        cash: { [AMER_ID]: "200000", [EMEAR_ID]: "150000" },
      },
    };
    const result = translateBudgetPayload(payload, VALUES) as {
      globalBudgets: Record<string, string>;
    };
    expect(result.globalBudgets.cash).toBe("350000");
  });

  it("passes through unchanged when locationValues is empty (hierarchy not loaded)", () => {
    const payload = {
      budgetMode: "per-location",
      locationBudgets: { cash: { AMERICAS: "200000" } },
    };
    const result = translateBudgetPayload(payload, []);
    expect(result).toBe(payload);
  });

  it("preserves selectedCurrencies and other unrelated fields untouched", () => {
    const payload = {
      selectedCurrencies: ["cash", "points"],
      budgetMode: "per-location",
      maxPerPartner: "5000",
      locationBudgets: { cash: { AMERICAS: "200000" } },
    };
    const result = translateBudgetPayload(payload, VALUES);
    expect(result).toMatchObject({
      selectedCurrencies: ["cash", "points"],
      maxPerPartner: "5000",
      budgetMode: "per-location",
    });
  });
});

describe("flattenLocationValuesForBudget", () => {
  it("walks a nested tree depth-first and emits id/name/parentId for every node", () => {
    const tree = [
      {
        id: "r1",
        name: "AMERICAS",
        parentId: null,
        children: [
          {
            id: "c1",
            name: "United States",
            parentId: "r1",
            children: [],
          },
          { id: "c2", name: "Canada", parentId: "r1", children: [] },
        ],
      },
      { id: "r2", name: "EMEAR", parentId: null, children: [] },
    ];
    const flat = flattenLocationValuesForBudget(tree);
    expect(flat).toEqual([
      { id: "r1", name: "AMERICAS", parentId: null },
      { id: "c1", name: "United States", parentId: "r1" },
      { id: "c2", name: "Canada", parentId: "r1" },
      { id: "r2", name: "EMEAR", parentId: null },
    ]);
  });

  it("handles empty input", () => {
    expect(flattenLocationValuesForBudget([])).toEqual([]);
  });
});
