---
id: US-03
title: "Export personal transaction data"
seed_id: "F-05.S-03"
layers: ["BE", "FE"]
touches_entities: ["RedemptionExportJob"]
depends_on_stories: ["US-01"]
---

# US-03: Export personal transaction data

## Description

**Actor:** `PARTNER_SELLER` or `PARTNER_ADMIN`
**Trigger:** User clicks "Export" button on `TransactionHistoryPage`

**Steps:**
1. `ExportDialog` opens; user selects format (CSV or XLSX)
2. User clicks "Export"; `useTriggerExport` fires `POST /api/v1/redemption/requests/export`
3. **Sync path (≤ 1,000 rows):** 200 response with file bytes → browser download triggered immediately; dialog closes or shows success state
4. **Async path (> 1,000 rows):** 202 response with `jobId` → dialog switches to polling state; `useExportJob` polls `GET /export/{jobId}` every 3 seconds
5. When job reaches COMPLETED → "Download" button appears; user clicks → `GET /export/{jobId}/download` returns presigned URL → browser opens URL
6. If job reaches FAILED → error state shown with "Try again" retry button

**Expected outcome:** User can export their transaction history in CSV or XLSX. Small datasets download immediately. Large datasets generate asynchronously with visible progress. An audit row is written on every export trigger.

**Negative paths:**
- Export with no matching records → 422 inline error in dialog
- 6th export within 1 hour → 429 toast "You've reached the export limit"
- Job FAILED → error message + retry CTA in dialog
- Different user polls job → 404; dialog closes gracefully

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/redemption/requests/export` when row count ≤ 1,000 → 200, `Content-Disposition: attachment; filename=redemption-history.{format}`, valid non-empty file bytes in body
- **AC-2:** `POST /api/v1/redemption/requests/export` when row count > 1,000 → 202, `RedemptionExportJobResponse` with `jobId` (UUID) and `status=PENDING`
- **AC-3:** `GET /api/v1/redemption/requests/export/{jobId}` returns correct status; COMPLETED response includes non-null `rowCount` and `expiresAt`
- **AC-4:** `GET /api/v1/redemption/requests/export/{jobId}/download` → 200 with `downloadUrl` (non-null) when COMPLETED; `downloadUrl` is null when PENDING or PROCESSING
- **AC-5:** `POST /export` with zero matching records → 422 with body `{ "errorCode": "VALIDATION_FAILED", "errorMessage": "No records match the selected filters" }`
- **AC-6:** Non-owner calling `GET /export/{jobId}` → 404 (not 403); `audit_log` row written with `action=DATA_EXPORTED` after every export trigger (both sync and async)
- **AC-7:** 6th `POST /export` within 1 hour → 429 with `Retry-After` header

---

## Out of Scope

- CLIENT_ADMIN all-tenant export (US-04)
- Company-scope export (same endpoint and ExportDialog are reused by US-02 FE after this story ships; no additional BE work needed for company scope — scope is determined by the caller's role)
- Export job admin dashboard or history of past jobs

---

## Non-Functional Notes

- **Audit:** `@Audited` on `POST /export` for both paths: async → `action=DATA_EXPORTED, resourceType=REDEMPTION_EXPORT_JOB, resourceId=jobId.toString()`; sync → `action=DATA_EXPORTED, resourceType=REDEMPTION_REQUEST, description="Redemption history exported synchronously"`. See `technical.md → ## Audit Annotations [BE]`.
- **Rate limit:** `POST /export` — 5 requests/user/hour via `RateLimitFilter`. See `spec.md → ## Security Design [BE]`.

---

## UI States

