import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LinkBankForm } from "@/components/redemption-payout/LinkBankForm";

const { linkState } = vi.hoisted(() => ({
  linkState: { mutate: vi.fn(), isPending: false, isError: false, error: null as unknown },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfileMutations", async (importActual) => {
  const actual = await importActual<typeof import("@/hooks/redemption-payout/useRedemptionProfileMutations")>();
  return { ...actual, useLinkBankAccount: () => linkState };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("LinkBankForm", () => {
  beforeEach(() => {
    linkState.mutate = vi.fn();
    linkState.isPending = false;
    linkState.isError = false;
    linkState.error = null;
  });

  it("blocks submit and shows errors when required fields are empty", async () => {
    const user = userEvent.setup();
    render(<LinkBankForm />);

    await user.click(screen.getByRole("button", { name: /link bank account/i }));

    expect(linkState.mutate).not.toHaveBeenCalled();
    expect(screen.getAllByText(/required/i).length).toBeGreaterThan(0);
  });

  it("submits a valid payload with withdrawType ACH", async () => {
    const user = userEvent.setup();
    render(<LinkBankForm />);

    await user.type(screen.getByLabelText(/account holder name/i), "Ada Lovelace");
    await user.type(screen.getByLabelText(/contact phone/i), "14085551234");
    await user.type(screen.getByLabelText(/bank name/i), "Wells Fargo");
    await user.type(screen.getByLabelText(/account number/i), "123456789");
    await user.type(screen.getByLabelText(/routing number/i), "021000021");
    await user.type(screen.getByLabelText(/address line 1/i), "123 Main St");
    await user.type(screen.getByLabelText(/city/i), "San Francisco");
    await user.type(screen.getByLabelText(/state \/ region/i), "CA");
    await user.type(screen.getByLabelText(/postal code/i), "94105");
    await user.type(screen.getByLabelText(/country/i), "US");

    await user.click(screen.getByRole("button", { name: /link bank account/i }));

    await waitFor(() => expect(linkState.mutate).toHaveBeenCalledTimes(1));
    const mockFn = linkState.mutate as ReturnType<typeof vi.fn>;
    const payload = mockFn.mock.calls[0]![0] as Record<string, unknown>;
    expect(payload).toMatchObject({
      contactName: "Ada Lovelace",
      contactPhone: "14085551234",
      accountNumber: "123456789",
      routingNumber: "021000021",
      countryIso2: "US",
      withdrawType: "ACH",
    });
  });

  it("surfaces an XTRM 422 duplicate error inline", () => {
    linkState.isError = true;
    linkState.error = { response: { data: { errorCode: "XTRM_BANK_DUPLICATE" } } };
    render(<LinkBankForm />);

    expect(screen.getByText(/this bank account is already linked/i)).toBeDefined();
  });
});
