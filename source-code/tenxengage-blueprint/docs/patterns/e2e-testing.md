# Pattern: e2e-testing

## When this applies

Writing or reviewing Playwright E2E specs that run against a real backend stack (`--real-backend`).

## Common gotchas

- **Shared fixture mutations must be fully reverted in `afterAll`.** When Playwright fix cycles re-run against a persistent DB, mutations from a previous run (e.g., renaming an entity title via UI) persist — making selectors that matched the original name fail on the next cycle. Either restore the original value in `afterAll`, or avoid naming/state mutations in non-teardown tests and assert correctness via API.

- **SSE stream state is client-side only — use one browser context per test.** SSE streams (draft buffers, progress state, streamed AI output) live in the client's in-memory state and do NOT survive browser context changes. Tests that verify SSE continuation (e.g., asserting a draft built up mid-stream) must use a single `page` instance throughout. Do not switch contexts or create a new `browser.newPage()` mid-stream and expect prior state to be present.

- **Scope `getByText()` selectors to avoid strict mode violations.** `page.getByText('X')` matches every element whose text content contains X literally — including help text, tooltips, and descriptive copy (e.g., `getByText('Published')` hits both the status badge `<span>` and a `<p>` reading "Locked once published"). Always scope with `.first()` on the correct container, or use a role-qualified locator (`page.locator('tbody').getByText('Published').first()`).

- **Accordion trigger buttons need an animation settle.** After clicking an accordion trigger (`button[aria-expanded]`), add a brief `waitForTimeout(300–400ms)` before asserting content visibility. Playwright's auto-wait catches the DOM change but not the CSS transition — asserting too quickly can hit the element at partial opacity.

- **`test.setTimeout` for real AI calls.** Playwright's default test timeout (30s) is insufficient for AI generation endpoints that call an LLM. Set `test.setTimeout(120_000)` at the top of any test that triggers a real AI call.

- **T1 real-backend specs require a bootstrap admin user in the DB.** Fresh test databases (started by `docker-compose.test.yml`) have no seed data — there is no admin user to authenticate, so `beforeAll` blocks cannot create tenants, users, wallets, or redemptions via API. Before T1 Playwright tests can run, the backend must be started with `--app.seed.enabled=true` (or a dedicated test-seed migration must run) to populate at least one `TENX_ADMIN` user and one test client. Tests that depend on this setup must use `test.skip()` with explicit enablement instructions when run against a fresh DB; do NOT remove the skip without provisioning the seed first.
