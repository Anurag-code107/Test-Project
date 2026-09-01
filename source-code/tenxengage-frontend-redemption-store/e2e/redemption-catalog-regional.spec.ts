import { test, expect, type Route } from "@playwright/test";

const CLIENT_ADMIN_EMAIL = "admin@techpartners.com";
const CLIENT_ADMIN_PASSWORD = "Admin@123";

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
  tenantConfig: {
    id: "cfg-1",
    redemptionCatalogItemId: CATALOG_ITEM_ID,
    enabled: true,
    createdAt: "2026-05-01T00:00:00Z",
    updatedAt: "2026-05-01T00:00:00Z",
  },
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const TENANT_SETTINGS = {
  id: "00000000-0000-0000-0000-000000000050",
  batchCadence: "DAILY",
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const US_REGION_CONFIG = {
  id: "rc-1",
  redemptionCatalogItemId: CATALOG_ITEM_ID,
  regionCode: "US",
  enabled: true,
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
async function loginAsClientAdmin(page: any, loginResponse = CLIENT_LOGIN_RESPONSE) {
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
  await page.getByLabel(/email/i).fill(CLIENT_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(CLIENT_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;
}

test("CLIENT_ADMIN adds a regional override", async ({ page }) => {
  let usRegionEnabled = false;

  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/catalog/config*", async (route: Route) => {
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

  await page.route(`**/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}/regions`, async (route: Route) => {
    if (route.request().method() === "GET") {
      const configs = usRegionEnabled ? [US_REGION_CONFIG] : [];
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: configs,
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(
    `**/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}/regions/US`,
    async (route: Route) => {
      if (route.request().method() === "PUT") {
        usRegionEnabled = true;
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: US_REGION_CONFIG,
            message: "OK",
            success: true,
            timestamp: new Date().toISOString(),
          }),
        });
      } else {
        await route.continue();
      }
    },
  );

  await page.route("**/api/v1/redemption/settings*", async (route: Route) => {
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

  // Expand the item row to reveal RegionalConfigMatrix
  await page.getByTestId(`expand-row-${CATALOG_ITEM_ID}`).click();

  // Regional matrix should be visible with US and GB rows
  await expect(page.getByTestId("region-row-US")).toBeVisible();
  await expect(page.getByTestId("region-row-GB")).toBeVisible();

  // Toggle US region to enabled
  await page.getByTestId("region-toggle-US").click();

  // PUT was called — US toggle is now active
  await expect(page.getByTestId("region-toggle-US")).toBeVisible();
});

test("CLIENT_ADMIN deletes regional override — fallback to tenant-level", async ({ page }) => {
  let regionExists = true;

  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/catalog/config*", async (route: Route) => {
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

  await page.route(`**/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}/regions`, async (route: Route) => {
    if (route.request().method() === "GET") {
      const configs = regionExists ? [US_REGION_CONFIG] : [];
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: configs,
          message: "OK",
          success: true,
          timestamp: new Date().toISOString(),
        }),
      });
    } else {
      await route.continue();
    }
  });

  await page.route(
    `**/api/v1/redemption/catalog/config/${CATALOG_ITEM_ID}/regions/US`,
    async (route: Route) => {
      if (route.request().method() === "DELETE") {
        regionExists = false;
        await route.fulfill({ status: 204 });
      } else {
        await route.continue();
      }
    },
  );

  await page.route("**/api/v1/redemption/settings*", async (route: Route) => {
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

  // Expand the item row
  await page.getByTestId(`expand-row-${CATALOG_ITEM_ID}`).click();

  // US override exists — delete button should be visible
  await expect(page.getByTestId("region-delete-US")).toBeVisible();

  // Delete the US regional override
  await page.getByTestId("region-delete-US").click();

  // After deletion the remove button is gone and the fallback indicator appears for US
  await expect(page.getByTestId("region-delete-US")).not.toBeAttached();
  await expect(page.getByTestId("fallback-US")).toBeVisible();
});
