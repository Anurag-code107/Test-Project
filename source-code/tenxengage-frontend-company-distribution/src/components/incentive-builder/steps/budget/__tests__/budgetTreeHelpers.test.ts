import { describe, it, expect } from "vitest";
import {
  areAllRootsFilled,
  buildBudgetTree,
  computeEffectiveAllocations,
  computeSiblingResidual,
  findOvershoots,
  flattenTree,
  parseAmount,
  summarizeAllocation,
} from "../budgetTreeHelpers";
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

// Eligibility: Americas + (US, Brazil) + California; EMEA + Germany; APJ root only.
const ELIGIBILITY: Record<string, string[]> = {
  [REGION_LEVEL.id]: ["Americas", "EMEA", "APJ"],
  [COUNTRY_LEVEL.id]: ["United States", "Brazil", "Germany"],
  [STATE_LEVEL.id]: ["California"],
};

describe("parseAmount", () => {
  it("returns null for blank, undefined, NaN, or negative inputs", () => {
    expect(parseAmount(undefined)).toBeNull();
    expect(parseAmount(null)).toBeNull();
    expect(parseAmount("")).toBeNull();
    expect(parseAmount("   ")).toBeNull();
    expect(parseAmount("not-a-number")).toBeNull();
    expect(parseAmount("-10")).toBeNull();
    expect(parseAmount("Infinity")).toBeNull();
  });

  it("parses non-negative finite numbers", () => {
    expect(parseAmount("0")).toBe(0);
    expect(parseAmount("100")).toBe(100);
    expect(parseAmount(" 250.5 ")).toBe(250.5);
  });
});

describe("buildBudgetTree", () => {
  it("returns empty when hierarchy is undefined", () => {
    expect(buildBudgetTree(undefined, ELIGIBILITY)).toEqual([]);
  });

  it("returns empty when no depth-0 selections", () => {
    expect(buildBudgetTree(HIERARCHY, {})).toEqual([]);
  });

  it("filters every depth to the user's eligibility scope", () => {
    const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);
    expect(tree).toHaveLength(3);

    const americas = tree.find((n) => n.locationValueName === "Americas");
    expect(americas).toBeDefined();
    expect(americas!.children.map((c) => c.locationValueName)).toEqual([
      "United States",
      "Brazil",
    ]);
    const us = americas!.children.find(
      (c) => c.locationValueName === "United States",
    );
    // Texas was not picked at the State level — it must not appear, only California.
    expect(us!.children.map((c) => c.locationValueName)).toEqual([
      "California",
    ]);

    const apj = tree.find((n) => n.locationValueName === "APJ");
    expect(apj).toBeDefined();
    // APJ has no deeper picks → leaf in budget tree.
    expect(apj!.children).toEqual([]);
  });

  it("populates levelName from the hierarchy's level definitions", () => {
    const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);
    const americas = tree.find((n) => n.locationValueName === "Americas")!;
    expect(americas.levelName).toBe("Region");
    expect(americas.children[0]?.levelName).toBe("Country");
  });
});

