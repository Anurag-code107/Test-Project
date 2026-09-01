# Test Plan: redemption-history

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` file alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & State Machine (Export Job)

_Full export job lifecycle requires US-03 BE and Foundation. Validates state machine from `spec.md → ## Workflow / Status Transitions`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionExportJobIntegrationTest` | Full lifecycle: PENDING → PROCESSING → COMPLETED | Job persisted with PENDING; async task transitions to PROCESSING; file uploaded; final state COMPLETED with `fileKey`, `rowCount`, `expiresAt` populated | US-03, Foundation |
| `RedemptionExportJobIntegrationTest` | PROCESSING → FAILED on exception | `failureReason` set; status = FAILED; no `fileKey` | US-03 |
| `RedemptionExportJobIntegrationTest` | Invalid transition: COMPLETED → any | 409 Conflict — terminal state rejection | US-03 |
| `RedemptionExportJobIntegrationTest` | Concurrent `processExportJob` calls for same job — `@Version` optimistic lock | Second invocation gets `OptimisticLockException`; job not double-processed | US-03, Foundation |

---

## Business Rule Enforcement

_Validates rules from `spec.md → ## Service Layer [BE]` against a real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionExportIntegrationTest` | Sync threshold: exactly 1,000 matching rows → sync path | 200 with file bytes; no `RedemptionExportJob` row created | US-03 |
| `RedemptionExportIntegrationTest` | Async threshold: exactly 1,001 matching rows → async path | 202 with jobId; `RedemptionExportJob` row in DB with status=PENDING | US-03 |
| `RedemptionExportIntegrationTest` | Export with 0 matching rows | 422 "No records match the selected filters"; no job created | US-03 |
| `RedemptionExportIntegrationTest` | Non-owner accesses export job | `GET /export/{jobId}` as different user → 404 | US-03 |
| `RedemptionExportIntegrationTest` | CLIENT_ADMIN accesses another user's export job | 200 (view_all_history bypass) | US-03, US-04 |

---

## Multi-Entity Workflows

_Verifies the full earn → redeem → history path end-to-end._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionHistoryIntegrationTest` | Submit redemption (F-03) → appears in personal history | `GET /requests` returns the redemption with correct `catalogItemName` from joined catalog item | US-01, F-03 |
| `RedemptionHistoryIntegrationTest` | Submit company redemption → appears in company history | `GET /requests/company` returns the company wallet redemption; not in personal history | US-02, F-03 |
| `RedemptionHistoryIntegrationTest` | Both personal and company redemptions → all visible in tenant history | `GET /requests/all` returns both; each row has `userDisplayName` + `partnerCompanyName` | US-01, US-02, US-04 |

---

## Permission Enforcement

_Same endpoint, different roles. Validates permission matrix from `spec.md → ## Permissions & Feature Flags`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionHistoryIntegrationTest` | `GET /requests/all` as CLIENT_ADMIN | 200 with all tenant records | US-04 |
| `RedemptionHistoryIntegrationTest` | `GET /requests/all` as PARTNER_ADMIN | 403 | US-04 |
| `RedemptionHistoryIntegrationTest` | `GET /requests/all` as PARTNER_SELLER | 403 | US-04 |
| `RedemptionHistoryIntegrationTest` | `POST /export` as PARTNER_SELLER | 200 or 202 (has `action.redemption.export`) | US-03 |
| `RedemptionHistoryIntegrationTest` | `POST /export` as unauthenticated | 401 | US-03 |

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionHistoryIntegrationTest` | RedemptionRequest created by Tenant A; queried via `GET /requests` as Tenant B | 0 results (Hibernate tenant filter effective) | US-01, Foundation |
| `RedemptionHistoryIntegrationTest` | RedemptionExportJob created by Tenant A; polled via `GET /export/{jobId}` as Tenant B | 404 (tenant filter on export job) | US-03, Foundation |
| `RedemptionHistoryIntegrationTest` | `GET /requests/{id}` wrong tenant | 404, never 403 | US-01 |

---

## Contract Conformance

_Response body shape matches `../tenxengage-contracts/endpoints/redemption-history.yaml`. Run after `/generate-contracts redemption-history`._

_Uses OpenAPI validator wired to contract file._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests` response shape | `PaginatedResponse<RedemptionRequestResponse>` with `catalogItemName` + `completedAt`; all required fields present | US-01 |
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests/{id}` response shape | `RedemptionRequestDetailResponse` with `linkedReturnId` field (null) | US-01 |
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests/company` response shape | `PaginatedResponse<RedemptionRequestResponse>` | US-02 |
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests/all` response shape | `PaginatedResponse<RedemptionAdminHistoryResponse>` with `userDisplayName` + `partnerCompanyName` | US-04 |
| `RedemptionHistoryContractConformanceTest` | `POST /api/v1/redemption/requests/export` async response shape | `RedemptionExportJobResponse` with `jobId` (UUID), `status` (string) | US-03 |
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests/export/{jobId}` response shape | `RedemptionExportJobResponse` fields correct | US-03 |
| `RedemptionHistoryContractConformanceTest` | `GET /api/v1/redemption/requests/export/{jobId}/download` response shape | `RedemptionExportJobDetailResponse` with `downloadUrl` field | US-03 |

---

## Audit & Events

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionHistoryIntegrationTest` | Sync export triggered | `audit_log` has record: `action=DATA_EXPORTED`, `resource_type=REDEMPTION_REQUEST`, correct `actor_id` and `client_id` | US-03 |
| `RedemptionHistoryIntegrationTest` | Async export triggered | `audit_log` has record: `action=DATA_EXPORTED`, `resource_type=REDEMPTION_EXPORT_JOB`, `resource_id=jobId` | US-03 |

---

## Cross-Cutting Checks [BE + FE]

| Check | Story / Foundation |
|---|---|
| Tenant isolation: export job from Tenant A → 404 to Tenant B | Foundation, US-03 |
| Rate limit: 6th `POST /export` within 1 hour → 429 with Retry-After header | US-03 |
| Rate limit: 31st `GET /requests/all` within 1 minute → 429 | US-04 |
| `vendorReferenceId` absent from `RedemptionRequestDetailResponse` when status ≠ COMPLETED | US-01 |
| `file_key` column never appears in any API response | US-03 |
| Audit log: DATA_EXPORTED written for every export trigger (sync and async) | US-03 |
| No PII field values in log output (amounts, names) — log lines contain only UUIDs | Foundation |
