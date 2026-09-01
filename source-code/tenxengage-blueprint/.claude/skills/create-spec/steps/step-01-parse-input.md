# Step 01: parse-input

## Goal
Detect the input format and extract / confirm functional + non-functional requirements before any file reading begins.

## Inputs (from prior steps)
- `$ARGUMENTS` — raw user input from the orchestrator

## Loads (just-in-time)
- Always: none
- Conditional:
  - If input is a file path → Read that file
  - If input is a BRD identifier `{slug} F-NN` → Read `roadmaps/{slug}/features/F-NN-*.md` (preferred) or `roadmaps/{slug}/roadmap.md` (legacy fallback)

## Procedure

1. **Detect input mode** from `$ARGUMENTS`:
   - **Mode 1 — BRD identifier:** matches kebab-case slug + space + `F-` + digits + optional letter suffix (e.g., `partner-revenue-readiness F-01`, `cosell F-07b`). Read `roadmaps/{slug}/features/F-NN-*.md` if it exists; otherwise read `roadmaps/{slug}/roadmap.md` and find the `### F-NN:` section.
   - **Mode 2 — direct prompt text:** plain description (e.g., `"Quiz engine with timed quizzes and scoring"`). Use as-is.
   - **Mode 3 — single file path:** read the file (any readable format).
   - **Mode 4 — multiple file paths:** read all and synthesize.

2. **Extract requirements** per detected mode. From a per-feature brief, extract: business outcome, primary persona, secondary personas, user journey, FRs, business rules, dependencies, ADR blockers, riskiest unknown, suggested story seeds.

3. **Confirm understanding with the user in two parts:**
   - **Part A — Functional requirements** (3–5 bullets summarizing what the feature does)
   - **Part B — Non-Functional requirements** — ask only for unknowns:
     - Expected load (concurrent users, peak req/min)
     - Data sensitivity (PII, financial, confidential)
     - Availability SLA (core flow vs internal admin)
     - Compliance constraints (GDPR, audit retention)
     - Event-driven needs (Kafka publish/consume)

4. **Lock the answers.** Once user confirms, the FR + NFR set is fixed for the rest of the run.

## Rules (scoped to this step)
- For Mode 1 (BRD identifier), record "digest will be loaded in step 02" — do NOT load it here.
- For per-feature briefs, FRs are inherited verbatim. Numbering preserved (FR-NN.X). Wording may be refined; new FRs may be appended; renumbering / dropping requires an explicit deviation note.
- Do NOT read project-context files in this step (that's step 03).
- Do NOT decide scope here (that's step 07). This step is requirements ingestion only.

## User interaction

Mark start of human wait:
```bash
date +%s%3N > /tmp/create_spec_wait_started
```

Present Part A + Part B to the user. After the draft:
- Itemize anything the skill could NOT derive from the brief as a separate "Items that need your input" list.
- Ask: "Anything in the FR/NFR draft that's missing or wrong?"
- Wait for the user to confirm, edit, or push back. If they push back, regenerate the draft and re-present.

On resume, accumulate the wait:
```bash
echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
```

## Output for downstream steps
- Locked FR list
- Locked NFR answers (load, sensitivity, SLA, compliance, events)
- Input mode flag (BRD identifier / direct / file path / multi-file)
- For Mode 1: BRD slug and feature ID

## Boundary
User confirms understanding → route to step 01a: read `steps/step-01a-functional-completeness-probe.md`.

**On resume from any user-gate in this step, FIRST run the wait-accumulation command above before doing anything else.**