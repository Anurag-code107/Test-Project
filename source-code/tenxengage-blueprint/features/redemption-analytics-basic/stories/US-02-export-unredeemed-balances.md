---
id: US-02
title: "Export unredeemed balances CSV"
layers: ["BE", "FE"]
seed_id: "S-02"
touches_entities: ["RewardWallet"]
depends_on_stories: ["US-01 BE (BE layer)", "US-01 FE (FE layer)"]
---

# US-02: Export unredeemed balances CSV

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** CLIENT_ADMIN clicks the "Export" button on the `RedemptionAnalyticsPage` (built in US-01)

**Steps:**
1. CLIENT_ADMIN clicks "Export" button
2. `ExportConfirmDialog` opens: "Download a CSV of all current unredeemed balances?"
3. CLIENT_ADMIN clicks "Confirm"
4. FE calls `GET /api/v1/redemption/analytics/export`
5. Browser downloads CSV file named `redemption-unredeemed-balances.csv`
6. Audit row is written: `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ANALYTICS_EXPORT`
7. Export button re-enables after download completes

**Rate limit path (negative):**
1. CLIENT_ADMIN clicks "Export" for the 4th time within 60s
2. API returns 429 with `Retry-After: N` header
3. Export button is disabled; text changes to "Export limit reached. You can export again in {N} seconds."
4. Countdown updates every second until re-enabled

**Expected outcome:** CSV file downloaded; one row per wallet; audit row written; button re-enables after download.

**Negative paths:**
- 429 (rate limit — 3 exports per tenant per 60s) → countdown UX, button disabled
- 5xx (internal error) → toast "Export failed. Please try again." — button re-enables immediately
- 403 (non-CLIENT_ADMIN) → route inaccessible; export button never visible (ProtectedRoute)
- 401 (no token) → 401; user cannot reach the page

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/analytics/export` returns 200 with `Content-Type: text/csv`, `Content-Disposition: attachment; filename="redemption-unredeemed-balances.csv"`, and CSV headers: `userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance`
- **AC-2:** CSV body contains one row per `RewardWallet` belonging to the tenant; wallets with null `partnerCompanyId` have `companyId=""` and `companyName="Individual"`; `currencyType` column value = `RewardWallet.currencyId` string (e.g. `"CASH"`)
- **AC-3:** Audit row is written on every successful 200 export: `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ANALYTICS_EXPORT`, `actorId=calling userId`; no audit row is written on 403 or 429 responses
- **AC-4:** 4th export request within 60s from the same tenant returns 429 with `Retry-After` header; 403 when caller lacks `action.redemption.view_analytics`; 401 with no token
- **AC-5:** FE opens `ExportConfirmDialog` when "Export" button is clicked; Confirm button shows spinner + is disabled while request is in flight; on 200, browser download is triggered via Blob URL; on 429, button is disabled showing "Export limit reached. You can export again in {N} seconds." countdown (refreshes every second); on 5xx, toast "Export failed. Please try again." — button re-enables immediately

---

## Out of Scope

- Analytics dashboard metrics (US-01)
- Background / async export jobs (Phase 2 — F-08): this story delivers synchronous query-on-demand export only
- Cross-tenant export
- Filtered exports (by date range, currency, or status) — Phase 2
- Email delivery of CSV

---

## UI States

- [ ] **Idle:** Export button enabled; label "Export"
- [ ] **Dialog open:** `ExportConfirmDialog` visible with confirm/cancel buttons; no request in flight yet
- [ ] **Loading (in-flight):** Confirm button shows spinner + disabled; Cancel button disabled; dialog cannot be closed via Escape
- [ ] **Success:** Dialog closes; browser download triggered; Export button re-enabled
- [ ] **Rate limited (429):** Dialog closes (or was not yet confirmed); Export button disabled; button label: "Export limit reached. You can export again in {N} seconds." — countdown decrements each second; button re-enables when N = 0
- [ ] **Error (5xx):** Dialog closes; toast shown: "Export failed. Please try again."; Export button re-enabled immediately

### Verbatim microcopy

- Export button (idle): `"Export"`
- Dialog title: `"Export unredeemed balances"`
- Dialog body: `"Download a CSV of all current unredeemed wallet balances for your program."`
- Dialog confirm button: `"Download CSV"`
- Dialog cancel button: `"Cancel"`
- Dialog loading state (confirm button): `"Downloading…"` (spinner prefix)
- Rate limit button label: `"Export limit reached. You can export again in {N} seconds."`
- Error toast: `"Export failed. Please try again."`

---

## Depends on

- **Foundation tasks:** F1, F2, F3 (same as US-01)
- **US-01 BE:** Export endpoint and audit logic extend `RedemptionAnalyticsController.java` and `RedemptionAnalyticsService.java` created in US-01 — must exist before US-02 BE starts
- **US-01 FE:** `RedemptionAnalyticsPage` (built in US-01 FE) is where the export button and `ExportConfirmDialog` are wired

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-07.6, FR-07.9
- `spec.md → ## API Endpoints [BE + FE]` — `GET /api/v1/redemption/analytics/export`
- `spec.md → ## Service Layer [BE]` — `exportUnredeemedBalances()` business rules; null `partnerCompanyId` handling
- `spec.md → ## Security Design [BE]` — export rate limit: 3 exports per tenant per 60s; `@Audited`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `action.redemption.view_analytics` (same permission as dashboard)
- `spec.md → ## Audit Trail [BE]` — `DATA_EXPORTED / REDEMPTION_ANALYTICS_EXPORT` audit pattern; no audit on 403/429
- `spec.md → ## Edge Cases` — items 6 (null partnerCompanyId → empty string), 7 (export rate limit), 9 (0 wallets — empty CSV with headers only), 10 (concurrent export — per-tenant lock prevents duplicate)
- `spec.md → ## Observability [BE]` — `analytics_export_downloaded` log event with `tenantId, userId, rowCount`
- `spec.md → ## Non-Functional Requirements` — max export size: 50,000 wallets; synchronous delivery only
- `spec.md → ## Frontend Specification [FE]` — `ExportConfirmDialog`, export button state machine on `RedemptionAnalyticsPage`
- `technical.md → ## Package Layout [BE]` — concrete file paths
- `technical.md → ## Package Layout [FE]` — concrete file paths
- `technical.md → ## Hook Specs [FE]` — `useAnalyticsExport` mutation hook
- `technical.md → ## Repository Queries [BE] → Extensions to RewardWalletRepository` — `findAllByClientIdForExport` projection

