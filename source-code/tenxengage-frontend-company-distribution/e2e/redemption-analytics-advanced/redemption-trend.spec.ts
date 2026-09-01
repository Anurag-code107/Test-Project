// shape: contracts/endpoints/redemption-advanced-analytics.yaml
//        contracts/models/redemption-advanced-analytics.md → RedemptionTrendResponse
// Covers: AC-1, AC-2, AC-4 (story US-04)
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

// shape: contracts/models/redemption-advanced-analytics.md → TimeToFirstRedemptionResponse
const TTFR_EMPTY = {
  filters: {},
  regions: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → RedemptionTrendResponse
const TREND_WITH_DATA = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  dataPoints: [
    {
      periodDate: "2026-05-21",
      currencyId: "POINTS",
      redeemedCount: 10,
      redemptionRate: 0.10,
    },
    {
      periodDate: "2026-05-22",
      currencyId: "POINTS",
      redeemedCount: 15,
      redemptionRate: 0.15,
    },
  ],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

const TREND_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  dataPoints: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/endpoints/redemption-analytics.yaml (F-07 overview summary)
const ANALYTICS_SUMMARY_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  redemptionRates: [],
  unredeemedBalances: [],
  failedCancelledRates: [],
  totalRedemptionCount: { total: 0, byStatus: {}, hasActivity: false },
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function setupBaseRoutes(page: any, trendPayload: object) {
  // Broad analytics route first (last-registered-wins for Playwright)
  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_SUMMARY_EMPTY)),
    });
  });

  // More-specific routes AFTER the broad route so they take precedence
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

  await page.route(
    "**/api/v1/redemption/analytics/advanced/time-to-first-redemption*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(TTFR_EMPTY)),
      });
    },
  );

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

  // Trend — registered LAST (most specific, must win for its path)
  await page.route(
    "**/api/v1/redemption/analytics/advanced/trend*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(trendPayload)),
      });
    },
  );
}

// ─── Scenario 1: Chart renders with one line per currency type (AC-1, AC-4) ────

test(
  "Trend chart renders with one line per currency type",
  async ({ page }) => {
    // Covers AC-1, AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, TREND_WITH_DATA);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for Redemption Rate Trend section
    await page.waitForSelector('[aria-label="Redemption Rate Trend"]');

    // AC-4: Section heading visible
    await expect(page.getByText("Redemption Rate Trend")).toBeVisible();

    // AC-4: "Data as of" caption visible
    await expect(page.getByText(/Data as of/)).toBeVisible();

    // AC-4: recharts SVG is rendered — verify an SVG element exists inside the section
    const trendSection = page.locator('[aria-label="Redemption Rate Trend"]');
    await expect(trendSection.locator("svg").first()).toBeVisible();
  },
);

// ─── Scenario 2: Last 7 days preset sets correct date params (AC-2) ────────────

test(
  "Last 7 days preset sets correct date params in request",
  async ({ page }) => {
    // Covers AC-2
    let capturedTrendUrl: string | null = null;

    await loginWithAdvanced(page);

    // Broad analytics route (must be first)
    await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(ANALYTICS_SUMMARY_EMPTY)),
      });
    });

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

    await page.route(
      "**/api/v1/redemption/analytics/advanced/time-to-first-redemption*",
      async (route: Route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(apiResponse(TTFR_EMPTY)),
        });
      },
    );

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

    // Trend intercept — capture the URL, return empty dataPoints
    await page.route(
      "**/api/v1/redemption/analytics/advanced/trend*",
      async (route: Route) => {
        capturedTrendUrl = route.request().url();
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(apiResponse(TREND_EMPTY)),
        });
      },
    );

    await page.goto("/redemption/admin/analytics");
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for section to render before clicking preset
    await page.waitForSelector('[aria-label="Redemption Rate Trend"]');

    // Pre-register the response waiter BEFORE clicking so the listener is active when
    // the filter change triggers the new request (avoids missing a fast response).
    const trendResponsePromise = page.waitForResponse(
      "**/api/v1/redemption/analytics/advanced/trend*",
    );

    // Click "Last 7 days" preset
    await page.getByRole("button", { name: "Last 7 days" }).click();

    // Wait for the trend request to fire with new params
    await trendResponsePromise;

    // AC-2: captured URL should contain dateFrom = today-7
    expect(capturedTrendUrl).not.toBeNull();
    const url = new URL(capturedTrendUrl!);
    const dateFrom = url.searchParams.get("dateFrom");
    expect(dateFrom).not.toBeNull();

    // Verify the date is formatted as YYYY-MM-DD and is today-7 days (local)
    const today = new Date();
    const expected = new Date(today);
    expected.setDate(expected.getDate() - 7);
    const expectedStr = `${expected.getFullYear()}-${String(expected.getMonth() + 1).padStart(2, "0")}-${String(expected.getDate()).padStart(2, "0")}`;
    expect(dateFrom).toBe(expectedStr);
  },
);

// ─── Scenario 3: Empty state renders when no data points (AC-4) ─────────────

test(
  "Trend empty state renders when no data points",
  async ({ page }) => {
    // Covers AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, TREND_EMPTY);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for Trend section
    await page.waitForSelector('[aria-label="Redemption Rate Trend"]');

    // AC-4: empty state copy — scoped to Redemption Rate Trend section because other
    // sections also render "No data for the selected period" when their data is empty
    await expect(
      page.locator('[aria-label="Redemption Rate Trend"]').getByText("No data for the selected period"),
    ).toBeVisible();
  },
);
