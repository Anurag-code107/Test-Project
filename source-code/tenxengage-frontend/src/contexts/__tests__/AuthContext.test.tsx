import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useContext } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

vi.mock("@/services/auth.service", () => ({
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
}));

vi.mock("@/hooks/useIdleTimer", () => ({
  useIdleTimer: () => ({
    showWarning: false,
    remainingSeconds: 0,
    stayActive: vi.fn(),
  }),
}));

vi.mock("@/components/SessionExpiryDialog", () => ({
  SessionExpiryDialog: () => null,
}));

import * as authService from "@/services/auth.service";
import { AuthContext, AuthProvider } from "@/contexts/AuthContext";
import type { AuthUser } from "@/types/auth.types";

const mockRefresh = vi.mocked(authService.refresh);

function testUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: "u-1",
    email: "test@example.com",
    firstName: "Alice",
    lastName: "Test",
    permissions: [],
    clientRoleId: "r-1",
    clientRoleName: "Client Admin",
    organizationId: null,
    clientId: "c-1",
    clientName: "Acme",
    partnerCompanyId: null,
    partnerCompanyName: null,
    status: "ACTIVE",
    ...overrides,
  };
}

function Consumer() {
  const ctx = useContext(AuthContext);
  if (!ctx) return null;
  return (
    <div>
      <span data-testid="first-name">{ctx.user?.firstName ?? "none"}</span>
      <span data-testid="template-name">
        {ctx.user?.homeDashboardTemplate?.name ?? "no-template"}
      </span>
      <button data-testid="refresh-btn" onClick={() => void ctx.refreshUser()}>
        refresh
      </button>
    </div>
  );
}

function wrap(children: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={qc}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}

describe("AuthContext.refreshUser", () => {
  beforeEach(() => {
    mockRefresh.mockReset();
  });

  it("re-fetches the user from authService.refresh() and updates state", async () => {
    mockRefresh.mockResolvedValueOnce({
      expiresIn: 3600,
      user: testUser({ firstName: "Alice" }),
      enabledFeatures: [],
    });

    render(wrap(<Consumer />));

    await waitFor(() =>
      expect(screen.getByTestId("first-name").textContent).toBe("Alice"),
    );
    expect(mockRefresh).toHaveBeenCalledTimes(1);

    // Simulate a server-side change — the next refresh returns a different template
    mockRefresh.mockResolvedValueOnce({
      expiresIn: 3600,
      user: testUser({
        firstName: "Alice",
        homeDashboardTemplate: {
          id: "t-2",
          clientId: "c-1",
          name: "Approver",
          description: null,
          roleType: "INTERNAL",
          layout: { rows: [] },
          isSystem: true,
          createdAt: "2026-04-19T00:00:00Z",
          updatedAt: "2026-04-19T00:00:00Z",
        },
      }),
      enabledFeatures: [],
    });

    await userEvent.click(screen.getByTestId("refresh-btn"));

    await waitFor(() =>
      expect(screen.getByTestId("template-name").textContent).toBe("Approver"),
    );
    expect(mockRefresh).toHaveBeenCalledTimes(2);
  });

  it("tolerates a refresh failure — user stays null without throwing", async () => {
    mockRefresh.mockRejectedValue(new Error("no cookie"));

    render(wrap(<Consumer />));

    await waitFor(() =>
      expect(screen.getByTestId("first-name").textContent).toBe("none"),
    );

    // Calling refreshUser after an initial failure should not throw either
    await userEvent.click(screen.getByTestId("refresh-btn"));

    expect(screen.getByTestId("first-name").textContent).toBe("none");
  });
});
