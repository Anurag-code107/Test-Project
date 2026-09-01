// shape: contracts/endpoints/redemption-advanced-analytics.yaml
//        contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse
// Covers: AC-1, AC-2, AC-4, AC-6 (story US-06)
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
const TREND_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  dataPoints: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse (with data)
const LIABILITY_TREND_WITH_DATA = {
  dateWindow: { from: "2026-06-01", to: "2026-06-20" },
  dataPoints: [
    { periodDate: "2026-06-01", currencyId: "POINTS", totalUnredeemedBalance: "1200.50" },
    { periodDate: "2026-06-02", currencyId: "POINTS", totalUnredeemedBalance: "1150.00" },
  ],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse (empty)
const LIABILITY_TREND_EMPTY = {
  dateWindow: { from: "2026-06-01", to: "2026-06-20" },
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

// ─── Shared base route setup ───────────────────────────────────────────────────
// Per anti-pattern register: register broad pattern FIRST, specific LAST
// (Playwright uses last-registered-wins precedence).

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function setupBaseRoutes(page: any, liabilityPayload: object) {
  // Broad analytics route FIRST
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

  await page.route(
    "**/api/v1/redemption/analytics/advanced/trend*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(TREND_EMPTY)),
      });
    },
  );

  // Liability trend registered LAST — must win for its path
  await page.route(
    "**/api/v1/redemption/analytics/advanced/liability-trend*",
    async (route: Route) => {
      // Do NOT match the export sub-path here; export is wired per-test
      const url: string = route.request().url();
      if (url.includes("/export")) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(liabilityPayload)),
      });
    },
  );
}

// ─── Scenario 1: Liability trend chart renders with one line per currency ──────

test(
  "Liability trend chart renders with one line per currency",
  async ({ page }) => {
    // Covers AC-1, AC-6
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, LIABILITY_TREND_WITH_DATA);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for Liability Trend section
    await page.waitForSelector('[aria-label="Liability Trend"]');

    // AC-6: Section heading visible
    await expect(page.getByText("Liability Trend")).toBeVisible();

    // AC-6: "Data as of" caption visible
    await expect(page.getByText(/Data as of/)).toBeVisible();

    // AC-6: Export CSV button visible
    await expect(page.getByRole("button", { name: /Export CSV/i })).toBeVisible();

    // AC-1: recharts SVG rendered inside the Liability Trend section
    const liabilitySection = page.locator('[aria-label="Liability Trend"]');
    await expect(liabilitySection.locator("svg").first()).toBeVisible();
  },
);

// ─── Scenario 2: Export CSV triggers download and rate limit disables button ───

test(
  "Export CSV triggers download and rate limit disables button",
  async ({ page }) => {
    // Covers AC-2, AC-4, AC-6
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, LIABILITY_TREND_WITH_DATA);

    // CSV content for successful exports
    const csvContent = "period_date,currency_type,total_unredeemed_balance\n2026-06-01,POINTS,1200.50\n";

    let exportCallCount = 0;

    // Export endpoint: first 3 calls → 200 CSV; 4th call → 429 with Retry-After
    // Register AFTER base routes so this wins for /export path (last-registered-wins)
    await page.route(
      "**/api/v1/redemption/analytics/advanced/liability-trend/export*",
      async (route: Route) => {
        exportCallCount++;
        if (exportCallCount <= 3) {
          await route.fulfill({
            status: 200,
            contentType: "text/csv; charset=UTF-8",
            headers: {
              "Content-Disposition": 'attachment; filename="redemption-liability-trend.csv"',
            },
            body: csvContent,
          });
        } else {
          await route.fulfill({
            status: 429,
            contentType: "application/json",
            headers: { "Retry-After": "45" },
            body: JSON.stringify({
              errorCode: "RATE_LIMIT_EXCEEDED",
              errorMessage: "Too many requests",
              status: 429,
              timestamp: new Date().toISOString(),
              path: "/api/v1/redemption/analytics/advanced/liability-trend/export",
            }),
          });
        }
      },
    );

    await page.goto("/redemption/admin/analytics");
    await page.getByRole("tab", { name: "Advanced" }).click();
    await page.waitForSelector('[aria-label="Liability Trend"]');

    // Clicks 1–3: expect download event each time
    for (let i = 0; i < 3; i++) {
      const [download] = await Promise.all([
        page.waitForEvent("download"),
        page.getByRole("button", { name: /Export CSV/i }).click(),
      ]);
      expect(download.suggestedFilename()).toBe("redemption-liability-trend.csv");
    }

    // Click 4: expect 429 → button disabled with countdown
    await page.getByRole("button", { name: /Export CSV/i }).click();

    // Wait for the rate-limit button to appear
    await expect(
      page.getByRole("button", { name: /Retry in/i }),
    ).toBeDisabled();
  },
);

// ─── Scenario 3: Liability trend empty state renders when no data points ────────

test(
  "Liability trend empty state renders when no data points",
  async ({ page }) => {
    // Covers AC-6
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, LIABILITY_TREND_EMPTY);

    await page.goto("/redemption/admin/analytics");
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for the Liability Trend section to mount
    await page.waitForSelector('[aria-label="Liability Trend"]');

    // Scoped to Liability Trend section — other sections also show the same empty-state copy
    await expect(
      page.locator('[aria-label="Liability Trend"]').getByText("No data for the selected period"),
    ).toBeVisible();
  },
);
