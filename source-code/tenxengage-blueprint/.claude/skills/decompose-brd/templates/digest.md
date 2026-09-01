# BRD Digest: {brd-name}

> Slug: `{slug}` · Source: `{path}` · Module/quadrant: `{module}` · Generated: {date}
>
> **Purpose:** BRD-specific cross-cutting context that every spec derived from this BRD inherits. Project-level patterns (multi-tenancy, RBAC, audit, package structure) live in `PROJECT-CONTEXT.md` and are NOT repeated here.
>
> **Boundary:** This digest captures business-truth only — vision, personas, business rules, decision logic, non-goals, KPIs, integration intent. BRD-stated technical artifacts (entity names, event names, API ops, RBAC matrices, error codes) live in `digest-annex.md` if emitted. Spec authors reconcile those against the codebase, not the BRD.

## Vision
{1–2 sentences from BRD vision/purpose section.}

## Scope boundaries
- **Owns**: {what this BRD owns}
- **Does NOT own**: {what's referenced but owned by another module/quadrant}

## Personas
- **{Persona name}** — {primary need, business language}

## BRD-specific cross-cutting concepts
{Domain-language framings unique to this BRD that every spec needs to inherit. Examples (illustrative, not template):
- Agent contract format (Trigger → Decision → Action → Escalation), AI guardrails, agent hierarchy
- Attribution model semantics with confidence scoring; append-only recalculation pattern
- State machine definitions; status transition rules

Skip if BRD has no cross-cutting concepts unique to it.}

## Integration intent (cross-module / cross-quadrant)
| Direction | Counterparty | Business intent |
|---|---|---|
| Sends to | {Module/quadrant or external system} | {what business signal — described in business language, not event name} |
| Receives from | {Module/quadrant} | {what business signal} |

## Phasing intent
- **Phase 1 ({timeframe if stated})** — {scope summary}. Exit gate: {gate}.
- **Phase 2 ({timeframe})** — {scope summary}.
- **Phase 3** — {deferred capabilities}.

## Open ADRs / decisions
| ADR | Decision | Owner | By when | Blocks |
|---|---|---|---|---|
| ADR-NN | {decision} | {owner} | {timing} | {feature(s)} |

---

## Mission-critical decision tables
{CONDITIONAL — preserve verbatim only when the BRD contains decision logic in tabular form (priority matrices, escalation rubrics, business-rule decision tables). NOT for entity inventories or API tables.}

### Table: {table name} (source: "{BRD heading text}")
{verbatim table}

## Concrete SLAs / numeric guarantees
{CONDITIONAL — BRD contains numeric time-bounds, caps, floors.}
| Capability / context | Numeric guarantee | Unit | Source anchor |
|---|---|---|---|

## v1 Acceptance criteria
{CONDITIONAL — BRD has explicit acceptance-criteria language ("acceptance criteria", "the system shall", "must", "is ready when") with bulleted lists. Preserve verbatim or near-verbatim — these are the team's "done" criteria for v1.}

| Capability / area | Acceptance criterion | Source anchor |
|---|---|---|

## KPIs / Success metrics
{CONDITIONAL — BRD has metric + target + data source.}
| KPI | Definition | Target | Data source | Source anchor |
|---|---|---|---|---|

## Reliability & UX guarantees (business intent)
{CONDITIONAL — keep only items that are user-facing or business-facing. Examples: WCAG accessibility, "preview before activation", "explanation must be visible". Move technical reliability claims (idempotency, retry policy) to digest-annex.md.}

## Non-goals (v1)
{CONDITIONAL — explicit non-goal / out-of-scope statements. Verbatim.}

## Undefined terms-of-art (candidate ADRs)
{CONDITIONAL — quoted/capitalized terms in business rules without definition.}

## Companion documents referenced
{CONDITIONAL — names + version + repo-existence flag.}

## Current-state foundation
{CONDITIONAL — BRD's claim about existing platform capabilities, verbatim. If confirmed stale, prefix with ⚠️ STALE — and explain what was confirmed incorrect.}

## Risks register
{CONDITIONAL — BRD-level strategic risks. Distinct from per-feature riskiest-unknown.}
