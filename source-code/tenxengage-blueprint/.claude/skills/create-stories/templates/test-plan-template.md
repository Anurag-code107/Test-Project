# Test Plan: {{feature-slug}}

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running — scenarios that isolated unit or story-level tests cannot catch._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16 + Kafka)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & CRUD

_One full-lifecycle test per entity. Tests the complete persistence round-trip through a real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Full CRUD lifecycle_ | _Create → Read → Update → Delete (soft) — entity persisted and retrievable at each step_ | _US-01, US-02, US-03_ |
| _e.g., `CourseIntegrationTest`_ | _Flyway migration applies_ | _V{{N}} tables created with correct columns, indexes, and FKs_ | _Foundation_ |

---

## Entity Relationships & Cascades

_One test per parent-child relationship defined in `spec.md → ## Data Model / Entities`. Tests cascade behavior on delete — cannot be caught by unit tests with mocks._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Delete course → verify lessons soft-deleted_ | _All child lessons have `deleted=true`; quiz questions also cascade_ | _US-01, US-04_ |

_Add rows for: each FK relationship (create child without parent → FK violation), ordering constraints (sort_order), orphan handling._

---

## State Machine Transitions

_One test per valid transition + one per invalid transition, derived from `spec.md → ## Workflow / Status Transitions`. Full lifecycle through all states in one test._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Valid: DRAFT → PUBLISHED_ | _Status updated, `updated_at` changed, audit record created_ | _US-01, US-05_ |
| _e.g., `CourseIntegrationTest`_ | _Invalid: ARCHIVED → DRAFT_ | _`400` with message "Cannot transition from ARCHIVED to DRAFT"_ | _US-01, US-05_ |
| _e.g., `CourseIntegrationTest`_ | _Full lifecycle: DRAFT → PUBLISHED → ARCHIVED_ | _Each transition succeeds; entity state correct after each step_ | _US-01, US-05_ |

_Remove this category if the feature has no status field._

---

## Business Rule Enforcement

_One test per business rule from `spec.md → ## Service Layer` and edge cases. These use a real DB — unit test mocks can mask DB constraint issues._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Publish course with 0 lessons_ | _`400` with message 'At least one lesson required'_ | _US-01, US-05_ |

_Extract every "cannot X when Y" rule from the spec's service layer business rules._

---

## Multi-Entity Workflows

_End-to-end workflows spanning multiple entities and services. Tests ordering bugs and transaction boundary issues that isolated tests miss._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `EnablementIntegrationTest`_ | _Create course → add lessons → add quiz → publish_ | _Entire object graph consistent; all children linked; course status propagated_ | _US-01, US-02, US-05_ |

_Extract from the stories index dep graph — stories that form a chain create a multi-entity workflow test._

---

## Contract Conformance

_One row per endpoint group verifying that the actual response body shape and status codes match the generated OpenAPI contract. Catches BE drift that per-story `@WebMvcTest` cannot — those tests assert what the BE author wrote, not what the contract declares._

_Uses `RestAssured` or `MockMvc` + an OpenAPI validator (e.g. `openapi-validator-restassured` or `atlassian-oai-validator`) wired to `../tenxengage-contracts/endpoints/{{feature}}.yaml`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseContractConformanceTest`_ | _GET /api/v1/{{path}} response shape_ | _Response body matches contract schema; 200 status; all required fields present with correct types_ | _US-01_ |
| _e.g., `CourseContractConformanceTest`_ | _POST /api/v1/{{path}} response shape_ | _201 response body matches contract schema for `{{Entity}}DetailResponse`_ | _US-03_ |
| _e.g., `CourseContractConformanceTest`_ | _POST /api/v1/{{path}} validation error response shape_ | _400 body matches contract `ValidationErrorResponse` schema; `errors[]` field shape correct_ | _US-03_ |
| _e.g., `CourseContractConformanceTest`_ | _GET /api/v1/{{path}}/{id} not-found response shape_ | _404 body matches contract `ErrorResponse` schema_ | _US-01_ |
| _e.g., `CourseContractConformanceTest`_ | _POST /api/v1/{{path}}/{id}/publish state-violation response shape_ | _422 body matches contract `BusinessRuleViolationResponse` schema; `code` field present_ | _US-NN_ |

_Add one row per endpoint group (list, detail, create, update, delete). Also add at least one row each for **400 (validation), 404 (not found), and 422 (business rule)** error response shapes — these catch contract drift on error paths that success-only tests cannot. Remove this category if the feature has no generated contract yet (contract must be generated before these tests can run)._

---

## Tenant Isolation & Security

_Cross-story security boundaries that require multiple stories or the full stack
to exercise: tenant isolation across all CRUD verbs, unauthenticated access,
permission enforcement under the most restrictive role, concurrent write
conflicts, and input sanitization end-to-end._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Unauthenticated GET /api/v1/{{path}}_ | _401 with no body leakage; not 403 or 500_ | _US-01_ |
| _e.g., `CourseIntegrationTest`_ | _Unauthenticated POST /api/v1/{{path}}_ | _401_ | _US-NN_ |
| _e.g., `CourseIntegrationTest`_ | _Tenant A creates; Tenant B queries by ID_ | _`404` (not 403 — prevents tenant enumeration)_ | _US-01, US-03_ |
| _e.g., `CourseIntegrationTest`_ | _Tenant A creates; Tenant B PUT /api/v1/{{path}}/{id}_ | _404 — IDOR on write blocked, not just GET_ | _US-NN_ |
| _e.g., `CourseIntegrationTest`_ | _Tenant A creates; Tenant B DELETE /api/v1/{{path}}/{id}_ | _404 — IDOR on write blocked_ | _US-NN_ |
| _e.g., `CourseIntegrationTest`_ | _Concurrent update conflict_ | _Two threads update same entity → one gets `OptimisticLockException`_ | _US-02_ |
| _e.g., `CourseIntegrationTest`_ | _CLIENT_ADMIN can create; PARTNER_SELLER cannot_ | _CLIENT_ADMIN → 201; PARTNER_SELLER → 403_ | _US-01_ |

_Add permission enforcement tests for each role from the permission matrix. Test the most restrictive role against every write endpoint._

---

## Audit & Events

_Audit rows for successful AND failed mutating operations; Kafka event consumer
round-trip; event payload schema conformance. Per-story producer tests verify
fire-and-forget; this section verifies the full chain and the negative path
(failed operation produces no audit record)._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseIntegrationTest`_ | _Audit record after create_ | _`audit_log` has record with `action=Created`, `resource_type=COURSE`, correct `actor_id`_ | _US-01_ |
| _e.g., `CourseIntegrationTest`_ | _Failed create (validation error) → NO audit row_ | _`audit_log` count unchanged after a 400 response; failed ops must not leak audit trail_ | _US-01_ |
| _e.g., `CourseIntegrationTest`_ | _Kafka event published on publish_ | _Correct event on `tenxengage.{{domain}}.course.published` with expected payload_ | _US-05_ |

