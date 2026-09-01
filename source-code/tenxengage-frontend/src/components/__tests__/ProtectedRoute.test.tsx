import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import ProtectedRoute from "@/components/ProtectedRoute";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";

const mockUseAuth = vi.mocked(useAuth);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseFeatures = vi.mocked(useFeatures);

function TestApp({
  initialPath = "/protected",
  permission,
  anyPermission,
  feature,
  anyFeature,
}: {
  initialPath?: string;
  permission?: string;
  anyPermission?: string[];
  feature?: string;
  anyFeature?: string[];
}) {
  return (
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          element={
            <ProtectedRoute
              permission={permission}
              anyPermission={anyPermission}
              feature={feature}
              anyFeature={anyFeature}
            />
          }
        >
          <Route path="/protected" element={<div>Protected Content</div>} />
        </Route>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/" element={<div>Home Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function mockPermissions(permissions: string[]) {
  const permSet = new Set(permissions);
  mockUsePermissions.mockReturnValue({
    can: (key: string) => permSet.has(key),
    canAny: (...keys: string[]) => keys.some((k) => permSet.has(k)),
    canAll: (...keys: string[]) => keys.every((k) => permSet.has(k)),
    permissions: permSet,
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

describe("ProtectedRoute", () => {
  beforeEach(() => {
    // Default: no features enabled. Tests that exercise feature gating
    // override this explicitly.
    mockFeatures([]);
  });

  it("redirects to login when not authenticated", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions([]);

    render(<TestApp />);

    expect(screen.getByText("Login Page")).toBeDefined();
  });

  it("renders outlet when authenticated with no permission requirement", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.home"]);

    render(<TestApp />);

    expect(screen.getByText("Protected Content")).toBeDefined();
  });

  it("renders outlet when user has the required permission", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.home", "module.incentive_builder"]);

    render(<TestApp permission="module.incentive_builder" />);

    expect(screen.getByText("Protected Content")).toBeDefined();
  });

  it("shows access denied when user lacks the required permission", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.reporting"]);

    render(<TestApp permission="module.incentive_builder" />);

    expect(screen.getByText(/you don't have access/i)).toBeDefined();
  });

  it("renders when user has any of the required permissions", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.incentives.sales"]);

    render(
      <TestApp
        anyPermission={["module.manage_incentives", "module.incentives.sales"]}
      />,
    );

    expect(screen.getByText("Protected Content")).toBeDefined();
  });

  it("shows access denied when user has none of the required permissions", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.reporting"]);

    render(
      <TestApp
        anyPermission={["module.manage_incentives", "module.incentives.sales"]}
      />,
    );

    expect(screen.getByText(/you don't have access/i)).toBeDefined();
  });

  it("renders outlet when feature is enabled for the tenant", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.activity_log"]);
    mockFeatures(["audit_log"]);

    render(<TestApp permission="module.activity_log" feature="audit_log" />);

    expect(screen.getByText("Protected Content")).toBeDefined();
  });

  it("shows access denied when permission passes but feature is disabled for the tier", () => {
    // Regression for BUG-035: a tenant with the permission but whose tier
    // no longer includes the feature must NOT see the gated route.
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.activity_log"]);
    mockFeatures([]);

    render(<TestApp permission="module.activity_log" feature="audit_log" />);

    expect(screen.getByText(/you don't have access/i)).toBeDefined();
    expect(screen.queryByText("Protected Content")).toBeNull();
  });

  it("renders outlet when any of the required features is enabled", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.home"]);
    mockFeatures(["deal_qualifier"]);

    render(
      <TestApp
        permission="module.home"
        anyFeature={["audit_log", "deal_qualifier"]}
      />,
    );

    expect(screen.getByText("Protected Content")).toBeDefined();
  });

  it("shows access denied when none of the anyFeature keys are enabled", () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
    } as unknown as ReturnType<typeof useAuth>);
    mockPermissions(["module.home"]);
    mockFeatures([]);

    render(
      <TestApp
        permission="module.home"
        anyFeature={["audit_log", "deal_qualifier"]}
      />,
    );

    expect(screen.getByText(/you don't have access/i)).toBeDefined();
  });
});
