import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/hooks/useRewardCurrencyApi", () => ({
  useRewardCurrencies: vi.fn(),
}));

vi.mock("@/hooks/useClaimApi", () => ({
  useRewardBalances: vi.fn(),
}));

import { useRewardCurrencies } from "@/hooks/useRewardCurrencyApi";
import { useRewardBalances } from "@/hooks/useClaimApi";
import { RewardsBalancesWidget } from "@/components/home/widgets/RewardsBalancesWidget";
import type { RewardCurrencyResponse } from "@/types/reward-currency.types";
import type { RewardBalanceResponse } from "@/types/claim.types";

const mockCurrencies = vi.mocked(useRewardCurrencies);
const mockBalances = vi.mocked(useRewardBalances);

function currency(
  code: string,
  name: string,
  type: "MONETARY" | "NON_MONETARY",
): RewardCurrencyResponse {
  return {
    id: code,
    code,
    name,
    type,
    unit: type === "MONETARY" ? "USD" : code,
    isCurrencyFormatted: type === "MONETARY",
    isDefault: true,
    createdAt: "2026-04-19T00:00:00Z",
    updatedAt: "2026-04-19T00:00:00Z",
  };
}

function stubCurrencies(list: RewardCurrencyResponse[]) {
  mockCurrencies.mockReturnValue({
    data: list,
  } as unknown as ReturnType<typeof useRewardCurrencies>);
}

function stubBalances(list: RewardBalanceResponse[]) {
  mockBalances.mockReturnValue({
    data: list,
  } as unknown as ReturnType<typeof useRewardBalances>);
}

function renderWidget() {
  return render(
    <MemoryRouter>
      <RewardsBalancesWidget />
    </MemoryRouter>,
  );
}

describe("RewardsBalancesWidget", () => {
  it("renders a tile for each tenant currency", () => {
    stubCurrencies([
      currency("cash", "Cash", "MONETARY"),
      currency("points", "Points", "MONETARY"),
      currency("credits", "Credits", "NON_MONETARY"),
      currency("tickets", "Tickets", "NON_MONETARY"),
    ]);
    stubBalances([]);

    renderWidget();

    expect(screen.getByText("Cash")).toBeDefined();
    expect(screen.getByText("Points")).toBeDefined();
    expect(screen.getByText("Credits")).toBeDefined();
    expect(screen.getByText("Tickets")).toBeDefined();
  });

  it("shows the user's balance when present and 0 otherwise", () => {
    stubCurrencies([
      currency("cash", "Cash", "MONETARY"),
      currency("points", "Points", "MONETARY"),
    ]);
    stubBalances([
      { currencyId: "cash", balance: "1250" } as RewardBalanceResponse,
    ]);

    renderWidget();

    expect(screen.getByText("$1,250")).toBeDefined();
    // Points wasn't returned, so it should default to 0 (formatted as "0 pts")
    expect(screen.getByText("0 pts")).toBeDefined();
  });

  it("shows an empty state when the tenant has no currencies configured", () => {
    stubCurrencies([]);
    stubBalances([]);

    renderWidget();

    expect(screen.getByText(/No currencies configured/)).toBeDefined();
  });

  it("stretches to fill its grid cell height", () => {
    stubCurrencies([currency("cash", "Cash", "MONETARY")]);
    stubBalances([]);

    const { container } = renderWidget();
    const link = container.querySelector("a");
    expect(link?.className).toContain("h-full");
  });

  it("links to the Rewards tab of Manage Rewards", () => {
    stubCurrencies([currency("cash", "Cash", "MONETARY")]);
    stubBalances([]);

    renderWidget();

    const link = screen.getByRole("link", { name: /go to manage rewards/i });
    expect(link.getAttribute("href")).toBe("/rewards?tab=rewards");
  });
});
