# Secondary-pass deviation playbook

Loaded by `steps/step-06-secondary-pass.md` when building the secondary spec.
Each group describes a class of interaction the secondary subagent should attempt
on top of the route manifest. The subagent emits one or more concrete test cases
per group, drawn from the interaction inventory in step 03.

---

**Group 1 — Direct navigation without prior context:**
```typescript
test("direct-nav: navigate to detail/edit page without loading list first", async ({ page }) => {
  const { consoleErrors, networkErrors } = await attachListeners(page);
  // Navigate directly to the edit/detail page without going through the list
  // This catches: component that relies on router state set by the list page
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const bodyText = await page.locator("body").textContent();
  // Anomaly: blank page or crash on direct navigation
  expect(bodyText?.trim().length, "Blank page on direct navigation").toBeGreaterThan(10);
  expect(bodyText).not.toContain("Cannot GET");
  expect(consoleErrors, `Console error on direct nav: ${consoleErrors.join(" | ")}`).toHaveLength(0);
});
```

**Group 2 — Back button during multi-step flows:**
```typescript
test("back-button: press back during form interaction", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  // Find the first visible text input and fill it
  const firstInput = page.locator("input[type='text']:visible, textarea:visible").first();
  if (await firstInput.count() > 0) {
    await firstInput.fill("qa-explore test value");
    await page.goBack();
    await page.waitForLoadState("networkidle");
    // No crash after back
    const bodyText = await page.locator("body").textContent();
    expect(bodyText?.trim().length, "Blank page after back button").toBeGreaterThan(10);
    expect(consoleErrors).toHaveLength(0);
  }
});
```

**Group 3 — Browser refresh mid-form:**
```typescript
test("refresh: reload page mid-form retains or gracefully resets", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const firstInput = page.locator("input[type='text']:visible, textarea:visible").first();
  if (await firstInput.count() > 0) {
    await firstInput.fill("qa-explore test value before refresh");
    await page.reload();
    await page.waitForLoadState("networkidle");
    // After refresh: no crash (form either retained or reset cleanly)
    const bodyText = await page.locator("body").textContent();
    expect(bodyText?.trim().length, "Blank page after refresh").toBeGreaterThan(10);
    expect(consoleErrors).toHaveLength(0);
  }
});
```

**Group 4 — Double-click on submit buttons:**
```typescript
test("double-click: double-click primary submit button", async ({ page }) => {
  const { consoleErrors, networkErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  // Find a primary submit/save button using microcopy from the interaction inventory
  // {{PRIMARY_BUTTON_SELECTOR from INTERACTION_INVENTORY[route].selectors}}
  const submitBtn = page.getByRole("button", { name: /save|submit|create|add|publish/i }).first();
  if (await submitBtn.count() > 0 && await submitBtn.isEnabled()) {
    await submitBtn.dblclick({ delay: 50 });
    await page.waitForTimeout(500);
    // Anomaly: duplicate submission, crash, or blank page
    const bodyText = await page.locator("body").textContent();
    expect(bodyText?.trim().length, "Blank page after double-click").toBeGreaterThan(10);
    expect(consoleErrors).toHaveLength(0);
  }
});
```

**Group 5 — Boundary inputs (XSS and max-length):**
```typescript
test("boundary-input: XSS attempt in text fields does not crash", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const textInputs = await page.locator("input[type='text']:visible, textarea:visible").all();
  for (const input of textInputs) {
    await input.fill("<script>alert(1)</script>");
    await page.keyboard.press("Tab");
    await page.waitForTimeout(200);
  }
  const bodyText = await page.locator("body").textContent();
  expect(bodyText?.trim().length).toBeGreaterThan(10);
  expect(consoleErrors).toHaveLength(0);
  // XSS test: the injected script must not execute
  // (if it did, a dialog would have appeared — Playwright would capture it)
});

test("boundary-input: max-length+1 value in text fields", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const textInputs = await page.locator("input[type='text']:visible, textarea:visible").all();
  for (const input of textInputs) {
    await input.fill("a".repeat(300)); // 300 chars — exceeds most field limits
    await page.keyboard.press("Tab");
    await page.waitForTimeout(200);
  }
  const bodyText = await page.locator("body").textContent();
  expect(bodyText?.trim().length).toBeGreaterThan(10);
  expect(consoleErrors).toHaveLength(0);
});
```

**Group 6 — Combined filter + search (list pages only):**

For routes that are list pages (identified by route pattern without `:id`):
```typescript
test("filter-combo: apply multiple filters simultaneously", async ({ page }) => {
  const { consoleErrors, networkErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  // Click the first two visible filter chips/buttons (type filter + status filter)
  const filterButtons = await page.locator("button[aria-pressed], [role='tab'], [data-filter]").all();
  for (let i = 0; i < Math.min(2, filterButtons.length); i++) {
    await filterButtons[i].click();
    await page.waitForTimeout(300);
  }
  // No crash from combined filters
  const bodyText = await page.locator("body").textContent();
  expect(bodyText?.trim().length).toBeGreaterThan(10);
  expect(consoleErrors).toHaveLength(0);
  // No unexpected 5xx from combined filter API call
  expect(networkErrors.filter(e => e.startsWith("5")), `5xx on combined filter: ${networkErrors}`).toHaveLength(0);
});
```

