# tenXengage Development Guide

## How It Works

Every feature starts with a **PRD, BRD, or feature description** — handed to `/create-spec` to produce a spec, the single document both frontend and backend teams use as their source of truth. The spec lives in the `tenxengage-blueprint` repo, shared across teams. API contracts are generated from the spec into `tenxengage-contracts`, enabling parallel development. Quality gates run before PRs to catch issues early.


---

## Workspace Layout

All repos sit as siblings in the same parent folder:

```
any-folder/
  tenxengage-blueprint/       ← Specs, contracts, templates
  tenxengage-contracts/       ← Shared data models, API endpoints, enums, conventions
  tenxengage-backend/         ← Java/Spring Boot backend
  tenxengage-frontend/        ← React/TypeScript frontend
  tenxengage-admin-backend/   ← Admin backend
  tenxengage-admin-frontend/  ← Admin frontend
```

The blueprint repo contains no code — only specs and test plans. The contracts repo is the source of truth for shared data models, API endpoint definitions, enums, and naming conventions — contracts are generated directly into this repo before implementation in any repo. It is also mounted as a git submodule at `contracts/` inside the backend and frontend repos, so both teams access the latest contracts without needing a relative sibling path.

---

## Project Standards

Every repo has a `PROJECT-CONTEXT.md` at its root documenting architecture, conventions, and coding rules. `docs/patterns/` contains detailed reference files for complex areas. `CLAUDE.md` in each repo directs you to read them before starting work — Claude loads this automatically.

| File | Purpose |
|---|---|
| `PROJECT-CONTEXT.md` | Standards, conventions, anti-patterns for the repo |
| `docs/patterns/builder-wizard.md` | Builder/wizard layout, state, animations, file structure |
| `docs/patterns/builder-config.md` | Config-driven dynamic sections and fields |
| `docs/patterns/ai-copilot.md` | SSE streaming, tool-based actions, document pipeline |
| `docs/patterns/permissions-and-feature-flags.md` | 5-layer permission resolution, `@RequiresPermission`, `usePermissions` |
| `docs/patterns/tenant-isolation.md` | TenantAware, `@Filter`, `TenantContext` (backend) |

The blueprint repo has `PROJECT-CONTEXT.md` — an aggregated view of all repo standards used when creating specs.

---

## Local Configuration (One-time Setup)

Each team member must complete these steps once before using the workflow skills and bug tools. The bug CLI skills (`/bug-reporter`, `/bug-fixer`) read credentials from Claude's environment; the in-app bug reporter reads them from the frontend `.env.local` file.

### Step 0 — Install the Superpowers plugin

Run once per machine in any Claude Code session:

```
/plugin install superpowers@claude-plugins-official
```

After the install completes, reload the VSCode window (`Cmd+Shift+P → Reload Window` on Mac, `Ctrl+Shift+P → Reload Window` on Windows/Linux).

This plugin provides the development workflow process skills — brainstorming, writing plans, test-driven development, systematic debugging, code review flows, and more. These power the `--tdd` flag, the review steps in `/ready-check`, and the spec-writing flow used by the project commands.

### Step 1 — Get your ClickUp API token

1. Open ClickUp → click your avatar (bottom-left) → **Settings** → **Apps**
2. Under **API Token**, click **Generate** (or copy your existing token)
3. Keep it handy — you'll paste it in the next two steps

### Step 2 — Find the bugs list ID

Open the ClickUp list where bugs are tracked. The list ID is the numeric segment in the URL:

```
https://app.clickup.com/12345678/v/li/90123456789
                                        ^^^^^^^^^^^  ← this is CLICKUP_BUGS_LIST_ID
```

Ask your team lead if you're unsure which list to use.

### Step 3 — Configure Claude Code (for CLI skills)

Add your credentials to `~/.claude/settings.json` so `/bug-reporter` and `/bug-fixer` can create and update ClickUp tickets:

```json
{
  "env": {
    "CLICKUP_API_TOKEN": "pk_xxxxxxxxxxxxxxxxxxxx",
    "CLICKUP_BUGS_LIST_ID": "90123456789",
    "CLICKUP_BACKLOG_LIST_ID": "90123456790"
  }
}
```

`CLICKUP_BACKLOG_LIST_ID` is optional and separate from the bugs list — used only by `/seed-clickup`. If omitted, `/seed-clickup` prompts you on first run and offers to save it here.

If `~/.claude/settings.json` already exists, merge the `env` block in — don't replace the whole file.

After saving, reload the VSCode window (`Cmd+Shift+P → Reload Window` on Mac, `Ctrl+Shift+P → Reload Window` on Windows/Linux) for the env vars to take effect.

### Step 4 — Configure the frontend dev server (for the in-app bug reporter)

Create or update `tenxengage-frontend/.env.local` with the following variables. This file is gitignored — it never gets committed.

```bash
# ClickUp integration (required for ticket creation from the 🐞 widget)
CLICKUP_API_TOKEN=pk_xxxxxxxxxxxxxxxxxxxx
CLICKUP_BUGS_LIST_ID=90123456789

# Your name — pre-fills the "Reported by" field in the report modal
BUG_REPORTER_NAME=Your Name

# Set to true to enable the ClickUp ticket toggle by default on every report
BUG_REPORTER_AUTO_TICKET=true
```

Restart the dev server after editing `.env.local`.

**Optional tuning variables:**

| Variable | Default | Purpose |
|---|---|---|
| `VITE_BUG_REPORTER_NETWORK_WINDOW_MS` | since last navigation | How far back (ms) to include network entries in a regular capture |
| `VITE_BUG_REPORTER_NOISE_ENDPOINTS` | `/notifications/unread-count` | Comma-separated URL substrings to suppress from network capture (successful calls only; failures always appear) |