- [ ] **ExportDialog idle:** Format selector (CSV / XLSX radio or select) + "Export" button enabled
- [ ] **ExportDialog syncing (sync path in-flight):** spinner overlay, buttons disabled, copy "Preparing your export…"
- [ ] **ExportDialog polling (async path):** progress badge "Generating…" with job status text; "Cancel" button hides (export cannot be cancelled); auto-polls every 3s
- [ ] **ExportDialog COMPLETED:** green checkmark icon + "Your export is ready" + "Download" button + expiry hint "Link expires in 24h"
- [ ] **ExportDialog FAILED:** red icon + "Export failed — please try again" + "Try again" button (triggers new POST)
- [ ] **ExportDialog zero-results error:** inline error below format selector "No records match the selected filters"; Export button disabled

### Verbatim microcopy

- Export button label: "Export"
- Dialog title: "Export transactions"
- Format label: "Format"
- Format options: "CSV (.csv)" and "Excel (.xlsx)"
- Submit button: "Export"
- Cancel button: "Cancel"
- Syncing state: "Preparing your export…"
- Polling state: "Generating your export…"
- Completed state: "Your export is ready"
- Download button: "Download"
- Expiry hint: "Link expires in 24 hours"
- Failed state: "Export failed — please try again"
- Retry button: "Try again"
- Zero results inline error: "No records match the selected filters"
- Rate limit toast: "You've reached the export limit. Please wait before exporting again."

### Conditional rendering

**Input: `ExportDialog` internal state machine**
- `idle`: format selector + Export button active; Cancel button active
- `syncing`: spinner; all buttons disabled
- `polling`: job status badge; no Download button; no Cancel button (user must wait or close)
- `completed`: green icon + Download button; no Export button
- `failed`: red icon + Try again button; no Download button
- `zero-results`: inline error shown; Export button disabled

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 FE (ExportDialog is wired into `TransactionHistoryPage` — page must exist)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-05.4
- `spec.md → ## Data Model / Entities [BE]` — `RedemptionExportJob` entity
- `spec.md → ## API Endpoints [BE + FE]` — `POST /export`, `GET /export/{jobId}`, `GET /export/{jobId}/download`
- `spec.md → ## Service Layer [BE]` — `RedemptionExportService` (triggerExport, getExportJob, getExportJobWithDownloadUrl, processExportJob)
- `spec.md → ## Workflow / Status Transitions [BE + FE]` — export job state machine
- `spec.md → ## DTOs [BE]` — `TriggerExportRequest`, `RedemptionExportJobResponse`, `RedemptionExportJobDetailResponse`
- `spec.md → ## Security Design [BE]` — rate limit 5/user/hour, job ownership check → 404
- `spec.md → ## Audit Trail [BE]` — `DATA_EXPORTED` annotation details
- `spec.md → ## Edge Cases [BE + FE]` — EC-2 (zero results), EC-3 (threshold), EC-4 (async failure), EC-5 (expired job), EC-6 (non-owner), EC-13 (concurrent jobs)
- `technical.md → ## Package Layout [BE]` — `RedemptionExportController`, `RedemptionExportService`, DTO paths
- `technical.md → ## Audit Annotations [BE]`
- `technical.md → ## Package Layout [FE]` — `ExportDialog`, `useTriggerExport`, `useExportJob`
- `technical.md → ## Hook Specs [FE]` — `useTriggerExport`, `useExportJob`

---

## BE tasks [BE]

### BE-1: Request + Response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/request/redemption/TriggerExportRequest.java`
  - Fields: `format: ExportFormat` (`@NotNull`), `dateFrom: LocalDate` (nullable), `dateTo: LocalDate` (nullable), `status: RedemptionStatus` (nullable), `category: RedemptionCategory` (nullable)
  - Validation: `@NotNull` on `format`; `dateFrom ≤ dateTo` cross-field check in service
- `src/main/java/com/tenxengage/app/dto/response/redemption/RedemptionExportJobResponse.java`
  - Fields: `id: UUID`, `status: String`, `rowCount: Integer` (nullable), `expiresAt: Instant` (nullable)
  - `from(RedemptionExportJob)` factory