---

## BE tasks [BE]

### BE-1: Export service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionAnalyticsService.java` _(extend existing class)_, `src/test/java/com/tenxengage/app/service/redemption/RedemptionAnalyticsServiceTest.java` _(extend existing test)_

Add method `exportUnredeemedBalances(): byte[]` (or `StreamingResponseBody`) to the service class created in US-01 BE.

**Implementation requirements:**
- `@Transactional(readOnly = true)`
- Call `findAllByClientIdForExport(clientId)` to get `List<RewardWalletExportProjection>` (F3)
- Map each projection to a CSV row; handle null `partnerCompanyId`:
  ```java
  String companyId   = wallet.getCompanyId() != null ? wallet.getCompanyId().toString() : "";
  String companyName = wallet.getCompanyName() != null ? wallet.getCompanyName() : "Individual";
  String currencyType = wallet.getCurrencyType(); // = RewardWallet.currencyId string value
  ```
- CSV header row: `userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance`
- Return as `byte[]` with `StandardCharsets.UTF_8` encoding — controller writes directly as response body
- Log `step=analytics_export_downloaded` with `tenantId, userId, rowCount` after write (not before — avoid logging before success)
- Annotate with `@Audited(action = AuditAction.DATA_EXPORTED, resourceType = AuditResourceType.REDEMPTION_ANALYTICS_EXPORT)` — or follow existing `@Audited` convention; audit written only on successful return

**Unit test cases:**
- Happy path: 3 wallets → CSV has header + 3 rows with correct field values
- Null `partnerCompanyId` wallet → `companyId=""`, `companyName="Individual"` in CSV output
- 0 wallets → returns CSV with header row only (no data rows), not an empty byte array
- Correct column order and delimiter

### BE-2: Controller export endpoint + @WebMvcTest

