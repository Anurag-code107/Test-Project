import { test, expect, type Route } from "@playwright/test";

const PARTNER_SELLER_EMAIL = "seller@techpartners.com";
const PARTNER_SELLER_PASSWORD = "Seller@123";
const PARTNER_ADMIN_EMAIL = "admin@techpartners.com";
const PARTNER_ADMIN_PASSWORD = "Admin@123";
const CLIENT_ADMIN_EMAIL = "cadmin@tenxengage.com";
const CLIENT_ADMIN_PASSWORD = "CAdmin@123";

const PARTNER_USER = {
  id: "00000000-0000-0000-0000-000000000010",
  email: PARTNER_SELLER_EMAIL,
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.incentives.sales", "module.redemption_store", "action.redemption.view_history", "action.redemption.export"],
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

const PARTNER_ADMIN_USER = {
  id: "00000000-0000-0000-0000-000000000011",
  email: PARTNER_ADMIN_EMAIL,
  firstName: "Partner",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.incentives.sales", "module.redemption_store", "action.redemption.view_history"],
  clientRoleId: "00000000-0000-0000-0000-000000000098",
  clientRoleName: "Partner Admin",
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
    "action.redemption.view_all_history",
    "action.redemption.export",
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

const ADMIN_TX_1 = {
  id: "atx-001",
  status: "COMPLETED",
  amount: "250.00",
  currencyId: "points",
  catalogItemId: "item-001",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT",
  userId: "u-001",
  userDisplayName: "Alice Johnson",
  partnerCompanyId: "p-001",
  partnerCompanyName: "Tech Corp",
  submittedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T10:05:00Z",
  estimatedDelivery: "Instant",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:05:00Z",
};

const ADMIN_TX_2 = {
  id: "atx-002",
  status: "PENDING_APPROVAL",
  amount: "100.00",
  currencyId: "points",
  catalogItemId: "item-002",
  catalogItemName: "Visa Prepaid",
  processingMode: "APPROVAL_REQUIRED",
  userId: "u-002",
  userDisplayName: "Bob Martinez",
  partnerCompanyId: null,
  partnerCompanyName: null,
  submittedAt: "2026-06-02T09:00:00Z",
  estimatedDelivery: "N/A",
  createdAt: "2026-06-02T09:00:00Z",
  updatedAt: "2026-06-02T09:00:00Z",
};

const PARTNER_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_USER,
  enabledFeatures: ["redemption_store"],
};

const PARTNER_ADMIN_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_ADMIN_USER,
  enabledFeatures: ["redemption_store"],
};

const TX_1 = {
  id: "tx-001",
  status: "COMPLETED",
  amount: "150.00",
  currencyId: "points",
  catalogItemId: "item-001",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT",
  submittedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T10:05:00Z",
  estimatedDelivery: "Instant",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:05:00Z",
};

const TX_2 = {
  id: "tx-002",
  status: "COMPLETED",
  amount: "50.00",
  currencyId: "points",
  catalogItemId: "item-002",
  catalogItemName: "Starbucks Card",
  processingMode: "BATCH",
  submittedAt: "2026-06-02T09:00:00Z",
  completedAt: "2026-06-02T12:00:00Z",
  estimatedDelivery: "Next batch",
  createdAt: "2026-06-02T09:00:00Z",
  updatedAt: "2026-06-02T12:00:00Z",
};

const TX_3 = {
  id: "tx-003",
  status: "FAILED",
  amount: "200.00",
  currencyId: "cash",
  catalogItemId: "item-003",
  catalogItemName: "Visa Prepaid",
  processingMode: "APPROVAL_REQUIRED",
  submittedAt: "2026-06-03T08:00:00Z",
  estimatedDelivery: "N/A",
  createdAt: "2026-06-03T08:00:00Z",
  updatedAt: "2026-06-03T08:30:00Z",
};

const TX_1_DETAIL = {
  ...TX_1,
  category: "NON_CASH",
  walletType: "INDIVIDUAL",
  vendorReferenceId: "VND-ABC123",
  linkedReturnId: null,
};

