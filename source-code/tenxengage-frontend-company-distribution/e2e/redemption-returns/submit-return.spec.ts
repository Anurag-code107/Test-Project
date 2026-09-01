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

// shape: contracts/endpoints/redemption-returns.yaml (RedemptionRequestResponse with isReturnEligible)
const ELIGIBLE_TX = {
  id: "tx-eligible-001",
  status: "COMPLETED",
  amount: "150.00",
  currencyId: "points",
  catalogItemId: "item-001",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT",
  submittedAt: "2026-06-01T10:00:00Z",
  completedAt: "2026-06-01T10:05:00Z",
  estimatedDelivery: "Instant",
  isReturnEligible: true,
  createdAt: "2026-06-01T10:00:00Z",
  updatedAt: "2026-06-01T10:05:00Z",
};

const INELIGIBLE_TX = {
  id: "tx-ineligible-001",
  status: "COMPLETED",
  amount: "50.00",
  currencyId: "cash",
  catalogItemId: "item-002",
  catalogItemName: "XTRM Cash Transfer",
  processingMode: "INSTANT",
  submittedAt: "2026-06-01T09:00:00Z",
  completedAt: "2026-06-01T09:05:00Z",
  estimatedDelivery: "Instant",
  isReturnEligible: false,
  createdAt: "2026-06-01T09:00:00Z",
  updatedAt: "2026-06-01T09:05:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnDetailResponse)
const SUBMIT_RETURN_RESPONSE = {
  id: "return-001",
  redemptionId: "tx-eligible-001",
  catalogItemName: "Amazon Gift Card",
  partnerDisplayName: "Partner Seller",
  amount: "150.00",
  currencyId: "points",
  status: "PENDING_APPROVAL",
  createdAt: "2026-06-13T10:00:00Z",
  updatedAt: "2026-06-13T10:00:00Z",
};

// shape: contracts/endpoints/redemption-returns.yaml (ReturnSummaryResponse)
const MY_RETURNS_RESPONSE = {
  data: [
    {
      id: "return-001",
      redemptionId: "tx-eligible-001",
      catalogItemName: "Amazon Gift Card",
      amount: "150.00",
      currencyId: "points",
      status: "PENDING_APPROVAL",
      createdAt: "2026-06-13T10:00:00Z",
      updatedAt: "2026-06-13T10:00:00Z",
    },
  ],
  page: 0,
  pageSize: 20,
  totalElements: 1,
  totalPages: 1,
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

test.describe("Submit Return — partner flow", () => {
  test.beforeEach(async ({ page }) => {
    await mockAuth(page);

    // Mock F-05 personal redemptions list (broad pattern first, specific second per anti-pattern register)
    page.route("**/api/v1/redemption/requests*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            data: [ELIGIBLE_TX, INELIGIBLE_TX],
            page: 0, pageSize: 20, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false,
          },
          message: "Success", success: true, timestamp: new Date().toISOString(),
        }),
      }),
    );

    // Mock my-returns list (empty initially)
    page.route("**/api/v1/redemption/returns*", (route) => {
      const url = route.request().url();
      if (url.includes("/redemption/returns/")) {
        // Detail endpoint — not needed for submit flow
        route.continue();
        return;
      }
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: { data: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
          message: "Success", success: true, timestamp: new Date().toISOString(),
        }),
      });
    });
  });

  test("Request Return appears only for eligible rows", async ({ page }) => {
    await page.goto("/redemptions/history");

    // Wait for the table to render
    await expect(page.getByText("Amazon Gift Card")).toBeVisible();

    // Eligible row should have Request Return button
    await expect(page.getByRole("button", { name: "Request Return for Amazon Gift Card" })).toBeVisible();

    // Ineligible row should NOT have Request Return button
    await expect(page.getByRole("button", { name: "Request Return for XTRM Cash Transfer" })).not.toBeVisible();
  });

  test("Partner submits a return request and sees it in My Returns tab", async ({ page }) => {
    // Mock POST /returns → 201
    await page.route("**/api/v1/redemption/returns", async (route) => {
      if (route.request().method() === "POST") {
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({ data: SUBMIT_RETURN_RESPONSE, message: "Success", success: true, timestamp: new Date().toISOString() }),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto("/redemptions/history");
    await expect(page.getByText("Amazon Gift Card")).toBeVisible();

    // Click Request Return on eligible row
    await page.getByRole("button", { name: "Request Return for Amazon Gift Card" }).click();

    // Dialog should open
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(page.getByText("Request Return")).toBeVisible();

    // Fill optional reason
    await page.getByPlaceholder("Describe why you're returning this item…").fill("Wrong item received");

    // Submit
    await page.getByRole("button", { name: "Submit Return Request" }).click();

    // Dialog closes on success
    await expect(page.getByRole("dialog")).not.toBeVisible();

    // Switch to My Returns tab and verify the return is listed
    await page.route("**/api/v1/redemption/returns*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: MY_RETURNS_RESPONSE, message: "Success", success: true, timestamp: new Date().toISOString() }),
      }),
    );

    await page.getByRole("tab", { name: "My Returns" }).click();
    await expect(page.getByText("You have no return requests yet.")).not.toBeVisible();
    await expect(page.getByText("Amazon Gift Card").first()).toBeVisible();
  });
});
