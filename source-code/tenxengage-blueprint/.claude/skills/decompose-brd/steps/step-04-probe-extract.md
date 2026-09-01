# Step 04: probe-extract

**Goal:** Single pass over the BRD. While reading each section, detect maturity (concrete vs vague), probe vague topics one question at a time, AND simultaneously classify all important BRD content by routing principle.

**Inputs:** Project standards loaded by step 03; locked BRD understanding from step 01; locked persona/partner-type decisions from step 02.

> **Step 04 — Detect what's concrete, probe what's vague, classify what's there.** A single BRD pass. No two-pass approach.

## Routing principle (the rule for everything)

- **Business-truth → `digest.md`** (verbatim where present): vision, personas, business rules, decision tables, KPIs, non-goals, current-state prose, reliability/UX guarantees in business language, v1 acceptance criteria.
- **Technical-truth → `digest-annex.md`** (advisory only, labeled): named entities, named events, API ops, RBAC matrices, error codes, technical reliability claims (idempotency, retry, durability).

This routing principle is the rule. Use [../references/brd-artifact-patterns.md](../references/brd-artifact-patterns.md) as a reference guide for common content shapes — but classify any content that doesn't match a named pattern by the routing principle alone.

## Maturity heuristics (probe only the vague)

| Topic | Concrete signal | Vague signal → probe |
|---|---|---|
| **Endpoints** | Endpoint table with verb + path + auth + body shape | "API surface" mentioned generically → ask: "Are there endpoint specs elsewhere, or should I propose them at slicing time?" |
| **RBAC** (if BRD enumerates a permission matrix) | Full role × permission matrix with explicit Y/— per role | Mentioned but no matrix → ask: "Per capability, who can do what? Which roles get view-only vs edit?" |
| **Agent contracts** (if AI agents present) | Trigger → decision logic → autonomous action → escalation per agent | Mentioned as "AI engine" or single component → ask: "Is this one agent or multiple? What triggers it? What autonomous action does it take?" |
| **KPIs / success metrics** (if BRD has a metrics table with numeric targets) | Number + target + data source per KPI | Listed as goals only → ask: "Per goal, what's the success metric numerically and by when?" |
| **Error handling** | Specific error codes + HTTP status | Generic mentions → ask: "Which error conditions are acceptance-critical? Specific codes?" |
| **ADRs / open decisions** | Explicit table with timing + owner + what they block | Implicit "X or Y" inline → ask: "Is this blocking? Who decides? By when? Which features are blocked?" |
| **UX journeys** ⚠ ALWAYS PROBE | — | Per slice you propose: "What's the entry point? Core action? Exit? Where does this sit in IA — top-level menu, nested, modal?" |
| **Persona-to-feature mapping** ⚠ ALWAYS PROBE | — | Per slice you propose: "Who is the primary persona of record for this slice? Secondary personas?" |
| **Workflow vs tool framing** ⚠ ALWAYS PROBE | — | Per slice: "Multi-step workflow with state machine, or one-shot tool/panel?" |

## Topics off-limits for probing (covered by step 03)

Skip entirely:
- Vision, business goals, why-this-matters (BRD has these)
- Persona inventory at module level (BRD has this — your job is to map them per slice)
- NFRs of any kind — response time, P50/P95, throughput, concurrency, page load, write latency. Never ask for numbers; never ask for qualitative targets either. Use platform defaults inherited from `PROJECT-CONTEXT.md`. If the BRD states numeric SLAs, the `numeric-sla-patterns` rule captures them verbatim — that's the only path NFR numbers enter the digest.
- Tenant isolation, permission model, audit, OWASP basics
- Modular monolith / Spring Boot / React stack questions

## How to probe

- **Ask one question at a time.** No question dumps. Prefer multiple-choice where possible. Move on as soon as you have enough.
- **Stop probing per proposed slice when you have:** a primary persona of record, a 1-line journey sketch (entry → core action → exit), a workflow-vs-tool decision, IA placement (or "TBD with FE").
- **For ADRs:** confirm timing, owner, blocked features.
- **For weak endpoint/RBAC sections:** enough granularity to identify slice boundaries (you don't need full API specs — `/create-spec` will do that).

## Classification while reading

For every section / paragraph / table you encounter, decide:
1. Is this **business-truth** (commitments to users, business rules, scope, KPIs, acceptance) → route to `digest.md`. Use the matching pattern in `brd-artifact-patterns.md` for the destination section name.
2. Is this **technical-truth** (BRD-stated names: events, entities, API ops, RBAC, error codes, technical reliability) → route to `digest-annex.md`, labeled advisory.
3. Is this neither (transition prose, restated platform standards, etc.) → discard.

Keep classification tags inline as you read so step 06 (validation) can verify completeness.

> Skip any digest section when the BRD has zero hits — never generate empty sections in `digest.md`.
> `digest-annex.md` is **always** produced. If the BRD has no technical-truth content, emit it as a stub with a "No BRD-stated technical artifacts found" note. Downstream skills (`/create-spec` Phase 0a.1) need a predictable path.

## Requirement inventory (for downstream coverage validation)

While reading the BRD, also produce a flat **requirement inventory** — every business commitment the future specs must honor. This inventory is internal scaffolding; it is not shown to the user during normal flow. It feeds the FR source tagging in step 05 and the coverage check in step 06.

**Format: see [../references/req-inventory-format.md](../references/req-inventory-format.md).** Each item is one line, `||`-delimited, five positional fields. Field 5 (`origin or gap-ref`) is left empty for BRD-verbatim items extracted in this step. Step 04c may append items with `gap:{slug}` populated.

**Extraction approach: semantic, not syntactic.** Capture every statement in the BRD that commits the system to a capability, behavior, or measurable outcome. These can appear as bulleted lists, prose paragraphs, table rows, definition-of-done blocks, inline sentences, or any other shape. Don't restrict to specific phrasings or section names; read with judgment and capture anything a future spec would need to honor.

Err toward inclusion when ambiguous — over-inventory is recoverable (the user can mark inventory items as "remove from inventory if not actually a requirement" during step 06 coverage prompts); under-inventory creates silent coverage holes.

If a verbatim requirement contains the literal sequence `||`, replace it with `/` in the inventory text and note the substitution in conversation context (per the format reference's collision policy).

The inventory is held in conversation context for steps 04c, 05, and 06; it is pinned to the plan file by step 07.

## Rules in scope for this step

- **Detect-and-probe, not fixed script** — scan section maturity; ask only on vague topics; always probe UX journeys + persona-of-record + workflow-vs-tool per slice.
- **Business truth verbatim, technical truth advisory** — preserve verbatim where the BRD has business commitments; route BRD-stated names to the annex with advisory framing.
- **Anchor labels, not section numbers** — reference BRD topics by heading text only.
- **Digest-annex always emitted** — stub mode when BRD has no technical-truth artifacts.
- **Shape-agnostic detection** — accept multiple identifier shapes (CamelCase, snake_case, UPPER_SNAKE_CASE) and multiple API styles (in-process function, REST path, webhook).
- **Requirement inventory is mandatory** — produce a flat REQ-NNN inventory of every business commitment in the BRD using semantic judgment, not pattern matching. The inventory is internal scaffolding and feeds step 05 (FR source tagging) and step 06 (coverage check).

## Routing

All probing complete and all artifacts classified → load `steps/step-04c-completeness-audit.md`.