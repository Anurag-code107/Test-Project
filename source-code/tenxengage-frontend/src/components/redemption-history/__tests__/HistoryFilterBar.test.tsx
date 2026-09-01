import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HistoryFilterBar } from "@/components/redemption-history/HistoryFilterBar";

describe("HistoryFilterBar", () => {
  it("renders all filter controls", () => {
    render(
      <HistoryFilterBar filters={{}} onFiltersChange={vi.fn()} />,
    );
    expect(screen.getByRole("button", { name: /date from filter/i })).toBeDefined();
    expect(screen.getByRole("combobox", { name: /status filter/i })).toBeDefined();
    expect(screen.getByRole("combobox", { name: /category filter/i })).toBeDefined();
  });

  it("shows inline error when dateFrom > dateTo", () => {
    render(
      <HistoryFilterBar
        filters={{ dateFrom: "2026-06-10", dateTo: "2026-06-01" }}
        onFiltersChange={vi.fn()}
      />,
    );
    expect(screen.getByRole("alert")).toBeDefined();
    expect(screen.getByText("Start date must be before end date")).toBeDefined();
  });

  it("does not show error when dateFrom <= dateTo", () => {
    render(
      <HistoryFilterBar
        filters={{ dateFrom: "2026-06-01", dateTo: "2026-06-10" }}
        onFiltersChange={vi.fn()}
      />,
    );
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("calls onFiltersChange when status is selected", async () => {
    const onFiltersChange = vi.fn();
    const user = userEvent.setup();

    render(
      <HistoryFilterBar filters={{}} onFiltersChange={onFiltersChange} />,
    );

    await user.click(screen.getByRole("combobox", { name: /status filter/i }));
    await user.click(screen.getByRole("option", { name: "Completed" }));

    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ status: "COMPLETED" }));
  });

  it("calls onFiltersChange when category is selected", async () => {
    const onFiltersChange = vi.fn();
    const user = userEvent.setup();

    render(
      <HistoryFilterBar filters={{}} onFiltersChange={onFiltersChange} />,
    );

    await user.click(screen.getByRole("combobox", { name: /category filter/i }));
    await user.click(screen.getByRole("option", { name: "Cash" }));

    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ category: "CASH" }));
  });
});
