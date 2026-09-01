import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AddCardForm } from "@/components/redemption-payout/AddCardForm";

const { addState } = vi.hoisted(() => ({
  addState: { mutate: vi.fn(), isPending: false, isError: false, error: null as unknown },
}));

vi.mock("@/hooks/redemption-payout/useRedemptionProfileMutations", async (importActual) => {
  const actual = await importActual<typeof import("@/hooks/redemption-payout/useRedemptionProfileMutations")>();
  return { ...actual, useAddCard: () => addState };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/card number/i), "4111111111111111");
  await user.click(screen.getByLabelText(/card type/i));
  await user.click(screen.getByRole("option", { name: "Visa Card" }));
  await user.type(screen.getByLabelText(/expiry month/i), "12");
  await user.type(screen.getByLabelText(/expiry year/i), "2029");
  await user.type(screen.getByLabelText(/cvv/i), "123");
  await user.type(screen.getByLabelText(/name on card/i), "Ada Lovelace");
  await user.type(screen.getByLabelText(/first name/i), "Ada");
  await user.type(screen.getByLabelText(/last name/i), "Lovelace");
  await user.type(screen.getByLabelText(/address line 1/i), "123 Main St");
  await user.type(screen.getByLabelText(/^city/i), "San Francisco");
  await user.type(screen.getByLabelText(/state \/ region/i), "CA");
  await user.type(screen.getByLabelText(/postal code/i), "94105");
  await user.type(screen.getByLabelText(/^country/i), "US");
}

describe("AddCardForm", () => {
  beforeEach(() => {
    addState.mutate = vi.fn();
    addState.isPending = false;
    addState.isError = false;
    addState.error = null;
  });

  it("shows the PCI 'never stored' assurance", () => {
    render(<AddCardForm />);
    expect(screen.getByText(/never stored on tenxengage/i)).toBeDefined();
  });

  it("blocks submit and shows errors when required fields are empty", async () => {
    const user = userEvent.setup();
    render(<AddCardForm />);

    await user.click(screen.getByRole("button", { name: /link card/i }));

    expect(addState.mutate).not.toHaveBeenCalled();
    expect(screen.getAllByText(/required/i).length).toBeGreaterThan(0);
  });

  it("validates the card number format", async () => {
    const user = userEvent.setup();
    render(<AddCardForm />);

    await user.type(screen.getByLabelText(/card number/i), "12x");
    await user.click(screen.getByRole("button", { name: /link card/i }));

    expect(addState.mutate).not.toHaveBeenCalled();
    expect(screen.getByText(/12–19 digits/i)).toBeDefined();
  });

  it("submits a valid payload (raw card fields pass through)", async () => {
    const user = userEvent.setup();
    render(<AddCardForm />);

    await fillValid(user);
    await user.click(screen.getByRole("button", { name: /link card/i }));

    await waitFor(() => expect(addState.mutate).toHaveBeenCalledTimes(1));
    const mockFn = addState.mutate as ReturnType<typeof vi.fn>;
    const payload = mockFn.mock.calls[0]![0] as Record<string, unknown>;
    expect(payload).toMatchObject({
      cardNumber: "4111111111111111",
      cardType: "Visa Card",
      expMonth: "12",
      expYear: "2029",
      cvv: "123",
      nameOnCard: "Ada Lovelace",
      countryIso2: "US",
    });
  });

  it("surfaces an XTRM 422 duplicate-card error inline", () => {
    addState.isError = true;
    addState.error = { response: { data: { errorCode: "XTRM_CARD_DUPLICATE" } } };
    render(<AddCardForm />);

    expect(screen.getByText(/this card is already linked/i)).toBeDefined();
  });
});
