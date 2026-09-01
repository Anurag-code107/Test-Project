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

// shape: contracts/models/balance-expiration-policy.md (BalanceExpirationPolicyResponse)
const POINTS_POLICY_SAVED: object = {
  currencyId: "points",
  currencyDisplayName: "Points",
  enabled: true,
  expirationMode: "INACTIVITY",
  inactivityDays: 90,
  fixedExpiryDate: null,
  leadTimeDays: 30,
  enabledAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-25T10:00:00Z",
};

// shape: contracts/models/balance-breakage-report.md (ExpiringBalancePreviewResponse)
const EXPIRING_SOON_DATA: object[] = [
  {
    currencyId: "points",
    currencyDisplayName: "Points",
    scheduledExpiryDate: "2026-09-01",
    affectedWalletCount: 12,
    totalAmountAtRisk: "3400.00",
  },
];

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

  // Navigate to login and submit credentials
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(CLIENT_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(CLIENT_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;
  authenticated = true;
}

// ─── Shared route setup ────────────────────────────────────────────────────────

/**
 * Register more-specific patterns AFTER the broad pattern
 * (Playwright last-registered-wins — per PROJECT-CONTEXT.md rule).
 */
async function setupPoliciesRoute(
  page: import("@playwright/test").Page,
  responseBody: object[],
) {
  await page.route("**/api/v1/redemption/expiration/policies", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(responseBody)),
    });
  });
}

async function setupExpiringSoonRoute(
  page: import("@playwright/test").Page,
  responseBody: object[],
) {
  await page.route("**/api/v1/redemption/expiration/expiring-soon*", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(responseBody)),
    });
  });
}

// ─── Scenario 1: Configure points inactivity policy — happy path ───────────────

test("configure points inactivity policy — happy path (AC-1, AC-2, AC-6)", async ({ page }) => {
  // Mock: GET /policies → empty list (no saved policies)
  await setupPoliciesRoute(page, []);

  // Mock: GET /expiring-soon → empty
  await setupExpiringSoonRoute(page, []);

  // Mock: PUT /policies/points → 200 saved policy
  await page.route("**/api/v1/redemption/expiration/policies/points", async (route: Route) => {
    if (route.request().method() !== "PUT") return route.continue();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(POINTS_POLICY_SAVED)),
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/settings/redemption/balance-expiration");

  // AC-6: All four currencies must be shown
  await expect(page.getByText("Cash").first()).toBeVisible();
  await expect(page.getByText("Points").first()).toBeVisible();
  await expect(page.getByText("Credits").first()).toBeVisible();
  await expect(page.getByText("Tickets").first()).toBeVisible();

  // Find the Points card — it renders with "Not configured" caption
  await expect(page.getByText("Not configured").first()).toBeVisible();

  // Enable the Points policy — find its switch (Points card is the second card)
  // Locate the Points card container then its switch
  const pointsCard = page.locator("form").filter({ hasText: "Points" }).first();
  const enableSwitch = pointsCard.getByRole("switch");
  await enableSwitch.click();

  // Mode is INACTIVITY by default — fill inactivity days and lead time
  const inactivityInput = pointsCard.getByLabel(/inactivity period \(days\)/i);
  await inactivityInput.fill("90");

  const leadTimeInput = pointsCard.getByLabel(/lead time \(days\)/i);
  await leadTimeInput.fill("30");

  // Save
  const saveBtn = pointsCard.getByRole("button", { name: /save/i });
  await saveBtn.click();

  // AC-2: Success toast
  await expect(page.getByText("Expiration policy saved")).toBeVisible();
});

// ─── Scenario 2: Invalid lead time shows field error ──────────────────────────

test("invalid lead time shows field error (AC-3)", async ({ page }) => {
  // Mock: GET /policies → points already enabled
  await setupPoliciesRoute(page, [
    {
      currencyId: "points",
      currencyDisplayName: "Points",
      enabled: true,
      expirationMode: "INACTIVITY",
      inactivityDays: 90,
      fixedExpiryDate: null,
      leadTimeDays: 30,
      enabledAt: "2026-06-01T10:00:00Z",
      updatedAt: "2026-06-25T10:00:00Z",
    },
  ]);
  await setupExpiringSoonRoute(page, []);

  // shape: contracts/endpoints/balance-expiration.yaml (ErrorResponse 422)
  await page.route("**/api/v1/redemption/expiration/policies/points", async (route: Route) => {
    if (route.request().method() !== "PUT") return route.continue();
    await route.fulfill({
      status: 422,
      contentType: "application/json",
      body: JSON.stringify({
        errorCode: "LEAD_TIME_MUST_BE_LESS_THAN_INACTIVITY",
        errorMessage: "Lead time must be less than inactivity period",
        status: 422,
        timestamp: new Date().toISOString(),
        path: "/api/v1/redemption/expiration/policies/points",
      }),
    });
  });

  await loginAsClientAdmin(page);
  await page.goto("/settings/redemption/balance-expiration");

  // Find the Points card and change lead time to 120 (> inactivityDays 90)
  const pointsCard = page.locator("form").filter({ hasText: "Points" }).first();
  const leadTimeInput = pointsCard.getByLabel(/lead time \(days\)/i);
  await leadTimeInput.fill("120");
  // Trigger validation by blurring the field
  await leadTimeInput.blur();

  // AC-3: Client-side cross-field validation shows the error message
  // (mode: onChange means the error appears without needing to submit)
  await expect(
    page.getByText("Lead time must be at least 1 day and less than the inactivity period"),
  ).toBeVisible();
});

// ─── Scenario 3: Expiring-soon preview lists at-risk totals ───────────────────

test("expiring-soon preview lists at-risk totals (AC-7)", async ({ page }) => {
  // Mock: GET /policies → one configured policy
  await setupPoliciesRoute(page, [POINTS_POLICY_SAVED]);

  // Mock: GET /expiring-soon → preview data
  await setupExpiringSoonRoute(page, EXPIRING_SOON_DATA);

  await loginAsClientAdmin(page);
  await page.goto("/settings/redemption/balance-expiration");

  // AC-7: Preview card shows affectedWalletCount = 12
  await expect(page.getByText("12 wallets")).toBeVisible();
});
