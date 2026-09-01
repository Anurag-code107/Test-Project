import { test, expect } from "@playwright/test";

/**
 * T1 Real-Backend Integration: Cross-Tenant Isolation
 *
 * Scenario: Partner A submits return; Partner B (different tenant) tries to access the
 *           same return URL directly → Partner B's returns list shows zero matches;
 *           direct GET /returns/{id} returns 404 page.
 *
 * WHY THIS TEST IS SKIPPED:
 * The backend starts with NO seed data (Flyway disabled, Hibernate DDL only).
 * Cross-tenant isolation tests require at least TWO fully configured tenants, each with:
 * - A client (tenant) record
 * - A partner user with redemption permissions
 * - A wallet with balance
 * - A completed redemption request
 * - A submitted return request
 *
 * All of these require prior authenticated TENX_ADMIN users which do not exist on a fresh DB.
 *
 * The backend enforces tenant isolation via TenantValidator.getCurrentClientId() on all
 * return endpoints. Specifically:
 * - GET /api/v1/redemption/returns → filtered by (userId, clientId) — Partner B's clientId
 *   will never match Partner A's returns, so the list will be empty (not 401/403).
 * - GET /api/v1/redemption/returns/{id} → calls returnService.getReturnById(id, userId, clientId, false)
 *   which throws ResourceNotFoundException (→ 404) when the return's clientId ≠ requesting user's clientId.
 *
 * TO ENABLE THESE TESTS:
 * 1. Run with a seeded database (app.seed.enabled=true) which creates multiple tenants, OR
 * 2. Create two separate tenants (clientA, clientB) with users and returns via:
 *    a. Bootstrap TENX_ADMIN → POST /api/v1/clients (×2) → POST /api/v1/users (×2) → seed wallets
 *    b. POST /api/v1/redemption/requests → POST /api/v1/redemption/returns for Partner A
 *    c. Login as Partner B (different clientId) and attempt to access Partner A's return
 */

const SKIP_REASON =
  "Requires two seeded tenants with users, wallets, and returns — backend has no seed data " +
  "on fresh DB and no unauthenticated tenant/user creation endpoint. Run after seeding with " +
  "app.seed.enabled=true or after manually bootstrapping two tenants via TENX_ADMIN API.";

// ── Shared state (populated in beforeAll when seeding is available) ────────────

let partnerAJwt = "";
let partnerBJwt = "";
let partnerAReturnId = "";
let partnerAClientId = "";
let partnerBClientId = "";

test.describe("T1 Real-Backend — Cross-Tenant Isolation", () => {
  test.beforeAll(async ({ request }) => {
    /**
     * Steps to create cross-tenant state (currently not runnable without seed data):
     *
     * 1. Login as TENX_ADMIN
     *    POST /api/v1/auth/login { email: TENX_ADMIN_EMAIL, password: TENX_ADMIN_PASSWORD }
     *
     * 2. Create Tenant A
     *    POST /api/v1/clients { name: "Tenant A", subdomain: "tenant-a-test", ... }
     *    → save clientAId
     *
     * 3. Create Partner A user in Tenant A
     *    POST /api/v1/users { email, clientId: clientAId, permissions: [
     *      "module.redemption_store", "action.redemption.view_history",
     *      "action.redemption.return.request", "action.redemption.redeem"] }
     *    → save partnerAUserId, set partnerAClientId = clientAId
     *
     * 4. Create Tenant B
     *    POST /api/v1/clients { name: "Tenant B", subdomain: "tenant-b-test", ... }
     *    → save clientBId
     *
     * 5. Create Partner B user in Tenant B
     *    POST /api/v1/users { email, clientId: clientBId, permissions: [...same...] }
     *    → save partnerBUserId, set partnerBClientId = clientBId
     *
     * 6. Seed wallet balance for Partner A (requires DB direct or reward_transaction endpoint)
     *
     * 7. Login as Partner A → save partnerAJwt
     *    POST /api/v1/auth/login
     *
     * 8. Submit redemption for Partner A
     *    POST /api/v1/redemption/requests { catalogItemId, ... }
     *    → save redemptionId (must reach COMPLETED status before return can be submitted)
     *
     * 9. Submit return for Partner A
     *    POST /api/v1/redemption/returns { redemptionId, reason }
     *    → save partnerAReturnId
     *
     * 10. Login as Partner B → save partnerBJwt
     *     POST /api/v1/auth/login
     */

    // All setup steps skipped — no bootstrap credentials available on fresh DB
  });

  test("Partner B's returns list shows zero matches for Partner A's returns", async ({ page, request }) => {
    test.skip(true, SKIP_REASON);

    // API-level isolation check: Partner B fetches their returns list
    // Backend filters by (userId=partnerBUserId, clientId=partnerBClientId)
    // Partner A's return has clientId=partnerAClientId — will never appear in B's list
    const resp = await request.get("/api/v1/redemption/returns", {
      headers: { Cookie: `access_token=${partnerBJwt}` },
    });
    expect(resp.ok()).toBe(true);
    const body = await resp.json();
    // Partner B sees zero returns (Partner A's return is invisible across tenant boundary)
    expect(body.totalElements).toBe(0);
    const partnerAReturnIds = (body.data as Array<{ id: string }>).map((r) => r.id);
    expect(partnerAReturnIds).not.toContain(partnerAReturnId);
  });

  test("Partner B cannot access Partner A's return by direct URL — 404 page", async ({ page }) => {
    test.skip(true, SKIP_REASON);

    // UI-level check: navigate to the return detail URL as Partner B
    // Backend: returnService.getReturnById(partnerAReturnId, partnerBUserId, partnerBClientId, false)
    // → throws ResourceNotFoundException because clientId does not match → 404
    await page.goto(`/redemptions/returns/${partnerAReturnId}`);

    // Expect a 404 page or "not found" message
    // (The FE should render a not-found state when the API returns 404)
    await expect(
      page.getByText(/not found|404|does not exist/i)
    ).toBeVisible({ timeout: 10000 });
  });

  test("Partner B cannot access Partner A's return via direct API GET", async ({ request }) => {
    test.skip(true, SKIP_REASON);

    // Direct API call: GET /api/v1/redemption/returns/{partnerAReturnId} as Partner B
    // TenantValidator ensures clientId=partnerBClientId is used, not partnerAClientId
    // Service throws ResourceNotFoundException → HTTP 404
    const resp = await request.get(`/api/v1/redemption/returns/${partnerAReturnId}`, {
      headers: { Cookie: `access_token=${partnerBJwt}` },
    });
    expect(resp.status()).toBe(404);
  });

  test("Partner A can still access their own return (isolation does not affect owner)", async ({ page, request }) => {
    test.skip(true, SKIP_REASON);

    // Verify Partner A's access is unaffected
    const resp = await request.get(`/api/v1/redemption/returns/${partnerAReturnId}`, {
      headers: { Cookie: `access_token=${partnerAJwt}` },
    });
    expect(resp.ok()).toBe(true);
    const body = await resp.json();
    expect(body.id).toBe(partnerAReturnId);
  });
});