function paginatedResponse<T>(items: T[], totalElements?: number) {
  const total = totalElements ?? items.length;
  return {
    data: items,
    page: 0,
    pageSize: 20,
    totalElements: total,
    totalPages: Math.ceil(total / 20),
    hasNext: false,
    hasPrevious: false,
  };
}

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
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(CLIENT_ADMIN_LOGIN_RESPONSE) });
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
async function loginAsPartnerAdmin(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(PARTNER_ADMIN_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(PARTNER_ADMIN_LOGIN_RESPONSE) });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill(PARTNER_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(PARTNER_ADMIN_PASSWORD);

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
      body: JSON.stringify(PARTNER_LOGIN_RESPONSE),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(PARTNER_LOGIN_RESPONSE) });
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

test("personal history list renders with filters", async ({ page }) => {
  await loginAsPartnerSeller(page);

  let callCount = 0;
  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    callCount++;
    if (url.includes("status=COMPLETED") && callCount > 1) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1, TX_2, TX_3]))),
      });
    }
  });

  await page.goto("/redemption/history");

  // Initial load — 1 header tr + 3 data trs (data rows have role="button" so getByRole("row") misses them)
  await expect(page.locator("tr")).toHaveCount(4);

  // Apply status=COMPLETED filter
  await page.getByRole("combobox", { name: /status filter/i }).click();
  await page.getByRole("option", { name: "Completed" }).click();

  // After filter — 1 header + 1 data row
  await expect(page.locator("tr")).toHaveCount(2);
});

test("transaction detail sheet opens on row click", async ({ page }) => {
  await loginAsPartnerSeller(page);

  // Register general list route first (lower priority)
  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    if (url.includes("/tx-001")) {
      // Detail request
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(TX_1_DETAIL)),
      });
    } else {
      // List request
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  // Click row
  await page.getByText("Amazon Gift Card").click();

  // Sheet appears
  await expect(page.getByText("Transaction detail")).toBeVisible();
  await expect(page.getByText("VND-ABC123")).toBeVisible();

  // Close sheet
  await page.getByRole("button", { name: /close transaction detail/i }).click();
  await expect(page.getByText("Transaction detail")).not.toBeVisible();
});

test("filter validation rejects invalid date range", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
    });
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  // Open date-from picker and select June 10
  await page.getByRole("button", { name: /date from filter/i }).click();
  // Navigate calendar to June if needed and click June 10
  const june10 = page.getByRole("gridcell", { name: "10" }).first();
  await june10.click();

  // Now set date-to to June 1 (earlier than June 10)
  await page.getByRole("button", { name: /date to filter/i }).click();
  const june1 = page.getByRole("gridcell", { name: "1" }).first();
  await june1.click();

  // Inline error should appear
  await expect(page.getByText("Start date must be before end date")).toBeVisible();
});

test("empty state shows correct copy based on filter state", async ({ page }) => {
  await loginAsPartnerSeller(page);

  // No filters → empty response
  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    if (url.includes("status=COMPLETED")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([]))),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([]))),
      });
    }
  });

  await page.goto("/redemption/history");

  // No filters — "No transactions yet"
  await expect(page.getByText("No transactions yet").first()).toBeVisible();

  // Apply a status filter → "No transactions match your filters"
  await page.getByRole("combobox", { name: /status filter/i }).click();
  const filteredResponse = page.waitForResponse("**/api/v1/redemption/requests**");
  await page.getByRole("option", { name: "Completed" }).click();
  await filteredResponse;

  await expect(page.getByText("No transactions match your filters").first()).toBeVisible({ timeout: 10000 });
});

test("PARTNER_SELLER cannot access all-tenant endpoint", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests/all**", async (route: Route) => {
    await route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ errorCode: "FORBIDDEN" }) });
  });

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    if (url.includes("/all")) {
      await route.fulfill({ status: 403 });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");

  // Page loads normally for personal history
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  // No all-tenant route or admin history page accessible
  await expect(page.getByRole("link", { name: /all.*history|tenant.*history/i })).not.toBeVisible();
});

// US-02 scenarios

