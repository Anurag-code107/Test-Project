### 5. Precision reads

**Before anything else:** Read `PROJECT-CONTEXT.md` (backend repo root) **in full**. It contains the coding conventions, API patterns, entity rules, and the anti-pattern register that govern all implementation in this repo — load it first so it primes every implementation decision you make while reading the story and spec.

Your next action is to read the story file at `$STORY_FILE_PATH` **in full**. It contains the BE task blocks, non-functional notes, UI states, execution checklist, and the `## Spec references` list that drives the rest of this step.

**Algorithm:**

1. **Read `PROJECT-CONTEXT.md` in full** (backend repo root) — the anti-pattern register. Extract every documented rule, especially those tagged with a "Root cause of US-NN" provenance note. Build an explicit checklist from them — at minimum: every `BusinessRuleException` (and sibling domain exceptions) MUST carry a stable `errorCode`; response DTO fields MUST be concretely typed (never `Object`); multi-wire-format inbound JSON (e.g. answer payloads) MUST be normalized at the boundary, not consumed raw. Carry this checklist into Step 6 and re-apply it at Step 7.5.

2. **Read the story file in full** at `$STORY_FILE_PATH`.

3. **Parse `## Spec references`.** Locate `^## Spec references` in the story file. The range from that line to the next `^## ` heading (or end of file) is the references block. Within the block, capture every line that cites `spec.md`, `technical.md`, or `patterns/<name>.md`. Expected line shapes:

   ```
   - `spec.md → ## Section Name` — optional advisory commentary
   - `technical.md → ## Section Name [optional suffix]` — advisory commentary
   - `patterns/ai-copilot.md` — optional advisory commentary (whole-file read)
   - `patterns/sse-streaming.md → ## Implementation guidance` — section-level read
   ```

   For each matched line, capture `(file, section_heading)` where `file ∈ {spec.md, technical.md, patterns/<name>.md}` and `section_heading` is either a `## ` heading or `null` (when the line cites a pattern with no `→ ## Section`). Strip backticks and surrounding whitespace. Text after the em-dash (`—`) is advisory and ignored by the parser. **Dedupe** pairs by `(file, section_heading)`.

4. **Read each cited section in full.** For each `(file, section_heading)` pair:
   - Resolve `file` to its absolute path: `spec.md` → `$SPEC_PATH`, `technical.md` → `$TECHNICAL_PATH`, `patterns/<name>.md` → `../tenxengage-blueprint/docs/patterns/<name>.md`.
   - **Whole-file read (pattern citations with no section).** If `section_heading` is `null` (only valid for `patterns/<name>.md` citations): `Read(file)` in full and continue to the next pair.
   - **Match algorithm (suffix-tolerant).** Story authors routinely drop audience suffixes like `[BE]` or `[BE + FE]` when citing sections. Strict exact-match would silently miss sections that are unambiguously identifiable. Normalize both sides by stripping the trailing ` [...]` suffix and compare on the base:
     1. Compute `cited_base` by stripping the trailing ` [...]` suffix from `section_heading` (regex applied to the end of the string: ` \[[^\]]+\]\s*$`). Examples: `## Edge Cases [BE + FE]` → `## Edge Cases`; `## DTOs [BE]` → `## DTOs`; `## Functional Requirements` → `## Functional Requirements` (unchanged).
     2. Grep `^## ` in the resolved path to enumerate every actual heading. For each actual heading, compute `actual_base` the same way.
     3. Find actual headings whose `actual_base` equals `cited_base` (case-sensitive on the base portion).
   - **One match:** the match line is `SECTION_START`. The next `^## ` line after that (or EOF) is `SECTION_END`. `Read(file, offset=SECTION_START, limit=SECTION_END − SECTION_START)`.
   - **Multiple matches** (rare — only when two sections in the same file share a base, e.g., `## Notes [BE]` and `## Notes [FE]` both present): prefer the actual heading whose original suffix exactly equals the cited heading's original suffix. If the cited heading was bare and multiple suffixed actuals exist, print `WARN: ambiguous match for "{section_heading}" in {file} — reading all variants` and read each in turn.
   - **Zero matches:** print `WARN: section "{section_heading}" not found in {file} — referenced by $STORY_ID` and continue. Do not abort.

5. **Safety net (legacy / malformed stories).** If the story file has **no** `## Spec references` heading at all: read `$SPEC_PATH` and `$TECHNICAL_PATH` in full and print `WARN: story $STORY_ID has no ## Spec references block — reading spec.md and technical.md in full as fallback`.

**Patterns registry (always read INDEX + auto-pick gates):**

This block runs in addition to any `patterns/<name>.md` citations parsed in steps 3–4. It ensures the subagent has visibility into the full pattern registry and auto-picks patterns the story didn't explicitly cite.

