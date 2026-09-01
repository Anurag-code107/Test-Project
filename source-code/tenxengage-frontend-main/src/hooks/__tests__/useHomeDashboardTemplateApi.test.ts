import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import React from "react";

vi.mock("@/services/home-dashboard-template.service", () => ({
  getHomeDashboardTemplates: vi.fn(),
  getHomeDashboardWidgets: vi.fn(),
  getHomeDashboardLayouts: vi.fn(),
  assignHomeDashboardTemplate: vi.fn(),
  clearHomeDashboardTemplate: vi.fn(),
}));

import * as service from "@/services/home-dashboard-template.service";
import {
  useHomeDashboardTemplates,
  useAssignHomeDashboardTemplate,
  useClearHomeDashboardTemplate,
} from "@/hooks/useHomeDashboardTemplateApi";

const mockGet = vi.mocked(service.getHomeDashboardTemplates);
const mockAssign = vi.mocked(service.assignHomeDashboardTemplate);
const mockClear = vi.mocked(service.clearHomeDashboardTemplate);

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client: qc }, children);
}

describe("useHomeDashboardTemplateApi", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockAssign.mockReset();
    mockClear.mockReset();
  });

  it("useHomeDashboardTemplates passes the roleType through to the service", async () => {
    mockGet.mockResolvedValue([]);
    const { result } = renderHook(() => useHomeDashboardTemplates("EXTERNAL"), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGet).toHaveBeenCalledWith("EXTERNAL");
  });

  it("useHomeDashboardTemplates without roleType calls service without a filter", async () => {
    mockGet.mockResolvedValue([]);
    const { result } = renderHook(() => useHomeDashboardTemplates(), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGet).toHaveBeenCalledWith(undefined);
  });

  it("useAssignHomeDashboardTemplate forwards roleId + templateId to the service", async () => {
    mockAssign.mockResolvedValue({} as never);
    const { result } = renderHook(() => useAssignHomeDashboardTemplate(), {
      wrapper,
    });
    result.current.mutate({ roleId: "r-1", templateId: "t-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockAssign).toHaveBeenCalledWith("r-1", "t-1");
  });

  it("useClearHomeDashboardTemplate forwards the role id to the service", async () => {
    mockClear.mockResolvedValue({} as never);
    const { result } = renderHook(() => useClearHomeDashboardTemplate(), {
      wrapper,
    });
    result.current.mutate("r-1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockClear).toHaveBeenCalledWith("r-1");
  });
});
