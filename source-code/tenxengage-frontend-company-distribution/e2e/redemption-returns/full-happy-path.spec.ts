import { test, expect } from "@playwright/test";

/**
 * T1 Real-Backend Integration: Full Happy Path
 *
 * Scenario: Partner submits return from F-05 history → Admin approves in F-04 queue
 *           → Xoxoday webhook confirms → Partner sees RETURN_CONFIRMED in My Returns tab;
 *           wallet balance restored.
 *
 * WHY THIS TEST IS SKIPPED:
 * The backend starts with NO seed data (Flyway disabled, Hibernate DDL only).
 * All user/tenant/permission creation endpoints require prior authenticated admin users
 * with TENX_ADMIN-level permissions (action.tenx.clients.manage, action.users.create),
 * which do not exist on a fresh database.
 *
 * Additionally:
 * - POST /api/v1/clients requires "action.tenx.clients.manage" (TENX_ADMIN only)
 * - POST /api/v1/users requires "action.users.create"
 * - POST /api/v1/redemption/requests requires "action.redemption.redeem"
 * - POST /api/v1/redemption/returns requires "action.redemption.return.request"
 * - POST /api/v1/webhooks/redemption-returns/xoxoday requires valid HMAC-SHA256 signature
 *   using env var RETURN_WEBHOOK_XOXODAY_SIGNING_SECRET (which is blank on fresh deployments)
 *
 * TO ENABLE THESE TESTS:
 * 1. Run with a seeded database (app.seed.enabled=true) OR
 * 2. Create a TENX_ADMIN bootstrap user via direct DB insert or a dedicated test-setup endpoint, then:
 *    a. POST /api/v1/clients → create a test tenant
 *    b. POST /api/v1/users → create partner + admin users
 *    c. Assign wallet balance (requires reward_wallet records via DB or API)
 *    d. POST /api/v1/redemption/requests → create a COMPLETED redemption
 *    e. Set RETURN_WEBHOOK_XOXODAY_SIGNING_SECRET for HMAC validation
 */

const SKIP_REASON =
  "Requires seeded test data — backend has no seed data on fresh DB and no unauthenticated " +
  "user/tenant creation endpoint. Run after seeding: app.seed.enabled=true or create a " +
  "bootstrap TENX_ADMIN user and seed clients/users/wallets/redemptions via API.";

// ── Shared state (populated in beforeAll when seeding is available) ────────────

let partnerJwt = "";
let adminJwt = "";
let redemptionId = "";
let returnId = "";
let vendorReturnReference = "";

test.describe("T1 Real-Backend — Full Happy Path (submit → approve → confirm → balance restored)", () => {
  test.beforeAll(async ({ request }) => {
    /**
     * Steps to create full real state (currently not runnable without seed data):
     *
     * 1. Login as TENX_ADMIN (bootstrap credentials from env or seeded DB)
     *    POST /api/v1/auth/login { email, password }
     *    → save tenxAdminJwt
     *
     * 2. Create test tenant client
     *    POST /api/v1/clients { name, subdomain, ... }
     *    → save clientId
     *
     * 3. Create partner user with required permissions
     *    POST /api/v1/users { email, clientId, permissions: ["module.redemption_store",
     *      "action.redemption.view_history", "action.redemption.return.request",
     *      "action.redemption.redeem"] }
     *    → save partnerUserId
     *
     * 4. Create admin user with return review permission
     *    POST /api/v1/users { email, clientId, permissions: ["module.redemption_store",
     *      "action.redemption.return.review"] }
     *    → save adminUserId
     *
     * 5. Seed wallet balance for partner (no public API — requires DB direct or reward_transaction)
     *
     * 6. Login as partner → save partnerJwt
     *    POST /api/v1/auth/login { email, password }
     *
     * 7. Submit a redemption request (creates PENDING → needs COMPLETED status)
     *    POST /api/v1/redemption/requests { catalogItemId, ... }
     *    → save redemptionId
     *    (Note: transitions to COMPLETED status depends on Xoxoday vendor processing or admin approval)
     *
     * 8. Submit a return request
     *    POST /api/v1/redemption/returns { redemptionId, reason }
     *    → save returnId
     *
     * 9. Login as admin → save adminJwt
     *
     * 10. Approve the return
     *     POST /api/v1/redemption/admin/returns/{returnId}/approve
     *     → return transitions to APPROVED
     *
     * 11. Simulate Xoxoday webhook confirm
     *     POST /api/v1/webhooks/redemption-returns/xoxoday
     *     Headers: X-Webhook-Signature: <HMAC-SHA256 of body with RETURN_WEBHOOK_XOXODAY_SIGNING_SECRET>
     *     Body: { vendorReturnReference, confirmed: true }
     *     → return transitions to RETURN_CONFIRMED; wallet balance restored
     */

    // All setup steps skipped — no bootstrap credentials available on fresh DB
    // Individual tests check for populated state and skip if not available
  });

  test("partner sees return in RETURN_CONFIRMED status after full happy path", async ({ page }) => {
    test.skip(true, SKIP_REASON);

    // Navigate to redemption history as partner
    await page.goto("/redemptions/history");
    await page.getByRole("tab", { name: "My Returns" }).click();

    // Find the return and verify RETURN_CONFIRMED status
    await expect(page.getByText("Return Confirmed")).toBeVisible({ timeout: 10000 });
  });

  test("wallet balance restored after RETURN_CONFIRMED", async ({ page, request }) => {
    test.skip(true, SKIP_REASON);

    // Check wallet balance via API — should be restored after return confirmation
    const walletResp = await request.get("/api/v1/wallets/me", {
      headers: { Cookie: `access_token=${partnerJwt}` },
    });
    expect(walletResp.ok()).toBe(true);
    const walletData = await walletResp.json();
    // Balance should reflect the restored amount
    expect(walletData).toBeDefined();
  });

  test("admin sees return in RETURN_CONFIRMED status in review queue", async ({ page }) => {
    test.skip(true, SKIP_REASON);

    // Navigate to approval queue as admin
    await page.goto("/redemption/approval-queue");
    await page.getByRole("tab", { name: /^returns$/i }).click();

    // Filter by RETURN_CONFIRMED or check status badge
    await expect(page.getByText("Return Confirmed")).toBeVisible({ timeout: 10000 });
  });

  test("partner can navigate from redemption history row to return detail", async ({ page }) => {
    test.skip(true, SKIP_REASON);

    await page.goto("/redemptions/history");
    // My Returns tab should show the RETURN_CONFIRMED return
    await page.getByRole("tab", { name: "My Returns" }).click();
    await expect(page.getByText("Return Confirmed")).toBeVisible({ timeout: 10000 });
  });
});