- `src/main/java/com/tenxengage/app/dto/response/redemption/RedemptionExportJobDetailResponse.java`
  - All fields of `RedemptionExportJobResponse` + `downloadUrl: String` (nullable — non-null only when COMPLETED)
  - `from(RedemptionExportJob, String presignedUrl)` factory

### BE-2: RedemptionExportService + unit test

**Files:**
- `src/main/java/com/tenxengage/app/service/redemption/RedemptionExportService.java`
- `src/test/java/com/tenxengage/app/service/redemption/RedemptionExportServiceTest.java`

Methods:
- `triggerExport(TriggerExportRequest request, UUID userId)` → `ExportResult` (`@Transactional`):
  1. Run COUNT query via `RedemptionHistoryRepository.countPersonalHistory()`
  2. Zero count → throw `BusinessRuleException` ("No records match the selected filters") → 422
  3. Count ≤ 1,000 → generate file synchronously; return `SyncExportResult(bytes, format)`
  4. Count > 1,000 → persist `RedemptionExportJob` (status=PENDING, scope=PERSONAL, filterSnapshot from request); dispatch `processExportJob(jobId)` @Async; return `AsyncExportResult(jobId)`
- `getExportJob(UUID jobId, UUID userId)` → `RedemptionExportJobResponse` (`@Transactional(readOnly=true)`): find by id + clientId; verify `job.requestedBy == userId` OR caller has `view_all_history`; else throw 404
- `getExportJobWithDownloadUrl(UUID jobId, UUID userId)` → `RedemptionExportJobDetailResponse`: same ownership check; generate presigned URL if COMPLETED; `downloadUrl=null` otherwise
- `processExportJob(UUID jobId)` → void (`@Async`, `@Transactional`): set status=PROCESSING; run full query; generate CSV/XLSX bytes; upload to object storage; set status=COMPLETED, fileKey, rowCount, expiresAt=NOW()+24h; on exception: set status=FAILED, failureReason

Unit test covers (state × action matrix for processExportJob):
- `triggerExport` with 500 rows (≤ 1,000) → sync path, file bytes returned
- `triggerExport` with 1,001 rows → async path, job persisted with PENDING
- `triggerExport` with 0 rows → 422 exception
- `getExportJob` owner access → 200
- `getExportJob` non-owner without view_all_history → 404 exception thrown
- `getExportJob` CLIENT_ADMIN (view_all_history) → 200 any job
- `getExportJobWithDownloadUrl` COMPLETED → presigned URL populated
- `getExportJobWithDownloadUrl` PENDING → downloadUrl null
- `processExportJob` → PROCESSING → COMPLETED (happy path)
- `processExportJob` → PROCESSING → FAILED (on exception)

### BE-3: RedemptionExportController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/redemption/RedemptionExportController.java`
  - `@RequestMapping("/api/v1/redemption/requests/export")`
  - `POST /` — `@RequiresPermission("action.redemption.export")`; returns `ResponseEntity<byte[]>` (200, sync) or `ResponseEntity<RedemptionExportJobResponse>` (202, async); `@Audited(action="DATA_EXPORTED")`
  - `GET /{jobId}` — `@RequiresPermission("action.redemption.export")`; returns `ResponseEntity<RedemptionExportJobResponse>`
  - `GET /{jobId}/download` — `@RequiresPermission("action.redemption.export")`; returns `ResponseEntity<RedemptionExportJobDetailResponse>`
- `src/test/java/com/tenxengage/app/controller/redemption/RedemptionExportControllerTest.java`

@WebMvcTest cases:
- `POST /export` sync → 200 with `Content-Disposition: attachment`
- `POST /export` async → 202 with `RedemptionExportJobResponse`
- `POST /export` zero results → 422
- `POST /export` no permission → 403
- `GET /export/{jobId}` COMPLETED → 200 with rowCount
- `GET /export/{jobId}` non-owner → 404
- `GET /export/{jobId}/download` COMPLETED → 200 with downloadUrl
- `GET /export/{jobId}/download` PENDING → 200 with downloadUrl=null

