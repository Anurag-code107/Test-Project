# Time Accounting — load-story BE

> Author reference only. Not loaded at runtime.
> Variables set in SKILL.md (claim point) and read in steps/step-11-tracker-done.md.

The tracker's `Duration (BE)` column means something specific — keep it distinct from wall-clock:

- The Notes column's `session=… started=…` and `BE completed {ISO8601}` are wall-clock ISO 8601 UTC. They span the full session including any human interaction.
- **`Duration (BE)`** is **Claude execution time only** — it explicitly excludes any time the session spent waiting on a human reply (the Step 8 approval pause is the dominant one).

To make this work, the skill maintains two shell variables (set at the BE claim in Step 3, used at the done flip in Step 11):

- `CLAIM_TIME_EPOCH` — `$(date +%s)` captured the instant the BE cell is claimed.
- `HUMAN_PAUSE_TOTAL_SECS` — initialized to `0` at claim. Incremented at every interactive prompt that occurs **after** the claim.

**Pause-wrap convention (applied at every post-claim interactive prompt — Step 8 approval pause is the main one):**

```bash
# Immediately before showing the prompt:
PAUSE_START=$(date +%s)

# Immediately after the user replies (and before any further work):
HUMAN_PAUSE_TOTAL_SECS=$(( HUMAN_PAUSE_TOTAL_SECS + $(date +%s) - PAUSE_START ))
```

If the `change X` loop re-enters the approval pause, **each iteration's wait counts separately** — wrap every entry.

At the done flip in Step 11:

```bash
COMPLETED_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
NOW_EPOCH=$(date +%s)
ACTIVE_SECS=$(( NOW_EPOCH - CLAIM_TIME_EPOCH - HUMAN_PAUSE_TOTAL_SECS ))
```

`ACTIVE_SECS` is formatted into `Duration (BE)` as `Xh Ym` (omit `0h ` prefix under 1 hour). The `BE completed {ISO8601}` note uses `$COMPLETED_ISO` verbatim — never retyped by hand (this is the safeguard against the date-typo class of bug).

The Step 12 `Wall time:` line is independent of this — it always reports raw `now − $SKILL_START_EPOCH` (no pause subtraction) so the developer can still see total elapsed session time.

Pauses **before** the claim (the blueprint branch guard A/B/C and behind-origin A/B prompts in Step 2) are naturally excluded because `CLAIM_TIME_EPOCH` has not started — no wrapping is needed there.
