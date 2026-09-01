import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import CompanyPayoutSetupPage from "../CompanyPayoutSetupPage";

const mutateAsync = vi.fn().mockResolvedValue({});
let profile: Record<string, unknown> | undefined = {};
let profileError: unknown = null;

// Every export the page imports has to appear here — a module factory replaces the module wholesale, so an
// export added to the real hook and forgotten here surfaces as "not a function" at render time.
vi.mock("@/hooks/useCompanyAdminProfile", () => ({
  useCompanyAdminProfile: () => ({
    data: profile,
    isLoading: false,
    error: profileError,
  }),
  useCompleteCompanyAdminProfile: () => ({ mutateAsync, isPending: false }),
  isNotYourPayoutSetup: (e: unknown) =>
    (e as { response?: { status?: number } })?.response?.status === 403,
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

/**
 * The admin supplies only their address; identity came from the login their client admin created. The
 * email is shown, never editable — it is already spent at the provider, which refuses to reuse it.
 */
describe("CompanyPayoutSetupPage", () => {
  beforeEach(() => {
    mutateAsync.mockClear();
    profileError = null;
    profile = {
      companyName: "Acme Corp",
      adminEmail: "admin@acme.test",
      adminCity: "",
      adminRegion: "",
      adminPostalCode: "",
      complete: false,
      xtrmAccount: undefined,
    };
  });

  it("submits the three address fields", async () => {
    render(<CompanyPayoutSetupPage />);

    fireEvent.change(screen.getByLabelText(/^city$/i), {
      target: { value: "San Francisco" },
    });
    fireEvent.change(screen.getByLabelText(/state \/ region/i), {
      target: { value: "CA" },
    });
    fireEvent.change(screen.getByLabelText(/postal code/i), {
      target: { value: "94105" },
    });
    fireEvent.click(screen.getByRole("button", { name: /finish setup/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync.mock.calls[0]?.[0]).toEqual({
      adminCity: "San Francisco",
      adminRegion: "CA",
      adminPostalCode: "94105",
    });
  });

  it("does not submit an incomplete address", async () => {
    render(<CompanyPayoutSetupPage />);

    fireEvent.change(screen.getByLabelText(/^city$/i), {
      target: { value: "San Francisco" },
    });
    fireEvent.click(screen.getByRole("button", { name: /finish setup/i }));

    await waitFor(() => expect(mutateAsync).not.toHaveBeenCalled());
  });

  it("shows the admin email without offering to change it", () => {
    render(<CompanyPayoutSetupPage />);

    // Spent once at the provider — showing it is useful, editing it would be a lie.
    expect(screen.getByText(/admin@acme.test/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/admin email/i)).toBeNull();
  });

  it("does not ask again for identity the login already carries", () => {
    render(<CompanyPayoutSetupPage />);

    expect(screen.queryByLabelText(/admin first name/i)).toBeNull();
    expect(screen.queryByLabelText(/admin mobile/i)).toBeNull();
  });

  it("shows the payout status once connected", () => {
    profile = {
      ...profile,
      complete: true,
      xtrmAccount: { status: "CONNECTED", accountNumber: "SPN26241004" },
    };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByText(/SPN26241004/)).toBeInTheDocument();
  });

  it("tells a second admin the account is someone else's, not that it is broken", () => {
    // Every company admin holds the same permissions, so a second one can reach this page. The server
    // refuses them, and the distinction matters: this is not a failure to investigate.
    profile = undefined;
    profileError = { response: { status: 403 } };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByText(/someone else manages/i)).toBeInTheDocument();
    expect(screen.queryByText(/unavailable/i)).toBeNull();
    expect(screen.queryByRole("button", { name: /finish setup/i })).toBeNull();
  });

  it("still reports a genuine failure as a failure", () => {
    profile = undefined;
    profileError = { response: { status: 500 } };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByText(/unavailable/i)).toBeInTheDocument();
  });

  it("pre-fills an address already on file", () => {
    profile = { ...profile, adminCity: "San Francisco" };

    render(<CompanyPayoutSetupPage />);

    expect(screen.getByLabelText(/^city$/i)).toHaveValue("San Francisco");
  });
});
