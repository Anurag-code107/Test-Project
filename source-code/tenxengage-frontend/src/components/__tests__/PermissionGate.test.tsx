import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { PermissionGate } from "@/components/PermissionGate";

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: vi.fn(),
}));

import { usePermissions } from "@/hooks/usePermissions";

const mockUsePermissions = vi.mocked(usePermissions);

describe("PermissionGate", () => {
  it("renders children when single permission granted", () => {
    mockUsePermissions.mockReturnValue({
      can: (key: string) => key === "action.create",
      canAny: vi.fn(),
      canAll: vi.fn(),
      permissions: new Set(["action.create"]),
    });

    render(
      <PermissionGate permission="action.create">
        <span>Visible</span>
      </PermissionGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });

  it("hides children when single permission denied", () => {
    mockUsePermissions.mockReturnValue({
      can: () => false,
      canAny: vi.fn(),
      canAll: vi.fn(),
      permissions: new Set(),
    });

    render(
      <PermissionGate permission="action.create">
        <span>Hidden</span>
      </PermissionGate>,
    );

    expect(screen.queryByText("Hidden")).toBeNull();
  });

  it("renders fallback when denied", () => {
    mockUsePermissions.mockReturnValue({
      can: () => false,
      canAny: vi.fn(),
      canAll: vi.fn(),
      permissions: new Set(),
    });

    render(
      <PermissionGate
        permission="action.create"
        fallback={<span>No Access</span>}
      >
        <span>Hidden</span>
      </PermissionGate>,
    );

    expect(screen.getByText("No Access")).toBeDefined();
    expect(screen.queryByText("Hidden")).toBeNull();
  });

  it("renders children when any permission matches", () => {
    mockUsePermissions.mockReturnValue({
      can: vi.fn(),
      canAny: (...keys: string[]) => keys.includes("action.edit"),
      canAll: vi.fn(),
      permissions: new Set(["action.edit"]),
    });

    render(
      <PermissionGate any={["action.edit", "action.delete"]}>
        <span>Visible</span>
      </PermissionGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });

  it("renders children when all permissions match", () => {
    mockUsePermissions.mockReturnValue({
      can: vi.fn(),
      canAny: vi.fn(),
      canAll: () => true,
      permissions: new Set(["action.edit", "action.delete"]),
    });

    render(
      <PermissionGate all={["action.edit", "action.delete"]}>
        <span>Visible</span>
      </PermissionGate>,
    );

    expect(screen.getByText("Visible")).toBeDefined();
  });
});
