---
name: classify-repos
description: Classification signals for determining which of the five TenXEngage repos is affected by a bug — used by bug-reporter and bug-fixer Step 0
type: reference
---

# Repo Classification Signals

Used by `bug-reporter` and `bug-fixer` Step 0 to determine `affected_repos`. Analyse task title, description, comments, screenshots, and stack traces.

**Output:** one or more of: `frontend`, `backend`, `admin-frontend`, `admin-backend`, `contracts`

---

## Frontend (`tenxengage-frontend`)

**Strong signals:**
- UI, screen, page, component, button, form, table, layout, style, Tailwind, className
- React, TypeScript, Vite, browser console error (not server logs)
- Rendering, display, chart, modal, sidebar, drawer, tab, route, navigation, redirect
- "Shows wrong data", "screen is blank", "button not working", "table doesn't load"
- Screenshots show a logged-in learner or course participant view in the main app
- Error originates from `app.tenxengage.com` or `localhost:5173`

**Test framework:** Vitest + Testing Library + Playwright

---

## Backend (`tenxengage-backend`)

**Strong signals:**
- API, endpoint, REST, 500, 400, 401, 403, 404, 409
- Database, SQL, query, migration, Flyway, PostgreSQL
- Kafka, consumer, producer, topic, event
- Redis, cache, TTL
- Spring Boot, Java, `@Service`, `@Controller`, `@Repository`, entity
- JWT, token, CORS, authentication, authorization
- Performance, timeout, slow query, N+1
- Stack traces from Java (lines like `at com.tenxengage...`)
- Postman/API client screenshots showing server errors

**Test framework:** JUnit 5 + Testcontainers

---

## Admin Frontend (`tenxengage-admin-frontend`)

**Strong signals:**
- Admin panel, admin UI, platform admin, tenant management
- "Admin screen", "admin dashboard", "admin portal"
- Error from `admin.tenxengage.com` or `localhost:5174`
- Screenshots show the admin-specific UI (tenant list, platform config, user management)

**Test framework:** Vitest + Testing Library

---

## Admin Backend (`tenxengage-admin-backend`)

**Strong signals:**
- Admin API, `/admin/api/`, admin service
- Tenant provisioning, tenant creation, platform-level operations
- Stack traces from Java in the admin service package
- Admin-specific authentication / super-admin JWT

**Test framework:** JUnit 5 + Testcontainers

---

## Contracts (`tenxengage-contracts`)

**Strong signals:**
- OpenAPI, `openapi.yaml`, contract mismatch
- "Field name changed", "missing field in response", "wrong type", "breaking change"
- The fix is purely a schema change (renaming a field, adding/removing an endpoint, changing a type)
- Frontend expects field `X` but backend sends field `Y`

**Note:** Contract changes almost always co-affect at least one of backend or frontend — list all affected repos.

---

## Multi-repo patterns

| Pattern | Likely repos |
|---|---|
| API contract mismatch (wrong field names) | `contracts` + `backend` + `frontend` |
| Auth / JWT flow | `backend` + `frontend` (or admin variants) |
| Data not showing correctly (no error shown) | `frontend` + `backend` |
| Admin action affects learner side | `admin-backend` + `backend` |
| Shared enum wrong | `contracts` + any consuming repo |
| CORS error in browser | `backend` (or `admin-backend`) + `frontend` |

---

## Ambiguity rules

- If bug clearly describes only one repo → classify as that repo only.
- If description is ambiguous ("data not showing") → classify as both `frontend` + `backend`; note the ambiguity in Step 0 normalization block.
- If classification cannot be made with confidence after reading all evidence → ask the dev: "I can't confidently classify this as frontend/backend/other. Which repo(s) should I target?"
- Never guess admin vs. non-admin without evidence. When in doubt, use non-admin repos.

---

## Scope of the fix branch

One branch name (`bug/<slug>` or `bug/clickup-<id>-<slug>`) is used in **every** affected repo. The same branch name is used as a git tag on the merge commit in each repo to allow cross-repo correlation.