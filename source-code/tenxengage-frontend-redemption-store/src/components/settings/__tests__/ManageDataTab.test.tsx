import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ManageDataTab } from "@/components/settings/ManageDataTab";

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

vi.mock("@/hooks/useDataObjectApi", () => ({
  useDataObjects: () => ({
    data: [
      {
        id: "obj-1",
        name: "Sales Data",
        description: "Sales transactions",
        fieldCount: 8,
        isDefault: true,
        connectorName: null,
      },
    ],
    isLoading: false,
  }),
  useCreateDataObject: () => ({ mutate: vi.fn() }),
  useDeleteDataObject: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/components/settings/DataObjectDetail", () => ({
  DataObjectDetail: () => <div>DataObjectDetail</div>,
}));

import { useFeatures } from "@/hooks/useFeatures";

const mockUseFeatures = vi.mocked(useFeatures);

function mockFeatures(features: string[]) {
  const set = new Set(features);
  mockUseFeatures.mockReturnValue({
    has: (key: string) => set.has(key),
    hasAny: (...keys: string[]) => keys.some((k) => set.has(k)),
    hasAll: (...keys: string[]) => keys.every((k) => set.has(k)),
    features: set,
  });
}

describe("ManageDataTab bulk_import gating", () => {
  it("renders the Manual Upload button when bulk_import is enabled", () => {
    mockFeatures(["bulk_import"]);

    render(<ManageDataTab />);

    expect(screen.getByText("Manual Upload")).toBeDefined();
  });

  it("hides the Manual Upload button when bulk_import is disabled", () => {
    // Other action buttons (e.g. "Tag Deals" on Sales Data) stay visible —
    // only the bulk-upload affordance is gated.
    mockFeatures([]);

    render(<ManageDataTab />);

    expect(screen.queryByText("Manual Upload")).toBeNull();
    // Sales Data card itself still renders
    expect(screen.getByText("Sales Data")).toBeDefined();
  });
});
