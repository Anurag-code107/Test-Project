---
name: "create-spec"
description: "Use when user says 'create a spec', 'write a spec', 'start a spec', or provides a PRD/requirements document to convert into a feature specification. Use for any new feature being added to the tenxengage-blueprint repository."
argument-hint: "Describe the feature or provide a path to a PRD/requirements document"
user-invocable: true
---

## User input

```text
$ARGUMENTS
```

## Initialization

Run this immediately:

```bash
date +%s > /tmp/create_spec_start && echo 0 > /tmp/create_spec_wait && echo "create-spec started: $(date '+%H:%M:%S')"
```

## Resumption check

Before routing, check whether a previous run produced an in-flight plan file. The plan-mode harness provides the plan file path; if a plan file exists at any path matching `**/specs/*-create-spec-*.md` with frontmatter `slug` matching the inferred feature slug:
- If frontmatter `stepsCompleted` includes `write-plan-file` AND `filesWritten` is empty → previous run reached step 15 but didn't write files. Resume at step 16.
- If `stepsCompleted` includes `write-plan-file` AND `filesWritten` is non-empty → previous run completed. Confirm with user before re-running.
- Otherwise → no in-flight state; start at step 01.

## Step overview

| Step | File | What it does |
|---|---|---|
| 01 | `step-01-parse-input.md` | Detect input mode; extract + confirm FRs and NFRs |
| 01a | `step-01a-functional-completeness-probe.md` | One-shot functional-completeness probe; fold approved gaps into locked FR list |
| 02 | `step-02-load-brd-context.md` | Load BRD digest (Mode 1 only) |
| 03–12 | `step-03` … `step-12` | Load project context, resolve questions, detect shape, load shape references, scope, security, events, test strategy, permissions, derive slug |
| 13 | `step-13-generate-spec-content.md` | Generate full `spec.md` content |
| 14 | `step-14-generate-technical-content.md` | Generate `technical.md` content |
| 15 | `step-15-write-plan-file.md` | Write plan file |
| 16 | `step-16-branch-write-review-finalize.md` | Write files, run `/review-spec`, finalize |

## Step protocol

- One step file is loaded into context at a time.
- Each step file ends with explicit routing to the next step.
- Never load future step files until the current step routes to them.
- Step files live in `steps/`. References live in `references/`. Templates live in `templates/`.

## Begin

Read `steps/step-01-parse-input.md` to begin.