# Failure Handling

Identical to `/execute-foundation`: on any error, flip the `BE` cell to `blocked` with a one-line Notes reason, commit + push tracker, surface to the developer, stop. Leave the sub-branch local and un-deleted for resumption.

Never flip to `done` without (a) green full test suite and (b) explicit `merge` approval.
