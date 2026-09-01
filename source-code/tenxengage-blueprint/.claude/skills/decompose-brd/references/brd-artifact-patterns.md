# BRD Artifact Patterns

> Reference guide used by `/decompose-brd` step 04 (probe-extract). All extraction works on any BRD regardless of section numbering or structure.
>
> **These are illustrative pattern examples for grounding, not closed matching rules.** The patterns below show *typical shapes* of BRD content; the LLM applies semantic judgment to identify content of each kind, including shapes not enumerated here. When in doubt, classify by routing principle (business-truth → `digest.md`, technical-truth → `digest-annex.md`) and route accordingly. **Never restrict extraction to only the patterns listed below — they are a floor, not a ceiling.**
>
> This semantic-intent-over-pattern-matching rule applies across the skill: the requirement inventory in step 04, gap detection in step 04c, FR quality validation in step 06, and any other content extraction. Patterns are appropriate only for syntactic structure (ID formats, file paths, CSV columns), never for semantic content.

## numeric-sla-patterns → digest.md §Concrete SLAs / numeric guarantees

| Pattern | Category |
|---|---|
| `"within N {seconds\|minutes\|hours\|days}"` | SLA |
| `"≤ N {unit}"` / `"no more than N"` / `"max N"` | Cap |
| `"≥ N"` / `"at least N"` / `"minimum N"` | Floor |
| `"{N}%"` near "weight", "share", "component" | Percentage |
| `"lead time"` / `"timeframe"` / `"window"` + numeric | Time-window |

Capture surrounding sentence as context.

## vocabulary-patterns → digest-annex.md §Event vocabulary / §Entity inventory / §API surface

- **Named events**: snake_case strings near "event", "publish", "emit", "consumes", "webhook" — e.g., `cert_expired`, `quiz_failed`, `deal_stage_changed`
- **Named entities**: any of the following shapes when appearing in a data-model / schema / table context (near "entity", "data model", "table", "schema", "primary key", "FK", "record"):
  - `CamelCase nouns` — e.g., `CertificationProgram`, `ReadinessGap`
  - `lowercase_snake_case identifiers` — e.g., `deals`, `participants`, `incentive_mapping`, `audit_log`
  - `UPPER_SNAKE_CASE enum values` listed as ENUM-typed field values — e.g., `OEM | Marketplace | SI_led | MSP | Advisor_TSD`
- **Named API ops**: any of the following shapes when appearing near "API", "endpoint", "operation", "webhook":
  - function-shaped in-process calls — e.g., `getUserTrainingState(...)`, `getReadinessByDimension(...)`
  - REST verb + path — e.g., `POST /api/v1/deals`, `PATCH /api/v1/deals/{id}/stage`, `GET /api/v1/deals/{id}/attribution`
  - webhook / event names with payload shape — e.g., `webhook: training_complete`

Group by BRD category/heading where possible.

## decision-table-patterns → digest.md §Mission-critical decision tables

- **Load-bearing** (preserve verbatim): cells consumed directly by slice logic — decision tables, stage×role mappings, priority matrices. Rule: if removing the table forces spec author back to the source, it's load-bearing. RBAC matrices, error contracts, KPI tables, and risk registers have dedicated sections — exclude them here.
- **Illustrative** (summarize + anchor only): supplementary examples where the prose suffices.

## term-of-art-patterns → digest.md §Undefined terms-of-art

- Quoted or capitalized terms in escalation/eligibility/priority logic without an explicit definition — e.g., `"high-value deal"`, `"active pipeline"`
- Terms appearing in 3+ places without a definitional anchor

Flag each as a candidate ADR; attempt to resolve during step-04 probing.

## reliability-ux-patterns → digest.md §Reliability & UX guarantees

- `"must be guaranteed"`, `"no missed"`, `"exactly-once"`, `"at-least-once"`
- `"preview before activation"`, `"with explanation"`, `"self-serve"`
- `"WCAG"`, `"accessible"`, any compliance-shaped term

Keep only items that are user-facing or business-facing here. Technical reliability claims (idempotency, retry policy, durability semantics) route to `digest-annex.md` instead.

## non-goal-patterns → digest.md §Non-goals (v1)

Phrases: `"out of scope"`, `"non-goals"`, `"we will not"`, `"not in v1"`, `"deferred to Phase 2/3"`, `"future consideration"`, `"explicitly excluded"` — and list items under headings containing those phrases.

These are **scoping** statements, separate from "Does NOT own" (cross-quadrant ownership).

## acceptance-criteria-patterns → digest.md §v1 Acceptance criteria

