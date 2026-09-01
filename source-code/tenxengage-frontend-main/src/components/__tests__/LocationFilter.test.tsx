import { describe, it, expect } from "vitest";
import {
  GLOBAL_VALUE,
  buildLeafCountMap,
  computeVisibleIds,
  flattenVisibleValues,
  getAncestorIds,
  getChildren,
  getValueName,
  getVisibleLevels,
} from "@/components/LocationFilter.helpers";
import type {
  LocationFilterLevel,
  LocationFilterOptionsResponse,
} from "@/types/location.types";

const region: LocationFilterLevel = {
  levelId: "lvl-region",
  levelName: "Region",
  depth: 0,
  values: [
    { id: "r-amer", name: "AMERICAS", code: null, parentId: null },
    { id: "r-emea", name: "EMEAR", code: null, parentId: null },
  ],
};

const country: LocationFilterLevel = {
  levelId: "lvl-country",
  levelName: "Country",
  depth: 1,
  values: [
    { id: "c-ca", name: "Canada", code: null, parentId: "r-amer" },
    { id: "c-us", name: "United States", code: null, parentId: "r-amer" },
    { id: "c-de", name: "Germany", code: null, parentId: "r-emea" },
    { id: "c-uk", name: "United Kingdom", code: null, parentId: "r-emea" },
  ],
};

const city: LocationFilterLevel = {
  levelId: "lvl-city",
  levelName: "City",
  depth: 2,
  values: [
    { id: "ci-to", name: "Toronto", code: null, parentId: "c-ca" },
    { id: "ci-ny", name: "New York", code: null, parentId: "c-us" },
  ],
};

const threeLevel: LocationFilterOptionsResponse = {
  levels: [region, country, city],
};

describe("getVisibleLevels", () => {
  it("returns [] for undefined input", () => {
    expect(getVisibleLevels(undefined)).toEqual([]);
  });

  it("returns all configured levels — regression for BUG-005 which only rendered levels[0]", () => {
    const levels = getVisibleLevels(threeLevel);
    expect(levels.map((l) => l.levelName)).toEqual([
      "Region",
      "Country",
      "City",
    ]);
  });

  it("drops levels with empty values arrays", () => {
    const input: LocationFilterOptionsResponse = {
      levels: [region, { ...country, values: [] }],
    };
    expect(getVisibleLevels(input).map((l) => l.levelName)).toEqual(["Region"]);
  });
});

describe("flattenVisibleValues", () => {
  it("returns [] when filterOptions is undefined", () => {
    expect(flattenVisibleValues(undefined)).toEqual([]);
  });

  it("returns every value across every visible level", () => {
    const flat = flattenVisibleValues(threeLevel);
    expect(flat.map((v) => v.id).sort()).toEqual(
      ["r-amer", "r-emea", "c-ca", "c-us", "c-de", "c-uk", "ci-to", "ci-ny"].sort(),
    );
  });

  it("re-parents orphaned values to null when their parent level is excluded", () => {
    // Region level turned off (useInFilters=false) — country values lose their
    // visible parent and must still render as roots of the visible tree.
    const input: LocationFilterOptionsResponse = {
      levels: [country, city], // Region excluded
    };
    const flat = flattenVisibleValues(input);
    const canada = flat.find((v) => v.id === "c-ca");
    expect(canada?.parentId).toBeNull();
    // But city still points to its country parent, which IS visible.
    const toronto = flat.find((v) => v.id === "ci-to");
    expect(toronto?.parentId).toBe("c-ca");
  });

  it("keeps real parentId when the parent is visible", () => {
    const flat = flattenVisibleValues(threeLevel);
    expect(flat.find((v) => v.id === "c-ca")?.parentId).toBe("r-amer");
    expect(flat.find((v) => v.id === "ci-to")?.parentId).toBe("c-ca");
  });

  it("tags each value with its source levelId for downstream grouping if needed", () => {
    const flat = flattenVisibleValues(threeLevel);
    expect(flat.find((v) => v.id === "c-ca")?.levelId).toBe("lvl-country");
    expect(flat.find((v) => v.id === "ci-to")?.levelId).toBe("lvl-city");
  });
});