test("PARTNER_ADMIN sees company tab and company redemptions", async ({ page }) => {
  await loginAsPartnerAdmin(page);

  const COMPANY_TX_1 = { ...TX_1, id: "ctx-001", catalogItemName: "Company Gift Card" };
  const COMPANY_TX_2 = { ...TX_2, id: "ctx-002", catalogItemName: "Company Starbucks" };

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    if (url.includes("/company")) {
      if (url.includes("status=COMPLETED")) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(apiResponse(paginatedResponse([COMPANY_TX_1]))),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(apiResponse(paginatedResponse([COMPANY_TX_1, COMPANY_TX_2]))),
        });
      }
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1, TX_2]))),
      });
    }
  });

  await page.goto("/redemption/history");

  // Both tabs visible
  await expect(page.getByRole("tab", { name: "Personal" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Company" })).toBeVisible();

  // Click Company tab → company data loads (2 rows + 1 header = 3)
  await page.getByRole("tab", { name: "Company" }).click();
  await expect(page.getByText("Company Gift Card")).toBeVisible();
  await expect(page.locator("tr")).toHaveCount(3);

  // Apply status filter → filtered results (1 row + 1 header = 2)
  await page.getByRole("combobox", { name: /status filter/i }).click();
  await page.getByRole("option", { name: "Completed" }).click();
  await expect(page.locator("tr")).toHaveCount(2);
});

test("PARTNER_SELLER sees only Personal tab", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
    });
  });

  await page.goto("/redemption/history");

  // Personal data loads normally
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  // Company tab is absent from DOM
  await expect(page.getByRole("tab", { name: "Company" })).toHaveCount(0);
});

// US-03 scenarios

test("sync export downloads immediately", async ({ page }) => {
  await loginAsPartnerSeller(page);

  const csvBytes = "id,status,amount\ntx-001,COMPLETED,150.00\n";

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/export") && method === "POST") {
      await route.fulfill({
        status: 200,
        headers: {
          "Content-Disposition": 'attachment; filename="redemption-history.csv"',
          "Content-Type": "text/csv",
        },
        body: csvBytes,
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export" }).click();
  await expect(page.getByText("Export transactions")).toBeVisible();
  await page.getByRole("button", { name: "Export", exact: true }).last().click();

  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain("redemption-history");
});

test("async export shows polling then download button", async ({ page }) => {
  await loginAsPartnerSeller(page);

  let pollCount = 0;

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/export/job-async/download")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse({
          id: "job-async",
          status: "COMPLETED",
          format: "XLSX",
          scope: "PERSONAL",
          rowCount: 1500,
          expiresAt: "2026-06-09T13:00:00Z",
          downloadUrl: "https://storage.example.com/job-async.xlsx",
          createdAt: "2026-06-08T13:00:00Z",
          updatedAt: "2026-06-08T13:01:00Z",
        })),
      });
    } else if (url.includes("/export/job-async")) {
      pollCount++;
      const status = pollCount >= 3 ? "COMPLETED" : "PENDING";
      const extra = pollCount >= 3 ? { rowCount: 1500, expiresAt: "2026-06-09T13:00:00Z" } : {};
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse({
          id: "job-async", status, format: "XLSX", scope: "PERSONAL",
          createdAt: "2026-06-08T13:00:00Z", updatedAt: "2026-06-08T13:01:00Z",
          ...extra,
        })),
      });
    } else if (url.includes("/export") && method === "POST") {
      // 202 — body must be JSON; axios receives as arraybuffer, TextDecoder converts back
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify(apiResponse({
          id: "job-async", status: "PENDING", format: "XLSX", scope: "PERSONAL",
          createdAt: "2026-06-08T13:00:00Z", updatedAt: "2026-06-08T13:00:00Z",
        })),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  await page.getByRole("button", { name: "Export" }).click();
  await expect(page.getByText("Export transactions")).toBeVisible();

  // Select XLSX format
  await page.getByRole("combobox").click();
  await page.getByRole("option", { name: /excel/i }).click();

  await page.getByRole("button", { name: "Export", exact: true }).last().click();

  await expect(page.getByText("Generating your export…")).toBeVisible();

  // Wait for polling to resolve to COMPLETED
  await expect(page.getByText("Your export is ready")).toBeVisible({ timeout: 15000 });
  await expect(page.getByRole("button", { name: "Download" })).toBeVisible();
});

