import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RedemptionRateCard } from "@/components/redemption-analytics/RedemptionRateCard";
// shape: contracts/models/redemption-analytics-summary.md

describe("RedemptionRateCard", () => {
  it("renders rate percentage when hasActivity is true", () => {
    render(
      <RedemptionRateCard
        data={{
          currencyId: "CASH",
          numerator: 5000,
          denominator: 10000,
          ratePercentage: "50.00",
          hasActivity: true,
        }}
      />,
    );
    expect(screen.getByText("50.00%")).toBeDefined();
  });

  it("renders the correct currencyId label", () => {
    render(
      <RedemptionRateCard
        data={{
          currencyId: "POINTS",
          numerator: 200,
          denominator: 1000,
          ratePercentage: "20.00",
          hasActivity: true,
        }}
      />,
    );
    expect(screen.getByText("Points Redemption Rate")).toBeDefined();
  });

  it("renders empty state when hasActivity is false", () => {
    render(
      <RedemptionRateCard
        data={{
          currencyId: "CASH",
          numerator: 0,
          denominator: 0,
          hasActivity: false,
        }}
      />,
    );
    expect(screen.getByText("No program activity yet")).toBeDefined();
  });
});