---

## FE tasks [FE]

### FE-1: Service calls

Add to `src/services/redemption-history/redemption-history.service.ts`:
- `triggerExport(request: TriggerExportRequest, filters?)` — `POST /api/v1/redemption/requests/export`; handles both 200 (returns blob) and 202 (returns `RedemptionExportJobResponse`) response shapes
- `getExportJob(jobId: string)` — `GET /api/v1/redemption/requests/export/{jobId}`
- `getExportJobDownload(jobId: string)` — `GET /api/v1/redemption/requests/export/{jobId}/download`

### FE-2: Hooks

**Files:**
- `src/hooks/redemption-history/useTriggerExport.ts` — mutation; `onSuccess(202)`: store `jobId` in state; `onSuccess(200)`: trigger browser file download from blob bytes; `onError`: `toast.error("Export failed. Please try again.")`
- `src/hooks/redemption-history/useExportJob.ts` — `queryKey: ['redemption-history', 'export-job', jobId]`; `staleTime: 0`; `enabled: jobId !== null`; `refetchInterval: (data) => (data?.status === 'PENDING' || data?.status === 'PROCESSING') ? 3000 : false`

### FE-3: ExportDialog component + Vitest test

**Files:**
- `src/components/redemption-history/ExportDialog.tsx` — uses `<Dialog>` from shadcn/ui; props: `open: boolean`, `onClose: () => void`, `filters: RedemptionHistoryFilters`; internal state machine (`idle | syncing | polling | completed | failed | zero-results`); wires `useTriggerExport` and `useExportJob(jobId)`; handles all 6 states from verbatim microcopy
- `src/components/redemption-history/__tests__/ExportDialog.test.tsx`

Vitest test covers:
- Idle state: format selector + Export button rendered
- Clicking Export calls `useTriggerExport` mutation
- 202 response → transitions to polling state; "Generating your export…" visible
- COMPLETED polling response → "Your export is ready" + Download button visible
- FAILED polling response → "Export failed — please try again" + Try again button visible
- 422 response → zero-results error "No records match the selected filters" visible
- 429 response → `toast.error` called with rate limit message

### FE-4: Wire ExportDialog into TransactionHistoryPage

**File:** `src/pages/redemption-history/TransactionHistoryPage.tsx` — add "Export" button to page header area (visible to users with `action.redemption.export` permission via `<PermissionGate>`); manages `exportDialogOpen` boolean state; passes active `filters` to `ExportDialog`

---

## E2E test [FE]

**File:** `e2e/redemption-history.spec.ts`

---

**Scenario 1:** `'sync export downloads immediately'` _(covers AC-1)_

| Field | Value |
|---|---|
| **User flow** | Click "Export" → ExportDialog opens → select CSV → click Export → mocked 200 with file bytes → browser download triggered |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests/export` → 200 with CSV bytes + `Content-Disposition: attachment; filename=redemption-history.csv` |
| **Visible assertion** | Download event fires (`page.waitForEvent('download')`); dialog closes or shows success |

---

**Scenario 2:** `'async export shows polling then download button'` _(covers AC-2, AC-3, AC-4)_

| Field | Value |
|---|---|
| **User flow** | Click Export → select XLSX → Export clicked → 202 returned → dialog shows "Generating…" → 2 polls return PENDING → 3rd poll returns COMPLETED → "Your export is ready" + Download button appear → click Download → presigned URL opened |
| **APIs to mock via `page.route()`** | `POST /export` → 202 `{jobId, status:'PENDING'}`; first 2 `GET /export/{jobId}` → `{status:'PENDING'}`; 3rd → `{status:'COMPLETED', rowCount:1500, expiresAt:'...'}` ; `GET /export/{jobId}/download` → `{downloadUrl:'https://storage.example.com/...'}`|
| **Visible assertion** | `expect(page.getByText('Generating your export…')).toBeVisible()` → then `expect(page.getByText('Your export is ready')).toBeVisible()` → Download button visible |

---

**Scenario 3:** `'export with no results shows inline error'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | Click Export → select CSV → click Export → 422 response → inline error shown in dialog |
| **APIs to mock via `page.route()`** | `POST /export` → 422 `{errorCode:'VALIDATION_FAILED', errorMessage:'No records match the selected filters'}` |
| **Visible assertion** | `expect(page.getByText('No records match the selected filters')).toBeVisible()` |

