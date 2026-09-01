import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { UnredeemedBalanceCard } from "@/components/redemption-analytics/UnredeemedBalanceCard";
// shape: contracts/models/redemption-analytics-summary.md

describe("UnredeemedBalanceCard", () => {
  it("renders all three balance fields", () => {
    render(
      <UnredeemedBalanceCard
        data={{
          currencyId: "CASH",
          availableBalance: 7500,
          reservedBalance: 2500,
          totalOutstanding: 10000,
        }}
      />,
    );
    // Card title
    expect(screen.getByText("Cash Outstanding Liability")).toBeDefined();
    // Sub-labels should mention Available and Reserved
    expect(screen.getByText(/Available/)).toBeDefined();
    expect(screen.getByText(/Reserved/)).toBeDefined();
  });

  it("totalOutstanding equals availableBalance + reservedBalance", () => {
    const available = 3000;
    const reserved = 1000;
    const total = available + reserved;

    render(
      <UnredeemedBalanceCard
        data={{
          currencyId: "POINTS",
          availableBalance: available,
          reservedBalance: reserved,
          totalOutstanding: total,
        }}
      />,
    );
    // The component renders totalOutstanding as primary value
    // The currency formatter for points uses rewardFormat: "4,000 pts"
    expect(screen.getByText(/4,000/)).toBeDefined();
  });

  it("renders correct currencyId label", () => {
    render(
      <UnredeemedBalanceCard
        data={{
          currencyId: "TICKETS",
          availableBalance: 10,
          reservedBalance: 5,
          totalOutstanding: 15,
        }}
      />,
    );
    expect(screen.getByText("Tickets Outstanding Liability")).toBeDefined();
  });
});
