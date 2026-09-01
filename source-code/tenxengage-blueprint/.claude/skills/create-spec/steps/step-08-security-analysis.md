# Step 08: security-analysis

## Goal
Threat-analyze the feature. Findings flow directly into spec.md's Security Design section in step 13.

## Inputs (from prior steps)
- Locked FRs and NFRs (step 01)
- Locked shape manifest (step 05)
- Loaded shape pattern guidance (step 06) — `rate-limit-sensitive.md` and `sse-streaming.md` if applicable

## Loads (just-in-time)
- Already loaded via shape manifest (no new reads)

## Procedure

For each dimension below, evaluate and record findings:

1. **PII & data classification.** Which fields contain personal data (names, emails, phone, user IDs linked to real people)? Which are confidential (financial, internal configs)? These must appear in spec.md's Data Retention & Compliance section.

2. **Attack surfaces.**
   - Free-text search fields → SQL injection / JSONB injection risk
   - File upload endpoints → path traversal, malicious content
   - User-controlled JSONB structures → schema injection

3. **Sensitive operations.** Auth flows, permissions, financial transactions, role assignments → elevated audit logging + stricter rate limits.

4. **Privilege escalation risk.** Can a CLIENT_ADMIN affect another tenant's data? Any endpoint taking an ID from request body (not resolved from token) is a risk.

5. **Rate limiting candidates.** Expensive, public-facing, or abuse-prone endpoints (search, AI calls, bulk imports, status transitions). If shape manifest includes `rate-limit-sensitive`, deep-detail per-bucket configuration referencing the loaded pattern's rules.

6. **SSE authentication.** If shape manifest includes `sse-streaming`, apply the SSE auth rules from `sse-streaming.md`: do NOT spec `?token=jwt` for `EventSource`. Spec either `fetch` + `ReadableStream` or short-lived SSE-specific token.

## Rules (scoped to this step)
- HTML sanitization rule applies if shape manifest includes `html-content` — record that finding here so step 13 picks it up. (The rule itself lives in `html-content.md`.)
- Document only OWASP risks that ACTUALLY apply to this feature. Do not list theoretical risks.
- Tenant isolation (every entity has client_id, every query filters by it) is mandatory; record that finding regardless of shape.

## User interaction
None.

## Output for downstream steps
- Security findings (PII fields list, attack surfaces, sensitive ops, privilege escalation risks, rate limit configs, SSE auth approach if applicable)

## Boundary
Security findings recorded in conversation context → route to step 09: read steps/step-09-events-analysis.md`.