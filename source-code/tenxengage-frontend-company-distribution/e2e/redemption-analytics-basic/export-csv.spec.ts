import { test, expect, type Route } from "@playwright/test";

// shape: contracts/endpoints/redemption-analytics.yaml

const CLIENT_ADMIN_EMAIL = "cadmin@tenxengage.com";
const CLIENT_ADMIN_PASSWORD = "CAdmin@123";

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
    "action.redemption.view_analytics",
    "action.redemption.view_all_history",
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

const CLIENT_ADMIN_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: CLIENT_ADMIN_USER,
  enabledFeatures: ["redemption_store"],
};

// shape: contracts/models/redemption-analytics-summary.md
const ANALYTICS_FIXTURE = {
  dateWindow: { from: "2026-05-18", to: "2026-06-17" },
  redemptionRates: [
    {
      currencyId: "CASH",
      numerator: 5000,
      denominator: 10000,
      ratePercentage: "50.00",
      hasActivity: true,
    },
  ],
  unredeemedBalances: [
    {
      currencyId: "CASH",
      availableBalance: 7500,
      reservedBalance: 2500,
      totalOutstanding: 10000,
    },
  ],
  failedCancelledRates: [
    {
      currencyId: "CASH",
      numerator: 3,
      denominator: 20,
      ratePercentage: "5.00",
      hasActivity: true,
    },
  ],
  totalRedemptionCount: {
    total: 20,
    byStatus: {
      PENDING: 1,
      PROCESSING: 2,
      COMPLETED: 15,
      FAILED: 1,
      CANCELLED: 1,
    },
    hasActivity: true,
  },
};

const CSV_CONTENT =
  "userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance\n" +
  "user-123,Jane Doe,company-001,Acme Corp,CASH,7500,2500\n";

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

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

test("export happy path — CSV file downloads", async ({ page }) => {
  await loginAsClientAdmin(page);

  // Register analytics summary route (broad — registered first per last-registered-wins)
  await page.route("**/api/v1/redemption/analytics", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_FIXTURE)),
    });
  });

  // Register export route more specific — registered after broad route
  await page.route("**/api/v1/redemption/analytics/export", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/csv",
      headers: {
        "Content-Disposition": 'attachment; filename="redemption-unredeemed-balances.csv"',
      },
      body: CSV_CONTENT,
    });
  });

  await page.goto("/redemption/admin/analytics");

  // Wait for page load
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();

  // Click Export button to open dialog
  await page.getByRole("button", { name: /^Export$/i }).click();

  // Verify dialog opens with correct microcopy
  await expect(page.getByText("Export unredeemed balances")).toBeVisible();
  await expect(
    page.getByText(
      "Download a CSV of all current unredeemed wallet balances for your program.",
    ),
  ).toBeVisible();

  // Wait for download event and click confirm
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: /download csv/i }).click();
  const download = await downloadPromise;

  // Verify download filename
  expect(download.suggestedFilename()).toBe("redemption-unredeemed-balances.csv");

  // Export button should be re-enabled with original label
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();
});

test("export rate limit — button shows countdown and re-enables", async ({ page }) => {
  await loginAsClientAdmin(page);

  // Register analytics summary route (broad — registered first)
  await page.route("**/api/v1/redemption/analytics", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_FIXTURE)),
    });
  });

  // Register export route (specific — registered after broad)
  // Use Retry-After: 2 so the real countdown naturally expires in the test
  await page.route("**/api/v1/redemption/analytics/export", async (route: Route) => {
    await route.fulfill({
      status: 429,
      contentType: "application/json",
      headers: {
        "Retry-After": "2",
      },
      body: JSON.stringify({
        errorCode: "RATE_LIMIT_EXCEEDED",
        errorMessage: "Too many export requests",
        status: 429,
        timestamp: new Date().toISOString(),
        path: "/api/v1/redemption/analytics/export",
      }),
    });
  });

  await page.goto("/redemption/admin/analytics");

  // Wait for page to load
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();

  // Click Export to open dialog
  await page.getByRole("button", { name: /^Export$/i }).click();
  await expect(page.getByText("Export unredeemed balances")).toBeVisible();

  // Confirm — triggers the 429 response
  await page.getByRole("button", { name: /download csv/i }).click();

  // Dialog closes; button should show rate-limit countdown
  await expect(
    page.getByRole("button", { name: /export limit reached/i }),
  ).toBeVisible();
  // Verify countdown text is present (shows "2 seconds" or "1 seconds")
  await expect(page.getByText(/seconds/i)).toBeVisible();

  // Wait for real countdown to expire (2s + 1s margin)
  await page.waitForTimeout(3500);

  // Button re-enables with original label
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();
});
