# Pattern: enablement-legacy-quarantine

## When this applies

Feature is part of the **enablement module** — i.e., it introduces or consumes any of:

- Enablement catalog entities (Course, LearningPath, Certification) or their sub-entities (Lesson, ContentAsset, Assessment, Question, AssessmentAttempt, CertificationExam, course/lesson assessment bindings).
- Enablement activity signals (enrollment, completion, progress, score, certification-earned).
- New Kafka topics, services, repositories, or DTOs that touch the enablement domain.

In practice this gate fires for every feature in `roadmaps/partner-revenue-readiness/features/` — F-01 through F-11.

## Spec authoring guidance

**The rule.** A pre-existing LMS / training stack exists in `tenxengage-backend`. The new enablement module is a clean reimplementation. Coexistence in Phase 1 is acceptable; reuse is not.

**Five sub-rules:**

1. **Do not import, query, mutate, extend, or call any quarantined entity / repository / service / controller / DTO / Kafka event class / Kafka topic.** The inventory below is the starting list; engineers MUST check the codebase before introducing a new enablement entity to confirm no quarantined name has been missed.

2. **New enablement entities and tables MUST use distinct names.** Specifically, do NOT use `Lms*` or `Training*` prefixes — those are quarantined. The new module's catalog entities are `Course`, `Lesson`, `LearningPath`, `Certification` (and their plurals as table names). The collision with the backend's existing `RewardTransaction` (see Common gotchas) is the highest-friction case to watch for in F-05.

3. **New Kafka topics MUST follow the existing repo convention and avoid quarantined topics.** Existing repo convention (see `tenxengage-backend/src/main/java/com/tenxengage/app/config/KafkaConfig.java`): kebab-case, suffix `-events` (or `-jobs` for job queues), **no version suffix**. Examples already in repo: `transaction-events`, `approval-events`, `notification-events`. The exact new topic name is a `/create-spec` per-feature decision; reasonable shapes are `enablement-events` (broad bucket) or per-concern (`enrollment-events`, `certification-events`). Producing to or consuming from `completion-events` or `training-sync-events` is prohibited. Avoid `training-*` and `lms-*` prefixes — those read as legacy-flavored.

4. **Coexistence is allowed.** The legacy stack continues to run in Phase 1. Enablement features must not break legacy callers and must not assume the legacy stack will be modified or removed during the roadmap.

5. **Removal is out of scope.** Deletion of the legacy stack is a separate cleanup project, not part of this roadmap. The pattern stays until that project completes.

**Quarantine inventory** — names in `tenxengage-backend/src/main/java/com/tenxengage/app/`:

- **Entities:** `LmsCourse`, `UserCourseCompletion`, `CourseProductMapping`, `TrainingCourseAssignment`
- **Repositories:** `LmsCourseRepository`, `UserCourseCompletionRepository`, `CourseProductMappingRepository`
- **Services:** `LmsCourseService`, `TrainingCompletionService`
- **Controllers:** `LmsCourseController`
- **DTOs:** `LmsCourseResponse`, `TrainingCourseRequest`, `TrainingCourseResponse`, `TrainingRecommendationResponse`, `RecommendationCompletionResponse`
- **Kafka events:** `CompletionEvent`, `CompletionEventProducer`, `CompletionEventConsumer`, `TrainingSyncEvent`, `TrainingSyncEventProducer`, `TrainingSyncEventConsumer`
- **Kafka topics:** `completion-events`, `training-sync-events`
- **Batch seeders:** `LmsCourseSeeder`, `CourseCompletionSeeder`, `CompletionSeeder`
- **Migrations:** Any pre-existing Flyway migration creating or altering the tables backing the entities above. Engineer inspects `tenxengage-backend/src/main/resources/db/migration/` for filenames matching `*lms*course*`, `*user*course*completion*`, `*training*course*`, `*course*product*`.

This list may be incomplete — the codebase is large. Before introducing any new training-, course-, completion-, lesson-, or learning-path-shaped name in an enablement spec, check the backend codebase for prior art and add the find to this list if it is another legacy stack member.

**Explicit non-quarantines** — these family names contain "training" or "completion" or look adjacent, but are NOT quarantined because they belong to different domains:

- `Activity*` family (`ActivityDefinition`, `ActivityCategory`, `UserActivityProgress`, `UserActivityDocumentSubmission`, `ActivityDocumentRequirement`, `ActivityCompletionService`, `ActivityCategoryService`, `ActivityDocumentService`) — incentive-domain (FK to `Incentive`).
- `Reward*` (`RewardTransaction`, `RewardBalance`, `RewardGrantService`, etc.) — incentive-domain reward ledger.
- `Journey*` (`JourneyStage`, `JourneyCompletionService`) — engagement-domain.
- `Recommendation*` config/scoring (`RecommendationConfig`, `RecommendationScore`, `RecommendationInteraction`, recommendation services) — cross-cutting recommendation engine; F-07 and F-08 may interact with it.
- `UserIncentiveCompletion` — incentive-domain.
- `ForecastTrainingCorrelation` — forecasting/analytics, excluded from quarantine.

## Implementation guidance

For backend implementers (consuming the spec via `/load-spec`):

- New entities live in `com.tenxengage.app.entity` alongside the legacy ones. Use the new names (`Course`, `Lesson`, etc.). Do not subclass quarantined entities; do not share a table.
- New repositories: name them with the new entity name (`CourseRepository`, NOT `LmsCourseRepository`).
- New services: avoid `Lms`, `Training`, and `LmsCourse` prefixes. Reasonable patterns: `CourseService`, `EnrollmentService`, `CertificationService`.
- New Flyway migrations: use a fresh version number; do not amend the legacy migrations.
- New Kafka producers/consumers: new classes, new topic names per rule 3 above. Do not reuse the legacy `CompletionEvent` / `CompletionEventProducer` classes.

For frontend implementers (consuming the spec via `/load-spec`):

- New API client modules call only the new endpoints (the spec defines them). Do not call `LmsCourseController`'s endpoints.
- New types/DTOs in the frontend mirror the new spec DTOs, not the legacy ones (`LmsCourseResponse`, etc.).

## Examples in codebase

None yet — the legacy stack is the *anti-example* (what NOT to reuse). The new pattern's canonical example will be whichever enablement feature implements first.

## Common gotchas

- **Naming collision: `RewardTransaction`.** F-05 introduces new reward entities. The backend already has a `RewardTransaction` (incentive-domain). New enablement reward entity MUST use a distinct name (e.g., `RewardGrant`, `EnablementRewardTransaction`, `LearningReward`). Decide at `/create-spec F-05`.
- **The `completion-events` topic is enticing.** It sounds generic ("hey, isn't every completion a completion?"). It is not — it is the legacy training-completion topic. Do not produce to it or consume from it. New topics per rule 3.
- **`Activity*` confusion.** `ActivityCompletionService` *sounds* like it might be a generic completion service, but it is specifically for incentive-qualifying activities. Do not reuse it for enablement-domain course/lesson completion.
- **Don't write deprecation markers on legacy code.** The user explicitly said "we're not going to touch them right now." This pattern is the documentation contract for the new module; it does not modify the legacy stack. A future removal project owns the deprecation/deletion lifecycle.
- **Don't conflate this with `lms-integration-modes`.** That pattern is about how enablement entities accommodate external LMS sourcing (`source = in_house` vs. `external`). This pattern is about not reusing pre-existing LMS-stack code. Both apply to enablement features and are loaded together when the gates fire.
