import { describe, it, expect } from "vitest";
import { getNarrowingDescriptor } from "../narrowingDescriptor";
import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

const REGION_LEVEL = {
  id: "level-region",
  name: "Region",
  depth: 0,
  valueCount: 0,
  useInBuilder: true,
  useInFilters: true,
  isRequired: true,
};
const COUNTRY_LEVEL = {
  id: "level-country",
  name: "Country",
  depth: 1,
  valueCount: 0,
  useInBuilder: true,
  useInFilters: true,
  isRequired: false,
};
const STATE_LEVEL = {
  id: "level-state",
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
  levels: [REGION_LEVEL, COUNTRY_LEVEL, STATE_LEVEL],
  tree: [
    loc("r-am", "Americas", REGION_LEVEL.id, null, [
      loc("c-us", "United States", COUNTRY_LEVEL.id, "r-am", [
        loc("s-ca", "California", STATE_LEVEL.id, "c-us"),
        loc("s-tx", "Texas", STATE_LEVEL.id, "c-us"),
      ]),
      loc("c-br", "Brazil", COUNTRY_LEVEL.id, "r-am"),
    ]),
    loc("r-em", "EMEA", REGION_LEVEL.id, null, [
      loc("c-de", "Germany", COUNTRY_LEVEL.id, "r-em"),
    ]),
    loc("r-ap", "APJ", REGION_LEVEL.id, null, []),
  ],
};

describe("getNarrowingDescriptor", () => {
  it("returns null when hierarchy is undefined (loading state)", () => {
    expect(getNarrowingDescriptor("Americas", {}, undefined)).toBeNull();
  });

  it("returns null when only region-level selections exist (no narrowing)", () => {
    const selections = { [REGION_LEVEL.id]: ["Americas", "EMEA"] };
    expect(getNarrowingDescriptor("Americas", selections, HIERARCHY)).toBeNull();
    expect(getNarrowingDescriptor("GLOBAL", selections, HIERARCHY)).toBeNull();
  });

  it("returns deeper picks for the active region only, ordered by depth", () => {
    // User picks Americas + United States + California, plus EMEA + Germany.
    const selections = {
      [REGION_LEVEL.id]: ["Americas", "EMEA"],
      [COUNTRY_LEVEL.id]: ["United States", "Germany"],
      [STATE_LEVEL.id]: ["California"],
    };

    const americas = getNarrowingDescriptor("Americas", selections, HIERARCHY);
    expect(americas).toEqual({
      kind: "region-narrowed",
      deeperPicks: [
        { levelName: "Country", names: ["United States"] },
        { levelName: "State", names: ["California"] },
      ],
    });

    const emea = getNarrowingDescriptor("EMEA", selections, HIERARCHY);
    expect(emea).toEqual({
      kind: "region-narrowed",
      deeperPicks: [{ levelName: "Country", names: ["Germany"] }],
    });

    // APJ has no deeper picks → no chip even though APJ was viewed.
    const apj = getNarrowingDescriptor("APJ", selections, HIERARCHY);
    expect(apj).toBeNull();
  });

  it("GLOBAL view lists only regions that have at least one deeper pick", () => {
    const selections = {
      [REGION_LEVEL.id]: ["Americas", "EMEA", "APJ"],
      [COUNTRY_LEVEL.id]: ["United States", "Germany"],
      [STATE_LEVEL.id]: ["California"],
    };

    expect(getNarrowingDescriptor("GLOBAL", selections, HIERARCHY)).toEqual({
      kind: "global-narrowed",
      narrowedRegions: ["Americas", "EMEA"],
    });
  });

  it("returns null gracefully when viewMode is an unknown region name", () => {
    const selections = {
      [REGION_LEVEL.id]: ["Americas"],
      [COUNTRY_LEVEL.id]: ["United States"],
    };
    expect(getNarrowingDescriptor("Atlantis", selections, HIERARCHY)).toBeNull();
  });
});
