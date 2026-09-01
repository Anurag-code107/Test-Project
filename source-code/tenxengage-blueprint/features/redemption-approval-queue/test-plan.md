# Test Plan: redemption-approval-queue

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running — scenarios that isolated unit or story-level tests cannot catch._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16 + Kafka)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & CRUD

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Full approval lifecycle: PENDING_APPROVAL → approve → RESERVED | Submit request in PENDING_APPROVAL; call approve; verify status=RESERVED, reviewedBy set to approverId, reviewedAt non-null, rejectionReason null | US-01, US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Full reject lifecycle: PENDING_APPROVAL → reject → CANCELLED | Submit request in PENDING_APPROVAL; call reject with reason; verify status=CANCELLED, rejectionReason set, reviewedBy+reviewedAt non-null | US-01, US-03 |
| `RedemptionApprovalQueueIntegrationTest` | V18 migration applies cleanly | `reviewed_by`, `reviewed_at`, `rejection_reason` columns present on `redemption_requests`; all nullable; `reviewed_by` FK points to `users(id)` | Foundation |

---

## State Machine Transitions

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Valid: PENDING_APPROVAL → approve → RESERVED | Status=RESERVED; audit row APPROVED written | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Valid: PENDING_APPROVAL → reject → CANCELLED | Status=CANCELLED; audit row REJECTED written | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Invalid: RESERVED → approve → 409 | Already-approved item re-submitted to approve endpoint → 409 "Redemption is not in PENDING_APPROVAL state" | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Invalid: CANCELLED → reject → 409 | Already-rejected item re-submitted to reject endpoint → 409 "Redemption is not in PENDING_APPROVAL state" | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Invalid: RESERVED → reject → 409 | RESERVED item submitted to reject endpoint → 409 "Redemption is not in PENDING_APPROVAL state" | US-02, US-03 |

---

## Business Rule Enforcement

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Concurrent approve — second approver gets 409 | Two threads attempt to approve the same PENDING_APPROVAL item simultaneously; one succeeds (RESERVED); second gets 409 due to pessimistic write lock | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Concurrent approve + reject — second action gets 409 | Thread A approves; Thread B concurrently rejects same item; loser gets 409 regardless of action type | US-02, US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Reject with blank reason rejected at API boundary | POST /{id}/reject with `rejectionReason: ""` → 400; status remains PENDING_APPROVAL | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Reject with reason > 1000 chars rejected at API boundary | POST /{id}/reject with 1001-char reason → 400; status unchanged | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | requestType=RETURN returns empty list | GET /approval-queue?requestType=RETURN → 200 + empty page (F-06 stub) | US-01 |
| `RedemptionApprovalQueueIntegrationTest` | Vendor routing failure rolls back approval | Mock `RedemptionRoutingService` to throw; call approve; verify status stays PENDING_APPROVAL; no RESERVED row; 500 returned | US-02 |

---

## Multi-Entity Workflows

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Queue displays only PENDING_APPROVAL items | Create items in PENDING_APPROVAL, RESERVED, CANCELLED states; GET /approval-queue → only PENDING_APPROVAL items returned | US-01, US-02, US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Approved item disappears from queue on re-fetch | Approve item; GET /approval-queue → approved item not in result set (status no longer PENDING_APPROVAL) | US-01, US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Rejected item disappears from queue on re-fetch | Reject item; GET /approval-queue → rejected item not in result set | US-01, US-03 |

---

## Contract Conformance

