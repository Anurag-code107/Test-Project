import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import React from "react";
import type { ClaimListParams } from "@/types/claim.types";

vi.mock("@/services/claim.service", () => ({
  getClaims: vi.fn(),
  getClaimSummary: vi.fn(),
  getClaimDetail: vi.fn(),
  claimDeal: vi.fn(),
  unclaimDeal: vi.fn(),
  updateClaim: vi.fn(),
  getRewardBalances: vi.fn(),
  getUserRewardBalances: vi.fn(),
}));

import * as service from "@/services/claim.service";
import { useClaims, useClaimSummary } from "@/hooks/useClaimApi";

function makeWrapper(qc: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return React.createElement(QueryClientProvider, { client: qc }, children);
  };
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

/**
 * Regression for BUG-028 — pre-fix, useClaims and useClaimSummary had
 * `staleTime: 0` and no `placeholderData`, so changing any filter param
 * dropped `data` to undefined and flipped `isLoading` to true. That triggered
 * the whole claims table on /claims and /rewards to swap to
 * PartnerGroupedClaimsSkeleton, producing ~608 ms INP.
 *
 * Post-fix, both queries use `placeholderData: keepPreviousData` so during a
 * filter change the prior data stays as the rendered value, isLoading stays
 * false, and isPlaceholderData flags the transition. The table re-renders
 * in place when new data arrives — no skeleton swap.
 */

describe("useClaims keeps previous data across filter-param changes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("data stays populated and isLoading stays false when params change", async () => {
    const first = { data: [{ id: "c-1" }], totalCount: 1 } as never;
    const second = { data: [{ id: "c-2" }], totalCount: 1 } as never;
    vi.mocked(service.getClaims)
      .mockResolvedValueOnce(first)
      .mockImplementationOnce(
        () => new Promise((r) => setTimeout(() => r(second), 40)),
      );

    const qc = makeQueryClient();
    const { result, rerender } = renderHook(
      ({ params }: { params: ClaimListParams }) => useClaims(params),
      {
        wrapper: makeWrapper(qc),
        initialProps: { params: { status: "CLAIMED" } as ClaimListParams },
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(first);

    // Simulate setStatusFilter on the page: rerender with new params.
    rerender({ params: { status: "UNCLAIMED" } as ClaimListParams });

    // Pre-fix, data would be undefined and isLoading would be true here —
    // triggering the skeleton swap the user measured as 608 ms INP.
    expect(result.current.data).toEqual(first);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isPlaceholderData).toBe(true);

    await waitFor(() => expect(result.current.data).toEqual(second));
    expect(result.current.isPlaceholderData).toBe(false);
  });
});

describe("useClaimSummary keeps previous data across filter-param changes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("data stays populated and isLoading stays false when params change", async () => {
    const first = {
      totalEarnings: "100",
      currencyBreakdown: {},
    } as never;
    const second = {
      totalEarnings: "200",
      currencyBreakdown: {},
    } as never;
    vi.mocked(service.getClaimSummary)
      .mockResolvedValueOnce(first)
      .mockImplementationOnce(
        () => new Promise((r) => setTimeout(() => r(second), 40)),
      );

    const qc = makeQueryClient();
    const { result, rerender } = renderHook(
      ({ params }: { params: Omit<ClaimListParams, "page" | "size"> }) =>
        useClaimSummary(params),
      {
        wrapper: makeWrapper(qc),
        initialProps: {
          params: { status: "CLAIMED" } as Omit<
            ClaimListParams,
            "page" | "size"
          >,
        },
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(first);

    rerender({
      params: { status: "UNCLAIMED" } as Omit<
        ClaimListParams,
        "page" | "size"
      >,
    });

    expect(result.current.data).toEqual(first);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isPlaceholderData).toBe(true);

    await waitFor(() => expect(result.current.data).toEqual(second));
    expect(result.current.isPlaceholderData).toBe(false);
  });
});