1. **Always read** `../tenxengage-blueprint/docs/patterns/INDEX.md` in full. It is the topic-keyed pattern registry (~50 lines).

2. **Build the set of already-read pattern paths** from steps 3–4: include `patterns/<name>.md` only if it was cited as a whole-file read (no `→ ## Section`). Section-level pattern citations are NOT in this set — the file should still be read in full by gate-matching below if its gate applies.

3. **Evaluate each pattern row's `Gate (when this applies)` column** against the story. Inputs available:
   - The full story file (already read in step 2).
   - The cited `spec.md` / `technical.md` sections (already read in step 4).
   - `$ENTITIES`, `$DOMAIN`, and other brief fields.

   Gates are written for boolean evaluation: `ALWAYS` matches every story; conditional gates like `Feature uses Server-Sent Events` or `Feature publishes Kafka events` match when the story content supports them. The `domain-registry` row is consumed by the existing "Domain registry (conditional)" block below and is NOT re-read here.

4. **For every matched row whose pattern path is NOT in the already-read set**, `Read` the pattern file in full. Path resolution:
   - For rows whose file column is a bare name (e.g., `ai-copilot.md`): resolve to `../tenxengage-blueprint/docs/patterns/<name>.md`.
   - For rows whose file column contains `../`-style relative paths (e.g., `../../tenxengage-frontend/docs/patterns/builder-widget-platform.md`): treat the path as relative to `../tenxengage-blueprint/docs/patterns/` (INDEX.md's own directory), then collapse. Example: `../../tenxengage-frontend/docs/patterns/builder-widget-platform.md` → `../tenxengage-frontend/docs/patterns/builder-widget-platform.md`.

   Pattern files follow a fixed 5-section structure (When this applies / Spec authoring guidance / Implementation guidance / Examples in codebase / Common gotchas).

5. **No cap.** The gates are crisp enough that the matched set will be small (typically 2–5 patterns per story including ALWAYS rows).

**Flyway migrations (technical.md — auxiliary read):**

Table names are not always cited in `## Spec references`, so this targeted read runs in addition. Tables live inside `### Vxx__...sql` migration blocks. For each entity in `$ENTITIES`:

1. Grep its snake_case table name in `$TECHNICAL_PATH` to find candidate match lines.
2. For each match: find the `### Vxx__...sql` heading at or above the match line (largest line number ≤ match), and the next `### Vxx__...sql` heading (or section end) below.
3. `Read($TECHNICAL_PATH, offset=MIGRATION_START, limit=NEXT_MIGRATION_START − MIGRATION_START)`.
4. Deduplicate: if two entities map to the same migration, read it only once. If the same migration block was already read via `## Spec references`, skip it.

**Contracts (always read in full):** Read `contracts/endpoints/{resource}*.yaml` and `contracts/models/{model}.md` for every entity in `$ENTITIES`. The contract — not the story prose or your own assumption — is the authoritative field-name/type/JSON-shape source for every DTO you emit or consume. Diff your DTOs against it field-by-field in Step 6.

**Reuse-discovery gate (mandatory before writing any new class):** For each entity/response this story needs, grep the codebase for an existing endpoint, service method, or DTO that already returns the required shape (e.g. `grep -rn "{Entity}DetailResponse\|{Entity}Service" src/main/java`). If a richer existing DTO/endpoint already serves the data, REUSE or extend it — do not clone a thinner sibling-feature endpoint. Record in the story Notes: which existing types you reused, or an explicit "no reusable analog found — building new because X" justification per new class. **If NO reusable analog is found, do not dead-end:** build new and record the one-line "no analog — building new because X" justification in the story Notes (and surface it in the Step 12 report's no-analog slot). A missing justification is a Step 7.5 blocker.

**Java reference files (4 — first 50 lines each):**

Use `Read(file, offset=1, limit=50)` for one of each, **preferring the subdirectory matching this story's domain** (e.g., `controller/assessment/` for assessment-domain entities):

- One controller from `src/main/java/com/tenxengage/app/controller/`
- One service from `src/main/java/com/tenxengage/app/service/`
- One `*ControllerTest.java` using `@WebMvcTest` from `src/test/java/com/tenxengage/app/controller/`
- One `*ServiceTest.java` using `@ExtendWith(MockitoExtension.class)` from `src/test/java/com/tenxengage/app/service/`

> The anti-pattern register read happens up front in step 1 (`PROJECT-CONTEXT.md`) so it primes every decision below — see step 1 for the checklist obligation carried into Steps 6 and 7.5.

**Domain registry (conditional):**

Only if `$DOMAIN` is non-null (passed in brief):
- Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md`
- Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`
- If `builder_type:` is set and `{domain}/{builder-type}.md` exists, read it too

## Next step

Read `subagent/step-06-implement-tasks.md`.