_Uses `RestAssured` + OpenAPI validator wired to `../tenxengage-contracts/endpoints/redemption-approval-queue.yaml`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueContractConformanceTest` | GET /api/v1/redemption/requests/approval-queue response shape | Response body matches `PaginatedResponse<ApprovalQueueItemResponse>` contract schema; all required fields present with correct types | US-01 |
| `RedemptionApprovalQueueContractConformanceTest` | POST /api/v1/redemption/requests/{id}/approve response shape | 200 response body matches `RedemptionRequestDetailResponse` contract schema; `reviewedBy`, `reviewedAt`, `rejectionReason` fields present | US-02 |
| `RedemptionApprovalQueueContractConformanceTest` | POST /api/v1/redemption/requests/{id}/reject response shape | 200 response body matches `RedemptionRequestDetailResponse` contract schema; `rejectionReason` non-null | US-03 |
| `RedemptionApprovalQueueContractConformanceTest` | 409 error response shape on double-approve | Response body matches contract error schema; message field matches "Redemption is not in PENDING_APPROVAL state" | US-02 |

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Cross-tenant approve → 404 | Tenant A creates PENDING_APPROVAL item; Tenant B JWT calls POST /{id}/approve → 404 (not 403) | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Cross-tenant reject → 404 | Tenant A creates PENDING_APPROVAL item; Tenant B JWT calls POST /{id}/reject → 404 (not 403) | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Cross-tenant queue view → empty list, not 404 | Tenant B calls GET /approval-queue → 200 + empty page (Tenant A items filtered by Hibernate tenant filter, not visible) | US-01 |
| `RedemptionApprovalQueueIntegrationTest` | PARTNER_SELLER cannot access approval queue | GET /approval-queue with PARTNER_SELLER JWT → 403 | US-01 |
| `RedemptionApprovalQueueIntegrationTest` | ACTIVITY_APPROVER can access approval queue and approve | GET /approval-queue → 200; POST /{id}/approve → 200 | US-01, US-02 |
| `RedemptionApprovalQueueIntegrationTest` | CLIENT_ADMIN can approve and reject | POST /{id}/approve → 200 with CLIENT_ADMIN JWT; POST /{id}/reject → 200 | US-02, US-03 |
| `RedemptionApprovalQueueIntegrationTest` | No JWT → 401 on all endpoints | GET /approval-queue, POST /approve, POST /reject all return 401 without Authorization header | Foundation, US-01, US-02, US-03 |

---

## Audit & Events

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionApprovalQueueIntegrationTest` | Audit row after approve | `audit_log` has record: `action=APPROVED`, `resource_type=REDEMPTION_REQUEST`, `resource_id={id}`, `actor_id=approverId` | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | Audit row after reject | `audit_log` has record: `action=REJECTED`, `resource_type=REDEMPTION_REQUEST`, `resource_id={id}`, `actor_id=rejecterId` | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | T1 — Kafka `redemption.approved` event round-trip | Approve a PENDING_APPROVAL item; assert Kafka consumer receives event on `notification-events` topic with `notificationTypeKey="redemption.approved"` and correct `targetUserIds` within 5 s; verify rejectionReason is NOT in payload | US-02 |
| `RedemptionApprovalQueueIntegrationTest` | T1 — Kafka `redemption.rejected` event round-trip | Reject a PENDING_APPROVAL item; assert Kafka consumer receives event on `notification-events` topic with `notificationTypeKey="redemption.rejected"` and correct `targetUserIds` within 5 s; verify rejectionReason is NOT in payload (confidentiality) | US-03 |
| `RedemptionApprovalQueueIntegrationTest` | Kafka publish failure does not roll back approval | Mock producer to throw on publish; call approve; verify status=RESERVED (commit succeeded); 200 returned; WARN log emitted | US-02 |

---

## Cross-Cutting Checks [BE + FE]

| Check | Story/Foundation |
|---|---|
| Tenant isolation: resource created by Tenant A returns 404 to Tenant B on approve/reject | Foundation, US-02, US-03 |
| Tenant isolation: Tenant B queue view returns empty (not 404); Tenant A items not leaked | Foundation, US-01 |
| Pessimistic lock prevents double-approval at DB level (concurrent threads) | US-02 |
| Soft delete: `deleted=true` redemption requests are excluded from approval queue | US-01 |
| Audit log: every approve and reject creates `audit_log` record with correct `tenantId`, `userId`, timestamp | US-02, US-03 |
| rejectionReason not included in Kafka event payload (confidentiality constraint from spec) | US-03 |
| Vendor routing failure inside `@Transactional` rolls back entire approval — status stays PENDING_APPROVAL | US-02 |
| Kafka publish failure after commit does NOT roll back status transition (advisory publish — loss tolerable) | US-02, US-03 |
| `action.redemption.approve` permission required for approve and reject endpoints; missing permission → 403 | Foundation (F4), US-02, US-03 |
