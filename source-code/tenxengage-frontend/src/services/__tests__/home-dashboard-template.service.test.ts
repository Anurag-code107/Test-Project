import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/lib/axios", () => {
  const mock = {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  };
  return { default: mock };
});

import api from "@/lib/axios";
import {
  getHomeDashboardTemplates,
  getHomeDashboardWidgets,
  getHomeDashboardLayouts,
  assignHomeDashboardTemplate,
  clearHomeDashboardTemplate,
} from "@/services/home-dashboard-template.service";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

function wrap<T>(data: T) {
  return {
    data: { data, success: true, message: "Success", timestamp: "" },
  };
}

describe("home-dashboard-template.service", () => {
  beforeEach(() => {
    mockApi.get.mockReset();
    mockApi.put.mockReset();
    mockApi.delete.mockReset();
  });

  it("getHomeDashboardTemplates without roleType does not pass params", async () => {
    mockApi.get.mockResolvedValue(wrap([]));
    await getHomeDashboardTemplates();
    expect(mockApi.get).toHaveBeenCalledWith(
      "/home-dashboard-templates",
      expect.objectContaining({ params: undefined }),
    );
  });

  it("getHomeDashboardTemplates with roleType passes it as a query param", async () => {
    mockApi.get.mockResolvedValue(wrap([]));
    await getHomeDashboardTemplates("INTERNAL");
    expect(mockApi.get).toHaveBeenCalledWith(
      "/home-dashboard-templates",
      expect.objectContaining({ params: { roleType: "INTERNAL" } }),
    );
  });

  it("getHomeDashboardWidgets hits the catalog endpoint", async () => {
    mockApi.get.mockResolvedValue(wrap([]));
    await getHomeDashboardWidgets();
    expect(mockApi.get).toHaveBeenCalledWith("/home-dashboard-widgets");
  });

  it("getHomeDashboardLayouts hits the layouts endpoint", async () => {
    mockApi.get.mockResolvedValue(wrap([]));
    await getHomeDashboardLayouts();
    expect(mockApi.get).toHaveBeenCalledWith("/home-dashboard-layouts");
  });

  it("assignHomeDashboardTemplate PUTs templateId to the role", async () => {
    const roleId = "role-1";
    const templateId = "tpl-2";
    mockApi.put.mockResolvedValue(wrap({ id: roleId }));
    await assignHomeDashboardTemplate(roleId, templateId);
    expect(mockApi.put).toHaveBeenCalledWith(
      `/client-roles/${roleId}/dashboard-template`,
      { templateId },
    );
  });

  it("clearHomeDashboardTemplate DELETEs the dashboard-template resource", async () => {
    mockApi.delete.mockResolvedValue(wrap({ id: "role-1" }));
    await clearHomeDashboardTemplate("role-1");
    expect(mockApi.delete).toHaveBeenCalledWith(
      "/client-roles/role-1/dashboard-template",
    );
  });
});
