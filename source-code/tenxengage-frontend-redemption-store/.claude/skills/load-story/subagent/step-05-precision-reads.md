### 5. Precision reads

Your first action in this step is to read the story file at `$STORY_FILE_PATH` **in full**. It contains the FE task blocks, E2E scenarios, UI states, execution checklist, and the `## Spec references` list that drives the rest of this step.

**Anti-pattern register (read FIRST, before anything else):** Before reading the story or any spec, read this repo's `PROJECT-CONTEXT.md` and `../tenxengage-blueprint/PROJECT-CONTEXT.md` in full. Build a checklist from every documented rule, especially those tagged "Root cause of US-NN" (e.g. never read raw multi-wire-format answer JSON without normalizing; never type a response field loosely; consume the contract discriminator, not an invented one). This register primes every read and decision that follows. Carry it into Step 6 and re-apply at Step 9.5.

**Algorithm:**

1. **Read the story file in full** at `$STORY_FILE_PATH`.

2. **Parse `## Spec references`.** Locate `^## Spec references` in the story file. The range from that line to the next `^## ` heading (or end of file) is the references block. Within the block, capture every line that cites `spec.md`, `technical.md`, or `patterns/<name>.md`. Expected line shapes:

   ```
   - `spec.md → ## Section Name` — optional advisory commentary
   - `technical.md → ## Section Name [optional suffix]` — advisory commentary
   - `patterns/ai-copilot.md` — optional advisory commentary (whole-file read)
   - `patterns/sse-streaming.md → ## Implementation guidance` — section-level read
   ```

   For each matched line, capture `(file, section_heading)` where `file ∈ {spec.md, technical.md, patterns/<name>.md}` and `section_heading` is either a `## ` heading or `null` (when the line cites a pattern with no `→ ## Section`). Strip backticks and surrounding whitespace. Text after the em-dash (`—`) is advisory and ignored by the parser. **Dedupe** pairs by `(file, section_heading)`.

3. **Read each cited section in full.** For each `(file, section_heading)` pair:
   - Resolve `file` to its absolute path: `spec.md` → `$SPEC_PATH`, `technical.md` → `$TECHNICAL_PATH`, `patterns/<name>.md` → `../tenxengage-blueprint/docs/patterns/<name>.md`.
   - **Whole-file read (pattern citations with no section).** If `section_heading` is `null` (only valid for `patterns/<name>.md` citations): `Read(file)` in full and continue to the next pair.
   - **Match algorithm (suffix-tolerant).** Story authors routinely drop audience suffixes like `[BE]` or `[BE + FE]` when citing sections. Strict exact-match would silently miss sections that are unambiguously identifiable. Normalize both sides by stripping the trailing ` [...]` suffix and compare on the base:
     1. Compute `cited_base` by stripping the trailing ` [...]` suffix from `section_heading` (regex applied to the end of the string: ` \[[^\]]+\]\s*$`). Examples: `## Edge Cases [BE + FE]` → `## Edge Cases`; `## DTOs [BE]` → `## DTOs`; `## Functional Requirements` → `## Functional Requirements` (unchanged).
     2. Grep `^## ` in the resolved path to enumerate every actual heading. For each actual heading, compute `actual_base` the same way.
     3. Find actual headings whose `actual_base` equals `cited_base` (case-sensitive on the base portion).
   - **One match:** the match line is `SECTION_START`. The next `^## ` line after that (or EOF) is `SECTION_END`. `Read(file, offset=SECTION_START, limit=SECTION_END − SECTION_START)`.
   - **Multiple matches** (rare — only when two sections in the same file share a base, e.g., `## Notes [BE]` and `## Notes [FE]` both present): prefer the actual heading whose original suffix exactly equals the cited heading's original suffix. If the cited heading was bare and multiple suffixed actuals exist, print `WARN: ambiguous match for "{section_heading}" in {file} — reading all variants` and read each in turn.
   - **Zero matches:** print `WARN: section "{section_heading}" not found in {file} — referenced by $STORY_ID` and continue. Do not abort.

4. **Safety net (legacy / malformed stories).** If the story file has **no** `## Spec references` heading at all: read `$SPEC_PATH` and `$TECHNICAL_PATH` in full and print `WARN: story $STORY_ID has no ## Spec references block — reading spec.md and technical.md in full as fallback`.

**Patterns registry (always read INDEX + auto-pick gates):**

This block runs in addition to any `patterns/<name>.md` citations parsed in steps 2–3. It ensures the subagent has visibility into the full pattern registry and auto-picks patterns the story didn't explicitly cite.

1. **Always read** `../tenxengage-blueprint/docs/patterns/INDEX.md` in full. It is the topic-keyed pattern registry (~50 lines).

2. **Build the set of already-read pattern paths** from steps 2–3: include `patterns/<name>.md` only if it was cited as a whole-file read (no `→ ## Section`). Section-level pattern citations are NOT in this set — the file should still be read in full by gate-matching below if its gate applies.

3. **Evaluate each pattern row's `Gate (when this applies)` column** against the story. Inputs available:
   - The full story file (already read in step 1).
   - The cited `spec.md` / `technical.md` sections (already read in step 3).
   - `$ENTITIES`, `$DOMAIN`, and other brief fields.

   Gates are written for boolean evaluation: `ALWAYS` matches every story; conditional gates like `Feature uses Server-Sent Events` or `Feature publishes Kafka events` match when the story content supports them. The `domain-registry` row is consumed by the existing "Domain registry (conditional)" block below and is NOT re-read here.

4. **For every matched row whose pattern path is NOT in the already-read set**, `Read` the pattern file in full. Path resolution:
   - For rows whose file column is a bare name (e.g., `ai-copilot.md`): resolve to `../tenxengage-blueprint/docs/patterns/<name>.md`.
   - For rows whose file column contains `../`-style relative paths (e.g., `../../tenxengage-frontend/docs/patterns/builder-widget-platform.md`): treat the path as relative to `../tenxengage-blueprint/docs/patterns/` (INDEX.md's own directory), then collapse. Example: `../../tenxengage-frontend/docs/patterns/builder-widget-platform.md` → `../tenxengage-frontend/docs/patterns/builder-widget-platform.md`.

   Pattern files follow a fixed 5-section structure (When this applies / Spec authoring guidance / Implementation guidance / Examples in codebase / Common gotchas).

5. **No cap.** The gates are crisp enough that the matched set will be small (typically 2–5 patterns per story including ALWAYS rows).

**Contracts (always read in full):**

Read `contracts/endpoints/{resource}*.yaml` and `contracts/models/{model-name}.md` for the entities in `$ENTITIES`. Contracts are small and always needed in full.

**FE reference files (3 — first 50 lines each):**

Use `Read(file, offset=1, limit=50)` for one of each, **preferring the domain-matching subfolder**:

- One existing hook in `src/hooks/` using TanStack Query
- One existing component test in `src/components/**/__tests__/*.test.tsx`
- One existing Playwright spec in `e2e/*.spec.ts`

**Mockup (conditional — read in FULL, not limited):**

If `$MOCKUP_FILE` is non-null: read the entire mockup file. This is a visual fidelity anchor — do NOT limit to 50 lines.

**Screen Pattern Mirror:**

Read `.claude/skills/create-mockups/SKILL.md → Phase 3` to identify production analog files for each FE task (by screen type). Read those production analog files (first 80 lines each).

**Domain registry (conditional):**

Only if `$DOMAIN` is non-null: read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md` and `../tenxengage-blueprint/docs/patterns/domains/$DOMAIN.md`.

## Next step

Read `subagent/step-05b-mockup-anchor.md`.