### Step 5 — Set up Chrome MCP (for `/bug-reporter --capture`)

Required only if you want CLI-based browser capture. Skip if you'll use the in-app 🐞 widget instead.

**Mac:**

```bash
# Launch Chrome with remote debugging enabled (do this once per machine login)
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug
```

**Windows (PowerShell):**

```powershell
# Launch Chrome with remote debugging enabled (do this once per machine login)
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 `
  --user-data-dir="$env:TEMP\chrome-debug"
```

**Windows (CMD):**

```bat
REM Launch Chrome with remote debugging enabled (do this once per machine login)
"C:\Program Files\Google\Chrome\Application\chrome.exe" ^
  --remote-debugging-port=9222 ^
  --user-data-dir=%TEMP%\chrome-debug
```

Then add the MCP server to `~/.claude.json`:

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": ["chrome-devtools-mcp", "--browserUrl", "http://127.0.0.1:9222"]
    }
  }
}
```

Reload the VSCode window after updating `~/.claude.json` (`Cmd+Shift+P → Reload Window` on Mac, `Ctrl+Shift+P → Reload Window` on Windows/Linux). Chrome must be running on port 9222 **before** the Claude Code session starts — if you see "MCP unavailable", start Chrome first and then reload the window.

### Configuration summary

| Where | File | What it enables |
|---|---|---|
| Global Claude settings | `~/.claude/settings.json` | `/bug-reporter` and `/bug-fixer` ClickUp integration |
| Frontend repo | `tenxengage-frontend/.env.local` | In-app 🐞 bug reporter ClickUp integration |
| Global Claude settings | `~/.claude.json` | `/bug-reporter --capture` browser auto-capture |
| Global Claude settings | `~/.claude/settings.json` `env.CLICKUP_BACKLOG_LIST_ID` | `/seed-clickup` ClickUp backlog list target (optional — prompted on first run) |

---

## Development Workflow

### Step 0: Decompose BRD (Initiative-scoped BRDs only)

**Who**: Any team member
**Where**: `tenxengage-blueprint/` repo

```
/decompose-brd /path/to/initiative-brd.pdf
```

Skip this step for single-feature work — go straight to Step 1.

Slices an initiative BRD into a feature roadmap and writes a digest of cross-cutting context. Outputs `roadmaps/{roadmap-slug}/` on a dedicated branch:

| File | Purpose |
|---|---|
| `roadmaps/{roadmap-slug}/roadmap.md` | Feature index — slices, dependencies, ADR blockers, recommended sequence |
| `roadmaps/{roadmap-slug}/digest.md` | Business truth auto-loaded by every downstream `/create-spec {roadmap-slug} F-NN` run |
| `roadmaps/{roadmap-slug}/features/F-NN-{roadmap-slug}.md` | Per-feature brief read automatically by `/create-spec` |
| `roadmaps/{roadmap-slug}/backlog-seeds.csv` | Story candidates per feature — input to `/seed-clickup` |

Each feature slice is listed in `roadmap.md` with a `/create-spec {roadmap-slug} F-NN` invocation. Run them in the recommended order, or in parallel where dependencies allow.

#### Seed ClickUp (Optional)

**Where**: `tenxengage-blueprint/` repo

```
/seed-clickup
```

Run after `/decompose-brd` to populate ClickUp from `backlog-seeds.csv`. Creates an **Epic** (the roadmap) → **Milestone** (per feature) → **Task** (per story candidate) hierarchy. Idempotent — already-created items are verified and skipped; safe to re-run at any time.

Requires `CLICKUP_API_TOKEN`. Prompts for `CLICKUP_BACKLOG_LIST_ID` on first run and offers to save it to `~/.claude/settings.json`. The backlog list (`CLICKUP_BACKLOG_LIST_ID`) is separate from the bugs list (`CLICKUP_BUGS_LIST_ID`).

### Step 1: Create the Spec

**Who**: Any team member
**Where**: `tenxengage-blueprint/` repo

```
/create-spec "Quiz engine with timed quizzes, scoring, and certificates"
```

Or provide a requirements document:
```
/create-spec /path/to/prd.md
/create-spec /path/to/feature-brd.pdf
```

Claude reads the requirements (from your prompt or the file), then automatically reads `PROJECT-CONTEXT.md` from each repo and relevant `docs/patterns/` files to ground the spec in actual project standards. Pattern files are selected based on the feature — builder features load `builder-wizard.md` and `builder-config.md`; AI features load `ai-copilot.md`; new entities always load `tenant-isolation.md`.

**Scope analysis**: If the requirement is large (many entities, endpoints, pages), Claude decomposes it into sub-requirements using domain boundaries and dependency ordering. It asks you whether to create one spec or separate specs per sub-requirement.

**Implementation tasks**: Every spec includes numbered tasks that can be implemented progressively — ordered by dependency, labeled `[BE]`/`[FE]`, and sized for a single work session. Developers reference these during implementation (e.g., "implement Task 3").

The spec runs in **plan mode** — you review it, ask for changes, and approve. After approval, Claude automatically runs a **15-check architectural review** checking for consistency, security gaps, missing edge cases, and more. If there are ambiguities, Claude asks you clarifying questions interactively. Once approved, you're asked if you want to commit and push.

The spec covers: data model, API endpoints, DTOs, service methods, frontend pages/components, workflow states, edge cases, test scenarios, and implementation tasks. Each section is labeled `[BE]`, `[FE]`, or `[BE + FE]`.

### Step 2: Create Stories

**Who**: Any team member (or the person who created the spec)
**Where**: `tenxengage-blueprint/` repo

```
/create-stories quiz-engine
```

