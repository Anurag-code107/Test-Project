// shape: contracts/endpoints/redemption-advanced-analytics.yaml
//        contracts/models/redemption-advanced-analytics.md → FailureBreakdownResponse
// Covers: AC-1, AC-2, AC-4 (story US-07)
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

// shape: contracts/models/redemption-advanced-analytics.md → LiabilityTrendResponse
const LIABILITY_TREND_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  dataPoints: [],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → FailureBreakdownResponse (with data)
// failureRate is a percentage (0–100) per the contract; rows pre-sorted by failureRate DESC.
// Note: the story spec AC-2 mock shows 0.35/0.12 (0–1 range), which conflicts with the contract.
// The contract is the source of truth — using 35.0/12.0 here.
const FAILURE_BREAKDOWN_WITH_DATA = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  failureModes: [
    {
      processingMode: "MANUAL",
      catalogItemId: "00000000-0000-0000-0000-000000000001",
      catalogItemName: "Gold Ring",
      currencyId: "POINTS",
      failedCount: 30,
      cancelledCount: 5,
      totalCount: 100,
      failureRate: 35.0,
    },
    {
      processingMode: "AUTOMATED",
      catalogItemId: "00000000-0000-0000-0000-000000000002",
      catalogItemName: "Silver Coin",
      currencyId: "POINTS",
      failedCount: 10,
      cancelledCount: 2,
      totalCount: 100,
      failureRate: 12.0,
    },
  ],
  lastRefreshedAt: "2026-06-20T06:00:00Z",
};

// shape: contracts/models/redemption-advanced-analytics.md → FailureBreakdownResponse (empty)
const FAILURE_BREAKDOWN_EMPTY = {
  dateWindow: { from: "2026-05-21", to: "2026-06-20" },
  failureModes: [],
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
async function setupBaseRoutes(page: any, failurePayload: object) {
  // Broad analytics route FIRST
  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_SUMMARY_EMPTY)),
    });
  });

  // More-specific routes AFTER the broad route so they take precedence (last-registered-wins)
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

  await page.route(
    "**/api/v1/redemption/analytics/advanced/liability-trend*",
    async (route: Route) => {
      // Do NOT match the export sub-path here; pass it through
      const url: string = route.request().url();
      if (url.includes("/export")) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(LIABILITY_TREND_EMPTY)),
      });
    },
  );

  // Failure breakdown registered LAST — must win for its path
  await page.route(
    "**/api/v1/redemption/analytics/advanced/failure-breakdown*",
    async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(failurePayload)),
      });
    },
  );
}

// ─── Scenario 1: Failure breakdown table renders MANUAL and AUTOMATED rows ─────

test(
  "Failure breakdown table renders MANUAL and AUTOMATED rows sorted by failure rate desc",
  async ({ page }) => {
    // Covers AC-1, AC-2, AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, FAILURE_BREAKDOWN_WITH_DATA);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for Failure Breakdown section to mount
    await page.waitForSelector('[aria-label="Failure Breakdown"]');

    // AC-4: Section heading visible
    await expect(page.getByText("Failure Breakdown")).toBeVisible();

    // AC-2/AC-4: First row shows "Manual" and "35.0%" (failureRate 0.35 → 35.0%)
    const rows = page.getByRole("row");
    // rows[0] = header, rows[1] = first data row (Gold Ring / Manual / 35.0%)
    await expect(rows.nth(1)).toContainText("Manual");
    await expect(rows.nth(1)).toContainText("35.0%");

    // AC-2/AC-4: Second row shows "Automated" and "12.0%" (failureRate 0.12 → 12.0%)
    await expect(rows.nth(2)).toContainText("Automated");
    await expect(rows.nth(2)).toContainText("12.0%");

    // AC-4: "Data as of" caption visible
    await expect(page.getByText(/Data as of/)).toBeVisible();
  },
);

// ─── Scenario 2: Failure breakdown empty state renders when no failure modes ───

test(
  "Failure breakdown empty state renders when no failure modes",
  async ({ page }) => {
    // Covers AC-4
    await loginWithAdvanced(page);
    await setupBaseRoutes(page, FAILURE_BREAKDOWN_EMPTY);

    await page.goto("/redemption/admin/analytics");

    // Navigate to Advanced tab
    await page.getByRole("tab", { name: "Advanced" }).click();

    // Wait for Failure Breakdown section to mount
    await page.waitForSelector('[aria-label="Failure Breakdown"]');

    // Scoped to Failure Breakdown section — other sections also show the same empty-state copy
    await expect(
      page.locator('[aria-label="Failure Breakdown"]').getByText("No data for the selected period"),
    ).toBeVisible();
  },
);
