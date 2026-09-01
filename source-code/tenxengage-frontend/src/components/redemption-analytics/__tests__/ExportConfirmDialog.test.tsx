import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ExportConfirmDialog } from "@/components/redemption-analytics/ExportConfirmDialog";

describe("ExportConfirmDialog", () => {
  it("renders dialog content when open is true", () => {
    render(
      <ExportConfirmDialog
        open={true}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        isPending={false}
      />,
    );
    expect(screen.getByText("Export unredeemed balances")).toBeDefined();
    expect(
      screen.getByText(
        "Download a CSV of all current unredeemed wallet balances for your program.",
      ),
    ).toBeDefined();
  });

  it("shows spinner and 'Downloading…' text when isPending is true", () => {
    render(
      <ExportConfirmDialog
        open={true}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        isPending={true}
      />,
    );
    expect(screen.getByText("Downloading…")).toBeDefined();
  });

  it("confirm button is disabled when isPending is true", () => {
    render(
      <ExportConfirmDialog
        open={true}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
        isPending={true}
      />,
    );
    const downloadBtn = screen.getByRole("button", { name: /downloading/i });
    expect((downloadBtn as HTMLButtonElement).disabled).toBe(true);
  });

  it("cancel button calls onClose when clicked", async () => {
    const onClose = vi.fn();
    render(
      <ExportConfirmDialog
        open={true}
        onClose={onClose}
        onConfirm={vi.fn()}
        isPending={false}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: /cancel/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("confirm button calls onConfirm when clicked", async () => {
    const onConfirm = vi.fn();
    render(
      <ExportConfirmDialog
        open={true}
        onClose={vi.fn()}
        onConfirm={onConfirm}
        isPending={false}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: /download csv/i }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
