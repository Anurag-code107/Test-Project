import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { JourneyIncentiveCard } from "@/components/journey/JourneyIncentiveCard";
import type {
  IncentiveResponse,
  JourneyStageSummary,
} from "@/types/incentive.types";

// Stub out PartnerIncentiveCard so the test can assert which incentive it
// receives without pulling in the full embedded card's styling / deps.
vi.mock("@/components/view-incentives/PartnerIncentiveCard", () => ({
  PartnerIncentiveCard: ({ incentive }: { incentive: IncentiveResponse }) => (
    <div data-testid="partner-incentive-card" data-incentive-name={incentive.name}>
      {incentive.name}
    </div>
  ),
}));

// Always-allow PermissionGate — component just renders children.
vi.mock("@/components/PermissionGate", () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function stageSummary(
  overrides: Partial<JourneyStageSummary> = {},
): JourneyStageSummary {
  return {
    sortOrder: 0,
    incentiveType: "SALES",
    incentiveName: "APJ Q2 Stage",
    incentiveStatus: "ACTIVE",
    userCompleted: false,
    ...overrides,
  };
}

function parentJourney(
  stages: JourneyStageSummary[],
  overrides: Partial<IncentiveResponse> = {},
): IncentiveResponse {
  return {
    id: "journey-id",
    name: "Parent Journey SPIF",
    incentiveType: "JOURNEY",
    status: "ACTIVE",
    createdByName: "Test User",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    journeyStages: stages,
    ...overrides,
  };
}

describe("JourneyIncentiveCard — BUG-075 manage stage shape", () => {
  it("renders a manage-shaped stage card (no reward banner, no progress, creator visible) when variant='manage'", () => {
    const stage = stageSummary({
      sortOrder: 0,
      incentiveType: "TRAINING",
      incentiveName: "Onboarding 101",
    });
    const childInc: IncentiveResponse = {
      id: "child-id",
      name: "Onboarding 101",
      description: "Complete the onboarding modules.",
      incentiveType: "TRAINING",
      status: "ACTIVE",
      createdByName: "Alice Admin",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      // Participant-facing fields that should NOT render in manage mode.
      rewardCurrencies: ["cash"],
      rewardMessage: "Earn up to $500",
      partnerProgressCompleted: 1,
      partnerProgressLabel: "courses",
      trainingRequiredCount: 3,
    };
    const journey = parentJourney([stage]);

    render(
      <JourneyIncentiveCard
        incentive={journey}
        variant="manage"
        allIncentives={[journey, childInc]}
      />,
    );

    // The participant-facing PartnerIncentiveCard must not be rendered for the
    // manage variant — that component owns the reward banner and progress bar.
    expect(
      screen.queryByTestId("partner-incentive-card"),
    ).not.toBeInTheDocument();

    // The reward banner copy from PartnerIncentiveCard must not appear anywhere.
    expect(screen.queryByText(/Earn up to/i)).not.toBeInTheDocument();

    // Creator must surface the way every other manage card does.
    expect(screen.getAllByText("Alice Admin").length).toBeGreaterThan(0);
  });
});

describe("JourneyIncentiveCard — BUG-019 stage fallback", () => {
  it("renders the stage's own incentive when it's present in allIncentives", () => {
    const stage = stageSummary({
      sortOrder: 0,
      incentiveName: "Eligible Stage",
    });
    const childInc: IncentiveResponse = {
      id: "child-id",
      name: "Eligible Stage",
      incentiveType: "SALES",
      status: "ACTIVE",
      createdByName: "Test User",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };
    const journey = parentJourney([stage]);

    render(
      <JourneyIncentiveCard
        incentive={journey}
        variant="view"
        allIncentives={[journey, childInc]}
      />,
    );

    const cards = screen.getAllByTestId("partner-incentive-card");
    // The active stage slot should show the child incentive by name,
    // never the parent Journey.
    expect(cards.some((el) => el.textContent === "Eligible Stage")).toBe(true);
    expect(
      cards.every((el) => el.textContent !== "Parent Journey SPIF"),
    ).toBe(true);
  });

  it("renders the 'Stage unavailable' placeholder when the stage isn't in allIncentives (never falls back to parent)", () => {
    // The scenario BUG-019 describes: partner has the Journey in their list,
    // but the stage's full IncentiveResponse is NOT in allIncentives (filtered
    // out upstream). The pre-fix code rendered the parent in the stage slot.
    const stage = stageSummary({
      sortOrder: 0,
      incentiveName: "APJ Q2 Partner Activity Advantage",
    });
    const journey = parentJourney([stage]);

    render(
      <JourneyIncentiveCard
        incentive={journey}
        variant="view"
        allIncentives={[journey]} // no child match
      />,
    );

    // Placeholder is present with the stage's own name.
    const placeholders = screen.getAllByTestId("stage-unavailable-placeholder");
    expect(placeholders.length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("APJ Q2 Partner Activity Advantage").length,
    ).toBeGreaterThan(0);

    // And crucially — the parent Journey is NOT rendered inside a
    // PartnerIncentiveCard for this stage slot.
    const cards = screen.queryAllByTestId("partner-incentive-card");
    expect(
      cards.some((el) => el.textContent === "Parent Journey SPIF"),
    ).toBe(false);
  });
});
