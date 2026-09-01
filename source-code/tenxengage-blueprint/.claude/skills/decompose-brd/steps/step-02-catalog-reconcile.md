# Step 02: catalog-reconcile

**Goal:** Reconcile BRD personas and partner-company types against the live system catalog. Force explicit user approval for any addition before slicing begins. Prevent downstream `/create-spec` runs from inventing roles or partner types that don't exist in the platform.

**Inputs:** Locked Phase 0 confirmations from step 01 (especially Part C personas).

> **Phase 0.5 — Compare BRD personas and partner-company types against the live system catalog.** Forces explicit user approval for any addition before slicing begins.

This step has two checks. Both run sequentially and require user approval before continuing.

## Read the system catalog

Read **only** [../references/system-catalog.md](../references/system-catalog.md). It is the hardcoded canonical list of system roles and partner-company types this skill reconciles against. Do not parse Flyway migrations or Java seed constants at runtime — the reference file is maintained by hand and points at the upstream sources for traceability.

If `references/system-catalog.md` is missing or empty, halt and ask the user to restore it before continuing — do not silently fall back to the codebase.

## Check A — Persona reconciliation

For every BRD persona surfaced in step 01 Part C:

1. Attempt to match by name OR description-similarity against the canonical role list. Use both name tokens and the role's responsibilities prose for matching.
2. Classify each BRD persona as:
   - **Exact match** — same name (case-insensitive) as a system role.
   - **Probable alias** — different name, but description aligns with one system role (e.g., BRD says "Channel Partner Manager", responsibilities match `PARTNER_ADMIN`).
   - **New** — no existing role aligns; would require a Flyway migration + role seed.
3. Surface the result as a single table:

   | BRD persona | Proposed mapping | Status | Reasoning |
   |---|---|---|---|
   | Channel Partner Manager | `PARTNER_ADMIN` | Probable alias | Both manage partner-org users + incentive participation |
   | Vendor Compliance Officer | — | New | No internal role aligns; BRD describes regulatory review duties |

4. Ask: **"For each BRD persona, confirm the mapping, override it, or declare it a new role. New roles will be flagged in Strategic Notes as a Flyway/seed prerequisite — they do NOT block this skill."**

5. Record the user's decisions. They become part of every per-feature brief's `Primary persona` line — use the canonical role name for matched/aliased personas; use the BRD persona name plus a `(new role)` tag for declared-new personas.

## Check B — Partner-type reconciliation

Run this check **only when** the BRD references partner companies and names one or more partner-type values (Reseller / Distributor / OEM, or anything that looks like a sibling — e.g., "MSP", "VAR", "Integrator").

Scope is **strict**: this check fires only on values for the existing `Partner Type` dimension. New classification dimensions (Partner Tier, Region, Segment) are out of scope here — they belong to per-feature data-model decisions in `/create-spec`.

1. Extract every partner-type-like noun from the BRD (look near phrases: *partner type, type of partner, reseller, distributor, OEM, MSP, integrator, ISV, channel partner type*).
2. Compare each candidate against the canonical list read above (today: `["Reseller","Distributor","OEM"]`). Classify:
   - **Existing** — exact match (case-insensitive).
   - **New** — no match; would require updating the `Partner Type` lookup values + (likely) `SeedConstants.PARTNER_TYPES`.
3. Surface as a table:

   | BRD partner type | Status | Notes |
   |---|---|---|
   | Reseller | Existing | — |
   | MSP | New | BRD anchor: "Eligible Partner Cohorts" |

4. If at least one new value is detected, ask: **"The BRD introduces partner type(s) not in the current taxonomy. Approve adding them? (approve all / approve subset / reject — treat BRD usage as documentation drift)"**

5. For approved new types, queue a Strategic Notes bullet (surfaced by step 06): "⚠️ NEW PARTNER TYPE(S): {names} — requires Flyway update to `data_object_fields` Partner Type values + `SeedConstants.PARTNER_TYPES` cross-update. Sequence before any feature that filters on partner type." If the BRD names no new partner types, skip Check B silently.

## What this step does NOT do

- Does not write Flyway migrations or seed updates — that's `/create-spec` and the implementing repo's job.
- Does not block on missing roles/types — flags them, never halts. The slicing must still proceed; the skill output documents the prerequisite.
- Does not extend to other classification dimensions beyond `Partner Type` (no Partner Tier / Region / Segment checks).

## Rules in scope for this step

- **Reconcile against system catalog before slicing** — runs on every invocation. Personas mapped to existing roles use the canonical `baseRoleName`; new personas/partner-types are flagged as Flyway/seed prerequisites, never silently invented.

## Routing

User approves persona mappings and (if applicable) partner-type decisions → load `steps/step-03-required-reading.md`.