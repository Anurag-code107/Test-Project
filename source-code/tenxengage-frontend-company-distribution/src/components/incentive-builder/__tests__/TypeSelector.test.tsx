import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { TypeSelector } from "@/components/incentive-builder/TypeSelector";

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

import { useFeatures } from "@/hooks/useFeatures";

const mockUseFeatures = vi.mocked(useFeatures);

function mockFeatures(features: string[]) {
  const set = new Set(features);
  mockUseFeatures.mockReturnValue({
    has: (key: string) => set.has(key),
    hasAny: (...keys: string[]) => keys.some((k) => set.has(k)),
    hasAll: (...keys: string[]) => keys.every((k) => set.has(k)),
    features: set,
  });
}

describe("TypeSelector journey_incentives gating", () => {
  it("renders the Journey card when journey_incentives is enabled", () => {
    mockFeatures(["journey_incentives"]);

    render(
      <TypeSelector
        onSelect={vi.fn()}
        onEnablement={vi.fn()}
        onBack={vi.fn()}
      />,
    );

    expect(screen.getByText("Journey")).toBeDefined();
    expect(screen.getByText("Multi-step")).toBeDefined();
  });

  it("hides the Journey card and OR divider when journey_incentives is disabled (BUG-052 regression)", () => {
    // The whole "OR ... Journey" lower section should disappear so non-Journey
    // tier tenants don't see a card they cannot use.
    mockFeatures([]);

    render(
      <TypeSelector
        onSelect={vi.fn()}
        onEnablement={vi.fn()}
        onBack={vi.fn()}
      />,
    );

    // The Journey card heading is gone.
    expect(screen.queryByText("Journey")).toBeNull();
    expect(screen.queryByText("Multi-step")).toBeNull();
  });

  it("Sales / Enablement options remain visible regardless of journey_incentives", () => {
    mockFeatures([]);

    render(
      <TypeSelector
        onSelect={vi.fn()}
        onEnablement={vi.fn()}
        onBack={vi.fn()}
      />,
    );

    // Sales and Enablement are not gated by journey_incentives — they always
    // show. The card headings appear as "Sales Incentive" / "Enablement
    // Incentive" in the picker.
    expect(screen.getByText("Sales Incentive")).toBeDefined();
    expect(screen.getByText("Enablement Incentive")).toBeDefined();
  });
});