describe("getChildren", () => {
  const flat = flattenVisibleValues(threeLevel);

  it("returns root values (parentId === null) when asked with null", () => {
    expect(getChildren(null, flat).map((v) => v.name)).toEqual([
      "AMERICAS",
      "EMEAR",
    ]);
  });

  it("returns only direct children of the given parent, sorted by name", () => {
    expect(getChildren("r-amer", flat).map((v) => v.name)).toEqual([
      "Canada",
      "United States",
    ]);
  });

  it("returns grandchildren via recursive calls (by design, only direct children)", () => {
    // AMERICAS's direct children are countries, NOT cities — grandchildren
    // come from a separate call chain.
    const canadaKids = getChildren("c-ca", flat);
    expect(canadaKids.map((v) => v.name)).toEqual(["Toronto"]);
  });

  it("returns [] when a leaf value is asked for children", () => {
    expect(getChildren("ci-to", flat)).toEqual([]);
  });
});

describe("getAncestorIds", () => {
  const flat = flattenVisibleValues(threeLevel);

  it("returns [] for a root value", () => {
    expect(getAncestorIds("r-amer", flat)).toEqual([]);
  });

  it("returns single ancestor for a mid-level value", () => {
    expect(getAncestorIds("c-ca", flat)).toEqual(["r-amer"]);
  });

  it("returns full ancestor chain for a leaf, deepest-first", () => {
    // Toronto → Canada → AMERICAS
    expect(getAncestorIds("ci-to", flat)).toEqual(["c-ca", "r-amer"]);
  });

  it("returns [] for a nonexistent id without throwing", () => {
    expect(getAncestorIds("ghost", flat)).toEqual([]);
  });
});

describe("getValueName", () => {
  const flat = flattenVisibleValues(threeLevel);

  it("returns the value's display name", () => {
    expect(getValueName("c-ca", flat)).toBe("Canada");
    expect(getValueName("ci-to", flat)).toBe("Toronto");
  });

  it("returns null for an unknown id", () => {
    expect(getValueName("ghost", flat)).toBeNull();
  });
});

describe("GLOBAL_VALUE", () => {
  it("is a stable sentinel distinct from empty string", () => {
    expect(GLOBAL_VALUE).toBe("GLOBAL");
    expect(GLOBAL_VALUE).not.toBe("");
  });
});

describe("buildLeafCountMap", () => {
  const flat = flattenVisibleValues(threeLevel);

  it("counts a leaf as 1", () => {
    const counts = buildLeafCountMap(flat);
    expect(counts.get("ci-to")).toBe(1);
    expect(counts.get("ci-ny")).toBe(1);
  });

  it("sums a node's children to get its count", () => {
    const counts = buildLeafCountMap(flat);
    // Canada has one city (Toronto) → 1 leaf
    expect(counts.get("c-ca")).toBe(1);
    // United States has one city (New York) → 1 leaf
    expect(counts.get("c-us")).toBe(1);
    // AMERICAS has two countries, each with 1 leaf → 2
    expect(counts.get("r-amer")).toBe(2);
  });

  it("counts an internal node with no leaf grandchildren as sum of its children (leaf fallback)", () => {
    // Germany and UK have no city children in the test fixture — so their
    // leaf count is 1 each (they are the deepest visible values on their
    // branch). EMEAR's count = 1 + 1 = 2.
    const counts = buildLeafCountMap(flat);
    expect(counts.get("c-de")).toBe(1);
    expect(counts.get("c-uk")).toBe(1);
    expect(counts.get("r-emea")).toBe(2);
  });
});

describe("computeVisibleIds", () => {
  const flat = flattenVisibleValues(threeLevel);

  it("returns null for an empty or whitespace-only query", () => {
    expect(computeVisibleIds("", flat)).toBeNull();
    expect(computeVisibleIds("   ", flat)).toBeNull();
  });

  it("matches by case-insensitive substring on value name", () => {
    const visible = computeVisibleIds("canad", flat);
    expect(visible).not.toBeNull();
    expect(visible?.has("c-ca")).toBe(true);
  });

  it("includes ancestors of matches so the tree context stays visible", () => {
    const visible = computeVisibleIds("toronto", flat);
    expect(visible?.has("ci-to")).toBe(true);
    expect(visible?.has("c-ca")).toBe(true);
    expect(visible?.has("r-amer")).toBe(true);
  });

  it("does not include unrelated branches", () => {
    const visible = computeVisibleIds("canada", flat);
    expect(visible?.has("r-emea")).toBe(false);
    expect(visible?.has("c-de")).toBe(false);
  });

  it("returns an empty set when nothing matches", () => {
    const visible = computeVisibleIds("xyzzy", flat);
    expect(visible).not.toBeNull();
    expect(visible?.size).toBe(0);
  });
});