**Group 7 — Viewport resize during interaction:**
```typescript
test("viewport: resize during interaction does not break layout", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  // Resize to mobile dimensions
  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForTimeout(300);
  const bodyText = await page.locator("body").textContent();
  expect(bodyText?.trim().length, "Blank page after viewport resize").toBeGreaterThan(10);
  expect(consoleErrors).toHaveLength(0);
  // Restore
  await page.setViewportSize({ width: 1280, height: 800 });
});
```

**Group 8 — Empty form submission and numeric/string mismatch:**
```typescript
test("boundary-input: empty form submission shows validation, not crash", async ({ page }) => {
  const { consoleErrors, networkErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  // Find and clear any pre-filled text inputs, then submit
  const textInputs = await page.locator("input[type='text']:visible, textarea:visible").all();
  for (const input of textInputs) {
    await input.fill("");
  }
  const submitBtn = page.getByRole("button", { name: /save|submit|create|add/i }).first();
  if (await submitBtn.count() > 0) {
    await submitBtn.click();
    await page.waitForTimeout(500);
    // Anomaly: crash or blank page on empty submit (validation should prevent it gracefully)
    const bodyText = await page.locator("body").textContent();
    expect(bodyText?.trim().length, "Blank page after empty form submit").toBeGreaterThan(10);
    expect(consoleErrors).toHaveLength(0);
  }
});

test("boundary-input: string value in numeric field does not crash", async ({ page }) => {
  const { consoleErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const numericInputs = await page.locator("input[type='number']:visible").all();
  for (const input of numericInputs) {
    await input.fill("not-a-number");
    await page.keyboard.press("Tab");
    await page.waitForTimeout(200);
  }
  const bodyText = await page.locator("body").textContent();
  expect(bodyText?.trim().length).toBeGreaterThan(10);
  expect(consoleErrors).toHaveLength(0);
});
```

**Group 9 — Search + filter combination (list pages only):**
```typescript
test("search-plus-filter: search text combined with type filter", async ({ page }) => {
  const { consoleErrors, networkErrors } = await attachListeners(page);
  await page.goto(`${FE_URL}${resolvedRoute}`);
  await page.waitForLoadState("networkidle");
  const searchInput = page.locator("input[type='search']:visible, input[placeholder*='earch']:visible").first();
  const firstFilterBtn = page.locator("button[aria-pressed], [role='tab']").first();
  if (await searchInput.count() > 0 && await firstFilterBtn.count() > 0) {
    await searchInput.fill("qa-explore");
    await firstFilterBtn.click();
    await page.waitForLoadState("networkidle");
    // Anomaly: crash or 5xx from combined search+filter
    const bodyText = await page.locator("body").textContent();
    expect(bodyText?.trim().length).toBeGreaterThan(10);
    expect(networkErrors.filter(e => e.startsWith("5")), `5xx on search+filter: ${networkErrors}`).toHaveLength(0);
    expect(consoleErrors).toHaveLength(0);
  }
});
```

**Group 10 — Multi-tab: same page in two browser contexts:**
```typescript
test("multi-tab: action in one context is handled gracefully in another", async ({ browser }) => {
  // Open the same route in two independent browser contexts
  const ctx1 = await browser.newContext();
  const ctx2 = await browser.newContext();
  const page1 = await ctx1.newPage();
  const page2 = await ctx2.newPage();

  const consoleErrors1: string[] = [];
  const consoleErrors2: string[] = [];
  page1.on("console", msg => { if (isFlagged(msg.text())) consoleErrors1.push(msg.text()); });
  page2.on("console", msg => { if (isFlagged(msg.text())) consoleErrors2.push(msg.text()); });

  // Both tabs load the same page
  await page1.goto(`${FE_URL}${resolvedRoute}`);
  await page2.goto(`${FE_URL}${resolvedRoute}`);
  await page1.waitForLoadState("networkidle");
  await page2.waitForLoadState("networkidle");

  // Page 1 triggers a data mutation if one exists (e.g., delete, archive, status change)
  // Page 2 then navigates to the same URL — should show updated state or graceful stale-state handling
  // (not crash, not blank page, not unhandled exception)
  await page1.reload();
  await page2.reload();
  await page1.waitForLoadState("networkidle");
  await page2.waitForLoadState("networkidle");

  const body1 = await page1.locator("body").textContent();
  const body2 = await page2.locator("body").textContent();
  expect(body1?.trim().length, "Blank page in tab 1 after multi-tab reload").toBeGreaterThan(10);
  expect(body2?.trim().length, "Blank page in tab 2 after multi-tab reload").toBeGreaterThan(10);
  expect(consoleErrors1, `Tab1 errors: ${consoleErrors1}`).toHaveLength(0);
  expect(consoleErrors2, `Tab2 errors: ${consoleErrors2}`).toHaveLength(0);

  await ctx1.close();
  await ctx2.close();
});
```
