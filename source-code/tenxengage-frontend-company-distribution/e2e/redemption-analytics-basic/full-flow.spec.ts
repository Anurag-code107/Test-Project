import { test, expect } from "@playwright/test";

// T-13: full flow — real BE, no mocked API
// Requires: BE running at localhost:8080; clientadmin@acme.com seeded in local DB;
// test tenant (Acme Corp) must have at least one RewardWallet row for analytics to return data.

const CLIENT_ADMIN_EMAIL = "clientadmin@acme.com";
const CLIENT_ADMIN_PASSWORD = "Password123!";

test("full flow: load dashboard → filter → export", async ({ page }) => {
  // ── 1. Real login — session cookie set by BE ──────────────────────────────
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(CLIENT_ADMIN_EMAIL);
  await page.getByLabel(/password/i).fill(CLIENT_ADMIN_PASSWORD);

  const loginDone = page.waitForResponse("**/api/v1/auth/login");
  await page.getByRole("button", { name: /sign in/i }).click();
  const loginRes = await loginDone;
  expect(loginRes.status()).toBe(200);

  // ── 2. Navigate to analytics page ────────────────────────────────────────
  const analyticsLoad = page.waitForResponse(
    (r) =>
      r.url().includes("/api/v1/redemption/analytics") &&
      !r.url().includes("/export"),
    { timeout: 15_000 },
  );
  await page.goto("/redemption/admin/analytics");
  const analyticsRes = await analyticsLoad;

  // BE must accept the session and return 200 — not 401/403
  expect(analyticsRes.status()).toBe(200);

  // Page is interactive; no error toast
  await expect(page.getByRole("button", { name: "Last 7 days" })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();
  await expect(page.getByText(/could not load analytics/i)).not.toBeVisible();

  // ── 3. Date preset — "Last 7 days" fires a refetch with updated dateFrom ──
  // Changing the preset changes the query key → TanStack Query fetches fresh.
  const filterReq = page.waitForRequest(
    (r) =>
      r.url().includes("/api/v1/redemption/analytics") &&
      !r.url().includes("/export"),
    { timeout: 10_000 },
  );
  await page.getByRole("button", { name: "Last 7 days" }).click();
  const req = await filterReq;
  expect(req.url()).toContain("dateFrom=");

  const filterRes = await page.waitForResponse(
    (r) =>
      r.url().includes("/api/v1/redemption/analytics") &&
      !r.url().includes("/export"),
    { timeout: 15_000 },
  );
  expect(filterRes.status()).toBe(200);

  // ── 4. Export — real CSV download from BE ────────────────────────────────
  await page.getByRole("button", { name: /^Export$/i }).click();
  await expect(page.getByText("Export unredeemed balances")).toBeVisible();

  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: /download csv/i }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe("redemption-unredeemed-balances.csv");

  // Export button re-enables (not rate-limited on first run)
  await expect(page.getByRole("button", { name: /^Export$/i })).toBeVisible();
});
