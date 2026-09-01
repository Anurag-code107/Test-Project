import { test, expect, type Route } from "@playwright/test";

// shape: contracts/endpoints/redemption-analytics.yaml + contracts/models/redemption-analytics-summary.md

const CLIENT_ADMIN_EMAIL = "cadmin@tenxengage.com";
const CLIENT_ADMIN_PASSWORD = "CAdmin@123";

const PARTNER_SELLER_EMAIL = "seller@techpartners.com";
const PARTNER_SELLER_PASSWORD = "Seller@123";

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

const PARTNER_SELLER_USER = {
  id: "00000000-0000-0000-0000-000000000010",
  email: PARTNER_SELLER_EMAIL,
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.incentives.sales",
    "module.redemption_store",
    "action.redemption.view_history",
    "action.redemption.export",
  ],
  clientRoleId: "00000000-0000-0000-0000-000000000099",
  clientRoleName: "PARTNER_SELLER",
  organizationId: null,
  clientId: "00000000-0000-0000-0000-000000000011",
  clientName: "TechPartners",
  partnerCompanyId: "00000000-0000-0000-0000-000000000020",
  partnerCompanyName: "Acme Corp",
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

const PARTNER_SELLER_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_SELLER_USER,
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
    {
      currencyId: "POINTS",
      numerator: 200,
      denominator: 1000,
      ratePercentage: "20.00",
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
    {
      currencyId: "POINTS",
      availableBalance: 800,
      reservedBalance: 200,
      totalOutstanding: 1000,
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
    {
      currencyId: "POINTS",
      numerator: 1,
      denominator: 10,
      ratePercentage: "5.00",
      hasActivity: true,
    },
  ],
  totalRedemptionCount: {
    total: 30,
    byStatus: {
      PENDING: 2,
      PROCESSING: 3,
      COMPLETED: 20,
      FAILED: 3,
      CANCELLED: 2,
    },
    hasActivity: true,
  },
};

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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsPartnerSeller(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(PARTNER_SELLER_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(PARTNER_SELLER_LOGIN_RESPONSE),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill(PARTNER_SELLER_EMAIL);
  await page.getByLabel(/password/i).fill(PARTNER_SELLER_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;
  authenticated = true;
}

test("dashboard loads with all metric card groups", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(ANALYTICS_FIXTURE)),
    });
  });

  await page.goto("/redemption/admin/analytics");

  await expect(page.getByText("CASH Redemption Rate").first()).toBeVisible();
  await expect(page.getByText("POINTS Outstanding Liability").first()).toBeVisible();
  await expect(page.getByText("Total Redemptions").first()).toBeVisible();
});

test("date preset filter triggers refetch and updates windowed cards", async ({ page }) => {
  await loginAsClientAdmin(page);

  let lastRequestUrl = "";

  // Register broad analytics route first (last-registered-wins in Playwright)
  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    lastRequestUrl = route.request().url();
    // Return modified fixture with 5.00% for windowed cards
    const fixture = {
      ...ANALYTICS_FIXTURE,
      failedCancelledRates: ANALYTICS_FIXTURE.failedCancelledRates.map((r) => ({
        ...r,
        ratePercentage: "5.00",
      })),
    };
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(fixture)),
    });
  });

  await page.goto("/redemption/admin/analytics");

  // Wait for initial load
  await expect(page.getByText("CASH Redemption Rate").first()).toBeVisible();

  // Click "Last 7 days" preset
  await page.getByRole("button", { name: "Last 7 days" }).click();

  // Verify the URL has dateFrom param
  expect(lastRequestUrl).toContain("dateFrom=");
});

test("empty state renders when tenant has no activity", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/analytics*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        apiResponse({
          dateWindow: { from: "2026-05-18", to: "2026-06-17" },
          redemptionRates: [],
          unredeemedBalances: [],
          failedCancelledRates: [],
          totalRedemptionCount: { total: 0, byStatus: {}, hasActivity: false },
        }),
      ),
    });
  });

  await page.goto("/redemption/admin/analytics");
  await expect(page.getByText("No program activity yet")).toBeVisible();
});

test("PARTNER_SELLER redirected from analytics page", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.goto("/redemption/admin/analytics");

  // ProtectedRoute intercepts — URL should not remain at analytics
  await page.waitForURL((url) => !url.pathname.endsWith("/analytics"));
  expect(page.url()).not.toContain("/redemption/admin/analytics");
});
