import { test, expect, type Route } from "@playwright/test";

// ── Fixture data — shape: contracts/endpoints/redemption-returns.yaml ─────────

const ADMIN_EMAIL = "admin@techpartners.com";
const ADMIN_PASSWORD = "Admin@123";

// shape: contracts/endpoints/redemption-returns.yaml (LoginResponse)
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
const TIMED_OUT_QUEUE_ITEM = {
  id: "return-004",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Alice Partner",
  partnerCompanyName: "Alpha Ltd",
  amount: "300.00",
  currencyId: "credits",
  status: "RETURN_TIMED_OUT",
  reason: "Item not received",
  createdAt: "2026-05-01T08:00:00Z",
  updatedAt: "2026-05-01T08:00:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnDetailResponse — after CONFIRM resolve)
const CONFIRMED_RETURN_DETAIL = {
  id: "return-004",
  redemptionId: "tx-004",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Alice Partner",
  amount: "300.00",
  currencyId: "credits",
  status: "RETURN_CONFIRMED",
  reason: "Item not received",
  confirmedAt: "2026-06-13T10:00:00Z",
  reviewNotes: "Confirmed after 7-day timeout — Xoxoday unresponsive",
  createdAt: "2026-05-01T08:00:00Z",
  updatedAt: "2026-06-13T10:00:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnDetailResponse — after REJECT resolve)
const REJECTED_RETURN_DETAIL = {
  id: "return-004",
  redemptionId: "tx-004",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Alice Partner",
  amount: "300.00",
  currencyId: "credits",
  status: "RETURN_REJECTED",
  reason: "Item not received",
  rejectedAt: "2026-06-13T10:30:00Z",
  reviewNotes: "Rejected after 7-day timeout — Xoxoday unresponsive",
  createdAt: "2026-05-01T08:00:00Z",
  updatedAt: "2026-06-13T10:30:00Z",
};

const TIMED_OUT_QUEUE_RESPONSE = {
  data: [TIMED_OUT_QUEUE_ITEM],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const CONFIRMED_QUEUE_RESPONSE = {
  data: [{ ...TIMED_OUT_QUEUE_ITEM, status: "RETURN_CONFIRMED", updatedAt: "2026-06-13T10:00:00Z" }],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const REJECTED_QUEUE_RESPONSE = {
  data: [{ ...TIMED_OUT_QUEUE_ITEM, status: "RETURN_REJECTED", updatedAt: "2026-06-13T10:30:00Z" }],
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

test.describe("Admin — Resolve Timed-Out Return (AC-1, AC-2, AC-5, AC-6)", () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);

    // Mock existing redemptions approval queue tab (no data)
    await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: apiWrap({ data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }),
      });
    });

    // Register specific route AFTER broad — Playwright last-registered-wins
    // Broad list endpoint registered first
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      const url = route.request().url();
      // Only intercept pure list URL (no path segment after returns)
      if (!url.match(/\/returns\/[^?]/)) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(TIMED_OUT_QUEUE_RESPONSE),
        });
        return;
      }
      await route.continue();
    });

    // Specific detail + action routes registered after (takes precedence)
    await page.route("**/api/v1/redemption/admin/returns/**", async (route: Route) => {
      const url = route.request().url();
      const method = route.request().method();

      if (url.includes("/resolve") && method === "POST") {
        const body = await route.request().postDataJSON();
        const detail = body?.resolution === "CONFIRM" ? CONFIRMED_RETURN_DETAIL : REJECTED_RETURN_DETAIL;
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(detail),
        });
        return;
      }

      if (method === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap({
            ...TIMED_OUT_QUEUE_ITEM,
            redemptionId: "tx-004",
            timedOutAt: "2026-06-08T08:00:00Z",
            reviewNotes: null,
            vendorReturnReference: null,
          }),
        });
        return;
      }

      await route.continue();
    });
  });

  test("admin resolves with CONFIRM — return shows RETURN_CONFIRMED (AC-1, AC-5, AC-6)", async ({ page }) => {
    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    // AC-6: RETURN_TIMED_OUT row is visible
    await expect(page.getByText("Amazon Gift Card")).toBeVisible({ timeout: 5000 });

    // AC-6: Resolve action visible in kebab for RETURN_TIMED_OUT
    await page.getByRole("button", { name: /actions for amazon gift card/i }).click();
    await expect(page.getByRole("menuitem", { name: /^resolve$/i })).toBeVisible();
    await page.getByRole("menuitem", { name: /^resolve$/i }).click();

    // AC-5: Dialog opens with correct title and subtitle
    await expect(page.getByText("Resolve Timed-Out Return")).toBeVisible();
    await expect(
      page.getByText("This return has been waiting for Xoxoday confirmation for more than 7 days.")
    ).toBeVisible();

    // AC-5: Resolve button disabled with no radio selected
    const resolveBtn = page.getByRole("button", { name: /resolve return/i });
    await expect(resolveBtn).toBeDisabled();

    // AC-5: Select CONFIRM radio
    await page.getByLabel(/confirm return \(credit wallet\)/i).click();

    // AC-5: Resolve button enabled after selection
    await expect(resolveBtn).toBeEnabled();

    // Update list mock to return CONFIRMED status after mutation
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      const url = route.request().url();
      if (!url.match(/\/returns\/[^?]/)) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(CONFIRMED_QUEUE_RESPONSE),
        });
        return;
      }
      await route.continue();
    });

    await resolveBtn.click();

    // AC-1: Dialog closes; AC-5: admin queue updated to RETURN_CONFIRMED
    await expect(page.getByText("Resolve Timed-Out Return")).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByText("Return Confirmed").first()).toBeVisible({ timeout: 5000 });
  });

  test("admin resolves with REJECT — return shows RETURN_REJECTED (AC-2)", async ({ page }) => {
    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    await expect(page.getByText("Amazon Gift Card")).toBeVisible({ timeout: 5000 });

    await page.getByRole("button", { name: /actions for amazon gift card/i }).click();
    await page.getByRole("menuitem", { name: /^resolve$/i }).click();

    await expect(page.getByText("Resolve Timed-Out Return")).toBeVisible();

    // Select REJECT radio
    await page.getByLabel(/reject return \(no credit\)/i).click();

    const resolveBtn = page.getByRole("button", { name: /resolve return/i });
    await expect(resolveBtn).toBeEnabled();

    // Update list mock to return REJECTED status
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      const url = route.request().url();
      if (!url.match(/\/returns\/[^?]/)) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap(REJECTED_QUEUE_RESPONSE),
        });
        return;
      }
      await route.continue();
    });

    await resolveBtn.click();

    // AC-2: Dialog closes; queue shows RETURN_REJECTED
    await expect(page.getByText("Resolve Timed-Out Return")).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByText("Return Rejected").first()).toBeVisible({ timeout: 5000 });
  });

  test("Resolve action not visible for non-TIMED_OUT rows (AC-6)", async ({ page }) => {
    // Override the list to return a PENDING_APPROVAL row instead
    await page.route("**/api/v1/redemption/admin/returns*", async (route: Route) => {
      const url = route.request().url();
      if (!url.match(/\/returns\/[^?]/)) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: apiWrap({
            ...TIMED_OUT_QUEUE_RESPONSE,
            data: [{ ...TIMED_OUT_QUEUE_ITEM, status: "PENDING_APPROVAL" }],
          }),
        });
        return;
      }
      await route.continue();
    });

    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    await expect(page.getByText("Amazon Gift Card")).toBeVisible({ timeout: 5000 });

    // Open kebab for PENDING_APPROVAL row
    await page.getByRole("button", { name: /actions for amazon gift card/i }).click();

    // AC-6: Resolve action should NOT be visible
    await expect(page.getByRole("menuitem", { name: /^resolve$/i })).not.toBeVisible();

    // Approve and Reject should be visible instead
    await expect(page.getByRole("menuitem", { name: /^approve$/i })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: /^reject$/i })).toBeVisible();
  });
});
