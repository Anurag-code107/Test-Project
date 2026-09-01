import { test, expect, type Route } from "@playwright/test";

const CLIENT_ADMIN_EMAIL = "admin@tenxengage.com";
const CLIENT_ADMIN_PASSWORD = "Admin@123";

const CLIENT_ADMIN_USER = {
  id: "00000000-0000-0000-0000-000000000001",
  email: CLIENT_ADMIN_EMAIL,
  firstName: "Admin",
  lastName: "User",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: [
    "module.redemption_store",
    "action.redemption.approve",
  ],
  clientRoleId: "00000000-0000-0000-0000-000000000099",
  clientRoleName: "CLIENT_ADMIN",
  organizationId: null,
  clientId: "00000000-0000-0000-0000-000000000011",
  clientName: "TechCorp",
  partnerCompanyId: null,
  partnerCompanyName: null,
  metadata: null,
  homeDashboardTemplate: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const PARTNER_SELLER_USER = {
  id: "00000000-0000-0000-0000-000000000002",
  email: "seller@techpartners.com",
  firstName: "Partner",
  lastName: "Seller",
  phone: null,
  avatar: null,
  status: "ACTIVE",
  permissions: ["module.incentives.sales", "module.redemption_store"],
  clientRoleId: "00000000-0000-0000-0000-000000000088",
  clientRoleName: "PARTNER_SELLER",
  organizationId: null,
  clientId: "00000000-0000-0000-0000-000000000011",
  clientName: "TechCorp",
  partnerCompanyId: "00000000-0000-0000-0000-000000000020",
  partnerCompanyName: "Acme Corp",
  metadata: null,
  homeDashboardTemplate: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const QUEUE_ITEM = {
  id: "00000000-0000-0000-0000-000000000100",
  requestingUserDisplayName: "Test User",
  catalogItemId: "00000000-0000-0000-0000-000000000200",
  catalogItemName: "Amazon Gift Card",
  currencyId: "points",
  amount: "150.00",
  walletType: "INDIVIDUAL",
  submittedAt: "2026-05-28T10:00:00Z",
  createdAt: "2026-05-28T10:00:00Z",
  updatedAt: "2026-05-28T10:00:00Z",
};

function apiResponse<T>(data: T) {
  return { data, message: "OK", success: true, timestamp: new Date().toISOString() };
}

function paginatedResponse<T>(items: T[], totalElements = items.length) {
  return {
    data: items,
    page: 0,
    pageSize: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
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
      body: JSON.stringify({ expiresIn: 3600, user: CLIENT_ADMIN_USER, enabledFeatures: ["redemption_store"] }),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ expiresIn: 3600, user: CLIENT_ADMIN_USER, enabledFeatures: ["redemption_store"] }),
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function loginAsPartnerSeller(page: any) {
  let authenticated = false;

  await page.route("**/api/v1/auth/login", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ expiresIn: 3600, user: PARTNER_SELLER_USER, enabledFeatures: ["redemption_store"] }),
    });
  });

  await page.route("**/api/v1/auth/refresh", async (route: Route) => {
    if (authenticated) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ expiresIn: 3600, user: PARTNER_SELLER_USER, enabledFeatures: ["redemption_store"] }),
      });
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

test("approval queue renders items for authorized user", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
    });
  });

  await page.goto("/redemption/approval-queue");

  await expect(page.getByRole("table")).toBeVisible();
  await expect(page.getByText("Test User")).toBeVisible();
});

test("approval queue shows empty state when no items", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([]))),
    });
  });

  await page.goto("/redemption/approval-queue");

  await expect(page.getByText("No pending redemptions")).toBeVisible();
});

test("RETURN filter shows empty state (stub behavior)", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    const url = route.request().url();
    if (url.includes("requestType=RETURN")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([]))),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
      });
    }
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByRole("table")).toBeVisible();

  // Change request type to Return
  await page.getByRole("combobox", { name: /request type filter/i }).click();
  await page.getByRole("option", { name: "Return" }).click();

  await expect(page.getByText("No pending redemptions")).toBeVisible();
});

test("approval queue hidden from PARTNER_SELLER", async ({ page }) => {
  await loginAsPartnerSeller(page);

  await page.goto("/home");

  await expect(page.getByRole("link", { name: "Approval Queue" })).not.toBeVisible();
});

const APPROVED_DETAIL: Record<string, unknown> = {
  id: QUEUE_ITEM.id,
  status: "RESERVED",
  amount: QUEUE_ITEM.amount,
  currencyId: QUEUE_ITEM.currencyId,
  catalogItemId: QUEUE_ITEM.catalogItemId,
  catalogItemName: QUEUE_ITEM.catalogItemName,
  processingMode: "APPROVAL_REQUIRED",
  category: "NON_CASH",
  walletType: QUEUE_ITEM.walletType,
  submittedAt: QUEUE_ITEM.submittedAt,
  estimatedDelivery: "3-5 business days",
  reviewedBy: CLIENT_ADMIN_USER.id,
  reviewedAt: "2026-05-29T05:30:00Z",
  rejectionReason: null,
  createdAt: QUEUE_ITEM.createdAt,
  updatedAt: "2026-05-29T05:30:00Z",
};

