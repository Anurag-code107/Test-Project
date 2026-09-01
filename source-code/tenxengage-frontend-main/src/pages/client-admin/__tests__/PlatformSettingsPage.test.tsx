import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PlatformSettingsPage from "@/pages/client-admin/PlatformSettingsPage";

vi.mock("@/hooks/useFeatures", () => ({
  useFeatures: vi.fn(),
}));

// Mock heavy child components — we are only asserting the gating wraps,
// not the children's behavior.
vi.mock("@/components/settings/IntegrationsTab", () => ({
  IntegrationsTab: () => <div>IntegrationsTab Content</div>,
}));
vi.mock("@/components/settings/ManageDataTab", () => ({
  ManageDataTab: () => <div>ManageDataTab Content</div>,
}));
vi.mock("@/components/settings/LocationMappingSection", () => ({
  LocationMappingSection: () => <div>LocationMappingSection</div>,
}));
vi.mock("@/components/settings/FiscalYearMappingSection", () => ({
  FiscalYearMappingSection: () => <div>FiscalYearMappingSection</div>,
}));
vi.mock("@/components/settings/ManageRewardTypesSection", () => ({
  ManageRewardTypesSection: () => <div>ManageRewardTypesSection</div>,
}));
vi.mock("@/components/settings/RecommendationSettingsSection", () => ({
  RecommendationSettingsSection: () => <div>RecommendationSettingsSection</div>,
}));
vi.mock("@/components/settings/BrandingSection", () => ({
  BrandingSection: () => <div>BrandingSection Content</div>,
}));
vi.mock("@/components/settings/BuilderConfigTab", () => ({
  BuilderConfigTab: () => <div>BuilderConfigTab Content</div>,
}));
vi.mock("@/components/PageBanner", () => ({
  PageBanner: () => <div />,
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

describe("PlatformSettingsPage feature flag gating", () => {
  it("renders Integrations tab + content when api_access is enabled", () => {
    mockFeatures(["api_access", "custom_branding"]);

    render(<PlatformSettingsPage />);

    expect(screen.getByText("Integrations")).toBeDefined();
    // default active tab is integrations when the flag is on
    expect(screen.getByText("IntegrationsTab Content")).toBeDefined();
  });

  it("hides the Integrations tab when api_access is disabled", () => {
    mockFeatures(["custom_branding"]);

    render(<PlatformSettingsPage />);

    // Tab trigger gone — there is no element with the text "Integrations"
    expect(screen.queryByText("Integrations")).toBeNull();
    // Content gone — the mocked content is not in the tree
    expect(screen.queryByText("IntegrationsTab Content")).toBeNull();
  });

  it("falls back to Manage Data as the default active tab when api_access is off", () => {
    // Regression: when integrations is the default tab but api_access is off,
    // the page used to render an empty content area. The default should shift.
    mockFeatures([]);

    render(<PlatformSettingsPage />);

    expect(screen.getByText("ManageDataTab Content")).toBeDefined();
  });

  it("renders Branding tab + content when custom_branding is enabled", () => {
    mockFeatures(["api_access", "custom_branding"]);

    render(<PlatformSettingsPage />);

    expect(screen.getByText("Branding")).toBeDefined();
  });

  it("hides the Branding tab when custom_branding is disabled", () => {
    mockFeatures(["api_access"]);

    render(<PlatformSettingsPage />);

    expect(screen.queryByText("Branding")).toBeNull();
    expect(screen.queryByText("BrandingSection Content")).toBeNull();
  });

  it("hides BOTH gated tabs when both flags are disabled, leaving Manage Data, Business Rules, and Builder Config visible", () => {
    mockFeatures([]);

    render(<PlatformSettingsPage />);

    expect(screen.queryByText("Integrations")).toBeNull();
    expect(screen.queryByText("Branding")).toBeNull();
    expect(screen.getByText("Manage Data")).toBeDefined();
    expect(screen.getByText("Manage Business Rules")).toBeDefined();
    expect(screen.getByText("Builder Config")).toBeDefined();
  });

  it("renders Manage Reward Types subtab + content when multi_currency is enabled", async () => {
    mockFeatures(["multi_currency"]);
    const user = userEvent.setup();

    render(<PlatformSettingsPage />);

    // Activate the parent tab so the inner Tabs mount
    await user.click(screen.getByText("Manage Business Rules"));

    expect(screen.getByText("Manage Reward Types")).toBeDefined();

    // Activate the subtab and confirm content renders
    await user.click(screen.getByText("Manage Reward Types"));
    expect(screen.getByText("ManageRewardTypesSection")).toBeDefined();
  });

  it("hides the Manage Reward Types subtab when multi_currency is disabled", async () => {
    mockFeatures([]);
    const user = userEvent.setup();

    render(<PlatformSettingsPage />);

    await user.click(screen.getByText("Manage Business Rules"));

    // Sibling subtabs still rendered
    expect(screen.getByText("Location Mapping")).toBeDefined();
    expect(screen.getByText("Fiscal Year Mapping")).toBeDefined();

    // Gated subtab + its content gone
    expect(screen.queryByText("Manage Reward Types")).toBeNull();
    expect(screen.queryByText("ManageRewardTypesSection")).toBeNull();
  });
});
