import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { usePermissions } from "@/hooks/usePermissions";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "@/hooks/useAuth";

const mockUseAuth = vi.mocked(useAuth);

describe("usePermissions", () => {
  it("can returns true for granted permission", () => {
    mockUseAuth.mockReturnValue({
      user: { permissions: ["action.claim.submit", "action.users.view"] },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(result.current.can("action.claim.submit")).toBe(true);
  });

  it("can returns false for missing permission", () => {
    mockUseAuth.mockReturnValue({
      user: { permissions: ["action.claim.submit"] },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(result.current.can("action.users.manage")).toBe(false);
  });

  it("canAny returns true if any match", () => {
    mockUseAuth.mockReturnValue({
      user: { permissions: ["action.claim.submit"] },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(
      result.current.canAny("action.claim.submit", "action.users.manage"),
    ).toBe(true);
  });

  it("canAny returns false if none match", () => {
    mockUseAuth.mockReturnValue({
      user: { permissions: ["action.claim.submit"] },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(
      result.current.canAny("action.users.manage", "action.incentives.create"),
    ).toBe(false);
  });

  it("canAll returns true if all match", () => {
    mockUseAuth.mockReturnValue({
      user: {
        permissions: ["action.claim.submit", "action.users.view"],
      },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(
      result.current.canAll("action.claim.submit", "action.users.view"),
    ).toBe(true);
  });

  it("canAll returns false if any missing", () => {
    mockUseAuth.mockReturnValue({
      user: { permissions: ["action.claim.submit"] },
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(
      result.current.canAll("action.claim.submit", "action.users.manage"),
    ).toBe(false);
  });

  it("handles null user gracefully", () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuth>);

    const { result } = renderHook(() => usePermissions());

    expect(result.current.can("anything")).toBe(false);
    expect(result.current.permissions.size).toBe(0);
  });
});