_Add one row for each audited operation and each Kafka event from `spec.md → ## Audit Trail` and `## Domain Events`. Remove this category if no events are published._

_**Kafka event testing split:** Per-story BE unit tests (Mockito) verify the producer fires with the correct topic + payload — those live in each story file. This category covers the **consumer side** and **full round-trip** tests: start an embedded Kafka / Testcontainers Kafka broker, trigger the producing operation, assert the consumer receives and processes the event correctly._

---

## Query Correctness at Scale

_Cross-story queries against real data volumes. Per-story unit tests cover simple
filtering with mocked repos; this category proves correctness with realistic data._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CourseQueryIT`_ | _Pagination: 250 rows, page 6 size 50_ | _Returns page 6 with exactly 50 rows; total=250; nextCursor present_ | _US-01_ |
| _e.g., `CourseQueryIT`_ | _Combined filter: status=PUBLISHED + type=X + tag in (A,B)_ | _Result set matches manual SQL; no N+1 (assert query count via Hibernate Statistics)_ | _US-01, US-NN_ |
| _e.g., `CourseQueryIT`_ | _Pagination edge: last page with fewer than page_size results_ | _hasNext=false; rows correctly bounded_ | _US-01_ |
| _e.g., `CourseQueryIT`_ | _Full-text search ordering: relevance vs created_at tiebreak_ | _Top result is highest-relevance; ties broken deterministically_ | _US-01_ |

_Remove this category if the feature has no list/search/filter endpoints._

---

## E2E Cross-Story Scenarios (Real Stack)

_Playwright scenarios run against a real running backend with real database state.
No `page.route()` mocking. These catch BE-FE shape drift and full-flow regressions
that per-story Playwright (mocked) cannot see._

_Generated by `/execute-integration-tests` and run with `run-tests --real-backend`._

_Setup convention: each spec's `beforeAll` creates required state via real API calls
(not test database fixtures), using a test-tenant JWT minted against the test-stack
auth endpoint. `afterAll` does not clean up — each T1 run starts from a fresh stack._

| Spec File | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `e2e/<feature-slug>/full-happy-path.spec.ts`_ | _Actor performs full multi-story flow; verifies final state in UI_ | _Each step renders correct UI against real API responses; final state matches expected_ | _US-NN, US-MM, US-PP_ |
| _e.g., `e2e/<feature-slug>/cross-tenant-isolation.spec.ts`_ | _Tenant A creates resource; Tenant B logged in as different user cannot see it_ | _Tenant B's list shows zero matches; direct URL returns 404 page_ | _US-NN, US-MM_ |

_Remove this category if the feature has no user-facing cross-story flows worth
running against the real stack._

---

## Cross-Cutting Checks

_Behaviors that span backend and frontend, extracted from `spec.md → ## Edge Cases`
and `## Security Design`. Each row maps to a concrete test class so that
`run-tests` can auto-generate or align coverage from this section._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| _e.g., `CrossCuttingIT`_ | _Soft delete: GET /{id} after delete_ | _404; deleted entity not visible in list query_ | _US-NN_ |
| _e.g., `CrossCuttingIT`_ | _Optimistic locking: stale `version` on PUT_ | _409 with version conflict body shape_ | _US-NN_ |
| _e.g., `CrossCuttingIT`_ | _PII in logs: capture INFO+DEBUG logs during a create with PII fields_ | _Log output does NOT contain any PII field value (assert via log appender capture)_ | _Foundation_ |
| _e.g., `CrossCuttingIT`_ | _XSS sanitization: payload `<script>alert(1)</script>` in any text field_ | _Stored sanitized per Jsoup `Safelist.basic()`; not reflected as script in GET response_ | _US-NN_ |
| _e.g., `CrossCuttingIT`_ | _Rate limiting: excess requests within window_ | _429 with `Retry-After` header; specifics defined in §Tenant Isolation & Security_ | _US-NN_ |

_Add rows for any feature-specific cross-cutting check from the spec. Each row
must have a concrete `Test Class` (not just a checkbox) so `run-tests` can
auto-generate or align it._

---

_Remove any category that doesn't apply to this feature (e.g., skip "State Machine" if no status field, skip "Audit & Events" if no events published)._
