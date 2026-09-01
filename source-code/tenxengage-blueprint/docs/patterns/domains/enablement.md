---
domain: enablement
status: active
anchored-on: unified-builder-config
authored: 2026-05-25
authored-by: features/course-authoring (F-02 of partner-revenue-readiness)
---

# Enablement Domain

The enablement domain uses the unified `BuilderSectionConfig` / `BuilderFieldConfig` stack — see [../builder-config.md](../builder-config.md). Authored by `/create-spec F-02` for `features/course-authoring/` — the first builder-shaped enablement feature. F-01 (assessment-authoring) is foundation-shaped and does not introduce builder infrastructure; its slot fillers are recorded as sub-entities of F-02's filler set.

Feature-shape patterns that govern enablement features:

- [`lms-integration-modes.md`](../lms-integration-modes.md) — `in_house` vs `external` mode on every catalog-bearing entity.
- [`enablement-legacy-quarantine.md`](../enablement-legacy-quarantine.md) — quarantines legacy `LmsCourse` / `TrainingCourseAssignment` / etc.
- [`tagging.md`](../tagging.md) — F-01 owns the platform tagging sidecar; new entity types add a `TaggableEntityType` value + ship a `TaggableEntityResolver`.

## How to read this file

**This is a GENERIC enablement-domain pattern, not a course spec.** Enablement is a *family* of builder-shaped modules. Today it has exactly one concrete module — **COURSE** (F-02) — so historically this file read as if `enablement == course`. It is now structured in two layers:

- **Universal layer** — sections and conventions shared by *every* enablement module: `basics`, `dates`, `audience`, `tags`, `rewards`, `approval_flow`, `publish`. A new module inherits these.
- **Module-specific layer** — sections a single module declares for itself. For COURSE these are `lessons` and `eoc_quiz` (plus an internal completion-config section). A Learning Path or Certification Program would *not* have these and would declare its own.

