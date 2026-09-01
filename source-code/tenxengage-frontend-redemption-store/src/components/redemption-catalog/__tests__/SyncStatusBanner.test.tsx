import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { SyncStatusBanner } from "@/components/redemption-catalog/SyncStatusBanner";

const mockUseIntegrationHealth = vi.fn();
const mockUseTriggerCatalogSync = vi.fn();
const mockToastError = vi.fn();
const mockToastSuccess = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useIntegrationHealth: () => mockUseIntegrationHealth(),
  useTriggerCatalogSync: () => mockUseTriggerCatalogSync(),
}));

vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  }),
}));

const HEALTH_DATA = {
  syncStatus: "SUCCESS" as const,
  lastSyncAt: "2026-05-13T10:25:49Z",
  failedSyncCount: 0,
  recentWebhooks: [],
};

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("SyncStatusBanner", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseIntegrationHealth.mockReturnValue({ isLoading: false, data: HEALTH_DATA });
    mockUseTriggerCatalogSync.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders lastSyncAt and syncStatus", () => {
    mockUseIntegrationHealth.mockReturnValue({ isLoading: false, data: HEALTH_DATA });

    render(<SyncStatusBanner />, { wrapper });

    expect(screen.getByTestId("sync-status-banner")).toBeDefined();
    expect(screen.getByTestId("sync-status-badge").textContent).toBe("SUCCESS");
    expect(screen.getByTestId("last-sync-at").textContent).not.toBe("Never");
  });

  it("trigger sync button calls mutation", async () => {
    const mutate = vi.fn();
    mockUseTriggerCatalogSync.mockReturnValue({ mutate, isPending: false });
    mockUseIntegrationHealth.mockReturnValue({ isLoading: false, data: HEALTH_DATA });

    render(<SyncStatusBanner />, { wrapper });

    await userEvent.click(screen.getByTestId("trigger-sync-btn"));

    expect(mutate).toHaveBeenCalledWith(undefined, expect.any(Object));
  });

  it("shows loading state during mutation", () => {
    mockUseTriggerCatalogSync.mockReturnValue({ mutate: vi.fn(), isPending: true });
    mockUseIntegrationHealth.mockReturnValue({ isLoading: false, data: HEALTH_DATA });

    render(<SyncStatusBanner />, { wrapper });

    const btn = screen.getByTestId("trigger-sync-btn");
    expect(btn.hasAttribute("disabled")).toBe(true);
    expect(btn.textContent).toContain("Syncing");
  });

  it("shows 429 toast on rate limit", async () => {
    const mutate = vi.fn().mockImplementation((_vars, options) => {
      options.onError({ response: { status: 429 } });
    });
    mockUseTriggerCatalogSync.mockReturnValue({ mutate, isPending: false });
    mockUseIntegrationHealth.mockReturnValue({ isLoading: false, data: HEALTH_DATA });

    render(<SyncStatusBanner />, { wrapper });

    await userEvent.click(screen.getByTestId("trigger-sync-btn"));

    expect(mockToastError).toHaveBeenCalledWith(
      expect.stringContaining("rate limit"),
    );
  });
});
