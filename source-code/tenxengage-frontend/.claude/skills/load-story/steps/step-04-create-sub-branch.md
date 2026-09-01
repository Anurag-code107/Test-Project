### 4. Create (or reuse) the sub-branch (local only)

**CWD guard — mandatory before any work-branch creation or checkout.** Run:

```bash
basename "$(git rev-parse --show-toplevel)"
```

The output **must** be `tenxengage-frontend`. If it is `tenxengage-blueprint` (or anything else), **stop**: `cd` back to the frontend repo and re-run the guard. Work branches must only ever be created in the frontend repo — creating `work/*` in the blueprint repo is a known bug class this guard exists to prevent.

- If `work/{feature-slug}-{US-NN}-fe` already exists locally (resume case): `git checkout work/{feature-slug}-{US-NN}-fe && git rebase features/{feature-id}`
- Otherwise: `git checkout -b work/{feature-slug}-{US-NN}-fe`

Immediately after creation/checkout, re-verify with `basename "$(git rev-parse --show-toplevel)"` → must be `tenxengage-frontend`. If it is `tenxengage-blueprint`, delete the misplaced branch (`git -C ../tenxengage-blueprint branch -D work/{feature-slug}-{US-NN}-fe`), `cd` to the frontend repo, and re-run step 4.

Do NOT push.

## Next step

Read `steps/step-04.5-dispatch-subagent.md`.