describe("computeSiblingResidual", () => {
  const nodes = [
    {
      locationValueId: "a",
      locationValueName: "A",
      locationLevelId: "lvl",
      levelName: "Lvl",
      children: [],
    },
    {
      locationValueId: "b",
      locationValueName: "B",
      locationLevelId: "lvl",
      levelName: "Lvl",
      children: [],
    },
    {
      locationValueId: "c",
      locationValueName: "C",
      locationLevelId: "lvl",
      levelName: "Lvl",
      children: [],
    },
  ];

  it("splits the residual evenly across blank siblings", () => {
    // parent=100, A=60, B=blank, C=blank → A=60, B=20, C=20.
    const out = computeSiblingResidual(nodes, { a: "60" }, 100);
    expect(out).toEqual({ a: 60, b: 20, c: 20 });
  });

  it("returns 0 for blanks when filled siblings already exhaust the parent", () => {
    const out = computeSiblingResidual(nodes, { a: "100" }, 100);
    expect(out).toEqual({ a: 100, b: 0, c: 0 });
  });

  it("returns 0 for blanks when filled siblings overshoot (no negative residuals)", () => {
    const out = computeSiblingResidual(nodes, { a: "120" }, 100);
    expect(out).toEqual({ a: 120, b: 0, c: 0 });
  });

  it("falls back to typed-or-zero when parentTotal is null", () => {
    const out = computeSiblingResidual(nodes, { a: "60" }, null);
    expect(out).toEqual({ a: 60, b: 0, c: 0 });
  });

  // BUG-068: residual must split into integers (no fractional cents) and the
  // sum across all siblings must equal the parent total exactly.
  it("distributes a non-evenly-divisible residual as integers summing to parent", () => {
    const out = computeSiblingResidual(nodes, {}, 100);
    expect(out).toEqual({ a: 34, b: 33, c: 33 });
    expect(out.a! + out.b! + out.c!).toBe(100);
  });

  it("hands the +1 leftover to the first blanks in tree order when one sibling is typed", () => {
    // parent=100, A typed=33, B and C blank → residual=67 across 2 blanks =
    // floor(67/2)=33 with 1 extra unit going to B (first blank in tree order).
    const out = computeSiblingResidual(nodes, { a: "33" }, 100);
    expect(out).toEqual({ a: 33, b: 34, c: 33 });
    expect(out.a! + out.b! + out.c!).toBe(100);
  });

  it("handles a tiny parent total without producing negative or fractional values", () => {
    // parent=1, all blank → first blank gets 1, others 0.
    const out = computeSiblingResidual(nodes, {}, 1);
    expect(out).toEqual({ a: 1, b: 0, c: 0 });
    expect(out.a! + out.b! + out.c!).toBe(1);
  });
});

describe("findOvershoots", () => {
  const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);

  it("returns empty when nothing is filled", () => {
    expect(findOvershoots(tree, {}, 1_000_000)).toEqual([]);
  });

  it("flags the top level when filled roots exceed the global total", () => {
    // Americas=600k + EMEA=500k = 1.1M > 1M global.
    const overshoots = findOvershoots(
      tree,
      { "r-am": "600000", "r-em": "500000" },
      1_000_000,
    );
    expect(overshoots).toHaveLength(1);
    expect(overshoots[0]?.parentLocationValueId).toBe("__GLOBAL__");
    expect(overshoots[0]?.childrenSum).toBe(1_100_000);
    // Names list only filled roots — APJ stays out because the user didn't fill it.
    expect(overshoots[0]?.filledChildNames).toEqual(["Americas", "EMEA"]);
  });

  it("flags an intermediate node when filled children exceed the parent", () => {
    // Americas = 100, US = 80, Brazil = 30 → US+Brazil = 110 > 100.
    const overshoots = findOvershoots(
      tree,
      { "r-am": "100", "c-us": "80", "c-br": "30" },
      1_000_000,
    );
    const americasOvershoot = overshoots.find(
      (o) => o.parentLocationValueId === "r-am",
    );
    expect(americasOvershoot).toBeDefined();
    expect(americasOvershoot!.parentAmount).toBe(100);
    expect(americasOvershoot!.childrenSum).toBe(110);
    expect(americasOvershoot!.filledChildNames).toEqual([
      "United States",
      "Brazil",
    ]);
  });

  it("does NOT flag underfill — auto-fill handles the residual", () => {
    // Americas = 100, US = 60, rest blank → no overshoot (Brazil auto-fills 40).
    const overshoots = findOvershoots(
      tree,
      { "r-am": "100", "c-us": "60" },
      1_000_000,
    );
    expect(
      overshoots.find((o) => o.parentLocationValueId === "r-am"),
    ).toBeUndefined();
  });
});