**Files:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionAnalyticsController.java` _(extend existing)_, `src/test/java/com/tenxengage/app/controller/redemption/RedemptionAnalyticsControllerTest.java` _(extend existing)_

Add endpoint to the controller class created in US-01 BE:

```java
@GetMapping("/export")
@RequiresPermission("action.redemption.view_analytics")
public ResponseEntity<byte[]> exportUnredeemedBalances() {
    byte[] csv = analyticsService.exportUnredeemedBalances();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv"));
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename("redemption-unredeemed-balances.csv").build());
    return ResponseEntity.ok().headers(headers).body(csv);
}
```

- Rate limit: 3 req/60s **per tenant** (not per user) via existing `RateLimitFilter` or tenant-level bucket — distinct from the dashboard analytics rate limit (10 req/min/user)
- `@Operation` annotation with summary
- Do NOT add `@Cacheable` — export is always fresh (no Redis caching for CSV)

**@WebMvcTest cases:**
- 200 with correct `Content-Type: text/csv` and `Content-Disposition: attachment; filename=...`
- 200 body contains header row
- 403 with insufficient permission — audit NOT written (mock audit service, assert no call)
- 401 with no token
- 429 when rate limit exceeded — includes `Retry-After` header

### BE-3: Export rate limiting

**File:** confirm existing `RateLimitFilter` or rate-limit configuration; add or configure tenant-level export bucket

The dashboard analytics endpoint (US-01) uses a per-user 10 req/min limit. The export endpoint uses a separate per-tenant 3 req/60s limit. Verify the existing rate limit mechanism supports tenant-scoped buckets — if yes, configure the export bucket. If not, implement a simple Redis `INCR` + `EXPIRE` pattern on key `export:{clientId}`.

This task has no story file of its own — it is captured here as a BE-3 task so the implementer explicitly addresses it rather than assuming it falls out of US-01's rate-limit setup.

---

## FE tasks [FE]

### FE-1: Export service call

**File:** `src/services/redemption-analytics.service.ts` _(extend existing file created in US-01 FE)_

Add export function to the existing service file:

```ts
exportUnredeemedBalances(): Promise<Blob>
// GET /api/v1/redemption/analytics/export
// Response: Blob (text/csv)
// Returns the raw Blob for the caller to trigger a browser download
```

The function must:
- Set `responseType` to blob (Axios) or call `.blob()` (fetch)
- Propagate HTTP errors (429, 403, 5xx) as thrown errors so the hook's `onError` receives them
- Do NOT create a download link — that is the hook's responsibility

### FE-2: useAnalyticsExport hook

**File:** `src/hooks/useAnalyticsExport.ts`

```ts
// useMutation hook — not a query
// onSuccess: create Blob URL, click anchor to trigger download, revoke URL
// onError: distinguish 429 from 5xx:
//   - 429: extract Retry-After header (seconds); expose as `retryAfter: number | null`
//   - 5xx: expose as `isServerError: boolean`
// Returns: { exportCsv: () => void, isPending, retryAfter, isServerError }
```

Countdown logic for `retryAfter`:
- When `retryAfter` is set (non-null), the component starts a `setInterval` decrementing a local `countdown` state every 1000ms
- When countdown reaches 0, re-enable the export button
- `useAnalyticsExport` exposes `retryAfter: number | null`; the component manages the countdown UI timer

Blob URL download pattern:
```ts
const url = URL.createObjectURL(blob)
const a = document.createElement('a')
a.href = url
a.download = 'redemption-unredeemed-balances.csv'
document.body.appendChild(a)
a.click()
document.body.removeChild(a)
URL.revokeObjectURL(url)
```

### FE-3: ExportConfirmDialog + Vitest test

**Files:**
- `src/components/redemption-analytics/ExportConfirmDialog.tsx`
- `src/components/redemption-analytics/__tests__/ExportConfirmDialog.test.tsx`

Props: `open: boolean`, `onConfirm: () => void`, `onClose: () => void`, `isPending: boolean`

Dialog structure (shadcn `<Dialog>`):
- Title: `"Export unredeemed balances"`
- Body: `"Download a CSV of all current unredeemed wallet balances for your program."`
- Confirm button: label `"Download CSV"` (idle) / `"Downloading…"` + spinner (when `isPending`); disabled when `isPending`
- Cancel button: label `"Cancel"`; disabled when `isPending`
- `onOpenChange` blocked when `isPending` (dialog cannot be closed while download is in flight)

**Vitest tests:**
- Renders with open=true: dialog visible
- Confirm button shows spinner when isPending=true
- Confirm button disabled when isPending=true
- Cancel button calls onClose
- Confirm button calls onConfirm

### FE-4: Wire export into RedemptionAnalyticsPage

**File:** `src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx` _(extend file created in US-01 FE)_

Replace the export button stub from US-01 FE with:
1. Import `useAnalyticsExport`, `ExportConfirmDialog`
2. Add state: `dialogOpen: boolean`
3. Add countdown state: `countdown: number` (initialized from `retryAfter` when it changes)
4. Wire Export button:
   - Disabled when: `isPending` OR `countdown > 0`
   - Label: `"Export"` (idle) → `"Export limit reached. You can export again in {countdown} seconds."` (rate limited)
   - `onClick`: sets `dialogOpen = true`
5. Wire `ExportConfirmDialog`:
   - `open={dialogOpen}`, `onClose={() => setDialogOpen(false)}`, `isPending={isPending}`, `onConfirm={() => { exportCsv(); setDialogOpen(false); }}`
6. Wire error toast: in `useEffect` watching `isServerError` → call `toast.error("Export failed. Please try again.")`
7. Wire countdown: in `useEffect` watching `retryAfter` → start `setInterval` decrementing `countdown` every 1000ms; clear interval when `countdown <= 0`

---

## E2E tests [FE]

**File:** `e2e/redemption-analytics-basic/export-csv.spec.ts`

---

**Scenario 1:** `'export happy path — CSV file downloads'` _(covers AC-1, AC-2, AC-5)_

| Field | Value |
|---|---|
| **User flow** | Navigate to `/redemption/admin/analytics` → click "Export" → dialog opens → click "Download CSV" → download triggered |
| **APIs to mock** | `GET /api/v1/redemption/analytics/export` → 200 with CSV content `userId,userName,...\nuser-123,Jane Doe,...` and headers `Content-Disposition: attachment; filename="redemption-unredeemed-balances.csv"` |
| **Visible assertion** | `expect(page.getByText('Export unredeemed balances')).toBeVisible()` (dialog); then assert download event via Playwright `page.waitForEvent('download')` |
| **Header assertion** | Inspect download URL or mock response: `Content-Type` = `text/csv`; `Content-Disposition` includes `filename="redemption-unredeemed-balances.csv"` |

---

**Scenario 2:** `'export rate limit — button shows countdown and re-enables'` _(covers AC-4, AC-5)_

| Field | Value |
|---|---|
| **User flow** | Mock API to return 429 with `Retry-After: 45` → click "Export" → dialog opens → click "Download CSV" |
| **APIs to mock** | `GET /api/v1/redemption/analytics/export` → 429 with `Retry-After: 45` |
| **Visible assertion** | Export button text contains `"Export limit reached"` and `"45 seconds"`; button is disabled |
| **Timer assertion** | After advancing fake timers (Playwright clock) by 45s, button is re-enabled with label `"Export"` |

---

## Execution checklist

**BE session:**
- [ ] `exportUnredeemedBalances()` method added to `RedemptionAnalyticsService.java`; `@Audited` annotation applied; `@Transactional(readOnly = true)`; `step=analytics_export_downloaded` log event emitted on success _(AC-3)_
- [ ] Null `partnerCompanyId` → `companyId=""`, `companyName="Individual"` in CSV output _(AC-2)_
- [ ] `currencyType` CSV column = `RewardWallet.currencyId` string value _(AC-2)_
- [ ] 0 wallets → CSV with header row only (not empty byte array) _(AC-1)_
- [ ] `RedemptionAnalyticsServiceTest` (new cases): happy path CSV row count and field values; null companyId handling; empty wallet set _(AC-1, AC-2, AC-3)_
- [ ] `GET /api/v1/redemption/analytics/export` endpoint added to `RedemptionAnalyticsController.java`; `Content-Type: text/csv`; `Content-Disposition: attachment; filename="redemption-unredeemed-balances.csv"` _(AC-1)_
- [ ] Export rate limit: 3 per tenant per 60s → 4th request returns 429 with `Retry-After` header _(AC-4)_
- [ ] `RedemptionAnalyticsControllerTest` (new cases): 200 with CSV headers; 403 — audit NOT written; 401; 429 with Retry-After _(AC-1, AC-3, AC-4)_
- [ ] No audit row on 403 response — confirmed by @WebMvcTest mock assertion _(AC-3)_

**FE session:**
- [ ] `exportUnredeemedBalances(): Promise<Blob>` added to `redemption-analytics.service.ts` (existing file); returns Blob, propagates 429/5xx errors _(AC-1, AC-4)_
- [ ] `useAnalyticsExport` hook created: mutation; Blob download on success; `retryAfter: number | null` on 429; `isServerError: boolean` on 5xx _(AC-5)_
- [ ] `ExportConfirmDialog` created; correct microcopy; Confirm button spinner when `isPending`; both buttons disabled when `isPending`; tests pass (FE-3) _(AC-5)_
- [ ] Export button wired into `RedemptionAnalyticsPage`: idle → "Export"; rate-limited → countdown label; disabled during `isPending` or countdown; `dialogOpen` state controls dialog _(AC-5)_
- [ ] Countdown timer in `RedemptionAnalyticsPage`: `setInterval` decrements each second; clears when countdown ≤ 0; button re-enables _(AC-5)_
- [ ] Error toast "Export failed. Please try again." shown on 5xx _(AC-5)_
- [x] E2E: `'export happy path — CSV file downloads'` passes _(AC-1, AC-2, AC-5)_
- [x] E2E: `'export rate limit — button shows countdown and re-enables'` passes _(AC-4, AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — all new `RedemptionAnalyticsServiceTest` + `RedemptionAnalyticsControllerTest` export cases green
2. **FE:** `npm run test` passes + both Playwright scenarios in `export-csv.spec.ts` pass against real BE
3. Every AC-1 through AC-5 is referenced by at least one passing test (unit, @WebMvcTest, Vitest, or E2E)
