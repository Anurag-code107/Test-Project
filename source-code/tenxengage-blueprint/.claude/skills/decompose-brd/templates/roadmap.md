# Feature Roadmap: {brd-name}

> **Slug**: `{slug}` · **BRD source**: `{path}`
> **Digest**: [digest.md](digest.md) · **Generated**: {date} via `/decompose-brd`
>
> Per-feature briefs live in [features/](features/). PM-friendly export at [backlog-seeds.csv](backlog-seeds.csv).
>
> This roadmap is a thin index. Deep-dive content (FRs, business rules, story seeds) is in each feature brief.

## At a glance

| F-NN | Name | Slug | Persona | Phase | Blockers | Brief |
|---|---|---|---|---|---|---|
| F-01 | {name} | `{slug}` | {persona} | 1 | — | [Brief](features/F-01-{slug}.md) |
| F-02 | {name} | `{slug}` | {persona} | 1 | F-01 | [Brief](features/F-02-{slug}.md) |
| ... | ... | ... | ... | ... | ... | ... |

## Recommended start: F-NN

{One feature with reasoning — usually a foundation entity or the riskiest-unknown de-risking slice. Explain why this one first.}

## Strategic notes

{3–7 bullets from challenge pass:
- Hidden assumptions to confirm
- Scope creep candidates (propose deferral)
- Riskiest unknown overall
- Missing requirements gaps
- Alternatives proposed
- Phase deviations from BRD — e.g., "BRD §18 places X in Phase 1; we recommend Phase 2 because F-X depends on F-Y which is itself a greenfield foundation"
- ⚠️ EXPLICIT DEFERRAL FROM BRD v1 ACCEPTANCE: {capability} (source: "{BRD anchor label}") — {reason}
}

## Open ADRs (blocking)

{Mirror from digest — shown here because roadmap is the coordination artifact.}

| ADR | Decision | Owner | By when | Blocks |
|---|---|---|---|---|
| ADR-NN | {decision} | {owner} | {timing} | {F-NN} |

## Phase 1 features

- **F-01** {name} — {one-line scope}. → [Brief](features/F-01-{slug}.md) · `/create-spec {slug} F-01`
- **F-02** {name} — {one-line scope}. → [Brief](features/F-02-{slug}.md) · `/create-spec {slug} F-02`

## Phase 2 features

- **F-NN** {name} — {one-line scope}. → [Brief](features/F-NN-{slug}.md) · `/create-spec {slug} F-NN`

## Phase 3 features (deferred)

- **F-NN** {name} — {one-line scope; why deferred}. → [Brief](features/F-NN-{slug}.md)
