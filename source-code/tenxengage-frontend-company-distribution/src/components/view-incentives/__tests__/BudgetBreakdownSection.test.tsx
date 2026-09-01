import { describe, it, expect } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { BudgetBreakdownSection } from "@/components/view-incentives/incentive-detail-shared";
import type { IncentiveDetailResponse } from "@/types/incentive.types";

function baseIncentive(
  overrides: Partial<IncentiveDetailResponse> = {},
): IncentiveDetailResponse {
  return {
    id: "inc-1",
    name: "Test Incentive",
    incentiveType: "SALES",
    status: "ACTIVE",
    createdByName: "tester",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    audienceRules: [],
    ...overrides,
  } as IncentiveDetailResponse;
}

function getRowAmount(label: string): string {
  // Each row renders the label and the amount inside the same flex container.
  // Walk up from the label text node to find the row, then read the amount span.
  const labelEl = screen.getByText(label);
  const row = labelEl.closest("div.flex.items-center.justify-between");
  if (!row) throw new Error(`row for ${label} not found`);
  const amountSpan = within(row as HTMLElement).getByText(
    (_, el) => el?.classList.contains("font-medium") ?? false,
  );
  return amountSpan.textContent ?? "";
}

describe("BudgetBreakdownSection (BUG-069)", () => {
  it("reads per-currency totals from incentive.budgets[] when budgets[] is the only source", () => {
    // Bug repro: incentive carries per-currency budgets in `budgets[]` but no
    // singular `budget` field and no `rewardAmounts`. Pre-fix, every row read
    // `rewardAmounts[currencyId]` and rendered `$0`.
    const incentive = baseIncentive({
      budgets: [
        {
          currencyId: "cash",
          totalBudget: "500000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
      ],
    });

    render(
      <BudgetBreakdownSection
        incentive={incentive}
        label="Total Budget"
        amount={500000}
      />,
    );

    expect(getRowAmount("Cash")).toBe("$500,000");
    expect(getRowAmount("Points")).toBe("$0");
    expect(getRowAmount("Credits")).toBe("0");
    expect(getRowAmount("Tickets")).toBe("0");
  });

  it("scales each currency row when amount is a fraction of the aggregate (Budget Utilized)", () => {
    // The "Budget Utilized" caller passes amount = total * util%. Per-currency
    // rows must scale by the same fraction so they sum back to `amount`.
    const incentive = baseIncentive({
      budgets: [
        {
          currencyId: "cash",
          totalBudget: "400000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
        {
          currencyId: "points",
          totalBudget: "100000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
      ],
    });

    // 50% utilization of $500k aggregate.
    render(
      <BudgetBreakdownSection
        incentive={incentive}
        label="Budget Utilized"
        amount={250000}
      />,
    );

    expect(getRowAmount("Cash")).toBe("$200,000");
    expect(getRowAmount("Points")).toBe("$50,000");
  });

  it("renders each per-currency total when amount equals the budgets[] aggregate", () => {
    // Repro from the user's screenshot: cash=$500K + points=$100K (aggregate
    // $600K), drawer caller now passes amount=$600K so ratio=1 and rows show
    // their persisted totals — not a scaled fraction.
    const incentive = baseIncentive({
      budgets: [
        {
          currencyId: "cash",
          totalBudget: "500000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
        {
          currencyId: "points",
          totalBudget: "100000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
      ],
    });

    render(
      <BudgetBreakdownSection
        incentive={incentive}
        label="Total Budget"
        amount={600000}
      />,
    );

    expect(getRowAmount("Cash")).toBe("$500,000");
    expect(getRowAmount("Points")).toBe("$100,000");
  });

  it("falls back to legacy singular budget when budgets[] is absent", () => {
    // Older payloads only carry `incentive.budget` (singular). The legacy
    // path must keep working — primary currency renders the displayed `amount`;
    // currencies without budget data render 0.
    const incentive = baseIncentive({
      budget: {
        totalBudget: "300000",
        currency: "cash",
        allocationMethod: "EQUAL",
        budgetMode: "GLOBAL",
      },
    });

    render(
      <BudgetBreakdownSection
        incentive={incentive}
        label="Total Budget"
        amount={300000}
      />,
    );

    expect(getRowAmount("Cash")).toBe("$300,000");
    expect(getRowAmount("Points")).toBe("$0");
  });
});

describe("BudgetBreakdownSection — BUG-076 reward bleed", () => {
  it("renders 0 for currencies without budget data even when rewardAmounts is populated for them", () => {
    // Repro: a Journey stage with a cash budget but no points budget can still
    // ship `rewardAmounts: { points: ... }`. The Budget panel must never read
    // from `rewardAmounts` — those numbers describe what partners earn, not
    // what the program is funded with.
    const incentive = baseIncentive({
      budgets: [
        {
          currencyId: "cash",
          totalBudget: "500000",
          allocationMethod: "EQUAL",
          budgetMode: "GLOBAL",
        },
      ],
      rewardAmounts: {
        cash: "500",
        points: "100",
        credits: "25",
        tickets: "5",
      },
    });

    render(
      <BudgetBreakdownSection
        incentive={incentive}
        label="Total Budget"
        amount={500000}
      />,
    );

    expect(getRowAmount("Cash")).toBe("$500,000");
    expect(getRowAmount("Points")).toBe("$0");
    expect(getRowAmount("Credits")).toBe("0");
    expect(getRowAmount("Tickets")).toBe("0");
  });
});