---

**Scenario 4:** `'failed async export shows retry button'` _(covers AC-6 — FAILED state)_

| Field | Value |
|---|---|
| **User flow** | Export triggers → 202 → poll returns FAILED → "Export failed" + "Try again" button visible → click Try again → new export triggered |
| **APIs to mock via `page.route()`** | `POST /export` → 202; `GET /export/{jobId}` → `{status:'FAILED', failureReason:'...'}` |
| **Visible assertion** | `expect(page.getByText('Export failed — please try again')).toBeVisible()`; `expect(page.getByRole('button', { name: 'Try again' })).toBeVisible()` |

---

## Execution checklist

**BE session:**
- [ ] `TriggerExportRequest.java` DTO created _(AC-1, AC-2)_
- [ ] `RedemptionExportJobResponse.java` DTO created _(AC-2, AC-3)_
- [ ] `RedemptionExportJobDetailResponse.java` DTO created _(AC-4)_
- [ ] `RedemptionExportService.triggerExport()` implemented: COUNT threshold, sync/async branching, zero-results 422 _(AC-1, AC-2, AC-5)_
- [ ] `RedemptionExportService.processExportJob()` @Async implemented: PROCESSING → COMPLETED/FAILED _(AC-2, AC-3)_
- [ ] `RedemptionExportService.getExportJob()` + ownership check → 404 _(AC-3, AC-6)_
- [ ] `RedemptionExportService.getExportJobWithDownloadUrl()` + presigned URL on COMPLETED _(AC-4)_
- [ ] `RedemptionExportServiceTest` all cases pass _(AC-1 through AC-6)_
- [ ] `RedemptionExportController` created: POST /export, GET /export/{jobId}, GET /export/{jobId}/download _(AC-1 through AC-7)_
- [ ] `@Audited` on both sync and async POST /export paths _(AC-6)_
- [ ] `RedemptionExportControllerTest` all cases pass _(AC-1 through AC-7)_

**FE session:**
- [ ] `triggerExport`, `getExportJob`, `getExportJobDownload` service calls added _(AC-1, AC-2)_
- [ ] `useTriggerExport` mutation hook created _(AC-1, AC-2)_
- [ ] `useExportJob` poll hook created: refetchInterval 3s while PENDING/PROCESSING _(AC-3, AC-4)_
- [ ] `ExportDialog.tsx` component created: all 6 state machine states _(AC-1 through AC-5)_
- [ ] `ExportDialog.test.tsx` Vitest tests pass: idle, 202→polling, COMPLETED, FAILED, 422 _(AC-1 through AC-5)_
- [ ] Export button wired into `TransactionHistoryPage` with `<PermissionGate>` _(AC-6)_
- [ ] `npm run build` — no TypeScript errors
- [ ] E2E Scenario 1 passes _(AC-1)_
- [ ] E2E Scenario 2 passes _(AC-2, AC-3, AC-4)_
- [ ] E2E Scenario 3 passes _(AC-5)_
- [ ] E2E Scenario 4 passes _(AC-6)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionExportServiceTest` + `RedemptionExportControllerTest` all green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-history.spec.ts -g 'export'` passes against real BE
3. Every AC (AC-1 through AC-7) referenced by at least one passing test
