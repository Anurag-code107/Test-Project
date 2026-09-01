import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useGiftCardPayoutReadiness } from "@/hooks/redemption-payout/useRedemptionProfile";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({ user: { id: "u1" } }),
}));

vi.mock("@/services/redemption-payout/redemption-payout.service", () => ({
  getRedemptionProfile: vi.fn(),
  listBanks: vi.fn(),
  listWallets: vi.fn(),
  listCards: vi.fn(),
  listWithdrawals: vi.fn(),
}));

import { getRedemptionProfile } from "@/services/redemption-payout/redemption-payout.service";

function renderReadiness() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderHook(() => useGiftCardPayoutReadiness(), {
    wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
  });
}

/**
 * The single rule gating the gift-card catalog: only an ENROLLED payout profile can receive a
 * gift-card payout. Shared by the enrollment banner and the store page's card dimming.
 */
describe("useGiftCardPayoutReadiness", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("is ready once ENROLLED", async () => {
    vi.mocked(getRedemptionProfile).mockResolvedValue({ enrollmentStatus: "ENROLLED" } as never);
    const { result } = renderReadiness();

    await waitFor(() => expect(result.current.isKnown).toBe(true));
    expect(result.current.isReady).toBe(true);
    expect(result.current.enrollmentStatus).toBe("ENROLLED");
  });

  it("is NOT ready when NOT_ENROLLED", async () => {
    vi.mocked(getRedemptionProfile).mockResolvedValue({ enrollmentStatus: "NOT_ENROLLED" } as never);
    const { result } = renderReadiness();

    await waitFor(() => expect(result.current.isKnown).toBe(true));
    expect(result.current.isReady).toBe(false);
    expect(result.current.enrollmentStatus).toBe("NOT_ENROLLED");
  });

  it("is NOT ready when enrollment FAILED", async () => {
    vi.mocked(getRedemptionProfile).mockResolvedValue({ enrollmentStatus: "FAILED" } as never);
    const { result } = renderReadiness();

    await waitFor(() => expect(result.current.isKnown).toBe(true));
    expect(result.current.isReady).toBe(false);
  });

  it("stays ready while loading — never dim the catalog on an unknown state", () => {
    vi.mocked(getRedemptionProfile).mockReturnValue(new Promise(() => {}) as never);
    const { result } = renderReadiness();

    expect(result.current.isKnown).toBe(false);
    expect(result.current.isReady).toBe(true);
  });

  it("stays ready on error (e.g. a non-payout role → 403)", async () => {
    vi.mocked(getRedemptionProfile).mockRejectedValue(new Error("403"));
    const { result } = renderReadiness();

    await waitFor(() => expect(vi.mocked(getRedemptionProfile)).toHaveBeenCalled());
    expect(result.current.isReady).toBe(true);
    expect(result.current.isKnown).toBe(false);
  });
});
