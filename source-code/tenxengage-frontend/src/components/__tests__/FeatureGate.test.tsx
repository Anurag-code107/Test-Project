import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { FeatureGate } from "@/components/FeatureGate";

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

describe("FeatureGate", () => {
  it("renders children when single feature enabled", () => {
    mockFeatures(["audit_log"]);

    render(
      <FeatureGate feature="audit_log">
        <span>Visible</span>
      </FeatureGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });

  it("hides children when single feature disabled (fail-closed)", () => {
    mockFeatures([]);

    render(
      <FeatureGate feature="audit_log">
        <span>Hidden</span>
      </FeatureGate>,
    );

    expect(screen.queryByText("Hidden")).toBeNull();
  });

  it("renders fallback when feature disabled", () => {
    mockFeatures([]);

    render(
      <FeatureGate feature="audit_log" fallback={<span>Upgrade</span>}>
        <span>Hidden</span>
      </FeatureGate>,
    );

    expect(screen.getByText("Upgrade")).toBeDefined();
    expect(screen.queryByText("Hidden")).toBeNull();
  });

  it("renders children when any feature matches", () => {
    mockFeatures(["deal_qualifier"]);

    render(
      <FeatureGate any={["audit_log", "deal_qualifier"]}>
        <span>Visible</span>
      </FeatureGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });

  it("renders children when all features match", () => {
    mockFeatures(["audit_log", "deal_qualifier"]);

    render(
      <FeatureGate all={["audit_log", "deal_qualifier"]}>
        <span>Visible</span>
      </FeatureGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });

  it("hides children when only some of `all` features are enabled", () => {
    mockFeatures(["audit_log"]);

    render(
      <FeatureGate all={["audit_log", "deal_qualifier"]}>
        <span>Hidden</span>
      </FeatureGate>,
    );

    expect(screen.queryByText("Hidden")).toBeNull();
  });
});
