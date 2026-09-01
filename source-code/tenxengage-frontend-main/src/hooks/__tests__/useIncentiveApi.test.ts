import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import React from "react";

vi.mock("@/services/incentive.service", () => ({
  getIncentiveById: vi.fn(),
  getIncentives: vi.fn(),
}));

import * as service from "@/services/incentive.service";
import { useIncentive } from "@/hooks/useIncentiveApi";
import type {
  IncentiveResponse,
  IncentiveDetailResponse,
} from "@/types/incentive.types";
import type { PaginatedResponse } from "@/types/api.types";

const mockGetById = vi.mocked(service.getIncentiveById);

function makeListRow(
  id: string,
  overrides: Partial<IncentiveResponse> = {},
): IncentiveResponse {
  return {
    id,
    name: `Incentive ${id}`,
    incentiveType: "TRAINING",
    status: "ACTIVE",
    createdByName: "Test",
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

function makeListCache(
  rows: IncentiveResponse[],
): PaginatedResponse<IncentiveResponse> {
  return {
    data: rows,
    page: 0,
    pageSize: rows.length,
    totalElements: rows.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

function makeDetail(
  id: string,
  trainingRequiredCount: number,
): IncentiveDetailResponse {
  return {
    id,
    name: `Incentive ${id}`,
    incentiveType: "TRAINING",
    status: "ACTIVE",
    createdByName: "Test",
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    trainingRequiredCount,
  } as IncentiveDetailResponse;
}

function buildWrapper(qc: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return React.createElement(QueryClientProvider, { client: qc }, children);
  };
}

function freshClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

describe("useIncentive — BUG-074 cache-write narrowing", () => {
  beforeEach(() => {
    mockGetById.mockReset();
  });

  it("preserves list-cache identity when the freshly fetched value already matches", async () => {
    const qc = freshClient();
    const list = makeListCache([
      makeListRow("a", { trainingRequiredCount: 3 }),
      makeListRow("b", { trainingRequiredCount: 5 }),
    ]);
    qc.setQueryData(["incentives", { pageSize: 500 }], list);

    mockGetById.mockResolvedValue(makeDetail("a", 3));

    const { result } = renderHook(() => useIncentive("a"), {
      wrapper: buildWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const after = qc.getQueryData<PaginatedResponse<IncentiveResponse>>([
      "incentives",
      { pageSize: 500 },
    ]);
    expect(after).toBe(list);
    expect(after?.data).toBe(list.data);
  });

  it("preserves list-cache identity when the row isn't present in that list", async () => {
    const qc = freshClient();
    const list = makeListCache([
      makeListRow("x", { trainingRequiredCount: 2 }),
      makeListRow("y", { trainingRequiredCount: 4 }),
    ]);
    qc.setQueryData(["incentives", { pageSize: 500 }], list);

    mockGetById.mockResolvedValue(makeDetail("a", 7));

    const { result } = renderHook(() => useIncentive("a"), {
      wrapper: buildWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const after = qc.getQueryData<PaginatedResponse<IncentiveResponse>>([
      "incentives",
      { pageSize: 500 },
    ]);
    expect(after).toBe(list);
    expect(after?.data).toBe(list.data);
  });

  it("rewrites the list-cache row when the value differs, but only the matching row gets a new identity", async () => {
    const qc = freshClient();
    const rowA = makeListRow("a", { trainingRequiredCount: 1 });
    const rowB = makeListRow("b", { trainingRequiredCount: 2 });
    const list = makeListCache([rowA, rowB]);
    qc.setQueryData(["incentives", { pageSize: 500 }], list);

    mockGetById.mockResolvedValue(makeDetail("a", 9));

    const { result } = renderHook(() => useIncentive("a"), {
      wrapper: buildWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const after = qc.getQueryData<PaginatedResponse<IncentiveResponse>>([
      "incentives",
      { pageSize: 500 },
    ]);
    expect(after).not.toBe(list);
    expect(after?.data).not.toBe(list.data);
    // Updated row has a new identity; sibling rows keep their identity
    // (slice is used instead of map).
    expect(after?.data[0]).not.toBe(rowA);
    expect(after?.data[0]?.trainingRequiredCount).toBe(9);
    expect(after?.data[1]).toBe(rowB);
  });

  it("does not touch sibling detail caches under the same prefix", async () => {
    const qc = freshClient();
    const list = makeListCache([
      makeListRow("a", { trainingRequiredCount: 1 }),
    ]);
    qc.setQueryData(["incentives", { pageSize: 500 }], list);

    const otherDetail = makeDetail("z", 12);
    qc.setQueryData(["incentives", "z"], otherDetail);

    mockGetById.mockResolvedValue(makeDetail("a", 9));

    const { result } = renderHook(() => useIncentive("a"), {
      wrapper: buildWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // The sibling detail cache for "z" must keep its exact reference — the
    // BUG-074 prefix-match used to invoke the updater on every cache key
    // under "incentives", which the predicate now excludes.
    expect(qc.getQueryData(["incentives", "z"])).toBe(otherDetail);
  });

  it("skips the cache write entirely when the detail has no trainingRequiredCount", async () => {
    const qc = freshClient();
    const list = makeListCache([makeListRow("a")]);
    qc.setQueryData(["incentives", { pageSize: 500 }], list);

    mockGetById.mockResolvedValue({
      ...makeDetail("a", 0),
      trainingRequiredCount: undefined,
    } as IncentiveDetailResponse);

    const { result } = renderHook(() => useIncentive("a"), {
      wrapper: buildWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    const after = qc.getQueryData<PaginatedResponse<IncentiveResponse>>([
      "incentives",
      { pageSize: 500 },
    ]);
    expect(after).toBe(list);
  });
});
