# Learnings Log

Running record of Tier 1 and Tier 2 knowledge promotions from T1 integration test runs.

---

## 2026-06-13 — redemption-returns (T1)

| Rule | Category | Applied to |
|---|---|---|
| T1 real-backend Playwright specs require a seeded bootstrap admin user; use `test.skip()` on fresh DBs until `app.seed.enabled=true` provides one | e2e-testing | docs/patterns/e2e-testing.md |
| JPQL optional filter parameters with null binds fail on PostgreSQL — use native SQL with COALESCE or Specification API; `@SQLRestriction` does not apply to native queries | cross-cutting (JPA/repository) | PROJECT-CONTEXT.md |
| JVM timezone "Asia/Calcutta" rejected by PostgreSQL 16 Docker — pass `-Duser.timezone=UTC` or `-Duser.timezone=Asia/Kolkata` in JDBC-using processes | cross-cutting (local dev) | PROJECT-CONTEXT.md |
| Flyway version numbers must be globally unique; regenerated schema scripts must not reuse existing version numbers; fresh-DB chain integrity is masked by Testcontainers `create-drop` | cross-cutting (migrations) | PROJECT-CONTEXT.md |
