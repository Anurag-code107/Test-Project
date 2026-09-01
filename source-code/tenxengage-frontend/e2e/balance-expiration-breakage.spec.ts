import { test, expect, type Route } from "@playwright/test";

// ─── Constants ────────────────────────────────────────────────────────────────

const CLIENT_ADMIN_EMAIL = "cadmin@tenxengage.com";
const CLIENT_ADMIN_PASSWORD = "CAdmin@123";

// shape: contracts/models/balance-expiration-policy.md
const CLIENT_ADMIN_USER = {
  id: "00000000-0000-0000-0000-000000000030",
  email: CLIENT_ADMIN_EMAIL,
  firstName: "Client",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.redemption_store",
    "action.redemption.expiration.configure",
    "action.redemption.expiration.view_breakage",
  ],
  clientRoleId: "00000000-0000-0000-0000-000000000097",
  clientRoleName: "CLIENT_ADMIN",
  organizationId: null,
  clientId: "a0000000-0000-0000-0000-000000000001",
  clientName: "TechPartners",
  partnerCompanyId: null,
  partnerCompanyName: null,
  metadata: null,
  homeDashboardTemplate: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CLIENT_ADMIN_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: CLIENT_ADMIN_USER,
  enabledFeatures: ["redemption_store", "reward_balance_expiration"],
};

// shape: contracts/models/balance-breakage-report.md (BalanceBreakageReportResponse)
const BREAKAGE_REPORT_2_ROWS = {
  from: "2026-01-01",
  to: "2026-03-31",
  granularity: "MONTH",
  rows: [
    {
      periodStart: "2026-01-01",
      periodEnd: "2026-01-31",
      currencyId: "points",
      currencyDisplayName: "Points",
      expiredCount: 5,
      totalExpiredAmount: "1250.00",
    },
    {
      periodStart: "2026-02-01",
      periodEnd: "2026-02-28",
      currencyId: "cash",
      currencyDisplayName: "Cash",
      expiredCount: 3,
      totalExpiredAmount: "450.00",
    },
  ],
};

const BREAKAGE_REPORT_EMPTY = {
  from: "2025-01-01",
  to: "2025-03-31",
  granularity: "MONTH",
  rows: [],
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

// ─── Auth helper ───────────────────────────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsClientAdmin(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(CLIENT_ADMIN_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(CLIENT_ADMIN_LOGIN_RESPONSE),
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
}

// ─── Scenario 1: breakage report renders rows ─────────────────────────────────

test("breakage report renders rows (AC-1)", async ({ page }) => {
  // Register broad breakage route first, then the export-specific one after
  // (Playwright last-registered-wins — PROJECT-CONTEXT.md anti-pattern register)
  await page.route("**/api/v1/redemption/expiration/breakage*", async (route: Route) => {
    // Do NOT intercept export calls here — let the specific route below handle them
    if (route.request().url().includes("/breakage/export")) {
      return route.continue();
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(BREAKAGE_REPORT_2_ROWS)),
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/redemption/breakage");

  // AC-1: table renders both rows — "Points" label (from getCurrency) is visible
  await expect(page.getByText("Points").first()).toBeVisible();

  // Non-zero totalExpiredAmount cell
  await expect(page.getByText(/1,250/i).first()).toBeVisible();
});

// ─── Scenario 1 negative: invalid range shows inline error ────────────────────

test("invalid range shows inline error (AC-4)", async ({ page }) => {
  await page.route("**/api/v1/redemption/expiration/breakage*", async (route: Route) => {
    if (route.request().url().includes("/breakage/export")) return route.continue();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(BREAKAGE_REPORT_2_ROWS)),
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/redemption/breakage");

  // Set end date before start date
  await page.getByLabel(/start date/i).fill("2026-06-01");
  await page.getByLabel(/end date/i).fill("2026-05-01");
  await page.getByRole("button", { name: /apply filters/i }).click();

  await expect(
    page.getByText("End date must be on or after start date"),
  ).toBeVisible();
});

// ─── Scenario 2: export CSV downloads ────────────────────────────────────────

test("export CSV downloads (AC-2)", async ({ page }) => {
  // Broad breakage GET (no export suffix)
  await page.route("**/api/v1/redemption/expiration/breakage*", async (route: Route) => {
    if (route.request().url().includes("/breakage/export")) return route.continue();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(BREAKAGE_REPORT_2_ROWS)),
    });
  });

  // More-specific export route registered AFTER the broad one (last-registered-wins)
  await page.route("**/api/v1/redemption/expiration/breakage/export*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/csv",
      headers: {
        "Content-Disposition": 'attachment; filename="balance-expiration-breakage.csv"',
      },
      body: "period_start,period_end,currency_id,expired_count,total_expired_amount\n2026-01-01,2026-01-31,points,5,1250.00\n",
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/redemption/breakage");

  // Wait for table rows to appear
  await expect(page.getByText("Points").first()).toBeVisible();

  // Click Export CSV and wait for the browser download event
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: /export breakage report as csv/i }).click();
  const download = await downloadPromise;

  // Assert the download was triggered (filename contains "breakage")
  expect(download.suggestedFilename()).toContain("breakage");
});

// ─── Scenario 3: empty state when no expiries ─────────────────────────────────

test("empty state when no expiries (AC-1)", async ({ page }) => {
  await page.route("**/api/v1/redemption/expiration/breakage*", async (route: Route) => {
    if (route.request().url().includes("/breakage/export")) return route.continue();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(BREAKAGE_REPORT_EMPTY)),
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/redemption/breakage");

  await expect(
    page.getByText("No expired balances in this period"),
  ).toBeVisible();
});
