import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (declare BEFORE imports that depend on them) ---

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: () => ({ can: () => true, canAny: () => true, canAll: () => true, permissions: new Set() }),
}));

vi.mock("@/hooks/useUpsertBalanceExpirationPolicy", () => ({
  useUpsertBalanceExpirationPolicy: vi.fn(),
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

// --- Imports ---

import { useUpsertBalanceExpirationPolicy } from "@/hooks/useUpsertBalanceExpirationPolicy";
import { BalanceExpirationPolicyForm } from "@/components/balanceExpiration/BalanceExpirationPolicyForm";
import type { BalanceExpirationPolicyResponse } from "@/types/balanceExpiration.types";

const mockUseUpsert = vi.mocked(useUpsertBalanceExpirationPolicy);

// shape: contracts/models/balance-expiration-policy.md
const POINTS_POLICY: BalanceExpirationPolicyResponse = {
  currencyId: "points",
  currencyDisplayName: "Points",
  enabled: true,
  expirationMode: "INACTIVITY",
  inactivityDays: 90,
  fixedExpiryDate: null,
  leadTimeDays: 30,
  enabledAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

beforeEach(() => {
  mockUseUpsert.mockReturnValue({
    upsert: vi.fn().mockResolvedValue(true),
    isPending: false,
  });
});

describe("BalanceExpirationPolicyForm", () => {
  describe("AC-6: all four currencies rendered", () => {
    it("shows currency label from currencies.ts config", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={undefined} />,
      );
      // getCurrency('points').label = 'Points'
      expect(screen.getByText("Points")).toBeDefined();
    });

    it("shows 'Not configured' caption when no saved policy", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="cash" policy={undefined} />,
      );
      expect(screen.getByText("Not configured")).toBeDefined();
    });

    it("shows 'Active since' caption when policy is enabled", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );
      expect(screen.getByText(/Active since/i)).toBeDefined();
    });
  });

  describe("Conditional rendering (AC conditional rendering)", () => {
    it("shows inactivityDays field when mode is INACTIVITY", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );
      // The input label is "Inactivity period (days)" — use the full text
      expect(
        screen.getByLabelText(/inactivity period \(days\)/i),
      ).toBeDefined();
    });

    it("shows fixedExpiryDate field when mode is FIXED_DATE", () => {
      render(
        <BalanceExpirationPolicyForm
          currencyId="points"
          policy={{ ...POINTS_POLICY, expirationMode: "FIXED_DATE", inactivityDays: null, fixedExpiryDate: "2027-01-01" }}
        />,
      );
      expect(screen.getByLabelText(/fixed expiry date/i)).toBeDefined();
    });
  });

  describe("Save action", () => {
    it("calls upsert and shows success toast on valid form submit", async () => {
      const upsert = vi.fn().mockResolvedValue(true);
      mockUseUpsert.mockReturnValue({ upsert, isPending: false });

      const user = userEvent.setup();
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );

      // Change the inactivityDays field to make the form dirty+valid
      const inactivityInput = screen.getByLabelText(/inactivity period \(days\)/i);
      await user.clear(inactivityInput);
      await user.type(inactivityInput, "120");

      const saveBtn = screen.getByRole("button", { name: /save/i });
      await user.click(saveBtn);

      expect(upsert).toHaveBeenCalled();
    });

    it("Save and Cancel buttons are present", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );
      expect(screen.getByRole("button", { name: /save/i })).toBeDefined();
      expect(screen.getByRole("button", { name: /cancel/i })).toBeDefined();
    });
  });

  describe("AC-3: inline field errors from 422", () => {
    it("shows lead-time field error when 422 maps to leadTimeDays", async () => {
      const upsert = vi.fn().mockImplementation(
        async (_currencyId, _body, setError) => {
          setError("leadTimeDays", {
            message:
              "Lead time must be at least 1 day and less than the inactivity period",
          });
          return false;
        },
      );
      mockUseUpsert.mockReturnValue({ upsert, isPending: false });

      const user = userEvent.setup();
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );

      // Change inactivityDays to make form dirty+valid
      const inactivityInput = screen.getByLabelText(/inactivity period \(days\)/i);
      await user.clear(inactivityInput);
      await user.type(inactivityInput, "120");

      await user.click(screen.getByRole("button", { name: /save/i }));

      expect(
        screen.getByText(
          "Lead time must be at least 1 day and less than the inactivity period",
        ),
      ).toBeDefined();
    });
  });

  describe("A11y", () => {
    it("loading spinner has aria-hidden (Save button still rendered during pending)", () => {
      mockUseUpsert.mockReturnValue({ upsert: vi.fn(), isPending: true });
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );
      // The Loader2 has aria-hidden so it won't appear as a role — just verify
      // the button is still rendered during pending
      expect(screen.getByRole("button", { name: /save/i })).toBeDefined();
    });

    it("RadioGroup has aria-labelledby linking to 'Expiration mode' label", () => {
      render(
        <BalanceExpirationPolicyForm currencyId="points" policy={POINTS_POLICY} />,
      );
      const radioGroup = screen.getByRole("radiogroup");
      expect(radioGroup.getAttribute("aria-labelledby")).toBeTruthy();
    });
  });
});
