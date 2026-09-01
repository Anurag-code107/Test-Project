---
name: "create-pr"
description: "Validate the ready-check report and prepare a pull/merge request. Works with any git platform (GitHub, GitLab, Bitbucket, etc.). Blocks if the report is missing, stale, has failed steps, or has steps awaiting user review."
argument-hint: "Optional: PR/MR title"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Step 1: Validate Ready-Check Report

1. **Get branch name**: `git branch --show-current`
2. **Locate report**: `.ready-check/{branch-name}/review.json`
3. **If report doesn't exist**:
   ```
   ERROR: No ready-check report found for branch '{branch-name}'.
   Run /ready-check before creating a PR/MR.
   ```
   STOP — do not proceed.

4. **Read the report and validate**:
   - **Commit check**: `headCommit` in the report must match current `git rev-parse HEAD`
     - If stale: "Report validated at {old-commit}, but HEAD is now {new-commit}. Run /ready-check again."
     - STOP.
   - **Steps check**: Every step must have status `passed` or `not_applicable`
     - If any step is `failed` or `pending`: "Steps not passed: {list}. Run /ready-check to fix."
     - If any step is `awaiting-user-review` (Step 5 only): "Step 5 needs an adversarial Codex review. Run /codex:adversarial-review --wait --base main (or paste the Codex output into the conversation), then re-run /ready-check 5."
     - STOP.

5. **If all checks pass**: Continue to Step 2.

---

## Step 2: Ensure Branch is Pushed

1. Check if the branch tracks a remote: `git rev-parse --abbrev-ref @{upstream}`
2. If not tracking or behind remote:
   - Push: `git push -u origin {branch-name}`
3. If already up to date: skip push.

---

## Step 3: Gather PR/MR Information

1. Get the list of commits: `git log main..HEAD --oneline`
2. Get the diff summary: `git diff main --stat`
3. If feature ID is available from the report (`featureId` field, not `none`):
   - Read the spec title from `../tenxengage-blueprint/features/{feature-id}/spec.md`
4. If user provided a title, use it. Otherwise, generate one from the commits.

---

## Step 4: Generate PR/MR Description

Build a description with:

```markdown
## Summary
{Brief description of changes from commits}

## Spec
{If feature ID exists: "Feature: {feature-id} — {spec title}"}
{If none: "No spec — bug fix / ad-hoc change"}

## Ready-Check Results
| Stage | Status | Details |
|---|---|---|
| Prerequisites | {PASSED} | |
| Code Review | {PASSED} | {N} fixes applied |
| Security Review | {NOT_APPLICABLE} | No controller changes |
| Contract Compliance | {PASSED} | |
| Adversarial Review | {PASSED} | {N} findings addressed |
| Tests | {PASSED} | {test names} |
| Coverage | {PASSED} | |

Validated at commit: {short-hash}
```

---

## Step 5: Detect Platform & Create PR/MR

1. **Detect the git remote platform** from the remote URL:
   - `git remote get-url origin`
   - If contains `github.com` → GitHub
   - If contains `gitlab.com` or `gitlab` → GitLab
   - If contains `bitbucket` → Bitbucket
   - Otherwise → Unknown

2. **Create the PR/MR based on platform**:

   **GitHub** (if `gh` CLI is available):
   ```bash
   gh pr create --title "{title}" --body "{description}"
   ```

   **GitLab** (if `glab` CLI is available):
   ```bash
   glab mr create --title "{title}" --description "{description}"
   ```

   **If no CLI tool is available or platform is unknown**:
   Output the PR/MR details and a link the user can open:
   ```
   Branch pushed. Create your PR/MR manually:

     Title: {title}
     Base: main
     Branch: {branch-name}
     Description: (copied to clipboard or shown below)

     {For GitLab}: {remote-url}/-/merge_requests/new?merge_request[source_branch]={branch-name}
     {For GitHub}: {remote-url}/compare/main...{branch-name}
   ```

3. Ask the user to confirm before creating.

---

## Output

```
PR/MR created successfully!
  URL: {PR/MR URL}
  Title: {title}
  Branch: {branch-name} → main

Ready-check: All stages passed at commit {short-hash}
```

Or if manual creation:
```
Branch '{branch-name}' is pushed and ready.
Ready-check: All stages passed at commit {short-hash}

Create your PR/MR at:
  {URL}
```
