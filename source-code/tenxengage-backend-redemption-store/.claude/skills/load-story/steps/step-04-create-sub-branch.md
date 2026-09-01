### 4. Create the sub-branch (local only)

**CWD guard — mandatory before any work-branch creation.** Run:

```bash
basename "$(git rev-parse --show-toplevel)"
```

The output **must** be `tenxengage-backend`. If it is `tenxengage-blueprint` (or anything else), **stop**: `cd` back to the backend repo and re-run the guard. Work branches must only ever be created in the backend repo — creating `work/*` in the blueprint repo is a known bug class this guard exists to prevent.

```
git checkout -b work/{feature-slug}-{US-NN}-be
```

Immediately after creation, re-verify with `basename "$(git rev-parse --show-toplevel)"` → must be `tenxengage-backend`. If it is `tenxengage-blueprint`, delete the misplaced branch (`git -C ../tenxengage-blueprint branch -D work/{feature-slug}-{US-NN}-be`), `cd` to the backend repo, and re-run step 4.

Do NOT push.

## Next step

Read `steps/step-04.5-dispatch-subagent.md`.
