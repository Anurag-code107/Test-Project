### 1. Pre-flight — feature branch

1. `git branch --show-current` → if on `features/{feature-id}`, skip to 1c
1a. If `$ROADMAP_SLUG` is non-empty, ensure `roadmaps/{ROADMAP_SLUG}` exists locally. Run this **single compound command verbatim** — do not split it:
    ```bash
    git checkout roadmaps/{ROADMAP_SLUG} 2>/dev/null || git fetch origin roadmaps/{ROADMAP_SLUG}:roadmaps/{ROADMAP_SLUG} 2>/dev/null && git checkout roadmaps/{ROADMAP_SLUG} || _b="${FEATURE_BASE_BRANCH:-main}" && echo "Creating roadmap branch from: $_b" && git checkout -b roadmaps/{ROADMAP_SLUG} "$_b"
    ```
1b. Ensure the feature branch exists locally. Run this **single compound command verbatim** — do not split it:
    ```bash
    git checkout features/{feature-id} 2>/dev/null || git fetch origin features/{feature-id}:features/{feature-id} 2>/dev/null && git checkout features/{feature-id} || echo "Creating branch from: $BASE_BRANCH" && git checkout -b features/{feature-id} "$BASE_BRANCH"
    ```
1c. Sync from remote only if all three guards pass:
    - Guard 1 (remote exists): `git ls-remote --exit-code origin features/{feature-id}` → non-zero exit: skip entirely
    - Guard 2 (no uncommitted changes): `git status --porcelain` → non-empty: skip, print "Skipping remote sync — uncommitted changes present"
    - Guard 3 (no unpushed commits): `git log origin/features/{feature-id}..HEAD --oneline` → non-empty: skip, print "Skipping remote sync — unpushed commits present"
    - All guards pass: `git pull --rebase origin features/{feature-id}`
1d. Sync the contracts submodule to the latest feature-branch files. Four substeps:

    **(i) Init guard.** If the submodule was never initialized (fresh clone), materialize it:
    ```bash
    if [ ! -f contracts/.git ] || ! git -C contracts rev-parse HEAD >/dev/null 2>&1; then
      git submodule update --init contracts || { echo "Contracts submodule failed to initialize. Run 'git submodule update --init contracts' manually and re-run /load-story."; exit 1; }
    fi
    ```

    **(ii) Select the target contracts branch.** Prefer `features/{feature-id}`; fall back to `$BASE_BRANCH` (computed in pre-flight). Records the chosen branch in `$CONTRACTS_BRANCH`:
    ```bash
    if git -C contracts checkout features/{feature-id} 2>/dev/null \
       || (git -C contracts fetch origin && git -C contracts checkout features/{feature-id} 2>/dev/null); then
      CONTRACTS_BRANCH="features/{feature-id}"
    else
      echo "Contracts feature branch not found — using base branch: $BASE_BRANCH"
      git -C contracts checkout "$BASE_BRANCH" 2>/dev/null \
        || (git -C contracts fetch origin && git -C contracts checkout "$BASE_BRANCH") \
        || { echo "Contracts submodule cannot reach any usable branch. Aborting."; exit 1; }
      CONTRACTS_BRANCH="$BASE_BRANCH"
    fi
    ```

    **(iii) Conditional pull.** Pull only when all three guards pass — otherwise print a one-line skip reason and continue (do NOT abort):
    - Guard 1 — remote branch exists: `git -C contracts ls-remote --exit-code origin "$CONTRACTS_BRANCH"` exits 0. If not: print `Skipping contracts pull — remote branch not found`.
    - Guard 2 — no uncommitted changes in the submodule working tree: `git -C contracts status --porcelain` is empty. If not: print `Skipping contracts pull — uncommitted changes in submodule`.
    - Guard 3 — no unpushed commits: `git -C contracts log "origin/$CONTRACTS_BRANCH..HEAD" --oneline` is empty. If not: print `Skipping contracts pull — unpushed commits in submodule`.
    - All pass → `git -C contracts pull --rebase origin "$CONTRACTS_BRANCH"`.

    **(iv) Commit pointer bump on the feature branch.** If the submodule pointer advanced, commit it on the feature branch (no push — later steps push the feature branch). This keeps step 1c's "no uncommitted changes" guard clean on the next invocation, and records what contracts SHA each story consumed in `git log features/{feature-id} -- contracts`:
    ```bash
    if git status --porcelain contracts | grep -q .; then
      CONTRACTS_SHA=$(git -C contracts rev-parse --short=12 HEAD)
      git add contracts
      git commit -m "chore: bump contracts pointer to ${CONTRACTS_SHA} for {US-NN} pre-flight"
    fi
    ```

## Next step

Read `steps/step-02-validate-story.md`.
