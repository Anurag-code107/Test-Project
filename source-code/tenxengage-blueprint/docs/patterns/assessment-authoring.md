# Pattern: assessment-authoring

## When this applies

Feature creates, edits, or scores assessment questions (inline quizzes, EOC quizzes, certification exams). Covers question format conventions, attempt lifecycle, submit/score endpoints, and test fixtures.

## Spec authoring guidance

- Questions are typed by `QuestionFormat`: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `SHORT_ANSWER`, `MATCHING`.
- `optionsJson` stores the options array; `correctAnswerJson` stores the scoring key. Both are free-form JSONB, but the `AnswerScorer` expects a specific shape per format (see Implementation guidance).
- Cert exam submit response (`CertExamSubmitResponse`) is intentionally minimal — no `reviewPayload`, no `attemptsRemaining`. US-09 minimal-reveal means the caller only sees `passed`, `score`, `attemptId`, timestamps.
- EOC quiz submit response (`EndOfCourseSubmitResponse`) exposes `perQuestionCorrect` on pass (not on fail), `attemptsRemaining`, and a `reviewPayload` for full-review mode.

## Implementation guidance

**AnswerScorer format contracts (single source of truth: `AnswerScorer.java`):**

| Format | `optionsJson` shape | `correctAnswerJson` shape | `submittedAnswerJson` shape |
|---|---|---|---|
| SINGLE_CHOICE | `[{"id":"a","text":"..."},...]` | `{"optionId":"a"}` | `{"optionId":"a"}` |
| MULTIPLE_CHOICE | `[{"id":"a","text":"..."},...]` | `{"optionIds":["a","c"]}` | `{"optionIds":["a","c"]}` |
| TRUE_FALSE | `[{"id":"true","text":"True"},...]` | `{"optionId":"true"}` | `{"optionId":"true"}` |
| SHORT_ANSWER | — | `{"keywords":["kw1","kw2"]}` | `{"text":"..."}` |
| MATCHING | — | `{"pairs":[{"left":"A","right":"1"},...]}` | `{"pairs":[...]}` |

`optionId` values are **string IDs you define** (e.g., `"a"`, `"b"`) — not array indices. The scorer string-compares them. Using `{ index: 1 }` or similar will always score as incorrect.

**Attempt lifecycle:**
1. `POST /api/v1/assessments/{id}/attempts` → `AssessmentAttemptResponse` (status: IN_PROGRESS)
2. `POST /api/v1/assessment-attempts/{id}/submit-end-of-course` | `submit-cert-exam`

**Assessment GET detail:** `GET /api/v1/assessments/{id}` returns `AssessmentDetailResponse` which includes a `questions` array (via `QuestionAuthoringResponse`). Questions are fetched per-tenant; an empty `questions: []` means no questions are stored for that assessment — not a permission issue.

## Examples in codebase

- `QuestionFixtures.java` — canonical option/answer format examples for all question types
- `AnswerScorer.java` — authoritative scoring logic; read this before writing answer format tests
- `AssessmentSubmitService.java` — submit flow for both EOC and cert exam

## Common gotchas

- **`{ index: N }` is wrong for SINGLE_CHOICE.** The scorer uses `optionId`, not `index`. Always match the format from `QuestionFixtures.java`.
- **AssessmentDetailResponse.from() must map the questions parameter.** The static factory accepts `List<Question>` but historically stubbed it with `List.of()`. If you see questions returning empty from the GET endpoint while the DB has rows, check the `from()` factory call in `AssessmentDetailResponse`.
- **Cert exam has no `attemptsRemaining` in the submit response.** Don't assert it in E2E tests — `CertExamSubmitResponse` only has `passed`, `score`, `attemptId`, and timestamps.
- **`allowedAttempts=1` blocks a second attempt.** The learner gets exactly one attempt; starting a second triggers 422. For retry-flow tests, set `allowedAttempts=2`.
