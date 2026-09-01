import { describe, it, expect, vi } from "vitest";
import { render, screen, act } from "@testing-library/react";
import {
  GuidedTourProvider,
  useGuidedTour,
} from "@/contexts/GuidedTourContext";
import type { GuidedTour } from "@/data/guidedTours";

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

const fakeTour: GuidedTour = {
  id: "test-tour",
  name: "Test tour",
  keywords: ["test"],
  roles: ["CLIENT_ADMIN"],
  steps: [
    {
      targetSelector: "#nowhere",
      title: "Step 1",
      message: "step body",
    },
  ],
};

function TourConsumer() {
  const { isActive, startTour } = useGuidedTour();
  return (
    <div>
      <span data-testid="active">{isActive ? "active" : "inactive"}</span>
      <button onClick={() => startTour(fakeTour)}>start</button>
    </div>
  );
}

describe("GuidedTourContext startTour gate", () => {
  it("activates the tour when guided_tours is enabled for the tenant", () => {
    mockFeatures(["guided_tours"]);

    render(
      <GuidedTourProvider>
        <TourConsumer />
      </GuidedTourProvider>,
    );

    expect(screen.getByTestId("active").textContent).toBe("inactive");
    act(() => {
      screen.getByText("start").click();
    });
    expect(screen.getByTestId("active").textContent).toBe("active");
  });

  it("no-ops startTour when guided_tours is disabled (BUG-051 regression)", () => {
    // Tier downgrade scenario: tenant doesn't have the feature. Calling
    // startTour from any caller (AI assistant input, replay button, etc.)
    // must not activate tour state.
    mockFeatures([]);

    render(
      <GuidedTourProvider>
        <TourConsumer />
      </GuidedTourProvider>,
    );

    expect(screen.getByTestId("active").textContent).toBe("inactive");
    act(() => {
      screen.getByText("start").click();
    });
    expect(screen.getByTestId("active").textContent).toBe("inactive");
  });

  it("no-ops startTour when feature set is empty (fail-closed default)", () => {
    mockFeatures([]);

    render(
      <GuidedTourProvider>
        <TourConsumer />
      </GuidedTourProvider>,
    );

    act(() => {
      screen.getByText("start").click();
    });
    expect(screen.getByTestId("active").textContent).toBe("inactive");
  });
});
