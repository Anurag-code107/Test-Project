import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { DataOperationsPanel } from "@/components/settings/DataOperationsPanel";
import type { DataObjectDetailResponse } from "@/types/data-object.types";

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

vi.mock("@/hooks/useDataOperationsApi", () => ({
  useUploadHistory: () => ({ data: { items: [] } }),
  useUploadFile: () => ({ mutate: vi.fn(), mutateAsync: vi.fn() }),
  useDownloadTemplate: () => ({ mutate: vi.fn() }),
  useConnectorPull: () => ({ mutate: vi.fn() }),
  useTaggingHistory: () => ({ data: { items: [] } }),
  useRunTaggingJob: () => ({ mutate: vi.fn() }),
  useSyncSchedule: () => ({ data: null }),
  useUpdateSyncSchedule: () => ({ mutate: vi.fn() }),
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

const fixture: DataObjectDetailResponse = {
  id: "obj-1",
  name: "Sales Data",
  description: "Sales transactions",
  isDefault: true,
  sortOrder: 0,
  fields: [],
  connectorMapping: null,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe("DataOperationsPanel bulk_import gating", () => {
  it("renders the Manual Data Uploads collapsible when bulk_import is enabled", () => {
    mockFeatures(["bulk_import"]);

    render(<DataOperationsPanel dataObject={fixture} />);

    expect(screen.getByText("Manual Data Uploads")).toBeDefined();
  });

  it("hides the Manual Data Uploads collapsible when bulk_import is disabled", () => {
    // Robert Snow direction: when bulk_import is off, the entire Manual Data
    // Uploads collapsible (connector pull + manual file upload) is gone.
    // Sibling collapsibles (e.g. Tag Eligible Deals on Sales Data) remain
    // unaffected — they're gated by other rules, not by bulk_import.
    mockFeatures([]);

    render(<DataOperationsPanel dataObject={fixture} />);

    expect(screen.queryByText("Manual Data Uploads")).toBeNull();
  });
});