Once the spec is reviewed, decompose it into executable work units before anyone writes code.

The skill reads the reviewed spec and generates:

- **`stories.md`** — story index with a dependency graph showing which stories can run in parallel and which must be sequenced
- **`tasks/foundation.md`** — sequential foundation tasks (F1–Fn, count varies per feature): enums, database migration, JPA entities + repositories + fixtures, permission seeds, BE infrastructure plumbing (Kafka, etc.) — only the tasks the feature actually needs
- **`stories/US-NN-*.md`** — one self-contained file per user story, sized for a single backend or frontend session
- **`tracker.md`** — session status tracker (single source of truth); all implementation sessions claim and update cells here
- **`test-plan.md`** — cross-story integration tests (Testcontainers, multi-entity workflows)

Runs in **plan mode** — review the proposed decomposition, request changes, and approve. The skill aborts if the spec is still `draft`; run `/review-spec` first if needed.

### Step 2b: Create Mockups (Optional)

**Who**: Frontend developer
**Where**: `tenxengage-frontend/` repo

```
/create-mockups quiz-engine
```

After stories are decomposed, optionally generate interactive TSX mockups before writing any production code. Useful when the UI is complex, novel, or needs stakeholder sign-off before implementation begins.

The skill reads your feature's story files from the blueprint, proposes a grouping of stories by screen (e.g., list + detail + builder), and waits for your confirmation. After you confirm the grouping, it generates one TSX file per screen in `src/mockups/{feature-id}/` plus a `FullFeatureMockup.tsx` feature navigator. No `App.tsx` edits are ever needed — `MockupRouter` auto-discovers every file placed under `src/mockups/` via `import.meta.glob`.

**Viewing mockups** — start the dev server and open one of these URLs:

```
npm run dev

# Feature navigator (all screens for this feature + floating nav widget)
http://localhost:5173/mockup/quiz-engine

# Jump directly to a specific screen
http://localhost:5173/mockup/quiz-engine/quiz-list

# Index of every mockup file across all features
http://localhost:5173/mockup/
```

The feature navigator (`/mockup/{feature-id}`) renders `FullFeatureMockup.tsx` — a card-grid index of all screens with a persistent floating nav pill at the bottom so you can switch between screens without leaving the browser tab. It is regenerated automatically on every `/create-mockups` pass.

**Dev-only — excluded from production builds.** Mockup routes are guarded by `import.meta.env.DEV` in `App.tsx`. The conditional is evaluated at build time by Vite, so all mockup code is tree-shaken out of the production bundle. No mockup code, routes, or data ever ships to users.

Each mockup is fully interactive: all UI states (empty/loading/populated), all status variants, realistic mock data, clickable navigation, and a floating MockupControls bar to switch views and states. No production code — pure TSX with the same shadcn/ui components, Tailwind tokens, and Framer Motion patterns the real implementation will use.

**Iterate with Chrome + Claude**: open the mockup in Chrome, open the Claude browser extension (side panel or popup), and describe changes — e.g. "move the status badge left" or take a screenshot and ask Claude to apply edits. Repeat until the mockup matches your vision.

Once mockups are created, the story frontmatter is automatically updated with the mockup file path. When you later run `/load-story`, it silently reads the mockup and uses it as a visual reference during implementation — no extra steps needed. If no mockup exists for a story, `/load-story` proceeds as normal.

Mockups are always optional. Every story can be implemented without one.

### Step 3: Generate API Contracts

**Who**: Contracts owner (or any team member after spec is reviewed)
**Where**: `tenxengage-contracts/` repo

```
/generate-contracts quiz-engine
```

Reads the reviewed spec from the blueprint repo, reads existing patterns from local `conventions.md` and `enums.md`, then generates:

- **Model markdown files** → `models/{model-name}.md`
- **Endpoint YAML files** → `endpoints/{resource}.yaml`
- **New enum sections** → appended to `enums.md` (existing values are never overwritten)

All files are written directly into `tenxengage-contracts` on the `features/quiz-engine` branch and committed automatically.

The spec must have `status: reviewed` before running this step — the skill aborts if it's still `draft`. Run `/review-spec` in the blueprint repo first if needed.

### Step 4: Implement in Parallel

**Who**: Backend developer + Frontend developer (each in their own repo)
**Where**: `tenxengage-backend/` and `tenxengage-frontend/`

Implementation follows a **two-phase story-sliced flow**:

#### Phase A: Foundation Tasks (backend only, sequential)

```
/execute-foundation quiz-engine F1        # implement task F1 (lean mode)
/execute-foundation quiz-engine F1 --tdd  # same, with TDD discipline enabled
```

Runs in the backend repo. Implements the five foundation tasks in fixed order — each must pass before the next begins:

| Task | What it does |
|---|---|
| F1 | Enums (Java enums + DB enum types) |
| F2 | Database migration (Flyway schema) |
| F3 | JPA entities, repositories, and test fixtures (with tenant isolation) |
| F4 | Permission seeds (roles + feature flags) |
| F5 | BE-only infrastructure plumbing (Kafka consumers/producers, etc.) — omitted if not applicable |

Each task follows Red → Green → Refactor. The skill claims the tracker row, creates a local sub-branch, implements, runs tests, pauses for your approval, then squash-merges into the feature branch and flips the tracker to `done`. Run one task per session — invoke `/execute-foundation` once per task (F1, then F2, then F3, etc.). Foundation tasks must complete before any BE story session starts — stories depend on the entities and permissions foundation creates.

Pass `--tdd` to additionally invoke four superpowers skills at their designated steps: `superpowers:test-driven-development` before implementation, `superpowers:systematic-debugging` when tests fail, `superpowers:verification-before-completion` before the approval pause, and `superpowers:requesting-code-review` at the approval pause. Without the flag, the same Red → Green → Refactor process applies — only the skill invocations are skipped.

