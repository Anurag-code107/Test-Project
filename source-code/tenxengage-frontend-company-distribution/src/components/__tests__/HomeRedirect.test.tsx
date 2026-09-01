import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// Navigate is mocked to surface its `to` target as text we can assert on.
vi.mock("react-router-dom", () => ({
  Navigate: ({ to }: { to: string }) => <div data-testid="navigate">{to}</div>,
}));
vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));
vi.mock("@/hooks/usePermissions", () => ({ usePermissions: vi.fn() }));

import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import HomeRedirect from "@/components/HomeRedirect";

const mockUseAuth = vi.mocked(useAuth);
const mockUsePermissions = vi.mocked(usePermissions);

function setup(perms: string[], isAuthenticated = true) {
  mockUseAuth.mockReturnValue({ isAuthenticated } as unknown as ReturnType<typeof useAuth>);
  const set = new Set(perms);
  mockUsePermissions.mockReturnValue({
    can: (k: string) => set.has(k),
    canAny: (...k: string[]) => k.some((x) => set.has(x)),
    canAll: (...k: string[]) => k.every((x) => set.has(x)),
    permissions: set,
  } as unknown as ReturnType<typeof usePermissions>);
}

function target() {
  return screen.getByTestId("navigate").textContent;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("HomeRedirect", () => {
  it("redirects a redemption_store-only user to /redemption-store", () => {
    setup(["module.redemption_store"]);
    render(<HomeRedirect />);
    expect(target()).toBe("/redemption-store");
  });

  it("prefers module.home over redemption_store when both are present", () => {
    setup(["module.home", "module.redemption_store"]);
    render(<HomeRedirect />);
    expect(target()).toBe("/home");
  });

  it("redirects unauthenticated users to /login", () => {
    setup([], false);
    render(<HomeRedirect />);
    expect(target()).toBe("/login");
  });

  it("falls back to /settings/profile when only the profile module is present", () => {
    setup(["module.settings.profile"]);
    render(<HomeRedirect />);
    expect(target()).toBe("/settings/profile");
  });
});
