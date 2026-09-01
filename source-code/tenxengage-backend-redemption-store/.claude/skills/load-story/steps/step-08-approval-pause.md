### 8. Approval pause — **gate-conditional**

**Phase routing — checked first, before gate mode:**

If `$PHASE == implement` (set only by run-feature's orchestrator):
1. Run a conditional fresh test: if you arrived here directly from `subagent/step-07.5-ready-check-loop.md` with `status=pass` and `git rev-parse HEAD` matches Step 7.5's final SHA, skip — Step 7.5 ended green on this same commit. Otherwise run `./gradlew test` fresh and confirm 0 failures.
2. Read `references/orchestrator-return-format.md`. Emit structured return lines with `status=awaiting-approval`. Use `none` for unused-but-required values.
3. Exit cleanly. Do NOT proceed to gate-mode logic, approval pause, or squash-merge.

**Gate behavior — applies only when `$PHASE` is unset or `$PHASE == revise`:**

- If `$GATE == every` (default) — perform the chat approval pause as described below (matches today's behavior).
- If `$GATE in {story, ready-check, feature-end}`:
  - Run `./gradlew test` fresh and the green-suite check.
  - If ready-check is green (strict stages): proceed directly to Step 8.5 (ready-check report commit) then Step 9 (squash-merge).
  - If ready-check failed on strict stages: read `references/orchestrator-return-format.md`, emit `status=failure`, exit.

**Before presenting to the developer (when `$GATE == every`):**
1. **Conditional fresh test run.** If you arrived here directly from `subagent/step-07.5-ready-check-loop.md` with `status=pass` (i.e., no `change X` re-entry between Step 7.5 and here, and `git rev-parse HEAD` matches the SHA at the end of Step 7.5), skip the fresh test run — Step 7.5's loop ended green on this same SHA. Otherwise (first entry after `change X`, or any re-entry), run `./gradlew test` fresh in this message, read full output, confirm exit code 0 and 0 failures. Do not claim "green" without this evidence. If `$USE_TDD = true`, also invoke `superpowers:verification-before-completion`.
2. Structure your approval-pause message with diff stat, summary of what was added, and explicit `merge` prompt. If `$USE_TDD = true`, also invoke `superpowers:requesting-code-review`.

**Pause-wrap (see `## Time accounting` above).** Immediately before showing the approval message below, capture:

```bash
PAUSE_START=$(date +%s)
```

The instant the developer's reply arrives — before doing anything else — accumulate the wait:

```bash
HUMAN_PAUSE_TOTAL_SECS=$(( HUMAN_PAUSE_TOTAL_SECS + $(date +%s) - PAUSE_START ))
```

If the reply is `change X` and the loop re-enters this step, **wrap again on every entry** — re-capture `PAUSE_START` before showing the re-iterated message and accumulate again after the next reply. Each iteration's wait counts separately.

Once the full suite is verified green, STOP and show:

```
{US-NN} BE ({title}) — ready for review

Summary:
{2–3 sentence summary: DTOs added, service method {X}, endpoint {METHOD} {path}, audit entry}

Checklist: all BE items checked
Tests: ./gradlew test — green ({N} new tests)
Ready-check: {$RC_FIXES} auto-fixes applied | {count($RC_MANUAL)} manual action items
Anti-pattern checklist: {$ANTIPATTERN_PASS}

{IF $RC_MANUAL is non-empty:}
Manual items (does not block merge — logged in tracker Notes):
{for each item in $RC_MANUAL:}
  ✗ [{item.severity}] {item.file}:{item.line} — {item.rule}
    Action: {item.manualAction}

{IF $RC_BLOCKED:}
⚠ Ready-check blocked: {one-line reason}. Merge will proceed with this issue logged.

git diff --stat features/{feature-id}..work/{feature-slug}-{US-NN}-be:
{output}

Reply 'merge' to squash-merge into features/{feature-id}, or 'change X' to revise.
```

## Routing

- On `merge` → read `steps/step-08.5-rc-report-commit.md`.
- On `change X` → implement change on the same sub-branch, rerun `./gradlew test`, re-read this file.
- On ambiguous reply → ask again; never merge on silence.
