import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import React from "react";

vi.mock("@/services/data-object.service", () => ({
  getDataObjects: vi.fn(),
  getDataObjectById: vi.fn(),
  createDataObject: vi.fn(),
  updateDataObject: vi.fn(),
  deleteDataObject: vi.fn(),
  addField: vi.fn(),
  updateField: vi.fn(),
  deleteField: vi.fn(),
  setConnectorMapping: vi.fn(),
  removeConnectorMapping: vi.fn(),
  getRuleFields: vi.fn(),
}));

import * as service from "@/services/data-object.service";
import {
  useDataObjects,
  useDataObject,
  useRuleFields,
  useCreateDataObject,
  useUpdateDataObject,
  useDeleteDataObject,
  useAddField,
  useUpdateField,
  useDeleteField,
  useSetConnectorMapping,
  useRemoveConnectorMapping,
} from "@/hooks/useDataObjectApi";
import { useAvailableCustomFields } from "@/hooks/useAvailableCustomFields";

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

describe("useDataObjectApi query keys are nested under a shared root prefix", () => {
  beforeEach(() => {
    vi.mocked(service.getDataObjects).mockReset();
    vi.mocked(service.getDataObjectById).mockReset();
    vi.mocked(service.getRuleFields).mockReset();
  });

  it("useDataObjects keys under ['data-objects', 'list']", async () => {
    vi.mocked(service.getDataObjects).mockResolvedValue([] as never);
    const qc = makeQueryClient();
    const { result } = renderHook(() => useDataObjects(), {
      wrapper: makeWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(qc.getQueryState(["data-objects", "list"])).toBeDefined();
  });

  it("useDataObject keys under ['data-objects', 'by-id', id]", async () => {
    vi.mocked(service.getDataObjectById).mockResolvedValue({
      id: "do-1",
      name: "Partner Data",
      fields: [],
    } as never);
    const qc = makeQueryClient();
    const { result } = renderHook(() => useDataObject("do-1"), {
      wrapper: makeWrapper(qc),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      qc.getQueryState(["data-objects", "by-id", "do-1"]),
    ).toBeDefined();
  });

  it("useRuleFields keys under ['data-objects', 'rule-fields', ...]", async () => {
    vi.mocked(service.getRuleFields).mockResolvedValue([] as never);
    const qc = makeQueryClient();
    const { result } = renderHook(
      () => useRuleFields("do-1", "Sales Data"),
      { wrapper: makeWrapper(qc) },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(
      qc.getQueryState([
        "data-objects",
        "rule-fields",
        "do-1",
        "Sales Data",
      ]),
    ).toBeDefined();
  });

  it("useAvailableCustomFields keys under ['data-objects', 'available-custom-fields', ...]", async () => {
    // Returns [] when sectionKey doesn't map to any relevant data objects, so
    // no service mocks need to fire — we still get the cache entry under the
    // expected key.
    const qc = makeQueryClient();
    const { result } = renderHook(
      () => useAvailableCustomFields("unknown-section", null),
      { wrapper: makeWrapper(qc) },
    );
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(
      qc.getQueryState([
        "data-objects",
        "available-custom-fields",
        "unknown-section",
        null,
      ]),
    ).toBeDefined();
  });
});

describe("useDataObjectApi mutations invalidate the ['data-objects'] root prefix", () => {
  /**
   * Regression for BUG-059 — pre-fix, mutations only invalidated
   * ["data-objects"] (or a narrower ["data-objects", id]), leaving the sibling
   * caches ["available-custom-fields", ...] (read by Builder Config's field
   * editor) and ["rule-fields", ...] (read by the Incentive Rules Engine
   * sentence builder) stale until staleTime expired or the page reloaded.
   *
   * Post-fix, every mutation's onSuccess/onSettled invalidates the
   * ["data-objects"] root once. TanStack Query's prefix matching cascades to
   * every nested key — list, by-id, rule-fields, available-custom-fields.
   *
   * Each test spies on queryClient.invalidateQueries (NOT state.isInvalidated,
   * which clears as soon as the auto-refetch completes) and asserts that after
   * a successful mutation it was called with { queryKey: ["data-objects"] }.
   */

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("useCreateDataObject invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.createDataObject).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useCreateDataObject(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ name: "Custom Data" } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useUpdateDataObject invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.updateDataObject).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUpdateDataObject(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ id: "do-1", data: { name: "Renamed" } } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useDeleteDataObject invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.deleteDataObject).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteDataObject(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate("do-1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useAddField invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.addField).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useAddField(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({
      dataObjectId: "do-1",
      data: { name: "New Region", dataType: "LIST" },
    } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useUpdateField invalidates the ['data-objects'] prefix on settle", async () => {
    vi.mocked(service.updateField).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useUpdateField(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({
      dataObjectId: "do-1",
      fieldId: "f-1",
      data: { name: "Renamed Field" },
    } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useDeleteField invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.deleteField).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useDeleteField(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({ dataObjectId: "do-1", fieldId: "f-1" } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useSetConnectorMapping invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.setConnectorMapping).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useSetConnectorMapping(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate({
      dataObjectId: "do-1",
      data: { connectorId: "c-1", mappings: [] },
    } as never);
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });

  it("useRemoveConnectorMapping invalidates the ['data-objects'] prefix on success", async () => {
    vi.mocked(service.removeConnectorMapping).mockResolvedValue({} as never);
    const qc = makeQueryClient();
    const spy = vi.spyOn(qc, "invalidateQueries");
    const { result } = renderHook(() => useRemoveConnectorMapping(), {
      wrapper: makeWrapper(qc),
    });
    result.current.mutate("do-1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith({ queryKey: ["data-objects"] });
  });
});
