# Failure Handling

On any error: flip the `FE` cell to `blocked` with a one-line Notes reason, commit + push tracker, surface to the developer, stop. Leave the sub-branch local and un-deleted.

Never flip FE to `done` without (a) Vitest green, (b) Playwright green against real BE, (c) explicit `merge` approval.
