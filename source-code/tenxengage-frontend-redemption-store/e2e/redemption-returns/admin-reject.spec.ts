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
  id: "return-002",
  catalogItemName: "Flipkart Voucher",
  partnerDisplayName: "Bob Partner",
  partnerCompanyName: "Beta Ltd",
  amount: "200.00",
  currencyId: "credits",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-02T09:00:00Z",
  updatedAt: "2026-06-02T09:00:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnDetailResponse — after reject)
const REJECTED_RETURN_DETAIL = {
  id: "return-002",
  redemptionId: "tx-002",
  catalogItemName: "Flipkart Voucher",
  partnerDisplayName: "Bob Partner",
  amount: "200.00",
  currencyId: "credits",
  status: "RETURN_REJECTED",
  reviewNotes: "Gift card was already redeemed by the partner.",
  rejectedAt: "2026-06-13T11:00:00Z",
  createdAt: "2026-06-02T09:00:00Z",
  updatedAt: "2026-06-13T11:00:00Z",
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

const REJECTED_QUEUE_RESPONSE = {
  data: [{ ...PENDING_RETURN_QUEUE_ITEM, status: "RETURN_REJECTED", updatedAt: "2026-06-13T11:00:00Z" }],
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

test.describe("Admin — Reject Return (AC-3, AC-6)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);

    // Mock redemptions approval queue (existing tab)
    await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap({ data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }),
      });
    });

    // Specific patterns registered before broad (Playwright last-registered-wins)
    await page.route("**/api/v1/redemption/admin/returns/**", async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes("/reject") && method === "POST") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(REJECTED_RETURN_DETAIL),
        });
        return;
      }

      if (method === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap({
            ...PENDING_RETURN_QUEUE_ITEM,
            redemptionId: "tx-002",
            reviewNotes: null,
            vendorReturnReference: null,
          }),
        });
        return;
      }

      await route.continue();
    });

    // Broad list endpoint
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap(PENDING_QUEUE_RESPONSE),
      });
    });
  });

  test("admin rejects return with reason — shows RETURN_REJECTED (AC-3, AC-6)", async ({ page }) => {
    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    await expect(page.getByText("Flipkart Voucher")).toBeVisible({ timeout: 5000 });

    // Open kebab → Reject
    await page.getByRole("button", { name: /actions for flipkart voucher/i }).click();
    await page.getByRole("menuitem", { name: /^reject$/i }).click();

    // AC-6: RejectReturnDialog opens
    await expect(page.getByText("Reject Return Request?")).toBeVisible();

    // AC-6: Submit disabled when reason is blank
    const submitBtn = page.getByRole("button", { name: /reject request/i });
    await expect(submitBtn).toBeDisabled();

    // Fill rejection reason
    await page.getByLabel("Rejection reason").fill("Gift card was already redeemed by the partner.");

    // AC-6: Submit enabled after filling
    await expect(submitBtn).toBeEnabled();

    // Update list mock to return REJECTED status after mutation
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap(REJECTED_QUEUE_RESPONSE),
      });
    });

    await submitBtn.click();

    // AC-3: Row status updates to RETURN_REJECTED
    await expect(page.getByText("Return Rejected").first()).toBeVisible({ timeout: 5000 });
  });

  test("blank rejection reason keeps submit button disabled (AC-6)", async ({ page }) => {
    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    await expect(page.getByText("Flipkart Voucher")).toBeVisible({ timeout: 5000 });

    await page.getByRole("button", { name: /actions for flipkart voucher/i }).click();
    await page.getByRole("menuitem", { name: /^reject$/i }).click();

    await expect(page.getByText("Reject Return Request?")).toBeVisible();

    // AC-6: Submit disabled with empty reason
    const submitBtn = page.getByRole("button", { name: /reject request/i });
    await expect(submitBtn).toBeDisabled();

    // Char counter visible
    await expect(page.getByText("0/1000")).toBeVisible();
  });
});
