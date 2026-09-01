import { test, expect } from "@playwright/test";

// ── Fixture data — shape: contracts/endpoints/redemption-returns.yaml ─────────

const PARTNER_SELLER_EMAIL = "seller@techpartners.com";

const PARTNER_USER = {
  id: "00000000-0000-0000-0000-000000000010",
  email: PARTNER_SELLER_EMAIL,
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.incentives.sales",
    "module.redemption_store",
    "action.redemption.view_history",
    "action.redemption.return.request",
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

const PARTNER_LOGIN_RESPONSE = {
  expiresIn: 3600,
  user: PARTNER_USER,
  enabledFeatures: ["redemption_store", "redemption_non_cash_returns"],
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnSummaryResponse)
const PENDING_RETURN = {
  id: "return-001",
  redemptionId: "tx-eligible-001",
  catalogItemName: "Amazon Gift Card",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-13T10:00:00Z",
  updatedAt: "2026-06-13T10:00:00Z",
};

const MY_RETURNS_WITH_PENDING = {
  data: [PENDING_RETURN],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
};

const MY_RETURNS_EMPTY = {
  data: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
};

function mockAuth(page: typeof import("@playwright/test").Page) {
  return Promise.all([
    page.route("**/api/v1/auth/login", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: PARTNER_LOGIN_RESPONSE, message: "Success", success: true, timestamp: new Date().toISOString() }),
      }),
    ),
    page.route("**/api/v1/auth/me", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: PARTNER_USER, message: "Success", success: true, timestamp: new Date().toISOString() }),
      }),
    ),
  ]);
}

test.describe("Cancel Return — partner flow", () => {
  test.beforeEach(async ({ page }) => {
    await mockAuth(page);

    // Mock F-05 personal redemptions list
    page.route("**/api/v1/redemption/requests*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: { data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
          message: "Success", success: true, timestamp: new Date().toISOString(),
        }),
      }),
    );
  });

  test("Partner cancels a PENDING_APPROVAL return via AlertDialog confirmation", async ({ page }) => {
    let returnsCancelled = false;

    // Mock my-returns: starts with a pending return, then returns empty after cancel
    await page.route("**/api/v1/redemption/returns*", (route) => {
      const method = route.request().method();

      if (method === "DELETE") {
        returnsCancelled = true;
        route.fulfill({ status: 204, body: "" });
        return;
      }

      // GET list — return pending if not cancelled yet, empty after
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: returnsCancelled ? MY_RETURNS_EMPTY : MY_RETURNS_WITH_PENDING,
          message: "Success", success: true, timestamp: new Date().toISOString(),
        }),
      });
    });

    await page.goto("/redemptions/history");

    // Navigate to My Returns tab
    await page.getByRole("tab", { name: "My Returns" }).click();

    // Verify the pending return is shown
    await expect(page.getByText("Amazon Gift Card").first()).toBeVisible();

    // Click Cancel Return button
    await page.getByRole("button", { name: "Cancel Return for Amazon Gift Card" }).click();

    // AlertDialog should appear
    await expect(page.getByRole("alertdialog")).toBeVisible();
    await expect(page.getByText("Cancel this return request?")).toBeVisible();
    await expect(page.getByText("This return request will be cancelled. You can submit a new request for the same redemption later.")).toBeVisible();

    // Confirm cancellation
    await page.getByRole("button", { name: "Yes, cancel it" }).click();

    // AlertDialog should close
    await expect(page.getByRole("alertdialog")).not.toBeVisible();

    // After cancel, the list should show empty state
    await expect(page.getByText("You have no return requests yet.")).toBeVisible();
  });

  test("Keep request button dismisses the AlertDialog without cancelling", async ({ page }) => {
    const cancelRequested = { called: false };

    await page.route("**/api/v1/redemption/returns*", (route) => {
      if (route.request().method() === "DELETE") {
        cancelRequested.called = true;
        route.fulfill({ status: 204, body: "" });
        return;
      }
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: MY_RETURNS_WITH_PENDING,
          message: "Success", success: true, timestamp: new Date().toISOString(),
        }),
      });
    });

    await page.goto("/redemptions/history");
    await page.getByRole("tab", { name: "My Returns" }).click();
    await expect(page.getByText("Amazon Gift Card").first()).toBeVisible();

    // Open AlertDialog
    await page.getByRole("button", { name: "Cancel Return for Amazon Gift Card" }).click();
    await expect(page.getByRole("alertdialog")).toBeVisible();

    // Click Keep request
    await page.getByRole("button", { name: "Keep request" }).click();
    await expect(page.getByRole("alertdialog")).not.toBeVisible();

    // Return still visible — not cancelled
    await expect(page.getByText("Amazon Gift Card").first()).toBeVisible();
    expect(cancelRequested.called).toBe(false);
  });
});