test("approve redemption happy path — item disappears from queue", async ({ page }) => {
  await loginAsClientAdmin(page);

  let callCount = 0;
  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    callCount++;
    // First call: return 1 item; subsequent calls (after invalidation): return empty
    const items = callCount === 1 ? [QUEUE_ITEM] : [];
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse(items))),
    });
  });

  await page.route(`**/api/v1/redemption/requests/${QUEUE_ITEM.id}/approve`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(APPROVED_DETAIL)),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  // Click Approve row action
  await page.getByRole("button", { name: /approve redemption for test user/i }).click();

  // Confirm dialog appears
  await expect(page.getByText("Approve this redemption?")).toBeVisible();

  // Click Approve in dialog
  await page.getByRole("button", { name: /^approve$/i }).click();

  // Success toast and queue clears
  await expect(page.getByText("Redemption approved")).toBeVisible();
  await expect(page.getByText("No pending redemptions")).toBeVisible();
});

test("approve concurrent 409 — shows specific toast", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
    });
  });

  await page.route(`**/api/v1/redemption/requests/${QUEUE_ITEM.id}/approve`, async (route: Route) => {
    await route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        errorCode: "CONFLICT",
        errorMessage: "Redemption is not in PENDING_APPROVAL state",
        status: 409,
        timestamp: new Date().toISOString(),
        path: `/api/v1/redemption/requests/${QUEUE_ITEM.id}/approve`,
      }),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  await page.getByRole("button", { name: /approve redemption for test user/i }).click();
  await expect(page.getByText("Approve this redemption?")).toBeVisible();
  await page.getByRole("button", { name: /^approve$/i }).click();

  await expect(
    page.getByText(
      "This redemption was just actioned by another approver. Please refresh the queue.",
    ),
  ).toBeVisible();
});

test("approve 404 — shows not found toast", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
    });
  });

  await page.route(`**/api/v1/redemption/requests/${QUEUE_ITEM.id}/approve`, async (route: Route) => {
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({
        errorCode: "NOT_FOUND",
        errorMessage: "Redemption not found",
        status: 404,
        timestamp: new Date().toISOString(),
        path: `/api/v1/redemption/requests/${QUEUE_ITEM.id}/approve`,
      }),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  await page.getByRole("button", { name: /approve redemption for test user/i }).click();
  await expect(page.getByText("Approve this redemption?")).toBeVisible();
  await page.getByRole("button", { name: /^approve$/i }).click();

  await expect(page.getByText("Redemption not found")).toBeVisible();
});

const REJECTED_DETAIL: Record<string, unknown> = {
  id: QUEUE_ITEM.id,
  status: "CANCELLED",
  amount: QUEUE_ITEM.amount,
  currencyId: QUEUE_ITEM.currencyId,
  catalogItemId: QUEUE_ITEM.catalogItemId,
  catalogItemName: QUEUE_ITEM.catalogItemName,
  processingMode: "APPROVAL_REQUIRED",
  category: "NON_CASH",
  walletType: QUEUE_ITEM.walletType,
  submittedAt: QUEUE_ITEM.submittedAt,
  estimatedDelivery: "N/A",
  reviewedBy: CLIENT_ADMIN_USER.id,
  reviewedAt: "2026-05-29T05:50:00Z",
  rejectionReason: "Duplicate request",
  createdAt: QUEUE_ITEM.createdAt,
  updatedAt: "2026-05-29T05:50:00Z",
};

test("reject redemption happy path — item disappears from queue", async ({ page }) => {
  await loginAsClientAdmin(page);

  let callCount = 0;
  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    callCount++;
    const items = callCount === 1 ? [QUEUE_ITEM] : [];
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse(items))),
    });
  });

  await page.route(`**/api/v1/redemption/requests/${QUEUE_ITEM.id}/reject`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(REJECTED_DETAIL)),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  await page.getByRole("button", { name: /reject redemption for test user/i }).click();
  await expect(page.getByText("Reject redemption")).toBeVisible();

  await page.getByPlaceholder("Enter reason for rejection...").fill("Duplicate request");
  await page.getByRole("button", { name: /^reject$/i }).click();

  await expect(page.getByText("Redemption rejected")).toBeVisible();
  await expect(page.getByText("No pending redemptions")).toBeVisible();
});

test("reject dialog submit disabled with blank reason", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  await page.getByRole("button", { name: /reject redemption for test user/i }).click();
  await expect(page.getByText("Reject redemption")).toBeVisible();

  // Submit disabled with empty field
  await expect(page.getByRole("button", { name: /^reject$/i })).toBeDisabled();

  // Still disabled with whitespace-only
  await page.getByPlaceholder("Enter reason for rejection...").fill("   ");
  await expect(page.getByRole("button", { name: /^reject$/i })).toBeDisabled();
});

test("reject concurrent 409 — shows specific toast", async ({ page }) => {
  await loginAsClientAdmin(page);

  await page.route("**/api/v1/redemption/requests/approval-queue**", async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(apiResponse(paginatedResponse([QUEUE_ITEM]))),
    });
  });

  await page.route(`**/api/v1/redemption/requests/${QUEUE_ITEM.id}/reject`, async (route: Route) => {
    await route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        errorCode: "CONFLICT",
        errorMessage: "Redemption is not in PENDING_APPROVAL state",
        status: 409,
        timestamp: new Date().toISOString(),
        path: `/api/v1/redemption/requests/${QUEUE_ITEM.id}/reject`,
      }),
    });
  });

  await page.goto("/redemption/approval-queue");
  await expect(page.getByText("Test User")).toBeVisible();

  await page.getByRole("button", { name: /reject redemption for test user/i }).click();
  await expect(page.getByText("Reject redemption")).toBeVisible();

  await page.getByPlaceholder("Enter reason for rejection...").fill("Test reason");
  await page.getByRole("button", { name: /^reject$/i }).click();

  await expect(
    page.getByText(
      "This redemption was just actioned by another approver. Please refresh the queue.",
    ),
  ).toBeVisible();
});