Phrases: `"acceptance criteria"`, `"the system shall"`, `"must"`, `"is ready when"` — and bulleted lists immediately following them, including per-capability and overall v1-launch acceptance lists.

Preserve verbatim or near-verbatim — these are the team's "done" criteria.

## companion-doc-patterns → digest.md §Companion documents referenced

- Document names with version markers — e.g., `"Agent Design Principles v1.0"`, `"Engagement BRD v2.0"`
- Phrases: `"see also"`, `"reference docs"`, `"companion documents"`, `"as defined in"`
- Top-matter or footer sections listing related artifacts

Check whether each doc exists in the repo; flag `⚠️ not found in repo` if missing.

## current-state-patterns → digest.md §Current-state foundation

Phrases: `"the existing platform"`, `"current state"`, `"preserves and extends"`, `"already includes"`, `"today the system"` — and lists of capabilities described as existing (not proposed).

Preserve BRD's prose **verbatim**. Grounding: each slice must decide extension vs greenfield accordingly.

## rbac-matrix-patterns → digest-annex.md §RBAC permission matrix

Detect by structural shape:
- A table with **role columns × permission rows** (or transposed: permission columns × role rows) containing `Yes` / `No` / scoped values (`Own deals`, `Invited only`, `Limited`, `Earnings only`, etc.)
- A `{role | action | scope}` row-shape table under headings containing `"RBAC"`, `"Permissions"`, `"Access Control"`, `"Permission Matrix"`

Preserve verbatim — RBAC enforcement is acceptance-critical and load-bearing for spec authors. Many BRDs lack this entirely; section is omitted when no hits.

## error-contract-patterns → digest-annex.md §Error contract

Detect by either:
- Tables shaped `{error_code | condition | response}` or `{error_code | condition | system response}`
- `UPPER_SNAKE_CASE` error tokens with suffixes like `*_INVALID`, `*_CONFLICT`, `*_NOT_FOUND`, `*_TOO_LOW`, `*_INCOMPLETE` — co-located with HTTP status codes (`HTTP 422`, `HTTP 404`) or behavioral descriptions (`reject update`, `fall back to X`, `notify Y`)

Preserve verbatim — these become acceptance-critical API contracts.

## kpi-patterns → digest.md §KPIs / Success metrics

Detect by structural shape:
- Tables shaped `{metric | definition | target | data source}` or `{KPI | definition | target}` with explicit numeric targets bound to named business outcomes
- Sections under headings containing `"KPIs"`, `"Success Metrics"`, `"Metrics & Measurement"`

Distinct from goals/vision (which are qualitative). KPIs have **numeric targets with measurement plans** — e.g., `"> 40% within 6 months"`, `"P95 < 500ms"`, `"+15% by Month 6"`. Many BRDs list goals only without KPIs; section is omitted when no measurement-plan hits.

## risk-register-patterns → digest.md §Risks register

Detect by structural shape:
- Tables shaped `{risk | mitigation}` (with optional `owner`, `severity`, `phase` columns) under headings containing `"Risks"`, `"Mitigations"`, `"Risk Register"`

Preserve verbatim. Distinct from per-slice "Riskiest unknown" in the roadmap — those are tactical per-feature risks; the digest §Risks register is BRD-level strategic risk inventory.

## requirement-inventory-patterns → conversation context (internal scaffolding)

The requirement inventory introduced in step 04 captures every business commitment in the BRD as REQ-NNN items. **Extraction is semantic, not pattern-bound** — the LLM reads the BRD with judgment and captures any statement that commits the system to a capability, behavior, or measurable outcome.

Typical shapes (illustrative — not exhaustive, not a closed match list):

- Bulleted capability lists ("Admin can…", "System publishes…", "Learner receives…")
- Acceptance criteria bullets under any heading containing "Acceptance", "Criteria", "Definition of Done", "Promise to users", or similar
- User stories in any form ("As a {role}, I want…", "{Persona} needs to…", or descriptive prose)
- KPI commitments with numeric targets
- Reliability / UX guarantees that commit to user-observable behavior
- Inline prose statements that are commitments rather than context (e.g., a paragraph that says "the system must guarantee X" without a bullet structure)

**Do not restrict to the shapes above.** If the BRD expresses a commitment in another shape, capture it. Err toward inclusion when ambiguous — over-inventory is recoverable; under-inventory creates silent coverage holes.

Each item: REQ-NNN local ID + verbatim text + BRD anchor label (heading text) + semantic class (capability statement, observable behavior promise, user story, KPI commitment, etc.).

The inventory is internal scaffolding, not part of `digest.md` or `digest-annex.md`. It feeds FR source tagging in step 05 and coverage validation in step 06.