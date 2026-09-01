import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ManualSummaryPanel } from "@/components/incentive-builder/ManualSummaryPanel";
import { BuilderProvider } from "@/contexts/BuilderContext";
import {
  initialBuilderState,
  type BuilderState,
} from "@/types/builder-state.types";

// Stub the role-options query to a fixed list so we can drive the
// UUID → display-name resolution deterministically.
vi.mock("@/hooks/useBuilderConfig", () => ({
  useExternalRoles: () => ({
    data: [
      { value: "uuid-partner-admin", label: "Partner Admin" },
      { value: "uuid-partner-seller", label: "Partner Seller" },
    ],
  }),
}));

function renderWithProviders(state: BuilderState) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <BuilderProvider state={state} dispatch={() => {}}>
        <ManualSummaryPanel />
      </BuilderProvider>
    </QueryClientProvider>,
  );
}

function stateWithUserRoles(userRoles: string[]): BuilderState {
  return {
    ...initialBuilderState,
    audience: { ...initialBuilderState.audience, userRoles },
  };
}

// BUG-082 / BUG-020 frontend follow-up. The seeded ROLE audience rules carry
// ClientRole.id UUIDs; when a Client Admin opens an existing incentive in
// the builder, `audience.userRoles` is hydrated with those UUIDs. The
// summary panel must resolve them to display names — printing raw UUIDs is
// the visible regression that prompted this fix.
describe("ManualSummaryPanel — Roles row (BUG-082)", () => {
  it("renders display names when userRoles holds ClientRole.id UUIDs", () => {
    renderWithProviders(
      stateWithUserRoles(["uuid-partner-admin", "uuid-partner-seller"]),
    );
    expect(screen.getByText("Roles:")).toBeInTheDocument();
    expect(
      screen.getByText("Partner Admin, Partner Seller"),
    ).toBeInTheDocument();
    // The bug was that UUIDs leaked into the visible text — assert they don't.
    expect(screen.queryByText(/uuid-partner-admin/)).toBeNull();
    expect(screen.queryByText(/uuid-partner-seller/)).toBeNull();
  });

  it("falls back to the raw value when an id can't be resolved (legacy/in-flight name-format data)", () => {
    // Defensive: until every writer is on the UUID path, a stale name-format
    // row should still render its label legibly (not as a broken empty cell).
    renderWithProviders(stateWithUserRoles(["Partner Seller"]));
    expect(screen.getByText("Partner Seller")).toBeInTheDocument();
  });

  it("omits the Roles row when no roles are selected", () => {
    renderWithProviders(stateWithUserRoles([]));
    expect(screen.queryByText("Roles:")).toBeNull();
  });
});