test("export with no results shows inline error", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/export") && method === "POST") {
      await route.fulfill({
        status: 422,
        contentType: "application/json",
        body: JSON.stringify({ errorCode: "VALIDATION_FAILED", errorMessage: "No records match the selected filters" }),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  await page.getByRole("button", { name: "Export" }).click();
  await expect(page.getByText("Export transactions")).toBeVisible();
  await page.getByRole("button", { name: "Export", exact: true }).last().click();

  await expect(page.getByText("No records match the selected filters")).toBeVisible();
});

// US-04 scenarios

test("CLIENT_ADMIN sees tenant-wide history with User and Company columns", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/all**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([ADMIN_TX_1, ADMIN_TX_2]))),
    });
  });

  await page.goto("/redemption/admin/history");

  // Page title and column headers
  await expect(page.getByText("Tenant transaction history")).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "User" })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "Company" })).toBeVisible();

  // Row data: user display names and company names
  await expect(page.getByText("Alice Johnson")).toBeVisible();
  await expect(page.getByText("Tech Corp")).toBeVisible();
  await expect(page.getByText("Bob Martinez")).toBeVisible();
});

test("PARTNER_SELLER cannot access tenant admin history page", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
    });
  });

  // Attempt to navigate to admin history — ProtectedRoute should redirect away
  await page.goto("/redemption/admin/history");

  // Should NOT be on the tenant history page
  await expect(page.getByText("Tenant transaction history")).not.toBeVisible();

  // The Tenant History nav entry should not be visible
  await expect(page.getByRole("link", { name: "Tenant History" })).toHaveCount(0);
});

test("CLIENT_ADMIN exports tenant-wide data", async ({ page }) => {
  await loginAsClientAdmin(page);

  const csvBytes = "id,userId,status,amount\natx-001,u-001,COMPLETED,250.00\n";
  let exportBody: Record<string, unknown> | null = null;

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/export") && method === "POST") {
      exportBody = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: 200,
        headers: {
          "Content-Disposition": 'attachment; filename="tenant-redemption-history.csv"',
          "Content-Type": "text/csv",
        },
        body: csvBytes,
      });
    } else if (url.includes("/all")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([ADMIN_TX_1]))),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([]))),
      });
    }
  });

  await page.goto("/redemption/admin/history");
  await expect(page.getByText("Tenant transaction history")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export" }).click();
  await expect(page.getByText("Export transactions")).toBeVisible();
  await page.getByRole("button", { name: "Export", exact: true }).last().click();

  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain("redemption-history");
  // Tenant export must be scoped ALL_TENANT (not the backend default) so the
  // whole tenant's records are included, not just the caller's.
  expect(exportBody?.scope).toBe("ALL_TENANT");
});

test("failed async export shows retry button", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.route("**/api/v1/redemption/requests**", async (route: Route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/export/job-fail")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse({
          id: "job-fail", status: "FAILED", format: "CSV", scope: "PERSONAL",
          failureReason: "Storage unavailable",
          createdAt: "2026-06-08T13:00:00Z", updatedAt: "2026-06-08T13:01:00Z",
        })),
      });
    } else if (url.includes("/export") && method === "POST") {
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify(apiResponse({
          id: "job-fail", status: "PENDING", format: "CSV", scope: "PERSONAL",
          createdAt: "2026-06-08T13:00:00Z", updatedAt: "2026-06-08T13:00:00Z",
        })),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([TX_1]))),
      });
    }
  });

  await page.goto("/redemption/history");
  await expect(page.getByText("Amazon Gift Card")).toBeVisible();

  await page.getByRole("button", { name: "Export" }).click();
  await expect(page.getByText("Export transactions")).toBeVisible();
  await page.getByRole("button", { name: "Export", exact: true }).last().click();

  await expect(page.getByText("Export failed — please try again")).toBeVisible({ timeout: 10000 });
  await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
});

// US-04: detail authorization guard
test("CLIENT_ADMIN with view_all_history sees interactive rows on tenant history page", async ({ page }) => {
  // CLIENT_ADMIN_USER has view_all_history. canViewDetail = canAny(view_history,
  // view_all_history), so a tenant auditor with view_all_history gets interactive rows.
  // (Backend/contract must also accept view_all_history on GET /redemption/requests/{id}.)
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/all**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([ADMIN_TX_1]))),
    });
  });

  await page.route("**/api/v1/redemption/requests/atx-001**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse({
        ...ADMIN_TX_1,
        category: "NON_CASH",
        walletType: "INDIVIDUAL",
        vendorReferenceId: null,
        linkedReturnId: null,
      })),
    });
  });

  await page.goto("/redemption/admin/history");
  await expect(page.getByText("Alice Johnson")).toBeVisible();

  // Row is interactive — click it and the detail sheet opens
  await page.getByText("Alice Johnson").click();
  await expect(page.getByText("Transaction detail")).toBeVisible();
});
