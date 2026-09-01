import { test, expect, type Route } from "@playwright/test";

const PARTNER_SELLER_EMAIL = "seller@techpartners.com";
const PARTNER_SELLER_PASSWORD = "Seller@123";

const ITEM_AFFORDABLE_ID = "f47ac10b-0000-0000-0000-000000000001";
const ITEM_UNAFFORDABLE_ID = "f47ac10b-0000-0000-0000-000000000002";

const PARTNER_USER = {
  id: "00000000-0000-0000-0000-000000000010",
  email: PARTNER_SELLER_EMAIL,
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.incentives.sales", "module.redemption_store"],
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

const PARTNER_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_USER,
  enabledFeatures: ["redemption_store"],
};

const AFFORDABLE_ITEM = {
  id: ITEM_AFFORDABLE_ID,
  name: "Amazon Gift Card",
  description: "Redeem your points for Amazon credits.",
  category: "NON_CASH",
  currencyId: "points",
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Instant delivery",
  canAfford: true,
  shortfallAmount: "0",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const UNAFFORDABLE_ITEM = {
  id: ITEM_UNAFFORDABLE_ID,
  name: "Visa Prepaid Card",
  description: "Redeem for a Visa prepaid card.",
  category: "CASH",
  currencyId: "points",
  effectiveMinTransactionAmount: "200",
  effectiveProcessingMode: "BATCH",
  estimatedPayoutTimeline: "Next daily batch run",
  canAfford: false,
  shortfallAmount: "75",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

function apiResponse<T>(data: T) {
  return {
    data,
    message: "OK",
    success: true,
    timestamp: new Date().toISOString(),
  };
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
async function loginAsPartnerSeller(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(PARTNER_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(PARTNER_LOGIN_RESPONSE),
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

test("Partner browses catalog and sees shortfall badge on unaffordable item", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route(`**/api/v1/redemption/catalog/${ITEM_UNAFFORDABLE_ID}`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(UNAFFORDABLE_ITEM)),
    });
  });

  await page.route("**/api/v1/redemption/catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          apiResponse(paginatedResponse([AFFORDABLE_ITEM, UNAFFORDABLE_ITEM])),
        ),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/redemption-store");
  await expect(page.getByTestId("redemption-store-page")).toBeVisible();

  // Payout timeline on affordable item
  await expect(page.getByTestId(`payout-timeline-${ITEM_AFFORDABLE_ID}`)).toContainText(
    "Instant delivery",
  );

  // ShortfallBadge on unaffordable item
  await expect(page.getByTestId("shortfall-badge")).toBeVisible();

  // Click the unaffordable item card to open detail sheet
  await page.getByTestId(`catalog-item-card-${ITEM_UNAFFORDABLE_ID}`).click();

  // Redeem CTA is disabled
  await expect(page.getByRole("button", { name: "Redeem" })).toBeDisabled();
});

test("Partner sees empty state when no items are enabled", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([]))),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/redemption-store");
  await expect(
    page.getByText("No rewards available yet. Check back soon."),
  ).toBeVisible();
});

test("Unauthenticated user is rejected", async ({ page }) => {
  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    await route.fulfill({ status: 401 });
  });

  await page.goto("/redemption-store");

  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByTestId("redemption-store-page")).not.toBeAttached();
});
