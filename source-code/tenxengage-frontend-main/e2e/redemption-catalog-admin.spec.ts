import { test, expect, type Route, type Page } from "@playwright/test";

const TENX_ADMIN_EMAIL = "admin@tenxengage.com";
const TENX_ADMIN_PASSWORD = "Admin@123";
const CLIENT_ADMIN_EMAIL = "admin@techpartners.com";
const CLIENT_ADMIN_PASSWORD = "Admin@123";

const ITEM_FIXTURE = {
  id: "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  name: "Amazon Gift Card",
  description: "Redeem points for Amazon gift cards",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "50.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: ["US", "GB"],
  providerItemId: "XOXO-AMZN-001",
  isReturnable: false,
  defaultReturnWindowDays: 0,
  isActive: false,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const ACTIVE_ITEM = { ...ITEM_FIXTURE, isActive: true };

const ADMIN_USER = {
  id: "00000000-0000-0000-0000-000000000001",
  email: TENX_ADMIN_EMAIL,
  firstName: "TenX",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["action.redemption.catalog.manage"],
  clientRoleId: null,
  clientRoleName: null,
  organizationId: null,
  clientId: null,
  clientName: null,
  partnerCompanyId: null,
  partnerCompanyName: null,
  metadata: null,
  homeDashboardTemplate: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const ADMIN_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: ADMIN_USER,
  enabledFeatures: ["redemption_store"],
};

const CLIENT_USER = {
  id: "00000000-0000-0000-0000-000000000002",
  email: CLIENT_ADMIN_EMAIL,
  firstName: "Client",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.home", "module.incentive_builder"],
  clientRoleId: "00000000-0000-0000-0000-000000000099",
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

const CLIENT_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: CLIENT_USER,
  enabledFeatures: [],
};

const paginatedResponse = (items: typeof ITEM_FIXTURE[]) => ({
  data: items,
  page: 0,
  pageSize: 20,
  totalElements: items.length,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
});

test("Platform Admin creates NON_CASH item and activates it", async ({ page }) => {
  let activated = false;
  // auth/refresh returns 401 until login completes; then returns admin user so
  // page.goto("/admin/...") can re-establish the session after the full reload.
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(ADMIN_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(ADMIN_LOGIN_RESPONSE),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  // Stateful catalog list — returns ACTIVE_ITEM after activate fires.
  // Trailing * matches query params (?page=0&pageSize=20) without matching /path segments.
  await page.route("**/api/v1/admin/redemption-catalog*", async (route) => {
    if (route.request().method() === "GET") {
      const item = activated ? ACTIVE_ITEM : ITEM_FIXTURE;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: paginatedResponse([item]), message: "OK", success: true, timestamp: new Date().toISOString() }),
      });
    } else if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ data: ITEM_FIXTURE, message: "Created", success: true, timestamp: new Date().toISOString() }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(`**/api/v1/admin/redemption-catalog/${ITEM_FIXTURE.id}/activate`, async (route) => {
    activated = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: ACTIVE_ITEM, message: "Activated", success: true, timestamp: new Date().toISOString() }),
    });
  });

  // refresh returns 401 here so the login page does not redirect away
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(TENX_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(TENX_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  // Allow refresh to return admin user before the catalog page reload
  authenticated = true;

  // page.goto causes a full SPA reload; auth/refresh now re-establishes the session
  await page.goto("/admin/redemption-catalog");
  await expect(page.getByTestId("catalog-admin-page")).toBeVisible();

  // Item appears in list
  await expect(page.getByTestId("catalog-item-row")).toBeVisible();
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  // Activate the item — mock flips activated=true, next GET returns ACTIVE_ITEM
  await page.getByRole("button", { name: /activate/i }).first().click();
  await expect(page.getByTestId("status-active")).toBeVisible();
});

test("Non-TENX_ADMIN cannot access catalog admin page", async ({ page }) => {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(CLIENT_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(CLIENT_LOGIN_RESPONSE),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill(CLIENT_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(CLIENT_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;

  await page.goto("/admin/redemption-catalog");

  // ProtectedRoute blocks render — page should not be attached
  await expect(page.getByTestId("catalog-admin-page")).not.toBeAttached();
});

// ─── US-05: Xoxoday sync + integration health ─────────────────────────────────

const INITIAL_HEALTH = {
  syncStatus: "SUCCESS",
  lastSyncAt: "2026-05-13T10:25:49Z",
  failedSyncCount: 0,
  recentWebhooks: [],
};

const SYNC_JOB_RESPONSE = {
  jobId: "test-job-id-0001",
  status: "QUEUED",
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

async function loginAsTenxAdmin(page: Page) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(ADMIN_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(ADMIN_LOGIN_RESPONSE),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill(TENX_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(TENX_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;
}

test("Platform Admin triggers sync and sees updated health status", async ({ page }) => {
  let syncTriggered = false;

  await loginAsTenxAdmin(page);

  await page.route("**/api/v1/admin/redemption-catalog/integration-health", async (route: Route) => {
    const health = syncTriggered
      ? { ...INITIAL_HEALTH, lastSyncAt: "2026-05-14T17:10:00Z" }
      : INITIAL_HEALTH;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(health)),
    });
  });

  await page.route("**/api/v1/admin/redemption-catalog/sync", async (route: Route) => {
    if (route.request().method() === "POST") {
      syncTriggered = true;
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(SYNC_JOB_RESPONSE)),
      });
    } else {
      await route.continue();
    }
  });

  await page.route("**/api/v1/admin/redemption-catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([ITEM_FIXTURE]))),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/admin/redemption-catalog");
  await expect(page.getByTestId("catalog-admin-page")).toBeVisible();
  await expect(page.getByTestId("sync-status-banner")).toBeVisible();

  // Click Trigger Sync
  await page.getByTestId("trigger-sync-btn").click();

  // Success toast appears
  await expect(page.getByText(/Sync job queued/i)).toBeVisible();
});

test("Sync trigger rate limit returns 429 toast", async ({ page }) => {
  await loginAsTenxAdmin(page);

  await page.route("**/api/v1/admin/redemption-catalog/integration-health", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(INITIAL_HEALTH)),
    });
  });

  await page.route("**/api/v1/admin/redemption-catalog/sync", async (route: Route) => {
    await route.fulfill({ status: 429 });
  });

  await page.route("**/api/v1/admin/redemption-catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([ITEM_FIXTURE]))),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/admin/redemption-catalog");
  await expect(page.getByTestId("catalog-admin-page")).toBeVisible();

  await page.getByTestId("trigger-sync-btn").click();

  await expect(page.getByText(/Sync rate limit reached/i)).toBeVisible();
});
