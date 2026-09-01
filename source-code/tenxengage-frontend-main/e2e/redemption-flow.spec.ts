import { test, expect, type Route } from "@playwright/test";

// ─── Shared fixtures ──────────────────────────────────────────────────────────

const ITEM_ID = "f47ac10b-0000-0000-0000-000000000001";
const REDEMPTION_ID = "req-00000000-0000-0000-0000-000000000001";
const COMPANY_REDEMPTION_ID = "req-00000000-0000-0000-0000-000000000002";
const WALLET_ID = "wallet-00000000-0000-0000-0000-000000000001";
const COMPANY_WALLET_ID = "wallet-00000000-0000-0000-0000-000000000002";
const COMPANY_ID = "00000000-0000-0000-0000-000000000020";

const PARTNER_USER_BASE = {
  id: "00000000-0000-0000-0000-000000000010",
  email: "seller@techpartners.com",
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.incentives.sales",
    "module.redemption_store",
    "action.redemption.redeem",
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

const CATALOG_ITEM = {
  id: ITEM_ID,
  name: "Amazon Gift Card",
  description: "Redeem your points for Amazon credits.",
  category: "NON_CASH",
  currencyId: "points",
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Within 24 hours",
  canAfford: true,
  shortfallAmount: "0",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const WALLET = {
  id: WALLET_ID,
  walletType: "INDIVIDUAL",
  currencyId: "points",
  availableBalance: "500",
  reservedBalance: "50",
};

const COMPANY_WALLET = {
  id: COMPANY_WALLET_ID,
  walletType: "COMPANY",
  currencyId: "points",
  availableBalance: "2000",
  reservedBalance: "100",
};

const PARTNER_ADMIN_USER = {
  ...{
    id: "00000000-0000-0000-0000-000000000011",
    email: "admin@techpartners.com",
    firstName: "Partner",
    lastName: "Admin",
    phone: null,
    avatar: null,
    status: "ACTIVE",
    clientRoleId: "00000000-0000-0000-0000-000000000098",
    clientRoleName: "PARTNER_ADMIN",
    organizationId: null,
    clientId: "00000000-0000-0000-0000-000000000011",
    clientName: "TechPartners",
    partnerCompanyId: COMPANY_ID,
    partnerCompanyName: "Acme Corp",
    metadata: null,
    homeDashboardTemplate: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  permissions: [
    "module.incentives.sales",
    "module.redemption_store",
    "action.redemption.redeem",
    "action.redemption.redeem_company",
  ],
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

function paginatedResponse<T>(items: T[]) {
  return {
    data: items,
    page: 0,
    pageSize: 20,
    totalElements: items.length,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsPartnerAdmin(page: any) {
  const loginResponse = {
    expiresIn: 3600,
    user: PARTNER_ADMIN_USER,
    enabledFeatures: ["redemption_store"],
  };
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(loginResponse),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(loginResponse),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill("admin@techpartners.com");
  await page.getByLabel(/password/i).fill("Admin@123");

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsPartnerSeller(page: any, userOverrides: Partial<typeof PARTNER_USER_BASE> = {}) {
  const user = { ...PARTNER_USER_BASE, ...userOverrides };
  const loginResponse = { expiresIn: 3600, user, enabledFeatures: ["redemption_store"] };
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(loginResponse),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(loginResponse),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill("seller@techpartners.com");
  await page.getByLabel(/password/i).fill("Seller@123");

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function setupCatalogRoutes(page: any) {
  await page.route("**/api/v1/wallets/me", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse([WALLET])),
    });
  });

  await page.route(`**/api/v1/redemption/catalog/${ITEM_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(CATALOG_ITEM)),
    });
  });

  await page.route("**/api/v1/redemption/catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([CATALOG_ITEM]))),
      });
    } else {
      await route.continue();
    }
  });
}

// ─── Scenario 1: INSTANT mode happy path ─────────────────────────────────────

test("Personal redemption — INSTANT mode happy path", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCatalogRoutes(page);

  await page.route("**/api/v1/redemption/requests", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(
          apiResponse({
            id: REDEMPTION_ID,
            status: "RESERVED",
            amount: "50",
            currencyId: "points",
            processingMode: "INSTANT",
            estimatedDelivery: "Within 24 hours",
            submittedAt: "2026-05-22T05:00:00Z",
            createdAt: "2026-05-22T05:00:00Z",
            updatedAt: "2026-05-22T05:00:00Z",
          }),
        ),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(`**/api/v1/redemption/requests/${REDEMPTION_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        apiResponse({
          id: REDEMPTION_ID,
          status: "RESERVED",
          amount: "50",
          currencyId: "points",
          catalogItemId: ITEM_ID,
          catalogItemName: "Amazon Gift Card",
          processingMode: "INSTANT",
          category: "NON_CASH",
          walletType: "INDIVIDUAL",
          estimatedDelivery: "Within 24 hours",
          submittedAt: "2026-05-22T05:00:00Z",
          createdAt: "2026-05-22T05:00:00Z",
          updatedAt: "2026-05-22T05:00:00Z",
        }),
      ),
    });
  });

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();
  await expect(page.getByRole("button", { name: "Redeem" })).toBeVisible();
  await page.getByRole("button", { name: "Redeem" }).click();

  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Redeem Reward" })).toBeVisible();

  await page.getByTestId("submit-button").click();

  await expect(page.getByText("Redemption Submitted")).toBeVisible();
  await expect(page.getByTestId("delivery-text")).toContainText("Estimated delivery: Within 24 hours");
});

