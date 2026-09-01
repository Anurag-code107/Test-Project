# BRD Digest Annex: {brd-name}

> **Advisory only.** This file preserves BRD-stated technical artifacts (entity names, event names, API operations, RBAC matrix, error codes, recommended modules). These are **not** authoritative — they reflect what the BRD writer guessed about implementation. `/create-spec` reconciles them against the actual codebase (contracts repo, existing entities, platform conventions).
>
> Spec authors: read this for context, not for naming. If the codebase already has a different name, use the codebase's name.

---

{If the BRD contains technical-truth content, emit one or more of the sections below. Each section is conditional — omit it if the BRD has no matching content.}

{If the BRD has NO technical-truth content (no named entities, no event names, no API ops, no RBAC matrix, no error codes, no recommended modules), replace the entire body below with the STUB MODE block and omit all conditional sections.}

---

## STUB MODE

> No BRD-stated technical artifacts found.
>
> This BRD does not name entities, events, API operations, RBAC matrices, error codes, or recommended modules. `/create-spec` will derive these directly from the codebase when needed. No reconciliation is required.

---

## Event vocabulary (advisory)
{CONDITIONAL — BRD-named events in snake_case. Reconcile against platform's Kafka topic conventions in `/create-spec`.}

| Event name | Trigger | Consumers (BRD-stated) |
|---|---|---|
| {snake_case_name} | {when it fires} | {who consumes} |

## Data-model entity inventory (advisory)
{CONDITIONAL — BRD-named entities in CamelCase. Final naming is decided in `/create-spec` from the codebase and platform conventions.}

| Entity | BRD description | Owns / belongs to feature |
|---|---|---|
| {EntityName} | {what it represents} | {F-NN} |

## API surface (advisory)
{CONDITIONAL — BRD-named operations, REST paths, or webhook shapes. Contracts are authored in `/generate-contracts`, not here.}

| Operation | HTTP / type | BRD description |
|---|---|---|
| {operation name} | {GET/POST/etc.} | {what it does} |

## RBAC permission matrix (advisory)
{CONDITIONAL — BRD-stated role-to-permission mappings. Platform RBAC conventions in `/create-spec` take precedence.}

| Role | Permission | Notes |
|---|---|---|
| {role} | {permission key} | {BRD-stated constraint} |

## Error contract (advisory)
{CONDITIONAL — BRD-named error codes in UPPER_SNAKE format. Final codes decided in `/create-spec`.}

| Error code | Trigger condition | BRD-stated behavior |
|---|---|---|
| {ERROR_CODE} | {when raised} | {what happens} |

## Recommended backend modules (advisory)
{CONDITIONAL — BRD-suggested module / package names. Actual package structure follows platform conventions (see `docs/patterns/package-structure.md`).}

- `{module_name}` — {BRD-stated purpose}

## Technical reliability claims (advisory)
{CONDITIONAL — BRD-stated idempotency, retry, durability, or consistency requirements that are implementation-level (as distinct from business-intent reliability in digest.md §Reliability & UX guarantees).}

- {Claim — e.g., "Events must be idempotent; duplicate delivery must not double-process"}
