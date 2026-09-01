# Time Accounting — load-story FE

> Author reference only. Not loaded at runtime.
> Variables set in SKILL.md (claim point) and read in steps/step-13-tracker-done.md.

The tracker's `Duration (FE)` column means something specific — keep it distinct from wall-clock:

- The Notes column's `session=… started=…` and `FE completed {ISO8601}` are wall-clock ISO 8601 UTC. They span the full session including any human interaction.
- **`Duration (FE)`** is **Claude execution time only** — it explicitly excludes any time the session spent waiting on a human reply (the Step 10 approval pause is the dominant one; the Step 9 "is BE already running?" prompt also counts).

To make this work, the skill maintains two shell variables (set at the FE claim in Step 3, used at the done flip in Step 13):

- `CLAIM_TIME_EPOCH` — `$(date +%s)` captured the instant the FE cell is claimed.
- `HUMAN_PAUSE_TOTAL_SECS` — initialized to `0` at claim. Incremented at every interactive prompt that occurs **after** the claim.

**Pause-wrap convention (applied at every post-claim interactive prompt):**

```bash
# Immediately before showing the prompt:
PAUSE_START=$(date +%s)

# Immediately after the user replies (and before any further work):
HUMAN_PAUSE_TOTAL_SECS=$(( HUMAN_PAUSE_TOTAL_SECS + $(date +%s) - PAUSE_START ))
```

If the `change X` loop re-enters the approval pause, **each iteration's wait counts separately** — wrap every entry.

At the done flip in Step 13:

```bash
COMPLETED_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
NOW_EPOCH=$(date +%s)
ACTIVE_SECS=$(( NOW_EPOCH - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))
```

`ACTIVE_SECS` is formatted into `Duration (FE)` as `Xh Ym` (omit `0h ` prefix under 1 hour). The `FE completed {ISO8601}` note uses `$COMPLETED_ISO` verbatim — never retyped by hand (this is the safeguard against the date-typo class of bug).

The Step 14 `Wall time:` line is independent of this — it always reports raw `now − $SKILL_START_EPOCH` (no pause subtraction) so the developer can still see total elapsed session time.

Pauses **before** the claim (the blueprint branch guard A/B/C and behind-origin A/B prompts in Step 2) are naturally excluded because `CLAIM_TIME_EPOCH` has not started — no wrapping is needed there.

**Resume / scaffold-and-wait:** when the skill exits at Step 8 (BE pending) and the user re-invokes after BE completes, the original session's `CLAIM_TIME_EPOCH` and `HUMAN_PAUSE_TOTAL_SECS` are out of process. To preserve correctness across the gap:

- **At scaffold-and-wait exit (Step 8):** compute `SCAFFOLD_ACTIVE_SECS=$(( $(date +%s) - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))` and append it to the row's Notes as `scaffold_active_secs=$SCAFFOLD_ACTIVE_SECS`. This freezes the FE's Claude-active time at the point of exit.
- **On resume (Step 3 detects existing in-progress with matching session):** parse `scaffold_active_secs=N` from Notes into `$SCAFFOLD_ACTIVE_SECS` (default `0` if absent — first run was on the old skill). Then re-init the clock for the resume window: `CLAIM_TIME_EPOCH=$(date +%s)`, `HUMAN_PAUSE_TOTAL_SECS=0`.
- **At done flip (Step 13):** the formula becomes `ACTIVE_SECS=$(( ${SCAFFOLD_ACTIVE_SECS:-0} + NOW_EPOCH - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))`. The (potentially days-long) gap between scaffold exit and resume is excluded from `Duration (FE)` as intended.