// ─── Scenario 2: BATCH mode shows scheduled date ──────────────────────────────

test("Personal redemption — BATCH mode shows scheduled date", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCatalogRoutes(page);

  await page.route("**/api/v1/redemption/requests", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(
          apiResponse({
            id: REDEMPTION_ID,
            status: "RESERVED",
            amount: "50",
            currencyId: "points",
            processingMode: "BATCH",
            estimatedDelivery: "Queued for processing on 2026-05-25",
            scheduledBatchDate: "2026-05-25",
            submittedAt: "2026-05-22T05:00:00Z",
            createdAt: "2026-05-22T05:00:00Z",
            updatedAt: "2026-05-22T05:00:00Z",
          }),
        ),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(`**/api/v1/redemption/requests/${REDEMPTION_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        apiResponse({
          id: REDEMPTION_ID,
          status: "RESERVED",
          amount: "50",
          currencyId: "points",
          catalogItemId: ITEM_ID,
          catalogItemName: "Amazon Gift Card",
          processingMode: "BATCH",
          category: "NON_CASH",
          walletType: "INDIVIDUAL",
          scheduledBatchDate: "2026-05-25",
          estimatedDelivery: "Queued for processing on 2026-05-25",
          submittedAt: "2026-05-22T05:00:00Z",
          createdAt: "2026-05-22T05:00:00Z",
          updatedAt: "2026-05-22T05:00:00Z",
        }),
      ),
    });
  });

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();
  await page.getByRole("button", { name: "Redeem" }).click();
  await page.getByTestId("submit-button").click();

  await expect(page.getByTestId("delivery-text")).toContainText(
    "Scheduled for processing on 2026-05-25",
  );
});

// ─── Scenario 3: Amount below minimum shows inline error ─────────────────────

test("Personal redemption — amount below minimum shows inline error", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCatalogRoutes(page);

  await page.route("**/api/v1/redemption/requests", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 422,
        contentType: "application/json",
        body: JSON.stringify({
          errorCode: "VALIDATION_ERROR",
          errorMessage: "Amount is below the minimum allowed: 10.00",
          status: 422,
          timestamp: new Date().toISOString(),
          path: "/api/v1/redemption/requests",
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();
  await page.getByRole("button", { name: "Redeem" }).click();

  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByTestId("submit-button").click();

  await expect(page.getByText("Amount is below the minimum allowed: 10.00")).toBeVisible();
  // Modal remains open after error
  await expect(page.getByRole("dialog")).toBeVisible();
});

// ─── Scenario 4: In-flight cap toast ─────────────────────────────────────────

test("Personal redemption — in-flight cap toast", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCatalogRoutes(page);

  await page.route("**/api/v1/redemption/requests", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({
          errorCode: "IN_FLIGHT_LIMIT_EXCEEDED",
          errorMessage: "Maximum in-flight redemptions reached",
          status: 409,
          timestamp: new Date().toISOString(),
          path: "/api/v1/redemption/requests",
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();
  await page.getByRole("button", { name: "Redeem" }).click();
  await page.getByTestId("submit-button").click();

  await expect(page.getByText("Maximum in-flight redemptions reached")).toBeVisible();
  // Redemption modal closes after 409 (sheet may remain open)
  await expect(page.getByRole("heading", { name: "Redeem Reward" })).not.toBeVisible();
});

// ─── Scenario 5: Missing permission hides Redeem button ──────────────────────

test("Personal redemption — missing permission hides Redeem button", async ({ page }) => {
  await loginAsPartnerSeller(page, {
    permissions: ["module.incentives.sales", "module.redemption_store"],
  });
  await setupCatalogRoutes(page);

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();

  await expect(page.getByRole("button", { name: "Redeem" })).not.toBeVisible();
});

// ─── Scenario 6: Company redemption — PARTNER_ADMIN happy path ───────────────

test("Company redemption — PARTNER_ADMIN happy path", async ({ page }) => {
  await loginAsPartnerAdmin(page);
  await setupCatalogRoutes(page);

  await page.route(`**/api/v1/wallets/company/${COMPANY_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse([COMPANY_WALLET])),
    });
  });

  await page.route("**/api/v1/redemption/requests/company", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(
          apiResponse({
            id: COMPANY_REDEMPTION_ID,
            status: "RESERVED",
            amount: "50",
            currencyId: "points",
            processingMode: "INSTANT",
            estimatedDelivery: "Within 24 hours",
            submittedAt: "2026-05-22T06:00:00Z",
            createdAt: "2026-05-22T06:00:00Z",
            updatedAt: "2026-05-22T06:00:00Z",
          }),
        ),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(`**/api/v1/redemption/requests/${COMPANY_REDEMPTION_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        apiResponse({
          id: COMPANY_REDEMPTION_ID,
          status: "RESERVED",
          amount: "50",
          currencyId: "points",
          catalogItemId: ITEM_ID,
          catalogItemName: "Amazon Gift Card",
          processingMode: "INSTANT",
          category: "NON_CASH",
          walletType: "COMPANY",
          estimatedDelivery: "Within 24 hours",
          submittedAt: "2026-05-22T06:00:00Z",
          createdAt: "2026-05-22T06:00:00Z",
          updatedAt: "2026-05-22T06:00:00Z",
        }),
      ),
    });
  });

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();
  await expect(page.getByRole("button", { name: "Redeem (Company)" })).toBeVisible();
  await page.getByRole("button", { name: "Redeem (Company)" }).click();

  await expect(page.getByRole("heading", { name: "Redeem Reward" })).toBeVisible();

  await page.getByTestId("submit-button").click();

  await expect(page.getByText("Redemption Submitted")).toBeVisible();
});

// ─── Scenario 7: PARTNER_SELLER cannot see company button ────────────────────

test("Company redemption — PARTNER_SELLER cannot see company button", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCatalogRoutes(page);

  await page.goto("/redemption-store");
  await page.getByTestId(`catalog-item-card-${ITEM_ID}`).click();

  // PARTNER_SELLER has action.redemption.redeem but not action.redemption.redeem_company
  await expect(page.getByRole("button", { name: "Redeem" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Redeem (Company)" })).not.toBeVisible();
});
