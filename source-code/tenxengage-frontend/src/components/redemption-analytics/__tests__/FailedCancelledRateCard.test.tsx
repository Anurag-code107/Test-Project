import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FailedCancelledRateCard } from "@/components/redemption-analytics/FailedCancelledRateCard";
// shape: contracts/models/redemption-analytics-summary.md

describe("FailedCancelledRateCard", () => {
  it("renders rate percentage when hasActivity is true", () => {
    render(
      <FailedCancelledRateCard
        data={{
          currencyId: "CASH",
          numerator: 3,
          denominator: 20,
          ratePercentage: "15.00",
          hasActivity: true,
        }}
      />,
    );
    expect(screen.getByText("15.00%")).toBeDefined();
  });

  it("renders the correct currencyId label", () => {
    render(
      <FailedCancelledRateCard
        data={{
          currencyId: "POINTS",
          numerator: 1,
          denominator: 10,
          ratePercentage: "10.00",
          hasActivity: true,
        }}
      />,
    );
    // Uses HTML entity &amp; — check rendered text
    const title = screen.getByText(/Points Failed/);
    expect(title).toBeDefined();
  });

  it("renders empty state when hasActivity is false", () => {
    render(
      <FailedCancelledRateCard
        data={{
          currencyId: "CASH",
          numerator: 0,
          denominator: 0,
          hasActivity: false,
        }}
      />,
    );
    expect(screen.getByText("No redemptions in this period")).toBeDefined();
  });
});
