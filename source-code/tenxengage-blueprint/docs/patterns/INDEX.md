# Pattern registry

Topic-keyed library of feature-shape patterns for TenXEngage. Skills (create-spec, etc.) load this index always; deep-read individual pattern files on-demand based on the gate signal.

## How to use

1. **Skills:** evaluate each row's "Gate" against the current feature requirements + project context. Build a "shape manifest" of matching patterns. Read the pattern file(s) only when you reach the consuming step.
2. **Adding a new pattern:** create the file in this directory, register it here. Zero skill changes needed.
3. **Pattern file shape:** every file follows the standard 5-section structure (When this applies / Spec authoring guidance / Implementation guidance / Examples in codebase / Common gotchas).

## Domain registry

Pattern files describe feature-shape conventions. For **builder-shaped features**, the [domain registry](domains/INDEX.md) is the structural authority (slot fillers, primitive names, parallel-rails strategy). Read it alongside this index when the feature is slot-filling.

| Registry | File | Gate | Consumed by step |
| --- | --- | --- | --- |
| domain-registry | domains/INDEX.md | ALWAYS for slot-filling features | create-spec step 04 |

## Registry

| Pattern | File | Gate (when this applies) | Consumed by step |
| --- | --- | --- | --- |
| permissions-and-feature-flags | permissions-and-feature-flags.md | ALWAYS | create-spec step 11 |
| package-structure | package-structure.md | ALWAYS | create-spec steps 13, 14 |
| new-entities | new-entities.md | Feature introduces new DB entities | create-spec step 13 |
| soft-delete | soft-delete.md | Feature introduces entities that use logical (soft) deletion | create-spec step 13 |
| managed-data | managed-data.md | Feature operates on a configurable data object (tenant-editable Managed Data) | create-spec step 13 |
| location-hierarchy | location-hierarchy.md | Feature filters/scopes by location (geographic/organizational tree) | create-spec step 13 |
| tenant-isolation | tenant-isolation.md | Feature introduces new DB entities | create-spec step 13 |
| lms-integration-modes | lms-integration-modes.md | Feature defines or consumes enablement catalog entities (Course, LearningPath, Certification) or activity signals (enrollment, completion, progress, score) | create-spec step 13 |
| enablement-legacy-quarantine | enablement-legacy-quarantine.md | Feature is part of the enablement module (introduces or consumes Course / Lesson / Assessment / LearningPath / Certification entities or enablement activity signals) | create-spec step 13 |
| builder-wizard | builder-wizard.md | Feature has multi-step UI for create/edit | create-spec step 13 |
| builder-widget-platform | ../../tenxengage-frontend/docs/patterns/builder-widget-platform.md | Feature is a builder backed by the platform builder widget (SetupHeader step-progress widget, ACTION_SECTIONS pattern, mode toggle pill) | create-spec step 13 |
| builder-config | builder-config.md | Feature uses dynamic builder configuration | create-spec step 13 |
| audience-rules | audience-rules.md | Feature introduces an owning entity that gates access by tenant-defined predicates | create-spec step 13 |
| tagging | tagging.md | Feature introduces or operates on an entity that benefits from content-intrinsic matching (cross-module sidecar) | create-spec step 13 |
| ai-copilot | ai-copilot.md | Feature integrates AI assistance | create-spec step 13 |
| html-content | html-content.md | Feature stores user-generated HTML / rich-text | create-spec step 13 |
| sse-streaming | sse-streaming.md | Feature uses Server-Sent Events | create-spec step 13 |
| currency-handling | currency-handling.md | Feature involves money / pricing | create-spec step 13 |
| lifecycle-dates | lifecycle-dates.md | Enablement entity has `effectiveAt`/`expiryAt` dates and `SCHEDULED` status | create-spec step 13 |
| enablement-rewards | enablement-rewards.md | Enablement entity awards completion rewards (normalized currency rows, no budget cap) | create-spec step 13 |
| enablement-approval-flow | enablement-approval-flow.md | Enablement entity has multi-approver publish gate with PENDING_APPROVAL/DENIED flow | create-spec step 13 |
| webhook-security | webhook-security.md | Feature introduces an inbound webhook endpoint secured by HMAC-SHA256 (no JWT) | create-spec step 08 |
| rate-limit-sensitive | rate-limit-sensitive.md | Feature has expensive or abuse-prone endpoints | create-spec step 08 |
| event-publishing | event-publishing.md | Feature publishes Kafka events | create-spec step 09 |
| event-consuming | event-consuming.md | Feature consumes Kafka events | create-spec step 09 |
| assessment-authoring | assessment-authoring.md | Feature creates/scores assessment questions (inline, EOC, cert exam) | create-spec step 13 |
| e2e-testing | e2e-testing.md | ALWAYS for features with Playwright real-backend specs (T1) | execute-integration-tests |
| entity-list-page | entity-list-page.md | Feature introduces a list/grid page for a new entity type | create-spec step 13 |
| dialogs-and-modals | dialogs-and-modals.md | Feature introduces confirmation dialogs, form modals, or side sheets | create-spec step 13 |
| data-states | data-states.md | Feature introduces data-fetching components (loading, empty, error states) | create-spec step 13 |
| save-flow | save-flow.md | Feature introduces a form or editor with a save/submit action | create-spec step 13 |
| status-and-badges | status-and-badges.md | Feature introduces entities with a status lifecycle or categorical labels | create-spec step 13 |
| list-and-table-actions | list-and-table-actions.md | Feature introduces per-row actions on a list or table page | create-spec step 13 |
| form-sections-and-fields | form-sections-and-fields.md | Feature introduces a settings form or multi-field configuration panel | create-spec step 13 |
| tabs-and-navigation | tabs-and-navigation.md | Feature introduces tab navigation within a page or back-button patterns | create-spec step 13 |

**Loading vs consumption:** All matching pattern files are loaded once in `create-spec` step 06 (`load-shape-references`). The "Consumed by step" column identifies which downstream step actually applies the pattern's rules. Each pattern enters context exactly once, then is referenced from multiple downstream steps as needed.

Other skills add their own consumer columns as they're refactored to follow the consumption rule.
