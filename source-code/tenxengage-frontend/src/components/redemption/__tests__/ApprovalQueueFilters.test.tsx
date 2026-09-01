import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApprovalQueueFilters } from "@/components/redemption/ApprovalQueueFilters";
import type { ApprovalQueueFilters as FiltersType } from "@/types/redemption/redemption.types";

// The component calls useTenantCatalogConfig (React Query) only to populate the
// catalog-item dropdown; the currency options are hardcoded. Mock the hook so the
// component mounts without a QueryClientProvider (was crashing: "No QueryClient set").
vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useTenantCatalogConfig: () => ({ data: undefined }),
}));

const DEFAULT_FILTERS: FiltersType = {};

describe("ApprovalQueueFilters", () => {
  it("renders all filter controls", () => {
    render(<ApprovalQueueFilters filters={DEFAULT_FILTERS} onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: /date from filter/i })).toBeDefined();
    expect(screen.getByRole("button", { name: /date to filter/i })).toBeDefined();
    expect(screen.getByRole("combobox", { name: /currency filter/i })).toBeDefined();
    expect(screen.queryByRole("combobox", { name: /request type filter/i })).toBeNull();
  });

  it("calls onChange with updated currencyId when currency is selected", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<ApprovalQueueFilters filters={DEFAULT_FILTERS} onChange={onChange} />);

    await user.click(screen.getByRole("combobox", { name: /currency filter/i }));
    await user.click(screen.getByRole("option", { name: "Points" }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ currencyId: "points" }));
  });

  it("shows date labels when filters have date values", () => {
    render(
      <ApprovalQueueFilters
        filters={{ startDate: "2026-05-01", endDate: "2026-05-31" }}
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByText(/May 1, 2026/i)).toBeDefined();
    expect(screen.getByText(/May 31, 2026/i)).toBeDefined();
  });
});