**The universal/module-specific split is a DESIGN CONTRACT, not a code-enforced row separation.** There is no shared "baseline" row-set in the database: each `builder_type` seeds its *own full set* of `builder_section_configs` rows, and the unified `/builder-config/{type}` endpoint today serves **COURSE only**. "Reuse the universal sections" therefore means *re-seed the same `section_keys` and reuse/generalize the (currently course-located) section components* — see [Adding a new enablement module](#adding-a-new-enablement-module). It is not a literal drop-in.

> **For Claude Code / skill authors:** when speccing a *non-course* enablement module, do **not** assume the course section set. Take the **universal sections** plus **only that module's declared module-specific sections**. Pick sections dynamically from the [Section model](#section-model--universal-vs-module-specific) below, not from COURSE's concrete ordering.

## Slot fillers (8 canonical slots + 2 enablement supplementary)

Slots are **domain-level abstractions** (always generic); the *fillers* shown below are COURSE's — the only module today. A future module supplies its own fillers for the per-owner slots (core aggregate, audience-rule entity, completion entity, approvers, rewards) while sharing the builder-config infrastructure slots.

Resolution order: per-`builder_type` override file (e.g., `enablement/course.md`) > this file > shared builder-config defaults (see [../builder-config.md](../builder-config.md)). No override file exists yet — COURSE is documented inline below.

| Slot | Filler | Location |
| --- | --- | --- |
| Core aggregate | `Course` (F-02), `LearningPath` (F-03), `CertificationProgram` (F-05, future) — **every core aggregate MUST `extends Enablement`** (the shared base entity); see note below | `entity/enablement/Enablement.java` (base) → `entity/course/Course.java`, `entity/learningpath/LearningPath.java` |
| Audience-rule entity | `CourseAudienceRule` (per-owner; future learning-path owners get their own). See [../audience-rules.md](../audience-rules.md). | `entity/course/CourseAudienceRule.java` |
| Eligibility engine contract | `AudienceRuleEvaluator` + `SubjectFacetResolver<E>` | `service/course/AudienceRuleEvaluator.java`, `platform/builder/SubjectFacetResolver.java` |
| Completion/participation entity | `AssessmentAttempt` (F-01). Course completion *rules* live in `CourseCompletionConfig` (backs the completion-config section). Completion *events* — `LessonCompletion` / `CourseCompletion` / `PathCompletion` — are F-04 (Learner Experience, future). | F-01 + F-04; `entity/course/CourseCompletionConfig.java` |
| Budget model | not applicable | — |
| Approval workflow entity | `CourseApprover` + `CourseApprovalDecision` (Course, F-02 v2). Pattern: [enablement-approval-flow.md](../enablement-approval-flow.md) | `entity/course/CourseApprover.java`, `CourseApprovalDecision.java` |
| Lifecycle dates | `effectiveAt` / `expiryAt` on Course + `SCHEDULED` status. Pattern: [lifecycle-dates.md](../lifecycle-dates.md) | `entity/course/Course.java` |
| Rewards | `CourseReward` (Course, F-02 v2). Pattern: [enablement-rewards.md](../enablement-rewards.md) | `entity/course/CourseReward.java` |
| Builder discriminator | `BuilderSectionConfig.builder_type = COURSE` + `builder_domain = ENABLEMENT` | on `BuilderSectionConfig` |
| Section/field storage entity | `BuilderSectionConfig` / `BuilderFieldConfig` (shared with incentive; distinguished by `incentive_type` vs `builder_type+builder_domain`) | `entity/Builder*Config.java` |

(8 canonical slots per [`INDEX.md`](INDEX.md) + 2 enablement supplementary: **Lifecycle dates** and **Rewards**.)

## Module conventions (apply to every enablement module)

These are cross-cutting rules every enablement module obeys — they are not slot fillers, they are the shape of the family. A new module that diverges from any of these must flag it interactively (drift event), not silently adopt a different shape.

### 1. Core aggregate extends `Enablement`

Every module's core aggregate **extends the shared `Enablement` base entity** (`entity/enablement/Enablement.java`) — exactly as `Course extends Enablement` and `LearningPath extends Enablement`. The base carries the fields and lifecycle common to all catalog-bearing enablement entities (status, lifecycle dates, audit/tenant scaffolding via `BaseEntity`/`TenantAware`, etc.). A future module (`CertificationProgram`) does the same. **Do not** fork an independent entity that re-declares these fields; extend the base so lifecycle, audience, rewards, approval, and builder-config wiring stay uniform.

### 2. Terminal save with a unified `Save{Entity}Request`

Enablement builders use **terminal save**, not incremental save. Each module ships **one** `Save{Entity}Request` DTO that carries the full builder state (basics, dates, the module's composition/steps, milestones, rewards, audience rules, …) and **binds it to BOTH the POST (create) and PUT (update) endpoints**. The service replaces/reconciles nested collections server-side.

- Reference: `SaveLearningPathRequest` (bound to `POST /learning-paths` + `PUT /learning-paths/{id}`); `SaveCourseRequest` (same shape for course).
- On create, `version` is `null` and `status`/`authorUserId` are derived server-side from the JWT (mass-assignment guard); on update `version` is the optimistic-lock counter.
- Conventions (null-section semantics, `@JsonAlias` on renamed fields, currency code-or-UUID resolution) live in [../save-flow.md](../save-flow.md) § "Unified `Save{Entity}Request` conventions".
- **Do not** introduce per-section PATCH endpoints or an incremental create-then-augment flow for a new module. (The course builder's incremental FE is a legacy path migrating to terminal save behind `course_builder_terminal_save`; do not copy it.)

### 3. Edit-mode step-completion is derived from builder config

When an existing entity is reopened in the builder, the step-progress widget reflects the **true** completion state of the loaded data, derived from the builder config: sections with ≥1 `is_mandatory` field are validated against the loaded entity; sections with no mandatory fields (or no config rows at all, e.g. `tags`) default to optional/complete; system-managed module-specific sections decide explicitly (e.g. `composition` requires ≥1 step). Reference: `deriveSectionCompletion` in `LearningPathBuilderLayout.tsx`. See [../builder-wizard.md](../builder-wizard.md) § "Step completion tracking" and the frontend `builder-widget-platform.md` § "Edit-mode completion derivation".

## Shared sub-entities

| Sub-entity | First introduced by | Used by |
| --- | --- | --- |
| `Assessment` (parent, with `type` discriminator) | F-01 | F-02 (lesson + course bindings), F-05 (cert exam binding) |
| `Question`, `QuestionBank`, `AssessmentAttempt`, `Tag`/`TagNamespace`/`EntityTag` | F-01 | F-02, F-05, F-07, F-08, F-10 |
| `Lesson`, `ContentAsset`, `ContentVersion` | F-02 | F-04 (learner) |
| `LessonInlineQuiz` (binding), `CourseEndOfCourseQuiz` (binding) | F-02 | F-04 (surfaces bound assessments to the learner) |
| `CourseAudienceRule` (per-course audience predicate rows) | F-02 | F-03 (learning paths), F-05 (cert programs), F-04 (eligibility evaluation) |
| `CourseCompletionConfig` (completion rules), `EnablementCategory` (catalog categorization) | F-02 | hydrated on `CourseDetailResponse` (`completionConfig`, `enablementCategory`) |

## Section model — universal vs module-specific

An enablement module's wizard is composed of **universal sections** (inherited) plus its own **module-specific sections** (declared). Both are stored as `builder_section_configs` rows discriminated by `builder_type + builder_domain`, *except* internal FE-injected sections (noted below) which have no config row.

### Universal sections (every enablement module)

| section_key | `display_name` | `is_locked` | Purpose | Generic FE component | Notes / pattern |
| --- | --- | --- | --- | --- | --- |
| basics | "Basics" | **TRUE** | Title, description, objectives, difficulty, language | `BasicsSection` | System-managed; no configurable fields added by admins |
| dates | **"Availability"** | FALSE | `effectiveAt` / `expiryAt` lifecycle window | `DatesSection` | Display name is always "Availability" — never "Dates". See [lifecycle-dates.md](../lifecycle-dates.md) |
| audience | "Audience" | FALSE | Tenant-defined targeting predicates | `AudienceSection` | [audience-rules.md](../audience-rules.md); per-owner rule table |
| tags | "Tags" | **TRUE** | Content-intrinsic tagging sidecar | `TagsSection` (wraps `TagsSectionWidget` from `@/platform/tagging`) | System-managed; no `builder_field_config` rows — renders `EntityTagPicker` widget directly. See [tagging.md](../tagging.md) |
| rewards | "Rewards" | FALSE | Completion currency rewards | `RewardsSection` | [enablement-rewards.md](../enablement-rewards.md) |
| approval_flow | "Approval flow" | FALSE | Multi-approver publish gate | `ApprovalFlowSection` | [enablement-approval-flow.md](../enablement-approval-flow.md) |
| publish | "Publish" | **TRUE** | Status + submit/publish CTA | `PublishSection` | System-managed; terminal action; excluded from the `ACTION_SECTIONS` step-numbering whitelist |

> **Seed-time requirement — `is_locked` and `info_message` are NOT optional.**
> Every new module's Flyway seed migration MUST:
> 1. Set `is_locked = TRUE` and a module-specific `info_message` for every section marked **TRUE** above (`basics`, `tags`, `publish`) and for each module-specific section that is system-managed (e.g. `composition` for LEARNING_PATH — see module-specific table below).
> 2. Seed `display_name = 'Availability'` (not "Dates") for the `dates` section.
>
> The template for `info_message` text follows the pattern: *"[Section content description] are managed via the [Module Name] Builder. [Field] configuration is not available here."* — see `V32`, `V44`, `V61` migrations for exact wording used by COURSE and LEARNING_PATH.
>
> **Root cause note (2026-06-02):** `V57__seed_learning_path_builder_config.sql` shipped without these flags because `technical.md` omitted them from the V57 spec description. This table is the authoritative source; `technical.md` V57 descriptions and any future `foundation.md` F2 equivalents must copy the `is_locked` and `display_name` values from here.

As of the enablement-builder generalization, the universal section components physically live in the **shell** at `frontend/src/components/enablement-builder/steps/` (`BasicsSection`, `DatesSection`, `AudienceSection`, `TagsSection`, `RewardsSection`, `ApprovalFlowSection`) and are **reused as-is** by any module — they read universal state via `useEnablementBuilder()` and AI/runtime bits (copilot lock, AI-drafted chips, validation errors, tag samples) via `useEnablementBuilderRuntime()`, which the module's layout populates. A new module does NOT copy them. (`TagsSection` also relies on the platform-level `TagsSectionWidget` with an `entityType`; `AudienceSection` still uses `useCourseAudienceFields`, a platform hook whose course-flavored name is a deferred cleanup.) See [Adding a new enablement module](#adding-a-new-enablement-module).

### Module-specific sections

| Module (`builder_type`) | section_key | `is_locked` | Purpose | FE component | Backed by config row? |
| --- | --- | --- | --- | --- | --- |
| COURSE | lessons | **TRUE** | Curriculum: ordered lessons, content assets, inline quizzes | `LessonsSection` | yes |
| COURSE | eoc_quiz | **TRUE** | End-of-course quiz binding | `EocQuizSection` | yes |
| COURSE | _(completion config)_ | n/a | Completion rules (lesson rule, min count, EOC required) | `CourseCompletionSection` | **no** — FE-injected after `eoc_quiz`, not a `builder_section_configs` row; backed by `CourseCompletionConfig` |
| LEARNING_PATH | composition | **TRUE** | Path composition: ordered `LearningPathStep`s (courses/assessments/`PathMilestone`s), required/optional, prerequisites, AI-suggested milestone placement | `CompositionSection` (full-panel embedded editor — frame-drawer slide, like the course `eoc_quiz` step) | yes (sort_order 30) |

`LearningPath` declares the `composition` module-specific section (F-03, `features/paths-and-assignments/`); it reuses the universal sections (`basics`/`dates`/`audience`/`tags`/`rewards`/`approval_flow`/`publish`) plus baseline slot fillers — its per-owner fillers are `LearningPathAudienceRule` + `AssignmentAudienceRule` (audience), `Enrollment` (participation), `LearningPathApprover`/`LearningPathApprovalDecision` (approval), `LearningPathReward`+`MilestoneReward` (rewards), `builder_type=LEARNING_PATH`. No override file needed (baseline-reuse + added section). `CertificationProgram` has **no module-specific sections defined yet** (future F-05).

### Course wizard — concrete ordering (the only module today)

Section keys and `sort_order` in `builder_section_configs` for `builder_type = COURSE`, `builder_domain = ENABLEMENT`. Current canonical order set by V48:

| sort_order | section_key | Layer | Provenance |
| --- | --- | --- | --- |
| 10 | basics | universal | V27 (legacy) → renumbered V41 |
| 20 | dates | universal | V41 (new) |
| 25 | audience | universal | V27 → renumbered V41 (40) → **moved to 25 in V48** |
| 30 | lessons | **course-specific** | V27 → renumbered V41 |
| 50 | eoc_quiz | **course-specific** | V27 → renumbered V41 |
| 52 | tags | universal | V43 (new) |
| 55 | rewards | universal | V41 (new) |
| 60 | approval_flow | universal | V41 (new) |
| 80 | publish | universal | V27 → renumbered V41 |

`metadata` (sort_order=35, V27) was soft-deleted in V43. `preview` (sort_order=70, V27) was hard-deleted in V44 — no trace in the table for existing or future tenants. `tags` was inserted unlocked in V43, then locked + `info_message` applied in V44 (it has no `builder_field_config` rows — it renders the `EntityTagPicker` widget directly).

**Renumbering rule:** multiples-of-10, with 5-step gaps reserved for insertions. V41 established the ×10 baseline (renumber + insert pattern); V48 used the `25` gap to reposition `audience` between `dates` and `lessons`. See backend migration `V41__course_lifecycle_rewards_approval.sql` for the renumber + insert pattern and `V48__reorder_audience_section_after_dates.sql` for the reorder.

**Config-driven, with FE dispatch glue.** The section *set, order, visibility, and labels* are backend-owned (`builder_section_configs` rows, served by `/builder-config/enablement/{builderType}`, consumed FE-side by `useBuilderDefinition(builderType)`). But two pieces are FE code, not config: (1) each `section_key` maps to a React component via the **section registry** (`enablement-builder/sectionRegistry.ts`: universal defaults + a module override map), resolved by the generic `EnablementBuilderAccordion` (an unknown key renders a "Coming soon" fallback); (2) the action-section whitelist + short labels are supplied by the module to `EnablementSetupHeader` (membership only — it preserves server order via filter and excludes `publish`).

## Adding a new enablement module

When a new module lands (e.g. Learning Path, F-03), it **reuses the universal layer and declares its own module-specific sections**. Because the split is a design contract (no shared baseline row-set, endpoint is COURSE-only today), the work spans three repos:

**Backend**
1. Add the value to the `BuilderType` enum (`entity/enums/BuilderType.java`). Contracts already reserve `LEARNING_PATH` / `CERTIFICATION_PROGRAM` — see `tenxengage-contracts/models/builder-definition.md`.
2. Seed `builder_section_configs` rows for `builder_type=<NEW>` + `builder_domain=ENABLEMENT`: the universal `section_keys` (`basics, dates, audience, tags, rewards, approval_flow, publish`) following the ×10 ordering convention, **plus** the module's own module-specific `section_keys`. Seed `builder_field_configs` for any config-driven fields.
   - **`is_locked` and `display_name` MUST match the [universal sections table](#universal-sections-every-enablement-module) and the [module-specific sections table](#module-specific-sections) exactly.** The `dates` section must be seeded with `display_name = 'Availability'`. `basics`, `tags`, and `publish` must be seeded with `is_locked = TRUE` and a module-appropriate `info_message`. Each system-managed module-specific section (e.g. `composition`) must also be seeded with `is_locked = TRUE` and an `info_message`. **Omitting these from the seed migration is a documentation gap — not a "fix later" item** (see V61, which corrected V57's omission).
3. Extend the `/builder-config/{type}` endpoint to serve the new `builder_type` — today it is COURSE-only.

**Frontend** (the builder is now a generalized shell in `enablement-builder/` + per-type modules — see `frontend/docs/patterns/builder-widget-platform.md` § "Shell + module architecture"):
4. Reuse the shell's universal sections as-is (they live in `enablement-builder/steps/` and read state via `useEnablementBuilder()` + runtime via `useEnablementBuilderRuntime()`). No copying.
5. Add the module's state interface (`extends EnablementBuilderState`) + reducer + a thin context wrapper (mirroring `CourseBuilderContext`); implement the module's module-specific section components (including its own `publish`).
6. Add a server-data channel for the module's sections (mirroring `CourseDataProvider`), and a thin accordion wrapper (mirroring `CourseBuilderAccordion`) that passes the module's `moduleSections` map + optional `injectAfter` to `EnablementBuilderAccordion`, provides the runtime + data contexts, and supplies the action-section whitelist/labels to `EnablementSetupHeader`. No edit to the shell's registry is required — module sections are passed in via the `moduleSections` map.
7. The FE builder-definition service is already generalized: `useBuilderDefinition(builderType)` → `getEnablementBuilderConfig(builderType)` → `/builder-config/enablement/{builderType}`. No throw to remove.

**Blueprint**
8. Create `domains/enablement/{builder-type}.md` **only if** the module's slot fillers diverge from this baseline (per the resolution order above). If it merely reuses the baseline + adds sections, record those sections in the tables above instead.

## Naming conventions

- Package roots: `com.tenxengage.app.entity.{feature}/...` (e.g., `entity/course/`). Frontend folders: kebab-case (`course/`, `course-builder/`).
- `BuilderSectionConfig` / `BuilderFieldConfig` — uses the same entities as incentive; rows are discriminated by `builder_type + builder_domain` (not `incentive_type`). No `Definition` suffix.
- Kafka topics: kebab-case `{domain-or-feature}-events` per existing repo convention (`assessment-events`, `course-events`). Quarantine forbids `training-*` / `lms-*` / `completion-events` / `training-sync-events`.
- New audit `AuditResourceType` values use SCREAMING_SNAKE_CASE entity names: `COURSE`, `LESSON`, `CONTENT_ASSET`, `LESSON_INLINE_QUIZ`, `COURSE_END_OF_COURSE_QUIZ`, `BUILDER_SECTION_CONFIG`, `BUILDER_FIELD_CONFIG`, `COURSE_AUDIENCE_RULE`.

## Drift policy

Per [`docs/patterns/domains/INDEX.md`](INDEX.md): `/create-spec` and `/create-stories` interactively prompt when a spec or story fills a slot with a different value than this file. Slot additions are governance events — neither skill silently adds slots. If a feature surfaces a need for a new slot, the operator handles it out-of-band and updates this file plus [`INDEX.md`](INDEX.md).

## Pitfalls

| Rule | Context |
| --- | --- |
| **Fire-and-forget secondary mutations must be called on ALL save paths (POST + PUT).** When a builder's `handleSave` has a secondary mutation (e.g., audience rules, tags), add the call in both the `mode === "edit"` PUT branch and the `mode === "new"` POST branch — with the appropriate baseline (empty `[]` for create, `initialX` for edit). Missing it on the create path silently drops buffered data before navigation. | Discovered US-11: audience rules not committed on first-save create path |
| **`AudienceRuleEditor` is a STATIC/CLIENT_ROLES MVP.** The editor ships with operators for LOCATION_HIERARCHY and NUMBER field types but no type-specific value inputs. Rules using those operators are syntactically submitted but may fail validation or match incorrectly. Do not seed LOCATION_HIERARCHY or NUMBER system fields without first implementing the corresponding value editor inputs. | Discovered US-11: operator list broader than value-entry UI |
| **PUT binding endpoints must be idempotent (state-setting, not create-only).** A `PUT` that binds one entity to another must first load the current binding by the *owner* ID. If the same target is already bound → return 200 (no-op). If a different target is bound → upsert. Only throw `ALREADY_BOUND` when the requested target is owned by a *different* owner entity. A create-only approach causes spurious 409s on network retries and FE optimistic-update races. | Discovered US-16: LessonInlineQuiz.bind() initially rejected retries as 409 |
| **All service read-paths returning a `CourseDetailResponse` must hydrate every optional sub-entity explicitly.** The `CourseDetailResponse.from(course, entityTags)` 2-arg overload defaults all sub-entity fields (e.g., `endOfCourseQuiz`) to `null`. When a new binding sub-entity is added to the DTO, every service method that calls any `from()` overload must be updated to pass the sub-entity — including read paths like `getCourseById`. A missing hydration call passes silently at compile time but causes the GET endpoint to return stale/contradictory state after a successful bind. | Discovered US-17: getCourseById returned endOfCourseQuiz=null after bind (adversarial review) |

## Migration trigger

This file makes enablement the first active domain on the unified builder-config stack. Per the INDEX migration policy, when a third domain lands on the unified stack, the engineer landing that third domain runs an incentive-migration audit as a merge prerequisite before the fourth domain ships. (This trigger is about *domains* on the unified stack — not about adding a new *module* within enablement, which is covered by [Adding a new enablement module](#adding-a-new-enablement-module).)
