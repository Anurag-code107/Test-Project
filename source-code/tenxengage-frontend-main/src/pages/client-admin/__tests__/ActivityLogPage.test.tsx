import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import ActivityLogPage from "@/pages/client-admin/ActivityLogPage";

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

vi.mock("@/hooks/useAuditApi", () => ({
  useAuditLogs: () => ({
    data: { data: [], totalElements: 0, totalPages: 0 },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/components/PageBanner", () => ({
  PageBanner: ({ actions }: { actions?: React.ReactNode }) => (
    <div data-testid="page-banner">{actions}</div>
  ),
}));

import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";

const mockUsePermissions = vi.mocked(usePermissions);
const mockUseFeatures = vi.mocked(useFeatures);

function mockPermissions(permissions: string[]) {
  const set = new Set(permissions);
  mockUsePermissions.mockReturnValue({
    can: (key: string) => set.has(key),
    canAny: (...keys: string[]) => keys.some((k) => set.has(k)),
    canAll: (...keys: string[]) => keys.every((k) => set.has(k)),
    permissions: set,
  });
}

function mockFeatures(features: string[]) {
  const set = new Set(features);
  mockUseFeatures.mockReturnValue({
    has: (key: string) => set.has(key),
    hasAny: (...keys: string[]) => keys.some((k) => set.has(k)),
    hasAll: (...keys: string[]) => keys.every((k) => set.has(k)),
    features: set,
  });
}

describe("ActivityLogPage export feature gating", () => {
  it("renders the Export CSV button when permission AND export_reports feature are both granted", () => {
    mockPermissions(["action.activity_log.export"]);
    mockFeatures(["export_reports"]);

    render(<ActivityLogPage />);

    expect(screen.getByText("Export CSV")).toBeDefined();
  });

  it("hides the Export CSV button when export_reports is disabled even though permission is granted", () => {
    // Regression for BUG-048: the permission gate alone is not enough; the
    // tier-level feature gate must also pass.
    mockPermissions(["action.activity_log.export"]);
    mockFeatures([]);

    render(<ActivityLogPage />);

    expect(screen.queryByText("Export CSV")).toBeNull();
  });

  it("hides the Export CSV button when permission is denied even though export_reports is enabled", () => {
    // Permission gate continues to apply alongside the new feature gate.
    mockPermissions([]);
    mockFeatures(["export_reports"]);

    render(<ActivityLogPage />);

    expect(screen.queryByText("Export CSV")).toBeNull();
  });
});
