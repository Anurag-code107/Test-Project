// shape: contracts/endpoints/redemption-advanced-analytics.yaml
//        contracts/models/redemption-advanced-analytics.md → TimeToFirstRedemptionResponse
// Covers: AC-1, AC-2, AC-4 (story US-03)
import { test, expect, type Route } from "@playwright/test";

// ─── User fixtures ─────────────────────────────────────────────────────────────
// shape: contracts/models/user.md + AuthContext permissions array

const CLIENT_ADMIN_WITH_ADVANCED = {
  id: "00000000-0000-0000-0000-000000000030",
  email: "cadmin@tenxengage.com",
  firstName: "Client",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.redemption_store",
    "action.redemption.view_analytics",
    "action.redemption.view_all_history",
    "action.redemption.analytics.advanced",
  ],
  clientRoleId: "00000000-0000-0000-0000-000000000097",
  clientRoleName: "CLIENT_ADMIN",
  organizationId: null,
  clientId: "00000000-0000-0000-0000-000000000011",
  clientName: "TechPartners",
  partnerCompanyId: null,
  partnerCompanyName: null,
  metadata: null,
  homeDashboardTemplate: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const LOGIN_RESPONSE_WITH_ADVANCED = {
  expiresIn: 3600,
  user: CLIENT_ADMIN_WITH_ADVANCED,
  enabledFeatures: ["redemption_store", "redemption_analytics_advanced"],
};

// ─── API response fixtures ─────────────────────────────────────────────────────

// shape: contracts/models/redemption-advanced-analytics.md → AnalyticsRefreshStatusResponse
const REFRESH_STATUS_FRESH = {
  isStale: false,
  lastRefreshedAt: "2026-06-20T06:00:00Z",
  stalenessThresholdHours: 4,
};

// shape: contracts/models/redemption-advanced-analytics.md → ItemBreakdownResponse
const ITEM_BREAKDOWN_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  items: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → SegmentBreakdownResponse
const SEGMENT_BREAKDOWN_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  segments: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-analytics-summary.md → AnalyticsSummaryResponse
const ANALYTICS_SUMMARY_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  redemptionRates: [],
  unredeemedBalances: [],
  failedCancelledRates: [],
  totalRedemptionCount: { total: 0, byStatus: {}, hasActivity: false },
};

// shape: contracts/models/redemption-advanced-analytics.md → TimeToFirstRedemptionResponse (Scenario 1)
const TTFR_WITH_MIXED_DATA = {
  filters: {},
  regions: [
    {
      region: "APAC",
      avgHoursToFirstRedemption: 24.5,
      medianHoursToFirstRedemption: 18.0,
      sampleCount: 120,
    },
    {
      region: "EMEA",
      avgHoursToFirstRedemption: null,
      medianHoursToFirstRedemption: null,
      sampleCount: 0,
    },
  ],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → TimeToFirstRedemptionResponse (Scenario 2)
const TTFR_EMPTY = {
  filters: {},
  regions: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

// ─── Login helper ──────────────────────────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginWithAdvanced(page: any) {
  // Catch-all: any unmocked /api/v1/* call returns an instant empty-success response
  // so TanStack Query doesn't exhaust retries (via the Vite proxy to a stopped backend).
  // Registered FIRST — specific mocks added later override it (last-registered-wins).
  await page.route("**/api/v1/**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: null, message: "OK", success: true, timestamp: "2026-01-01T00:00:00Z" }),
    });
  });

  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(LOGIN_RESPONSE_WITH_ADVANCED),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(LOGIN_RESPONSE_WITH_ADVANCED),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill("cadmin@tenxengage.com");
  await page.getByLabel(/password/i).fill("CAdmin@123");
  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;
  authenticated = true;
}

// ─── Shared route setup helper ─────────────────────────────────────────────────
// Route registration: broad first, specific last (Playwright last-registered-wins)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function setupBaseRoutes(page: any, ttfrPayload: object) {
  // Broad analytics route FIRST (less specific)
  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_SUMMARY_EMPTY)),
    });
  });

  // Item breakdown
  await page.route(
    "**/api/v1/redemption/analytics/advanced/item-breakdown*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(ITEM_BREAKDOWN_EMPTY)),
      });
    },
  );

  // Refresh status
  await page.route(
    "**/api/v1/redemption/analytics/advanced/refresh-status*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(REFRESH_STATUS_FRESH)),
      });
    },
  );

  // Segment breakdown
  await page.route(
    "**/api/v1/redemption/analytics/advanced/segment-breakdown*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(SEGMENT_BREAKDOWN_EMPTY)),
      });
    },
  );

  // TTFR — registered LAST (most specific, wins over broad route)
  await page.route(
    "**/api/v1/redemption/analytics/advanced/time-to-first-redemption*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(ttfrPayload)),
      });
    },
  );
}

// ─── Scenario 1: TTFR table renders N/A for regions with zero sample count ────

test(
  "TTFR table renders N/A for regions with zero sample count",
  async ({ page }) => {
    // Covers AC-1, AC-2, AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, TTFR_WITH_MIXED_DATA);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for TTFR section to be visible
    await page.waitForSelector('[aria-label="Time to First Redemption"]');

    // AC-4: APAC row avg shows "24.5"
    await expect(page.getByRole("cell", { name: "APAC" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "24.5" })).toBeVisible();

    // AC-2: EMEA row with sampleCount=0 — avg and median show "N/A"
    const naCells = page.getByRole("cell", { name: "N/A" });
    await expect(naCells.first()).toBeVisible();
  },
);

// ─── Scenario 2: TTFR empty state renders when no regions returned ─────────────

test(
  "TTFR empty state renders when no regions returned",
  async ({ page }) => {
    // Covers AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, TTFR_EMPTY);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for TTFR section
    await page.waitForSelector('[aria-label="Time to First Redemption"]');

    // AC-4: empty state copy
    await expect(
      page.getByText("No data for the selected period").first(),
    ).toBeVisible();
  },
);
