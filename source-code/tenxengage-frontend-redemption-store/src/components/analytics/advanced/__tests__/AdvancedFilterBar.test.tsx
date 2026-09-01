import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdvancedFilterBar } from "@/components/analytics/advanced/AdvancedFilterBar";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

const defaultFilters: AdvancedAnalyticsFilters = {
  dateFrom: "2026-05-23",
  dateTo: "2026-06-22",
};

describe("AdvancedFilterBar", () => {
  it("renders all three date preset buttons", () => {
    render(
      <AdvancedFilterBar
        onFilterChange={vi.fn()}
        isSegmentDataEmpty={false}
        currentFilters={defaultFilters}
      />,
    );
    expect(screen.getByRole("button", { name: "Last 7 days" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Last 30 days" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Last 90 days" })).toBeDefined();
  });

  it("calls onFilterChange with correct date range when a preset is clicked", async () => {
    const onFilterChange = vi.fn();
    const user = userEvent.setup();
    render(
      <AdvancedFilterBar
        onFilterChange={onFilterChange}
        isSegmentDataEmpty={false}
        currentFilters={defaultFilters}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Last 7 days" }));
    expect(onFilterChange).toHaveBeenCalledOnce();
    const arg = onFilterChange.mock.calls[0]![0] as AdvancedAnalyticsFilters;
    expect(arg.dateFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(arg.dateTo).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // 7-day window: dateTo − dateFrom = 7 days
    const diff =
      (new Date(arg.dateTo).getTime() - new Date(arg.dateFrom).getTime()) /
      (1000 * 60 * 60 * 24);
    expect(diff).toBe(7);
  });

  it("region and role multi-selects are disabled when isSegmentDataEmpty=true", () => {
    render(
      <AdvancedFilterBar
        onFilterChange={vi.fn()}
        isSegmentDataEmpty={true}
        currentFilters={defaultFilters}
      />,
    );
    // MultiSelect trigger is a <button> labelled via aria-label; disabled via disabled attr.
    const regionSelect = screen.getByRole("button", { name: "Region filter" });
    const roleSelect = screen.getByRole("button", { name: "Role filter" });
    expect((regionSelect as HTMLButtonElement).disabled).toBe(true);
    expect((roleSelect as HTMLButtonElement).disabled).toBe(true);
  });

  it("shows 'No data available' placeholder when isSegmentDataEmpty=true", () => {
    render(
      <AdvancedFilterBar
        onFilterChange={vi.fn()}
        isSegmentDataEmpty={true}
        currentFilters={defaultFilters}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Region filter" }).textContent,
    ).toContain("No data available");
    expect(
      screen.getByRole("button", { name: "Role filter" }).textContent,
    ).toContain("No data available");
  });

  it("region and role multi-selects are enabled when isSegmentDataEmpty=false", () => {
    render(
      <AdvancedFilterBar
        onFilterChange={vi.fn()}
        isSegmentDataEmpty={false}
        currentFilters={defaultFilters}
        regionOptions={["EMEA", "APAC"]}
        roleOptions={["CLIENT_ADMIN"]}
      />,
    );
    const regionSelect = screen.getByRole("button", { name: "Region filter" });
    const roleSelect = screen.getByRole("button", { name: "Role filter" });
    expect((regionSelect as HTMLButtonElement).disabled).toBe(false);
    expect((roleSelect as HTMLButtonElement).disabled).toBe(false);
  });

  it("populates region options from the segment data and reports the selection comma-joined", async () => {
    const onFilterChange = vi.fn();
    const user = userEvent.setup();
    render(
      <AdvancedFilterBar
        onFilterChange={onFilterChange}
        isSegmentDataEmpty={false}
        currentFilters={defaultFilters}
        regionOptions={["EMEA", "APAC"]}
        roleOptions={[]}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Region filter" }));
    // Options come from regionOptions (FR-08.6), not a hardcoded "All regions" item.
    await user.click(screen.getByRole("button", { name: "APAC" }));
    expect(onFilterChange).toHaveBeenCalledOnce();
    const arg = onFilterChange.mock.calls[0]![0] as AdvancedAnalyticsFilters;
    expect(arg.region).toBe("APAC");
  });

  it("Apply button is present in the custom range picker", async () => {
    const user = userEvent.setup();
    render(
      <AdvancedFilterBar
        onFilterChange={vi.fn()}
        isSegmentDataEmpty={false}
        currentFilters={defaultFilters}
      />,
    );
    await user.click(screen.getByRole("button", { name: /Custom range/ }));
    const applyButton = screen.getByRole("button", { name: "Apply custom date range" });
    expect(applyButton).toBeDefined();
    // Apply is disabled until a full range is selected
    expect((applyButton as HTMLButtonElement).disabled).toBe(true);
  });
});
