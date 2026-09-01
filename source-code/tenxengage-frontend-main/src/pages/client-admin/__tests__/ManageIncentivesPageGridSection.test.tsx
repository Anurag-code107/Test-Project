import { describe, it, expect, vi, beforeEach } from "vitest";
import { useState } from "react";
import { render, fireEvent, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

// Render-counting spy injected through the ManagedIncentiveCard mock — every
// time GridSection's render function runs, it walks the cards and the mock
// fires once per card. With React.memo on GridSection and stable props from
// the parent, a parent re-render must NOT re-execute GridSection, and so the
// spy must NOT fire again.
const cardRenderSpy = vi.fn();

vi.mock("@/components/manage-incentives/ManagedIncentiveCard", () => ({
  ManagedIncentiveCard: ({ incentive }: { incentive: { id: string } }) => {
    cardRenderSpy(incentive.id);
    return <div data-testid={`card-${incentive.id}`} />;
  },
}));

vi.mock("@/components/PermissionGate", () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

import { GridSection } from "@/pages/client-admin/ManageIncentivesPage";
import type {
  IncentiveResponse,
  IncentiveStatus,
} from "@/types/incentive.types";

function makeIncentive(id: string): IncentiveResponse {
  return {
    id,
    name: `Incentive ${id}`,
    incentiveType: "SALES",
    status: "ACTIVE",
    createdByName: "Test",
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
  };
}

const noop = vi.fn();
const noopId = vi.fn<(id: string) => void>();
const noopStatus = vi.fn<(id: string, next: IncentiveStatus) => void>();

interface HostProps {
  incentives: IncentiveResponse[];
}

function ToggleHost({ incentives }: HostProps) {
  // Simulates the page-level state that previously cascaded into the grid
  // (e.g. drawerIncentiveId on ManageIncentivesPage). The host re-renders
  // when this toggles, but GridSection's props are referentially stable.
  const [, setTick] = useState(0);
  return (
    <>
      <button
        data-testid="toggle"
        onClick={() => setTick((t) => t + 1)}
        type="button"
      />
      <GridSection
        type="SALES"
        incentives={incentives}
        onStatusChange={noopStatus}
        onDelete={noopId}
        onCreateNew={noop}
        onEdit={noopId}
        onSubmitForApproval={noopId}
        onResubmitForApproval={noopId}
        onCardClick={noopId}
      />
    </>
  );
}

describe("GridSection — BUG-073 memo bailout on stable props", () => {
  beforeEach(() => {
    cardRenderSpy.mockClear();
  });

  it("does not re-render the card grid when the host toggles unrelated state", () => {
    // Stable references for both renders — same array, same handlers.
    const incentives = [makeIncentive("a"), makeIncentive("b"), makeIncentive("c")];

    const { getByTestId } = render(
      <MemoryRouter>
        <ToggleHost incentives={incentives} />
      </MemoryRouter>,
    );

    // Initial render hits the card mock once per incentive.
    expect(cardRenderSpy).toHaveBeenCalledTimes(incentives.length);

    cardRenderSpy.mockClear();

    // Toggle host state twice. With memo on GridSection and stable props,
    // GridSection should bail out — cards must NOT re-render.
    act(() => {
      fireEvent.click(getByTestId("toggle"));
      fireEvent.click(getByTestId("toggle"));
    });

    expect(cardRenderSpy).not.toHaveBeenCalled();
  });

  it("re-renders the card grid when the incentives array reference changes", () => {
    const initial = [makeIncentive("a"), makeIncentive("b")];

    const { rerender } = render(
      <MemoryRouter>
        <ToggleHost incentives={initial} />
      </MemoryRouter>,
    );
    cardRenderSpy.mockClear();

    const next = [makeIncentive("a"), makeIncentive("b"), makeIncentive("c")];
    rerender(
      <MemoryRouter>
        <ToggleHost incentives={next} />
      </MemoryRouter>,
    );

    // New array reference → memo doesn't bail; cards reconcile.
    expect(cardRenderSpy).toHaveBeenCalled();
  });
});