#### Phase B: User Stories (BE + FE, parallel where independent)

Check what's eligible to pick up from the blueprint repo:
```
/next-eligible quiz-engine
```

This is read-only — it reads the tracker and prints a punch list of foundation tasks and story layers that are unblocked, along with what's still blocked and why.

Then implement one story per session in the backend or frontend repo:
```
/load-story quiz-engine US-01        # lean mode
/load-story quiz-engine US-01 --tdd  # with TDD discipline enabled
```

What `/load-story` does:
- Claims the tracker cell for this story (BE or FE column)
- Reads the story file (`stories/US-01-*.md`) and the relevant spec sections
- Implements all tasks in the story following Red → Green → Refactor
- Runs tests and verifies coverage
- Flips the tracker cell to `done` with the commit SHA

Pass `--tdd` to additionally invoke four superpowers skills at their designated steps: `superpowers:test-driven-development` before implementation, `superpowers:systematic-debugging` when tests fail, `superpowers:verification-before-completion` before the approval pause, and `superpowers:requesting-code-review` at the approval pause. Without the flag, the same process applies — only the skill invocations are skipped.

Stories that touch disjoint entities can run concurrently in separate sessions. The `stories.md` dependency graph and `/next-eligible` output show which stories are safe to parallelize.

**Before writing any code**, read `PROJECT-CONTEXT.md` at the root of your repo (Claude's `CLAUDE.md` directive points you there). Then read any relevant pattern files from `docs/patterns/`:

- **Backend**: `permissions-and-feature-flags.md` and `tenant-isolation.md` for almost everything; `builder-config.md` for configurable sections; `ai-copilot.md` for AI streaming features.
- **Frontend**: `permissions-and-feature-flags.md` for any new page or action; `builder-wizard.md` + `builder-config.md` for builder/wizard UIs; `ai-copilot.md` for AI panel integration.

FE story sessions can start immediately after contracts are generated (scaffold against types + mocks; wire to real BE as foundation completes). Foundation must complete before any BE story session starts.

### Bug Workflow (independent of features)

The bug workflow is fully separate from feature development. It can be used at any time, on any branch, without a spec.

---

#### Bug Reporting

Capture and file bugs before or instead of fixing them. `/bug-reporter` never touches code.

```
/bug-reporter                          # interactive: prompts for description
/bug-reporter "<free text>"            # one-shot: description → ClickUp ticket immediately
/bug-reporter --capture                # auto-capture from your open browser → evidence folder
/bug-reporter --from-evidence <id>     # escalate a captured folder → ClickUp ticket
```

**Three modes in practice:**

| Mode | What you do | What you get |
|---|---|---|
| Inline text | Describe the bug in plain text | ClickUp ticket filed immediately |
| `--capture` | Run the command while the bug is visible in Chrome | Evidence folder with screenshots, console errors, network calls, user/screen context |
| `--from-evidence` | Escalate a folder you captured earlier | ClickUp ticket + evidence folder updated with ticket reference |

**What gets captured automatically (with `--capture`):**

- Full-page and viewport screenshots
- Last 100 `console.error` / `console.warn` entries
- Last 50 network requests (method, URL, status, response snippet)
- Current URL, page title, React Router route
- Logged-in user (email, ID, roles, tenant)
- Active feature flags, app build SHA, browser and viewport info

**Evidence folders** live in `bugs-evidence/` (gitignored) and are named `bug-<8hexchars>` (e.g., `bug-a3f2b1c2`) — a random ID, never derived from the description. Open `bugs-evidence/index.html` to browse all captured bugs.

**ClickUp integration:** bugs filed with a ClickUp ticket ID can be picked up and closed by `/bug-fixer`. The evidence folder's `meta.md` stores the ticket reference so the two tools stay in sync.

**MCP browser setup (required for `--capture`):** See [Local Configuration → Step 5](#local-configuration-one-time-setup) above. Chrome must be running on port 9222 before the Claude Code session starts. If `--capture` says MCP is unavailable, start Chrome with the debugging flag and reload the VSCode window.

Playwright MCP is a fallback when Chrome isn't available — it launches a fresh Chromium with no session, so user context won't be captured.

---

#### In-App Bug Reporter (Frontend Dev Mode)

A floating widget embedded directly in the frontend dev server. Always available during `npm run dev` — no MCP setup, no Chrome flags, no separate tool.

**How to open:**
- Click the 🐞 button fixed to the bottom-right of the app
- Keyboard shortcut: `Cmd+Shift+B` (Mac) / `Ctrl+Shift+B` (Windows/Linux)
- Press `Esc` to close, `Cmd+Enter` (Mac) / `Ctrl+Enter` (Windows/Linux) to submit

**What it captures automatically before the modal even opens:**
- Full-page and viewport screenshots
- Last 50 network requests (method, URL, status, response snippet) — high-frequency polling endpoints (e.g. `/notifications/unread-count`) are suppressed unless they fail
- Console `error` and `warn` entries from the current session
- Current URL, page title, React Router route pattern
- Logged-in user — email, ID, name, roles, tenant, active feature flags (probed from AuthContext, TanStack Query cache, and localStorage)
- Build SHA, app version, environment, browser, viewport size

**The report form:**
1. **What went wrong** (required) — describe the bug in plain text
2. **What should have happened** (optional)
3. **Attachments** — drag or pick up to 5 extra files (screenshots, screen recordings, log files)
4. **Reported by** — pre-filled from the `BUG_REPORTER_NAME` env var; editable
5. **ClickUp ticket toggle** — on by default if `BUG_REPORTER_AUTO_TICKET=true`; toggleable per report

**Session recording mode** — for bugs you need to reproduce step-by-step:

1. Open the modal → click **Start session recording** → the modal closes and the 🐞 button shows a red pulsing dot
2. Navigate through the app and reproduce the bug
3. Click 🐞 again → fill in the description → **Stop & Capture Bug**

Network and console entries are scoped from the recording start time, so you get a clean signal with no pre-existing noise.

**After capture:**
- The Vite dev server writes an evidence folder to `bugs-evidence/bug-<id>/` (e.g., `bugs-evidence/bug-a3f2b1c2/`) — same schema as all other capture methods
- The success screen shows the folder path and, if created, the ClickUp ticket link
- The success screen also shows the `/bug-fixer --evidence <id>` command to fix it immediately

**ClickUp integration:**
When `CLICKUP_API_TOKEN` and `CLICKUP_BUGS_LIST_ID` are set and the ClickUp toggle is on, a ClickUp task is created at submission time — screenshots and network HAR are attached automatically. The ticket ID is embedded in `meta.md` so `/bug-fixer` picks it up as an M3 tracked bug.

**Configuration:** See [Local Configuration → Step 4](#local-configuration-one-time-setup) for the full `.env.local` setup. All variables are server-side only and never included in the browser bundle.

---

#### Bug Fixing

`/bug-fixer` is a full end-to-end loop: reproduce → failing test → fix → ready-check → MRs → learnings.

```
/bug-fixer                                   # scan pending evidence folders, then oldest ClickUp bug
/bug-fixer <clickup-task-id>                 # fix a specific ClickUp task
/bug-fixer --evidence <slug>                 # fix from a captured evidence folder
/bug-fixer --inline "<description>"          # quick inline fix on the current branch
/bug-fixer --standalone "<description>"      # fix on a dedicated branch, no ClickUp ticket
/bug-fixer --wont-fix <slug-or-id> "reason"  # close without fixing
```

**Three modes:**

| Mode | When to use | Branch | MRs | ClickUp updates |
|---|---|---|---|---|
| **M1 Inline** | Quick fix on current feature branch | No new branch | No | No |
| **M2 Standalone** | Fix without a ClickUp ticket | `bug/<id>` | Yes | No |
| **M3 Tracked** | Fix a filed ClickUp ticket | `bug/clickup-<clickup-id>-<id>` | Yes | Yes (status + comments) |

Mode is auto-detected: a ClickUp ID → M3; `--evidence` with a ticket reference → M3; `--inline` → M1; on `main` → M2; on a feature branch → M1.

**What the fix loop does (step by step):**

1. **Normalize** — reads the ticket, evidence folder, or inline text; interprets screenshots and network HAR to form a starting hypothesis; traces the critical data path; prints a summary and waits for your `go`
2. **Duplicate detection** — searches evidence folders, bugs-index, and ClickUp for similar bugs; asks you how to proceed if any are found
3. **Reproduce** — explores the critical path only; forms explicit hypotheses before reading each file
4. **Write failing test** — JUnit (backend), Vitest (frontend), or Playwright E2E (cross-repo); confirms the test fails at an assertion (not a typo / setup error) and the failure message matches the reported symptom
5. **Implement fix** — cause is already pinned by the critical path (Step 1) and the failing test (Step 4); no workarounds, no unrelated changes; if 3+ fix attempts fail, stops and re-examines the path/hypothesis instead of attempting fix #4
6. **Ready-check** — runs `/ready-check` in each affected repo; no MR until green
7. **Verification gate** — runs `superpowers:verification-before-completion`; requires live evidence the test passes, full suite is green, and ready-check is clean
8. **Visual re-confirmation** — if there were screenshots in evidence, Playwright captures before/after and Claude vision confirms the fix is visible
9. **Create MRs** — one per affected repo on the shared branch; ClickUp status → `needs-review`
10. **Learnings** — promotes cross-cutting lessons to `docs/patterns/`, `PROJECT-CONTEXT.md`, or `docs/learnings.md` in the blueprint
11. **bugs-index.md** — appends one line to the blueprint's append-only corpus
12. **Structured final report** — printed to screen; evidence folder updated with root cause, fix summary, and learnings

**Dev's current checkout is never touched** — all work happens in `/tmp/` worktrees (except M1).

**`--wont-fix`**: closes the evidence folder and ClickUp ticket without doing any fix work. No branch, no MRs, no test.

---

#### Bug Workflow Data Surfaces

| Surface | Location | Purpose |
|---|---|---|
| `bugs-evidence/` | Blueprint repo (gitignored) | Staging area for captured bugs; one subfolder per bug |
| `bugs-evidence/index.html` | Auto-generated | Browse all captured bugs in the browser |
| `docs/bugs-index.md` | Blueprint repo (committed) | Append-only historical corpus of all completed fix runs |
| `docs/learnings.md` | Blueprint repo (committed) | Cross-repo learnings promoted from bug fixes (Tier 3) |
| ClickUp list | External | Source of truth for tracked bugs (M3 flow) |

---

### Step 5: Ready Check

**Who**: Each developer before raising a PR
**Where**: Their respective repo

```
/ready-check
```

This runs a series of quality checks on your changes. Each check auto-fixes issues it finds, then re-validates.

**Backend checks:**
| # | Stage | What it does |
|---|---|---|
| 1 | Prerequisites | Build compiles, migrations are valid |
| 2 | Code Review | Checks patterns, conventions, structure |
| 3 | Security Review | Permissions, tenant isolation, input validation |
| 4 | Contract Compliance | Your code matches the API contract |
| 5 | Adversarial Review | Finds production risks (tenant bypasses, race conditions, data corruption). **Blocks** on `critical`/`high` findings with confidence ≥ 0.70; advisory findings pass through. |
| 6 | Tests | Runs tests for changed files, fixes failures |
| 7 | Coverage | Verifies new code has tests |

**Frontend checks:**
| # | Stage | What it does |
|---|---|---|
| 1 | Prerequisites | Build and lint pass, types match contract |
| 2 | Code Review | React patterns, TypeScript strictness |
| 3 | UI/UX Review | Design consistency, responsiveness, states |
| 4 | Security Review | No tokens in storage, XSS prevention |
| 5 | Adversarial Review | What breaks with 0 items? 10,000? Slow network? **Blocks** on `critical`/`high` findings with confidence ≥ 0.70; advisory findings pass through. |
| 6 | Tests | Unit tests + Playwright E2E with mocked APIs |

**Stages that don't apply are automatically marked `NOT_APPLICABLE`** — for example, a bug fix with no contract changes won't run Contract Compliance.

The check tracks progress in a report file. If interrupted, running `/ready-check` again resumes from where it left off. If you make new commits, only the affected stages re-run.

**Running a single step:** Pass a step slug or step number to target one stage in isolation — other stages' report statuses are preserved:

```
/ready-check code-review
/ready-check step:3
/ready-check quiz-engine adversarial-review
/ready-check tests
```

Valid step selectors:

| Number | Backend slug | Frontend slug |
|---|---|---|
| 1 | `prerequisites` | `prerequisites` |
| 2 | `code-review` | `code-review` |
| 3 | `security-review` | `ui-ux-review` |
| 4 | `contract-compliance` | `security-review` |
| 5 | `adversarial-review` | `adversarial-review` |
| 6 | `tests` | `tests` |
| 7 | `coverage` | — |

Both `step:<selector>` and bare `<selector>` are accepted. Step numbers resolve per-repo (e.g., `3` means `security-review` in the backend, `ui-ux-review` in the frontend).

### Step 6: Create PR

**Who**: Each developer after ready-check passes
**Where**: Their respective repo

```
/create-pr
```

Validates the ready-check report (all stages must be passed or not applicable at the current commit), pushes the branch, and creates a PR/MR. Works with GitHub, GitLab, Bitbucket, or any git platform.

---

## Quick Reference: All Commands

### Feature Development

| Command | Where to run | What it does |
|---|---|---|
| `/decompose-brd /path/to/brd.pdf` | blueprint repo | Slice an initiative BRD into a feature roadmap + digest; produces `roadmaps/{roadmap-slug}/` on a dedicated branch |
| `/seed-clickup` | blueprint repo | Seed ClickUp Epic → Milestone → Task hierarchy from a roadmap's `backlog-seeds.csv`; idempotent, safe to re-run |
| `/create-spec {roadmap-slug} F-NN` | blueprint repo | Generate a feature spec from a roadmap slice; reads digest + feature brief automatically; branch cut from `roadmaps/{roadmap-slug}` |
| `/create-spec "description"` | blueprint repo | Generate a feature spec from a prompt; creates feature branch in blueprint + contracts |
| `/create-spec /path/to/file` | blueprint repo | Generate a feature spec from a PRD/BRD file |
| `/review-spec <feature-slug>` | blueprint repo | Re-validate a spec (standalone) |
| `/create-stories <feature-slug>` | blueprint repo | Decompose reviewed spec into stories, foundation tasks, tracker, and test plan |
| `/next-eligible <feature-slug>` | blueprint repo | Print which foundation tasks and story layers are eligible to pick up next (read-only) |
| `/create-mockups <feature-slug>` | frontend repo | Create interactive TSX mockups for FE stories; groups stories by screen, writes mockup_file back to story frontmatter |
| `/create-mockups <feature-slug> US-01,US-03` | frontend repo | Same but scoped to specific story IDs |
| `/generate-contracts <feature-slug>` | contracts repo | Generate API contracts from reviewed spec, write + commit directly into contracts repo |
| `/load-spec` | backend or frontend | Load spec and contracts into context; auto-detects feature ID from the current branch name so you don't need to specify it repeatedly |
| `/load-spec <feature-slug>` | backend or frontend | Same, but with an explicit feature ID (use when not on the feature branch) |
| `/execute-foundation <feature-slug> F1` | backend | Implement foundation task F1 (one task per session; run once per task, in order) |
| `/execute-foundation <feature-slug> F1 --tdd` | backend | Same, with TDD + debugging + verification + code-review skill invocations enabled |
| `/load-story <feature-slug> US-NN` | backend or frontend | Implement one user story: claims tracker cell, implements, marks done |
| `/load-story <feature-slug> US-NN --tdd` | backend or frontend | Same, with TDD + debugging + verification + code-review skill invocations enabled |
| `/ready-check` | backend or frontend | Run all quality gates on your changes |
| `/ready-check <step>` | backend or frontend | Run a single step (e.g., `code-review`, `tests`, `step:3`) |
| `/run-tests` | backend or frontend | Run tests for changed files (standalone) |
| `/ui-ux-review` | frontend only | Review UI/UX quality (standalone) |
| `/create-pr` | backend or frontend | Validate report and create PR/MR |

### Bug Workflow

| Command | Where to run | What it does |
|---|---|---|
| `/bug-reporter` | blueprint repo | Interactive — prompts for description, files a ClickUp ticket |
| `/bug-reporter "<text>"` | blueprint repo | One-shot — description → ClickUp ticket immediately |
| `/bug-reporter --capture` | blueprint repo | Auto-capture from open browser → evidence folder named `bug-<id>` (no ClickUp) |
| `/bug-reporter --from-evidence <id>` | blueprint repo | Escalate evidence folder → ClickUp ticket |
| `/bug-fixer` | blueprint repo | Scan pending evidence folders, then oldest ClickUp bug → full fix loop |
| `/bug-fixer <clickup-id>` | blueprint repo | Fix a specific ClickUp task (M3 tracked) |
| `/bug-fixer --evidence <id>` | blueprint repo | Fix from an evidence folder (M2 or M3) |
| `/bug-fixer --inline "<text>"` | any repo | Quick inline fix on current branch, no MR (M1) |
| `/bug-fixer --standalone "<text>"` | any repo | Fix on a dedicated branch, no ClickUp ticket (M2) |
| `/bug-fixer --wont-fix <id-or-clickup-id> "reason"` | blueprint repo | Close bug without fixing; updates evidence folder + ClickUp |

---

## Branching

All repos use the same branch name for a feature: `features/<slug>` (e.g., `features/quiz-engine`).

Branches are created automatically:
- **Blueprint + contracts**: created by `/create-spec` before the spec files are written; `/create-stories` also ensures the branch exists before writing story files
- **Backend + frontend**: created by `/execute-foundation` or `/load-story` on their first run in each repo

Any branch name works — you'll just be asked to provide the feature ID manually when running commands that need it. Developers can push to any branch at any time — the quality gate is at PR creation, not at push.

### Configuring the Base Branch

By default, every feature branch is cut from `main`. To change this, set the `FEATURE_BASE_BRANCH` environment variable in your Claude settings.

**Global (all repos)** — add to `~/.claude/settings.json`:

```json
{
  "env": {
    "FEATURE_BASE_BRANCH": "develop"
  }
}
```

**Per-repo override** — add to `{repo}/.claude/settings.json`:

```json
{
  "env": {
    "FEATURE_BASE_BRANCH": "release/2.0"
  }
}
```

The variable is read at runtime by every skill that creates a feature branch. If it is not set, skills fall back to `main`. Skills that read it:

| Skill | Repo | Where it's used |
|---|---|---|
| `/create-spec` | blueprint | Feature branch in blueprint + contracts repo |
| `/create-stories` | blueprint | Feature branch in blueprint repo |
| `/generate-contracts` | contracts | Feature branch in contracts repo |
| `/execute-foundation` | backend | Feature branch creation + contracts submodule fallback |
| `/load-story` | backend, frontend | Feature branch creation + contracts submodule fallback |

---

## Feature Naming

Features use slug-only kebab-case folder names:

```
quiz-engine
bulk-import
partner-onboarding
```

`/create-spec` checks that `features/<slug>/` does not already exist; if it does, it asks the user to pick a different slug.

---

## Large Features & Progressive Development

**Initiative-scoped (multiple features, multiple personas):** Run `/decompose-brd /path/to/brd.pdf` first. It slices the BRD into a feature roadmap and writes a digest of cross-cutting context. Each slice in the roadmap is then implemented with `/create-spec {roadmap-slug} F-NN`, which reads the digest automatically — no copy-paste needed. Teams can work slices in the recommended sequence, or in parallel where dependencies allow.

**Single-feature:** Run `/create-spec` directly with a description or file path. When the requirement is large enough to span multiple domains, `/create-spec` will detect this and suggest splitting into sub-requirements, each with its own spec and feature ID. Within a single spec, implementation tasks are numbered and ordered by dependency, labeled `[BE]` or `[FE]`, so each team knows exactly what to work on and in what order.

---

## What If...

**...I have a large initiative BRD covering multiple features?**
Run `/decompose-brd /path/to/brd.pdf` in the blueprint repo. It slices the BRD into a feature roadmap (`roadmaps/{roadmap-slug}/roadmap.md`) and writes a digest of cross-cutting context. Each feature slice is listed with a `/create-spec {roadmap-slug} F-NN` invocation you can run directly — no copy-paste needed.

**...the adversarial review step fails saying Codex is unavailable?**
Codex CLI is not authenticated. Run `codex login` in your terminal (uses your ChatGPT/OpenAI account) or add `OPENAI_API_KEY` to the `env` block in `~/.claude/settings.json`. See [Step 6 — Set up Codex](#step6) in the Local Configuration section for full instructions.

**...I'm doing a bug fix with no spec?**
Work normally. When running `/ready-check`, provide `none` as the feature ID. Stages like Contract Compliance will be marked `NOT_APPLICABLE`.

**...I need to change the spec after implementation started?**
Edit the spec in the blueprint repo, run `/review-spec` to re-validate, and re-generate contracts if the API changed.

**...the frontend needs something not in the spec?**
Discuss with the team, update the spec, and re-generate contracts if needed. The spec is a living document.

**...I want to run just the tests, not the full ready-check?**
Use `/run-tests` — it runs tests scoped to your changes and updates the ready-check report.

**...I want to review just the UI/UX?**
Use `/ui-ux-review` in the frontend repo — standalone review against design standards.

**...I forgot to create a spec and already started coding?**
That's fine. You can create the spec retroactively, or run `/ready-check` with feature ID `none`.

**...the requirement is too big for one spec?**
`/create-spec` will detect this and suggest splitting into sub-requirements. You decide whether to split or keep it as one spec with ordered implementation tasks.

**...I have a PRD/BRD document instead of typing out the requirement?**
Pass the file path directly: `/create-spec /path/to/document.pdf`. Claude reads it, summarizes the requirements, and confirms with you before generating the spec.

**...the adversarial review failed?**
The step blocks when it finds `critical` or `high` severity findings with confidence ≥ 0.70. The summary output lists each blocking issue with its file, line range, risk, and recommended fix. Fix the issues, then re-run just that step: `/ready-check adversarial-review`. Advisory findings (lower severity or confidence) are captured in the report but do not block the PR.

**...I want to re-run just one ready-check step?**
Use `/ready-check <slug-or-number>` — for example `/ready-check tests` or `/ready-check step:2`. The targeted step runs, the report updates for that step only, and the overall status recalculates. Other steps keep their existing statuses.

**...I want to know what story to work on next?**
Run `/next-eligible <feature-slug>` in the blueprint repo. It reads the tracker and prints a punch list — foundation tasks and story layers that are unblocked, plus what's still blocked and why. It never modifies anything.

**...I want to implement a specific story?**
Run `/load-story <feature-slug> US-NN` in the backend or frontend repo. The skill claims the tracker cell, implements all tasks for that story with TDD, runs tests, and flips the tracker to `done` when complete. One story per session.

**...I found a bug and want to report it quickly?**
Run `/bug-reporter "<description>"` from the blueprint repo. It files a ClickUp ticket immediately with no browser required. If the bug is currently visible in Chrome, use `--capture` instead to automatically grab screenshots, console errors, and network calls.

**...I want to capture a bug visually before filing a ticket?**
Run `/bug-reporter --capture` while the bug is visible in your browser. This creates an evidence folder in `bugs-evidence/bug-<id>/` (e.g., `bugs-evidence/bug-a3f2b1c2/`) with screenshots, console logs, and network data. Open `bugs-evidence/index.html` to browse captured bugs. File the ClickUp ticket later with `--from-evidence <id>`.

**...`--capture` says MCP is unavailable?**
Chrome must be running with `--remote-debugging-port=9222` before the Claude Code session starts. Start Chrome with that flag, then reload the VSCode window (`Cmd+Shift+P → Reload Window` on Mac, `Ctrl+Shift+P → Reload Window` on Windows/Linux). See the "MCP browser setup" section above for the full setup steps.

**...I want to fix a bug that's already been filed in ClickUp?**
Run `/bug-fixer <clickup-task-id>` from the blueprint repo. The skill reads the ticket, normalizes the bug, traces the critical data path, prints a summary for your `go`, then runs the full reproduce → test → fix → ready-check → MR loop.

**...I want to fix the oldest open bug in the queue?**
Run `/bug-fixer` with no arguments. It first scans `bugs-evidence/` for pending evidence folders, then falls back to the oldest open ClickUp bug.

**...I want to fix a quick bug without filing a ticket or creating a branch?**
Use `/bug-fixer --inline "<description>"`. It runs the same reproduce → fix loop but commits directly to your current branch with no MR. Best for trivial issues or bugs found while already on a feature branch.

**...a bug turns out to not be worth fixing?**
Run `/bug-fixer --wont-fix <slug-or-id> "reason"`. It updates the evidence folder status and posts a comment to ClickUp (if a ticket exists), then closes it. No fix work is done.

**...I see `bugs-evidence/` filling up with test captures?**
The folder is gitignored — it never gets committed. Evidence folders are retained for historical reference. If you want to clean up, delete them manually. The `index.html` browse page regenerates automatically the next time any bug tool runs.

**...a bug fix touches both the backend and frontend?**
`/bug-fixer` handles cross-repo bugs automatically. It creates one branch and one worktree per affected repo (`/tmp/bugfix-<slug>-backend/`, `/tmp/bugfix-<slug>-frontend/`), runs `/ready-check` in each, and creates one MR per repo on the same shared branch name. Your current checkout in either repo is never touched.

**...I want to create mockups for FE stories before implementing them?**
Run `/create-mockups <feature-slug>` from the frontend repo after `/create-stories` has been run. The skill proposes a screen grouping (which stories share a screen), waits for your confirmation, then generates per-screen TSX mockups plus a `FullFeatureMockup.tsx` feature navigator. Routes are auto-served by `MockupRouter` — no `App.tsx` edits needed. Open `http://localhost:5173/mockup/<feature-slug>` to see the feature navigator with a floating pill to switch between screens, or go directly to `/mockup/<feature-slug>/{screen-slug}` for a specific screen. Mockup routes are dev-only and excluded from production builds. Review mockups in Chrome using the Claude extension, iterate until satisfied, then proceed to implementation — `/load-story` will automatically pick up the mockup paths. Mockups are always optional.

**...I want maximum discipline with TDD skill, structured debugging, and formal review?**
Pass `--tdd` to `/execute-foundation` or `/load-story`. This enables four superpowers skill invocations: `superpowers:test-driven-development` before implementation, `superpowers:systematic-debugging` when tests fail, `superpowers:verification-before-completion` before the approval pause, and `superpowers:requesting-code-review` at the approval pause. Without the flag, no TDD discipline is imposed — neither Red → Green → Refactor nor the skill invocations apply.

**...my team branches features from `develop` (or another base branch) instead of `main`?**
Set `FEATURE_BASE_BRANCH` in your Claude settings. Add `"FEATURE_BASE_BRANCH": "develop"` to the `env` section of `~/.claude/settings.json` to apply globally across all repos, or to a specific repo's `.claude/settings.json` to override just that repo. All feature-branching skills (`/create-spec`, `/generate-contracts`, `/load-spec`, `/execute-foundation`, `/load-story`) read this variable at runtime and fall back to `main` if it is not set. See the **Configuring the Base Branch** section under Branching for the full setup.

**...the foundation tasks aren't done yet but I want to start FE work?**
Go ahead — FE story sessions can start immediately after contracts are generated. Scaffold against the generated types and mock the API; wire to real BE endpoints as foundation completes. Foundation must finish before any BE story session starts, since BE stories depend on the entities and permissions foundation creates.
