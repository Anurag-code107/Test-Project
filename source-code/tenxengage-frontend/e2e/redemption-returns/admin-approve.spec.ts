import { test, expect, type Route } from "@playwright/test";

// ── Fixture data — shape: contracts/endpoints/redemption-returns.yaml ─────────

const ADMIN_EMAIL = "admin@techpartners.com";
const ADMIN_PASSWORD = "Admin@123";

const ADMIN_USER = {
  id: "00000000-0000-0000-0000-000000000030",
  email: ADMIN_EMAIL,
  firstName: "Client",
  lastName: "Admin",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.redemption_store",
    "action.redemption.return.review",
    "action.redemption.approve",
  ],
  clientRoleId: "00000000-0000-0000-0000-000000000098",
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

// shape: contracts/endpoints/redemption-returns.yaml (ReturnQueueItemResponse)
const PENDING_RETURN_QUEUE_ITEM = {
  id: "return-001",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Jane Seller",
  partnerCompanyName: "Acme Corp",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnDetailResponse)
const APPROVED_RETURN_DETAIL = {
  id: "return-001",
  redemptionId: "tx-001",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Jane Seller",
  amount: "150.00",
  currencyId: "points",
  status: "APPROVED",
  approvedAt: "2026-06-13T10:05:00Z",
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-13T10:05:00Z",
};

const PENDING_QUEUE_RESPONSE = {
  data: [PENDING_RETURN_QUEUE_ITEM],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const APPROVED_QUEUE_RESPONSE = {
  data: [{ ...PENDING_RETURN_QUEUE_ITEM, status: "APPROVED", updatedAt: "2026-06-13T10:05:00Z" }],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

function apiWrap<T>(data: T) {
  return JSON.stringify({ data, message: "Success", success: true, timestamp: new Date().toISOString() });
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsAdmin(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        expiresIn: 3600,
        user: ADMIN_USER,
        enabledFeatures: ["redemption_store", "redemption_non_cash_returns"],
      }),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          expiresIn: 3600,
          user: ADMIN_USER,
          enabledFeatures: ["redemption_store", "redemption_non_cash_returns"],
        }),
      });
    } else {
      await route.fulfill({ status: 401 });
    }
  });

  await page.goto("/login");
  await page.getByLabel(/email/i).fill(ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  await loginDone;

  authenticated = true;
}

test.describe("Admin — Approve Return (AC-2, AC-5, AC-7)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);

    // Mock redemptions approval queue (existing tab — not returns)
    await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap({ data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }),
      });
    });

    // Mock admin returns queue — broad first, specific second (anti-pattern register)
    await page.route("**/api/v1/redemption/admin/returns/**", async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes("/approve") && method === "POST") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(APPROVED_RETURN_DETAIL),
        });
        return;
      }

      if (method === "GET") {
        // Detail endpoint — return pending detail
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap({
            ...PENDING_RETURN_QUEUE_ITEM,
            redemptionId: "tx-001",
            reviewNotes: null,
            vendorReturnReference: null,
          }),
        });
        return;
      }

      await route.continue();
    });

    // Broad list endpoint (registered after specific to lose on overlap)
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap(PENDING_QUEUE_RESPONSE),
      });
    });
  });

  test("admin views Returns tab with PENDING_APPROVAL default and approves a return (AC-2, AC-5, AC-7)", async ({ page }) => {
    await page.goto("/redemption/approval-queue");

    // Navigate to Returns tab
    await page.getByRole("tab", { name: /^returns$/i }).click();

    // AC-5: table rows with PENDING_APPROVAL data
    await expect(page.getByText("Amazon Gift Card")).toBeVisible({ timeout: 5000 });
    await expect(page.getByText("Jane Seller")).toBeVisible();
    await expect(page.getByText("Acme Corp")).toBeVisible();
    await expect(page.getByText("Pending Approval").first()).toBeVisible();

    // Open kebab menu
    await page.getByRole("button", { name: /actions for amazon gift card/i }).click();

    // AC-7: Approve menu item visible for PENDING_APPROVAL
    await expect(page.getByRole("menuitem", { name: /^approve$/i })).toBeVisible();
    await page.getByRole("menuitem", { name: /^approve$/i }).click();

    // AC-7: AlertDialog with verbatim copy
    await expect(page.getByText("Approve this return request?")).toBeVisible();
    await expect(page.getByText(/the return will be forwarded to xoxoday/i)).toBeVisible();

    // Update list mock to return APPROVED status after mutation
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap(APPROVED_QUEUE_RESPONSE),
      });
    });

    // Confirm approval
    await page.getByRole("button", { name: /^approve$/i }).last().click();

    // AC-2: row status updates to APPROVED
    await expect(page.getByText("Approved").first()).toBeVisible({ timeout: 5000 });
  });

  test("admin sees empty state when no returns match filter (AC-5)", async ({ page }) => {
    // Override list to return empty
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap({ data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }),
      });
    });

    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    // AC-5: empty state message
    await expect(page.getByText("No return requests to review.")).toBeVisible({ timeout: 5000 });
  });
});
