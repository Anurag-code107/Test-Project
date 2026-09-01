import { test, expect, type Route } from "@playwright/test";

// E2E-1: Redemption store → Bank Transfer mode. Empty-state CTA → Payout tab; and, with a linked
// bank, entering an amount and submitting a bank transfer lands on the confirmation.

const REDEMPTION_ID = "req-00000000-0000-0000-0000-0000000000b1";
const CASH_WALLET_ID = "wallet-00000000-0000-0000-0000-0000000000c1";

const PARTNER_SELLER = {
  id: "00000000-0000-0000-0000-000000000010",
  email: "seller@techpartners.com",
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.incentives.sales", "module.redemption_store", "action.redemption.redeem"],
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

const CASH_WALLET = {
  id: CASH_WALLET_ID,
  walletType: "INDIVIDUAL",
  currencyId: "cash",
  availableBalance: "500",
  reservedBalance: "0",
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

function paginatedResponse<T>(items: T[]) {
  return { data: items, page: 0, pageSize: 20, totalElements: items.length, totalPages: 1, hasNext: false, hasPrevious: false };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsPartnerSeller(page: any) {
  const loginResponse = { expiresIn: 3600, user: PARTNER_SELLER, enabledFeatures: ["redemption_store"] };
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(loginResponse) });
  });
  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(loginResponse) });
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
async function setupCommonRoutes(page: any) {
  await page.route("**/api/v1/wallets/me", async (route: Route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(apiResponse([CASH_WALLET])) });
  });
  // Gift-card browse (default mode) — empty is fine; the test toggles to Bank Transfer.
  await page.route("**/api/v1/redemption/catalog*", async (route: Route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(apiResponse(paginatedResponse([]))) });
    } else {
      await route.continue();
    }
  });
}

test("Bank Transfer — no linked bank shows the empty state + CTA to the Payout tab", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCommonRoutes(page);
  await page.route("**/api/v1/redemption/profile/banks", async (route: Route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(apiResponse([])) });
  });

  await page.goto("/redemption-store");
  await page.getByRole("tab", { name: /bank transfer/i }).click();

  await expect(page.getByTestId("bank-transfer-empty")).toBeVisible();
  await page.getByRole("button", { name: /link a bank account/i }).click();
  await expect(page).toHaveURL(/\/settings\/profile\?tab=payout/);
});

test("Bank Transfer — with a linked bank, submitting an amount lands on the confirmation", async ({ page }) => {
  await loginAsPartnerSeller(page);
  await setupCommonRoutes(page);
  await page.route("**/api/v1/redemption/profile/banks", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse([{ id: "bank-1", label: "Wells Fargo ••1898", currency: "USD", isDefault: true }])),
    });
  });

  await page.route("**/api/v1/redemption/requests/bank-transfer", async (route: Route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify(
          apiResponse({
            id: REDEMPTION_ID,
            status: "PROCESSING",
            amount: "1",
            currencyId: "cash",
            processingMode: "INSTANT",
            estimatedDelivery: "1-2 business days",
            submittedAt: "2026-07-23T05:00:00Z",
            createdAt: "2026-07-23T05:00:00Z",
            updatedAt: "2026-07-23T05:00:00Z",
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
          status: "PROCESSING",
          amount: "1",
          currencyId: "cash",
          catalogItemId: "bank-transfer-card",
          catalogItemName: "Bank Transfer",
          processingMode: "INSTANT",
          category: "CASH",
          walletType: "INDIVIDUAL",
          estimatedDelivery: "1-2 business days",
          submittedAt: "2026-07-23T05:00:00Z",
          createdAt: "2026-07-23T05:00:00Z",
          updatedAt: "2026-07-23T05:00:00Z",
        }),
      ),
    });
  });

  await page.goto("/redemption-store");
  await page.getByRole("tab", { name: /bank transfer/i }).click();

  await expect(page.getByText("Wells Fargo ••1898")).toBeVisible();
  await expect(page.getByLabel(/amount/i)).toBeVisible();
  await page.getByTestId("bank-transfer-submit").click();

  await expect(page.getByText("Redemption Submitted")).toBeVisible();
});
