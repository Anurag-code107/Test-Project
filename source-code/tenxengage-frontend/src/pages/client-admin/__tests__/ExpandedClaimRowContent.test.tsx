import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render } from "@testing-library/react";
import { ExpandedClaimRowContent } from "@/pages/client-admin/ManageClaimsPage";
import type { ClaimDetailResponse } from "@/types/claim.types";

// Mock the claim-detail hook at the module boundary so we don't need a
// QueryClient / axios / auth stack for this unit test.
vi.mock("@/hooks/useClaimApi", () => ({
  useClaimDetail: vi.fn(),
}));

import { useClaimDetail } from "@/hooks/useClaimApi";

const mockUseClaimDetail = vi.mocked(useClaimDetail);

// Minimal detail with every built-in currency represented in the breakdown
// — two monetary (cash, points) and two non-monetary (credits, tickets).
// Values are deliberately fractional so the whole-number formatting path is
// exercised: "100.5", "50.25", "10.9", "3.4".
const detailWithAllCurrencies = (): ClaimDetailResponse => ({
  id: "claim-1",
  orderNumber: "PO-1",
  orderDate: "2026-04-19",
  status: "CLAIMED",
  sellerName: "Seller",
  sellerId: "seller-1",
  partnerCompanyName: "Partner",
  partnerCompanyId: "partner-1",
  region: "NA",
  customerName: "Customer",
  totalAmount: 0,
  totalMonetaryReward: 0,
  rewardBreakdown: { monetary: {}, nonMonetary: {} },
  claimers: [],
  maxClaimersPerDeal: 1,
  eligibleIncentives: [
    {
      incentiveId: "inc-1",
      incentiveName: "Test Incentive",
      rewardBreakdown: {
        monetary: { cash: "100.5", points: "50.25" },
        nonMonetary: { credits: "10.9", tickets: "3.4" },
      },
      totalReward: 164,
    },
  ],
  ineligibleIncentives: [],
  adminComment: null,
  createdAt: "2026-04-19T00:00:00Z",
  updatedAt: "2026-04-19T00:00:00Z",
});

function mockDetail(detail: ClaimDetailResponse | undefined, isLoading = false) {
  mockUseClaimDetail.mockReturnValue({
    data: detail,
    isLoading,
  } as ReturnType<typeof useClaimDetail>);
}

describe("ExpandedClaimRowContent — eligible card currency rendering", () => {
  beforeEach(() => {
    mockUseClaimDetail.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders whole-number formatted amounts for every currency, not the raw backend string", () => {
    mockDetail(detailWithAllCurrencies());

    const { container } = render(<ExpandedClaimRowContent claimId="claim-1" />);

    // The per-currency row is the `<div className="flex items-center gap-2 text-xs text-muted-foreground">`
    // sibling of the totalReward line — query it directly and inspect each
    // span's text so adjacent numeric amounts don't smear into a single string
    // in a whole-container textContent readout.
    const perCurrencyRow = container.querySelector(
      ".flex.items-center.gap-2.text-xs.text-muted-foreground",
    );
    expect(perCurrencyRow).not.toBeNull();

    const rowTexts = Array.from(perCurrencyRow!.querySelectorAll("span")).map(
      (s) => (s.textContent ?? "").trim(),
    );

    // Order matches the source iteration: monetary first (cash, points), then
    // non-monetary (credits, tickets).
    expect(rowTexts).toContain("$101"); // cash 100.5 → fmtUsd rounds to $101
    expect(rowTexts).toContain("$50"); // points 50.25 → fmtUsd rounds to $50
    expect(rowTexts).toContain("11"); // credits 10.9 → fmtNum rounds to 11
    expect(rowTexts).toContain("3"); // tickets 3.4 → fmtNum rounds to 3

    // Regression guard: none of the raw fractional strings should leak to the
    // DOM — the old buggy renderer printed `{value}` directly, so "100.5" etc.
    // would appear verbatim.
    const allText = container.textContent ?? "";
    expect(allText).not.toContain("100.5");
    expect(allText).not.toContain("50.25");
    expect(allText).not.toContain("10.9");
    expect(allText).not.toContain("3.4");
  });

  it("uses the per-currency icon from currencies.ts for every built-in currency", () => {
    mockDetail(detailWithAllCurrencies());

    const { container } = render(<ExpandedClaimRowContent claimId="claim-1" />);

    // Lucide React renders icons as <svg> with a `lucide-<kebab>` class. The
    // previous buggy renderer only produced `.lucide-coins` and `.lucide-gift`
    // regardless of the currency; the fix routes through `getCurrency(key).icon`
    // so cash/points/credits/tickets each get their declared icon.
    //
    // Note on `.lucide-gift`: `points` maps to the Gift icon in currencies.ts,
    // so `.lucide-gift` SHOULD still appear exactly once for the points row.
    expect(
      container.querySelector(".lucide-dollar-sign"),
      "cash should render DollarSign",
    ).not.toBeNull();
    expect(
      container.querySelector(".lucide-gift"),
      "points should render Gift",
    ).not.toBeNull();
    expect(
      container.querySelector(".lucide-award"),
      "credits should render Award",
    ).not.toBeNull();
    expect(
      container.querySelector(".lucide-ticket"),
      "tickets should render Ticket",
    ).not.toBeNull();

    // The old code hard-coded `<Coins />` for every monetary entry. None of the
    // built-in currencies map to the Coins icon, so no coin SVG should be
    // present inside the per-currency row after the fix.
    const perCurrencyRow = container.querySelector(
      ".flex.items-center.gap-2.text-xs.text-muted-foreground",
    );
    expect(perCurrencyRow).not.toBeNull();
    expect(
      perCurrencyRow!.querySelector(".lucide-coins"),
      "per-currency row should not use the generic Coins icon for any built-in currency",
    ).toBeNull();
  });

  it("renders a ClaimDetailSkeleton while the detail request is loading", () => {
    mockDetail(undefined, true);

    const { container } = render(<ExpandedClaimRowContent claimId="claim-1" />);

    // The skeleton uses `skeleton-shimmer` blocks — at least one should be
    // in the DOM while loading. This is a sanity check that the loading branch
    // short-circuits before the currency-rendering code runs.
    expect(container.querySelector(".skeleton-shimmer")).not.toBeNull();
  });
});
