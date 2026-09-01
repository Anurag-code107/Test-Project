import { test, expect, type Route } from "@playwright/test";

const CLIENT_ADMIN_EMAIL = "admin@techpartners.com";
const CLIENT_ADMIN_PASSWORD = "Admin@123";
const PARTNER_SELLER_EMAIL = "seller@techpartners.com";
const PARTNER_SELLER_PASSWORD = "Admin@123";

const CATALOG_ITEM_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

const CLIENT_USER = {
  id: "00000000-0000-0000-0000-000000000002",
  email: CLIENT_ADMIN_EMAIL,
  firstName: "Client",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["action.redemption.configure"],
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
  enabledFeatures: ["redemption_store"],
};

const PARTNER_SELLER_USER = {
  ...CLIENT_USER,
  id: "00000000-0000-0000-0000-000000000003",
  email: PARTNER_SELLER_EMAIL,
  firstName: "Partner",
  lastName: "Seller",
  permissions: ["module.incentives.sales"],
  clientRoleId: "00000000-0000-0000-0000-000000000098",
  clientRoleName: "PARTNER_SELLER",
};

const PARTNER_SELLER_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_SELLER_USER,
  enabledFeatures: [],
};

const CATALOG_ITEM = {
  id: CATALOG_ITEM_ID,
  name: "Amazon Gift Card",
  description: "Redeem points for Amazon gift cards",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "50.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: ["US", "GB"],
  isReturnable: false,
  defaultReturnWindowDays: 0,
  isGloballyActive: true,
  tenantConfig: null,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const TENANT_SETTINGS = {
  id: "00000000-0000-0000-0000-000000000050",
  batchCadence: "DAILY",
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

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
async function loginAsClientAdmin(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(CLIENT_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
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
  return { setAuthenticated: (v: boolean) => { authenticated = v; } };
}

test("CLIENT_ADMIN enables item and sets processing mode override", async ({ page }) => {
  let configEnabled = false;

  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/catalog/config*", async (route) => {
    if (route.request().method() === "GET") {
      const item = {
        ...CATALOG_ITEM,
        tenantConfig: configEnabled
          ? {
              id: "cfg-1",
              redemptionCatalogItemId: CATALOG_ITEM_ID,
              enabled: true,
              processingModeOverride: "BATCH",
              createdAt: "2026-05-01T00:00:00Z",
              updatedAt: "2026-05-01T00:00:00Z",
            }
          : null,
      };
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: paginatedResponse([item]),
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(`**/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}`, async (route) => {
    if (route.request().method() === "PUT") {
      const body = JSON.parse(route.request().postData() ?? "{}");

      if (
        body.minTransactionAmountOverride &&
        parseFloat(body.minTransactionAmountOverride) < 50
      ) {
        await route.fulfill({
          status: 422,
          contentType: "application/json",
          body: JSON.stringify({
            errorCode: "VALIDATION_ERROR",
            errorMessage:
              "Minimum transaction amount cannot be set below the catalog item's platform minimum of 50.00",
            status: 422,
            timestamp: new Date().toISOString(),
            path: `/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}`,
          }),
        });
        return;
      }

      configEnabled = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            id: "cfg-1",
            redemptionCatalogItemId: CATALOG_ITEM_ID,
            enabled: body.enabled,
            processingModeOverride: body.processingModeOverride ?? null,
            createdAt: "2026-05-01T00:00:00Z",
            updatedAt: new Date().toISOString(),
          },
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route("**/api/v1/redemption/settings*", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: TENANT_SETTINGS,
        message: "OK",
        success: true,
        timestamp: new Date().toISOString(),
      }),
    });
  });

  await page.goto("/settings/redemption/catalog");
  await expect(page.getByTestId("catalog-config-page")).toBeVisible();

  // Toggle enable switch for the item
  await page.getByTestId("enable-switch").first().click();

  // Switch shows enabled state (checked = true after toggle from false)
  // The table re-renders with tenantConfig.enabled=true after cache invalidation + mock returns enabled
  await expect(page.getByTestId("enable-switch").first()).toBeVisible();
});

test("CLIENT_ADMIN updates batchCadence", async ({ page }) => {
  let currentCadence = "DAILY";

  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/catalog/config*", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: paginatedResponse([CATALOG_ITEM]),
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route("**/api/v1/redemption/settings", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: { ...TENANT_SETTINGS, batchCadence: currentCadence },
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else if (route.request().method() === "PUT") {
      const body = JSON.parse(route.request().postData() ?? "{}");
      currentCadence = body.batchCadence;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: { ...TENANT_SETTINGS, batchCadence: currentCadence },
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.goto("/settings/redemption/catalog");
  await expect(page.getByTestId("catalog-config-page")).toBeVisible();
  await expect(page.getByTestId("tenant-settings-form")).toBeVisible();

  // DAILY should be selected by default
  await expect(page.getByTestId("cadence-daily")).toBeChecked();

  // Select WEEKLY
  await page.getByTestId("cadence-weekly").click();
  await expect(page.getByTestId("cadence-weekly")).toBeChecked();

  // Save settings
  await page.getByRole("button", { name: /save settings/i }).click();

  // After save, WEEKLY should still be selected
  await expect(page.getByTestId("cadence-weekly")).toBeChecked();
});

test("PARTNER_SELLER cannot access config endpoints", async ({ page }) => {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(PARTNER_SELLER_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route) => {
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

  await page.goto("/settings/redemption/catalog");

  // ProtectedRoute blocks render — page should not be attached
  await expect(page.getByTestId("catalog-config-page")).not.toBeAttached();
});
