import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DateRangeFilter } from "@/components/redemption-analytics/DateRangeFilter";

const defaultValue = { from: "2026-05-18", to: "2026-06-17" };

describe("DateRangeFilter", () => {
  it("renders all 5 preset buttons", () => {
    render(<DateRangeFilter value={defaultValue} onChange={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Last 7 days" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Last 30 days" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Last 90 days" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Last 12 months" })).toBeDefined();
    expect(screen.getByRole("button", { name: /Custom range/ })).toBeDefined();
  });

  it("calls onChange when a preset button is clicked", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<DateRangeFilter value={defaultValue} onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: "Last 7 days" }));
    expect(onChange).toHaveBeenCalledOnce();
    const callArg = onChange.mock.calls[0]![0] as { from: string; to: string };
    expect(callArg).toHaveProperty("from");
    expect(callArg).toHaveProperty("to");
  });

  it("calls onChange with last 30d range", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<DateRangeFilter value={defaultValue} onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: "Last 30 days" }));
    expect(onChange).toHaveBeenCalledOnce();
    const callArg = onChange.mock.calls[0]![0] as { from: string; to: string };
    // Both from and to should be valid date strings
    expect(callArg.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(callArg.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
