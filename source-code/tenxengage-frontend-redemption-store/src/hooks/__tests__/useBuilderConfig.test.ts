import { describe, it, expect } from "vitest";
import {
  getLocationValuesForLevel,
  mapExternalRolesToOptions,
} from "@/hooks/useBuilderConfig";
import type { LocationValueResponse } from "@/types/location.types";
import type { ClientRole } from "@/types/permission.types";

function makeRole(overrides: Partial<ClientRole>): ClientRole {
  return {
    id: "role-id",
    clientId: "client-id",
    name: "Some Role",
    description: "",
    baseRoleName: null,
    isSystem: false,
    isDefault: false,
    roleType: "EXTERNAL",
    permissions: {},
    createdAt: "",
    updatedAt: "",
    ...overrides,
  };
}

// Three-level fixture: Region → Country → State
// Region:  AMERICAS / EMEAR
// Country: United States (under AMERICAS), Canada (under AMERICAS),
//          Germany (under EMEAR)
// State:   California (under United States), Bavaria (under Germany)

const REGION_LEVEL = "lvl-region";
const COUNTRY_LEVEL = "lvl-country";
const STATE_LEVEL = "lvl-state";

const california: LocationValueResponse = {
  id: "v-california",
  name: "California",
  code: null,
  levelName: "State",
  levelId: STATE_LEVEL,
  parentId: "v-us",
  children: [],
};

const unitedStates: LocationValueResponse = {
  id: "v-us",
  name: "United States",
  code: null,
  levelName: "Country",
  levelId: COUNTRY_LEVEL,
  parentId: "v-amer",
  children: [california],
};

const canada: LocationValueResponse = {
  id: "v-ca",
  name: "Canada",
  code: null,
  levelName: "Country",
  levelId: COUNTRY_LEVEL,
  parentId: "v-amer",
  children: [],
};

const bavaria: LocationValueResponse = {
  id: "v-bavaria",
  name: "Bavaria",
  code: null,
  levelName: "State",
  levelId: STATE_LEVEL,
  parentId: "v-de",
  children: [],
};

const germany: LocationValueResponse = {
  id: "v-de",
  name: "Germany",
  code: null,
  levelName: "Country",
  levelId: COUNTRY_LEVEL,
  parentId: "v-emea",
  children: [bavaria],
};

const americas: LocationValueResponse = {
  id: "v-amer",
  name: "AMERICAS",
  code: null,
  levelName: "Region",
  levelId: REGION_LEVEL,
  parentId: null,
  children: [unitedStates, canada],
};

const emear: LocationValueResponse = {
  id: "v-emea",
  name: "EMEAR",
  code: null,
  levelName: "Region",
  levelId: REGION_LEVEL,
  parentId: null,
  children: [germany],
};

const hierarchy = { tree: [americas, emear] };

describe("getLocationValuesForLevel", () => {
  it("returns [] for null levelId", () => {
    expect(getLocationValuesForLevel(null, hierarchy)).toEqual([]);
  });

  it("returns [] for undefined hierarchy", () => {
    expect(getLocationValuesForLevel(REGION_LEVEL, undefined)).toEqual([]);
  });

  it("returns every value at the top level when no ancestor filter is supplied", () => {
    const result = getLocationValuesForLevel(REGION_LEVEL, hierarchy);
    expect(result.map((v) => v.value)).toEqual(["AMERICAS", "EMEAR"]);
  });

  it("returns every value at a deeper level when no ancestor filter is supplied", () => {
    const result = getLocationValuesForLevel(STATE_LEVEL, hierarchy);
    expect(result.map((v) => v.value).sort()).toEqual(
      ["Bavaria", "California"],
    );
  });

  it("filters by a single ancestor level — Region=AMERICAS returns only AMERICAS countries", () => {
    const result = getLocationValuesForLevel(COUNTRY_LEVEL, hierarchy, {
      [REGION_LEVEL]: ["AMERICAS"],
    });
    expect(result.map((v) => v.value).sort()).toEqual(["Canada", "United States"]);
  });

  it("BUG-031 regression — filters at depth 2 with only the Country filter supplied. Old walker would fail the Country filter at the Region node and return []. Fixed walker passes the Region level through freely because the ancestor map has no Region entry.", () => {
    const result = getLocationValuesForLevel(STATE_LEVEL, hierarchy, {
      [COUNTRY_LEVEL]: ["United States"],
    });
    expect(result.map((v) => v.value)).toEqual(["California"]);
  });

  it("BUG-031 regression — filters at depth 2 with the full ancestor chain supplied (Region + Country) — returns only matching States", () => {
    const result = getLocationValuesForLevel(STATE_LEVEL, hierarchy, {
      [REGION_LEVEL]: ["AMERICAS"],
      [COUNTRY_LEVEL]: ["United States"],
    });
    expect(result.map((v) => v.value)).toEqual(["California"]);
  });

  it("narrows correctly when a Region filter excludes the whole subtree — EMEAR selected but asked for States under United States returns []", () => {
    const result = getLocationValuesForLevel(STATE_LEVEL, hierarchy, {
      [REGION_LEVEL]: ["EMEAR"],
      [COUNTRY_LEVEL]: ["United States"],
    });
    expect(result).toEqual([]);
  });

  it("treats an empty array at an ancestor level as no filter (passes through)", () => {
    const result = getLocationValuesForLevel(STATE_LEVEL, hierarchy, {
      [REGION_LEVEL]: [],
      [COUNTRY_LEVEL]: ["United States"],
    });
    expect(result.map((v) => v.value)).toEqual(["California"]);
  });

  it("ignores a stale ancestor key whose level doesn't exist in the hierarchy", () => {
    const result = getLocationValuesForLevel(COUNTRY_LEVEL, hierarchy, {
      "lvl-ghost": ["something"],
      [REGION_LEVEL]: ["EMEAR"],
    });
    expect(result.map((v) => v.value)).toEqual(["Germany"]);
  });
});

// BUG-082 / BUG-020 frontend follow-up. The User Roles MultiSelect compares
// `selected[]` to `options[].value` exactly; the wire format for ROLE
// audience rules is the role's UUID; therefore the option `value` must be
// the role's id (not the display name) so seeded incentives' UUID-keyed
// `userRoles` round-trip into rendered pills.
describe("mapExternalRolesToOptions (BUG-082)", () => {
  it("filters to EXTERNAL roles and uses ClientRole.id as the option value", () => {
    const roles = [
      makeRole({
        id: "uuid-partner-admin",
        name: "Partner Admin",
        roleType: "EXTERNAL",
      }),
      makeRole({
        id: "uuid-partner-seller",
        name: "Partner Seller",
        roleType: "EXTERNAL",
      }),
      makeRole({
        id: "uuid-client-admin",
        name: "Client Admin",
        roleType: "INTERNAL",
      }),
    ];
    const options = mapExternalRolesToOptions(roles);
    expect(options).toEqual([
      { value: "uuid-partner-admin", label: "Partner Admin" },
      { value: "uuid-partner-seller", label: "Partner Seller" },
    ]);
    // Belt and suspenders — the bug was specifically that `value` was the
    // display name, leaving seeded UUID selections orphaned.
    expect(options.map((o) => o.value)).not.toContain("Partner Admin");
    expect(options.map((o) => o.value)).not.toContain("Partner Seller");
  });

  it("returns an empty list when no EXTERNAL roles are present", () => {
    const roles = [
      makeRole({ id: "uuid-1", name: "Client Admin", roleType: "INTERNAL" }),
    ];
    expect(mapExternalRolesToOptions(roles)).toEqual([]);
  });
});
