import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import React from "react";

vi.mock("@/services/location.service", () => ({
  getLocationHierarchy: vi.fn(),
  getLocationFilterOptions: vi.fn(),
  getLocationBuilderOptions: vi.fn(),
  createLocationLevel: vi.fn(),
  updateLocationLevel: vi.fn(),
  deleteLocationLevel: vi.fn(),
  createLocationValue: vi.fn(),
  updateLocationValue: vi.fn(),
  deleteLocationValue: vi.fn(),
  updateLocationLevelSettings: vi.fn(),
}));

import * as service from "@/services/location.service";
import {
  useLocationHierarchy,
  useLocationFilterOptions,
  useLocationBuilderOptions,
  useCreateLocationLevel,
  useUpdateLocationLevel,
  useDeleteLocationLevel,
  useCreateLocationValue,
  useUpdateLocationValue,
  useDeleteLocationValue,
  useUpdateLocationLevelSettings,
} from "@/hooks/useLocationApi";

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function makeWrapper(qc: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return React.createElement(QueryClientProvider, { client: qc }, children);
  };
}

describe("useLocationApi query keys are nested under a shared root prefix", () => {
  beforeEach(() => {
    vi.mocked(service.getLocationHierarchy).mockReset();
    vi.mocked(service.getLocationFilterOptions).mockReset();
    vi.mocked(service.getLocationBuilderOptions).mockReset();
  });

  it("useLocationHierarchy keys under ['location', 'hierarchy']", async () => {
    vi.mocked(service.getLocationHierarchy).mockResolvedValue({
      levels: [],
    } as never);
    const qc = makeQueryClient();
    const { result } = renderHook(() => useLocationHierarchy(), {
      wrapper: makeWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      qc.getQueryState(["location", "hierarchy"]),
    ).toBeDefined();
  });

  it("useLocationFilterOptions keys under ['location', 'filter-options']", async () => {
    vi.mocked(service.getLocationFilterOptions).mockResolvedValue({
      levels: [],
    } as never);
    const qc = makeQueryClient();
    const { result } = renderHook(() => useLocationFilterOptions(), {
      wrapper: makeWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      qc.getQueryState(["location", "filter-options"]),
    ).toBeDefined();
  });

  it("useLocationBuilderOptions keys under ['location', 'builder-options']", async () => {
    vi.mocked(service.getLocationBuilderOptions).mockResolvedValue({
      levels: [],
    } as never);
    const qc = makeQueryClient();
    const { result } = renderHook(() => useLocationBuilderOptions(), {
      wrapper: makeWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      qc.getQueryState(["location", "builder-options"]),
    ).toBeDefined();
  });
});

describe("useLocationApi mutations invalidate every location-related cache", () => {
  /**
   * Regression for BUG-029 — pre-fix, mutations only invalidated
   * ["location-hierarchy"], leaving ["location-filter-options"] stale until
   * the user manually refreshed. Post-fix, every mutation's onSuccess calls
   * invalidateQueries with the ["location"] root prefix, which TanStack
   * Query matches against every nested query.
   *
   * Each test spies on queryClient.invalidateQueries and asserts that after
   * a successful mutation, it was called with { queryKey: ["location"] }.
   */

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("useCreateLocationLevel invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.createLocationLevel).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useCreateLocationLevel(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ name: "Country", depth: 1 } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useUpdateLocationLevel invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.updateLocationLevel).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUpdateLocationLevel(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ id: "lvl-1", data: { name: "Country" } } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useDeleteLocationLevel invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.deleteLocationLevel).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteLocationLevel(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate("lvl-1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useCreateLocationValue invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.createLocationValue).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useCreateLocationValue(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ name: "Canada", levelId: "lvl-1" } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useUpdateLocationValue invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.updateLocationValue).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUpdateLocationValue(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ id: "v-1", data: { name: "Canada" } } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useDeleteLocationValue invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.deleteLocationValue).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteLocationValue(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate("v-1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });

  it("useUpdateLocationLevelSettings invalidates the ['location'] prefix on success", async () => {
    vi.mocked(service.updateLocationLevelSettings).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUpdateLocationLevelSettings(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({
      id: "lvl-1",
      data: { useInFilters: true },
    } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["location"] });
  });
});
