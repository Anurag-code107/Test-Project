import { test, expect, type Page } from "@playwright/test";

/**
 * REAL-STACK E2E (no API mocking) for advanced redemption analytics.
 *
 * Prerequisites (NOT auto-managed by Playwright's webServer):
 *   - Backend running on :8080 (./gradlew bootRun --args='--spring.profiles.active=localtest')
 *     against the docker-compose Postgres, which is seeded with the `acme` ENTERPRISE
 *     tenant (flag redemption_analytics_advanced enabled) + completed redemptions +
 *     refreshed materialized views.
 *   - Vite dev server on :3000 (Playwright webServer starts this), proxying /api -> :8080.
 *
 * Unlike the per-story specs, this performs a genuine /api/v1/auth/login and reads the
 * real analytics endpoints — exercising the full BE+FE+DB path end to end.
 */

const EMAIL = "clientadmin@acme.com";
const PASSWORD = "Password123!";

async function realLogin(page: Page) {
  await page.goto("/login");
  await page.getByLabel(/email/i).fill(EMAIL);
  await page.getByLabel(/password/i).fill(PASSWORD);
  const loginResp = page.waitForResponse(
    (r) => r.url().includes("/api/v1/auth/login") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: /sign in/i }).click();
  const resp = await loginResp;
  expect(resp.status(), "real /auth/login should succeed").toBe(200);
}

test.describe("Advanced analytics — real stack (acme tenant)", () => {
  test("CLIENT_ADMIN sees the Advanced tab and all six sections render live data", async ({
    page,
  }) => {
    await realLogin(page);

    await page.goto("/redemption/admin/analytics");

    // Advanced tab is present (permission action.redemption.analytics.advanced + flag on for ENTERPRISE)
    const advancedTab = page.getByRole("tab", { name: "Advanced" });
    await expect(advancedTab).toBeVisible();
    await advancedTab.click();

    // All six section headings render against real data
    for (const heading of [
      "Item Breakdown",
      "Segment Breakdown",
      "Time to First Redemption",
      "Redemption Rate Trend",
      "Liability Trend",
      "Failure Breakdown",
    ]) {
      await expect(
        page.getByText(heading, { exact: false }).first(),
        `section "${heading}" should be visible`,
      ).toBeVisible({ timeout: 15000 });
    }

    // Freshness caption from real refresh log
    await expect(page.getByText(/Data as of/i).first()).toBeVisible();

    // At least one real data row in the item breakdown table (acme has 18 MV rows)
    await expect(page.getByRole("row").nth(1)).toBeVisible({ timeout: 15000 });
  });

  test("Liability CSV export downloads against the real export endpoint", async ({ page }) => {
    await realLogin(page);

    // Wait for the liability-trend data fetch to resolve so the chart leaves its
    // loading skeleton and the Export CSV button mounts (it is not rendered while
    // isLoading or isError — only in the empty/data states).
    const liabilityResp = page.waitForResponse(
      (r) =>
        r.url().includes("/redemption/analytics/advanced/liability-trend") &&
        !r.url().includes("/export") &&
        r.request().method() === "GET",
    );
    await page.goto("/redemption/admin/analytics");
    await page.getByRole("tab", { name: "Advanced" }).click();
    expect((await liabilityResp).status()).toBe(200);

    const exportBtn = page.getByRole("button", { name: /export csv/i }).first();
    await exportBtn.scrollIntoViewIfNeeded();
    await expect(exportBtn).toBeVisible({ timeout: 15000 });

    const downloadPromise = page.waitForEvent("download");
    await exportBtn.click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain("liability");
  });
});