describe("computeEffectiveAllocations", () => {
  const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);

  it("walks top-down, applying parent residual at each depth", () => {
    // Global=1M. Americas typed=600k, EMEA blank, APJ blank → EMEA=200k,
    // APJ=200k. Inside Americas: US=400k, Brazil blank → Brazil=200k. Inside
    // US: California blank → California=400k.
    const values = { "r-am": "600000", "c-us": "400000" };
    const out = computeEffectiveAllocations(tree, values, 1_000_000);
    expect(out["r-am"]).toBe(600_000);
    expect(out["r-em"]).toBe(200_000);
    expect(out["r-ap"]).toBe(200_000);
    expect(out["c-us"]).toBe(400_000);
    expect(out["c-br"]).toBe(200_000);
    expect(out["s-ca"]).toBe(400_000);
  });

  it("handles fully-typed trees by passing values through verbatim", () => {
    const values = {
      "r-am": "500000",
      "r-em": "300000",
      "r-ap": "200000",
      "c-us": "300000",
      "c-br": "200000",
      "s-ca": "300000",
    };
    const out = computeEffectiveAllocations(tree, values, 1_000_000);
    expect(out["r-am"]).toBe(500_000);
    expect(out["s-ca"]).toBe(300_000);
  });

  it("returns 0s when both global total and user values are missing", () => {
    const out = computeEffectiveAllocations(tree, {}, null);
    for (const node of flattenTree(tree)) {
      expect(out[node.locationValueId]).toBe(0);
    }
  });

  // BUG-068: integer distribution must propagate cleanly across multiple
  // levels. Build an ad-hoc tree where every level has siblings so the
  // residual splits at each depth.
  it("propagates integer-only residuals through three levels of recursion", () => {
    // Tree:
    //   root → [a, b, c]
    //     a → [a1, a2, a3]
    //   parent=$100, user types only a=$40, leaves b/c blank, leaves a's
    //   children blank. Expected:
    //     b/c residual = 60 split into [30, 30].
    //     a residual = 40 across [a1, a2, a3] → floor(40/3)=13, remainder=1
    //     → [14, 13, 13]; sum back to 40. None fractional.
    const buildLeaf = (id: string, name: string) => ({
      locationValueId: id,
      locationValueName: name,
      locationLevelId: "lvl-3",
      levelName: "L3",
      children: [],
    });
    const localTree = [
      {
        locationValueId: "a",
        locationValueName: "A",
        locationLevelId: "lvl-2",
        levelName: "L2",
        children: [
          buildLeaf("a1", "A1"),
          buildLeaf("a2", "A2"),
          buildLeaf("a3", "A3"),
        ],
      },
      {
        locationValueId: "b",
        locationValueName: "B",
        locationLevelId: "lvl-2",
        levelName: "L2",
        children: [],
      },
      {
        locationValueId: "c",
        locationValueName: "C",
        locationLevelId: "lvl-2",
        levelName: "L2",
        children: [],
      },
    ];
    const out = computeEffectiveAllocations(localTree, { a: "40" }, 100);
    expect(out.a).toBe(40);
    expect(out.b).toBe(30);
    expect(out.c).toBe(30);
    expect(out.a1).toBe(14);
    expect(out.a2).toBe(13);
    expect(out.a3).toBe(13);
    // Every child sums exactly back to its parent at every depth.
    expect(out.b! + out.c! + out.a!).toBe(100);
    expect(out.a1! + out.a2! + out.a3!).toBe(out.a);
    // No fractional values anywhere.
    for (const v of Object.values(out)) {
      expect(Number.isInteger(v)).toBe(true);
    }
  });
});

describe("summarizeAllocation", () => {
  const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);

  it("reports all-blank state with the full residual auto-distributed", () => {
    const summary = summarizeAllocation(tree, {}, 1_000_000);
    expect(summary.allocatedSum).toBe(0);
    expect(summary.residual).toBe(1_000_000);
    expect(summary.blankCount).toBe(3);
    expect(summary.blankNames).toEqual(["Americas", "EMEA", "APJ"]);
    expect(summary.isFullyAllocated).toBe(false);
    expect(summary.hasOvershoot).toBe(false);
    expect(summary.parentTotal).toBe(1_000_000);
  });

  it("reports partially-typed state", () => {
    const summary = summarizeAllocation(
      tree,
      { "r-am": "600000" },
      1_000_000,
    );
    expect(summary.allocatedSum).toBe(600_000);
    expect(summary.residual).toBe(400_000);
    expect(summary.blankCount).toBe(2);
    expect(summary.blankNames).toEqual(["EMEA", "APJ"]);
    expect(summary.isFullyAllocated).toBe(false);
    expect(summary.hasOvershoot).toBe(false);
  });

  it("reports fully-allocated state when every child is typed and sums match parent", () => {
    const summary = summarizeAllocation(
      tree,
      { "r-am": "500000", "r-em": "300000", "r-ap": "200000" },
      1_000_000,
    );
    expect(summary.allocatedSum).toBe(1_000_000);
    expect(summary.residual).toBe(0);
    expect(summary.blankCount).toBe(0);
    expect(summary.blankNames).toEqual([]);
    expect(summary.isFullyAllocated).toBe(true);
    expect(summary.hasOvershoot).toBe(false);
  });

  it("preserves tree order in blankNames so the indicator's 'first name' is stable", () => {
    // EMEA + APJ typed → only Americas remains blank.
    const summary = summarizeAllocation(
      tree,
      { "r-em": "300000", "r-ap": "200000" },
      1_000_000,
    );
    expect(summary.blankNames).toEqual(["Americas"]);
    expect(summary.blankCount).toBe(1);
  });

  it("flags overshoot when typed children exceed parent", () => {
    const summary = summarizeAllocation(
      tree,
      { "r-am": "600000", "r-em": "500000" },
      1_000_000,
    );
    expect(summary.allocatedSum).toBe(1_100_000);
    expect(summary.residual).toBe(0);
    expect(summary.hasOvershoot).toBe(true);
    expect(summary.isFullyAllocated).toBe(false);
  });

  it("returns a well-formed summary when parentTotal is null (caller hides indicator)", () => {
    const summary = summarizeAllocation(tree, {}, null);
    expect(summary.parentTotal).toBe(0);
    expect(summary.residual).toBe(0);
    expect(summary.hasOvershoot).toBe(false);
    expect(summary.isFullyAllocated).toBe(false);
  });
});

describe("areAllRootsFilled", () => {
  const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);

  it("returns false for an empty tree (nothing to allocate against)", () => {
    expect(areAllRootsFilled([], { anything: "100" })).toBe(false);
  });

  it("returns false when no roots have been typed", () => {
    expect(areAllRootsFilled(tree, {})).toBe(false);
  });

  it("returns false when only some roots are typed (top level is required)", () => {
    expect(
      areAllRootsFilled(tree, { "r-am": "500000", "r-em": "300000" }),
    ).toBe(false);
  });

  it("returns true when every root has a value, regardless of child fill state", () => {
    // All three roots typed, every child blank — children auto-fill so the
    // root requirement alone is sufficient for the gate.
    expect(
      areAllRootsFilled(tree, {
        "r-am": "500000",
        "r-em": "300000",
        "r-ap": "200000",
      }),
    ).toBe(true);
  });

  it("treats blank-string roots as unfilled", () => {
    expect(
      areAllRootsFilled(tree, {
        "r-am": "500000",
        "r-em": "   ",
        "r-ap": "200000",
      }),
    ).toBe(false);
  });
});

describe("flattenTree", () => {
  it("returns every node depth-first", () => {
    const tree = buildBudgetTree(HIERARCHY, ELIGIBILITY);
    const ids = flattenTree(tree).map((n) => n.locationValueId);
    expect(ids).toEqual([
      "r-am",
      "c-us",
      "s-ca",
      "c-br",
      "r-em",
      "c-de",
      "r-ap",
    ]);
  });
});
