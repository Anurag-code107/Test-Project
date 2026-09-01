---
name: bug-reporter
description: AI-assisted bug filer. Produces ClickUp tickets and/or evidence folders from human input, MCP browser auto-capture, or evidence folder escalation. Use when the user says "report bug", "capture bug", "file a bug", or "report a clickup bug".
---

# Bug Reporter Skill

Creates ClickUp tickets and/or `bugs-evidence/` folders from any input source. Does NOT fix — pass to `/bug-fixer` after reporting.

## Invocations

```
/bug-reporter                          # interactive: prompts for description
/bug-reporter "<free text>"            # one-shot description → ClickUp ticket
/bug-reporter --from-evidence <slug>   # escalate captured folder → ClickUp ticket
/bug-reporter --capture                # MCP browser auto-capture → evidence folder (no ClickUp)
/bug-reporter --capture <slug>         # same, with explicit slug
```

## Shared contracts

Read before acting:
- `_bug-shared/clickup-client.md` — all ClickUp API patterns
- `_bug-shared/clickup-lifecycle.md` — status vocabulary (initial status = `pending`)
- `_bug-shared/payload-schema.md` — canonical ticket shape and `reporter_source` enum
- `_bug-shared/classify-repos.md` — repo classification signals
- `_bug-shared/evidence-schema.md` — meta.md schema and folder conventions
- `_bug-shared/redaction.md` — what to redact before external writes
- `_bug-shared/mcp-capture-keys.md` — probe strategy for user/screen/env context

---

## Flow

### Step 1 — Collect raw input

**Interactive / inline text:**
- Prompt: "Describe the bug. Paste any error messages, stack traces, or screenshots."
- Accept clipboard image paste.
- Ask for a short title if not provided.

**`--from-evidence <slug>`:**
- Read `bugs-evidence/bug-<id>/meta.md` + all attachment files.
- Use the Read tool on each screenshot in `screenshots/` — including `screenshots/extra-*.png` (user-attached files from the in-app reporter, up to 5).
- **Session recording:** If `source: in-app-reporter` in meta.md, console and network entries may be scoped to a recording window (developer clicked "Start session recording" before reproducing). Fewer than 100/50 entries is expected — not a capture gap.
- Reconstruct the bug summary from the folder contents.
- **Check `ticket` field in meta.md.** If it is set to anything other than `-`, a ClickUp ticket already exists (likely created by the in-app reporter). Do not create a duplicate — proceed to Step 7 and print the existing ticket URL. Only create a new ticket when `ticket: -`.

**`--capture`:**
- Invoke Playwright or Chrome DevTools MCP against the dev's current browser.
- Capture the following (all best-effort; missing fields are omitted, never hard-fail):

  **Visual:**
  - Full-page screenshot via `page.screenshot({ fullPage: true })`
  - Viewport screenshot via `page.screenshot({ fullPage: false })`

  **Screen context (optional):**
  - Current URL, pathname, query string
  - `document.title`
  - React Router route pattern from `[data-route-pattern]` attribute or `window.__APP__?.currentRoute`
  - Screen name from `[data-screen-name]` attribute or `window.__APP__?.screenName`

  **User context (optional, per `mcp-capture-keys.md` probe strategy):**
  - Email, user ID, display name, roles
  - Tenant ID + name
  - Active feature flags

  **Build/env context (optional):**
  - Build SHA from `<meta name="build-sha">` or `VITE_BUILD_SHA`
  - App version, environment label, browser user-agent, viewport size

  **Runtime diagnostics:**
  - Last 100 `console.error` + `console.warn` entries (via `Runtime.consoleAPICalled` CDP or injected JS)
  - Last 50 network requests — **all calls, not just errors** (method, URL, status, timestamp, response snippet). High-frequency polling endpoints (e.g. `/notifications/unread-count`) are noise-filtered and omitted unless they fail (status ≥ 400 or network error `0`). Noise list is configurable via `VITE_BUG_REPORTER_NOISE_ENDPOINTS` in the frontend `.env`.

  Then ask the dev: "Describe what went wrong (one sentence is enough)."

---

### Step 2 — Classify affected repos

Use signals from `_bug-shared/classify-repos.md`. If ambiguous → ask the dev: "I can't confidently classify this as frontend/backend/other. Which repo(s) should I target?"

---

### Step 3 — Redact sensitive data (external writes only)

Apply all patterns from `_bug-shared/redaction.md` to any text destined for ClickUp or MR descriptions. Do NOT redact local evidence folder contents.

---

### Step 4 — Generate canonical ticket fields

From the raw input + classification, produce:
- Clean title: short imperative description only, max 80 chars — **no repo prefix** (repo is captured in `affected_repos` custom field, not the title)
- Structured description body (Observed / Expected / Reproduction / Environment sections from `payload-schema.md`)
- `affected_repos` tags
- `reporter_source` value (see `payload-schema.md` enum)

---

### Step 5 — Write to target

**Default (`/bug-reporter "<text>"`):  → ClickUp task**
```bash
# Use clickup-client.md "Create a new task" pattern
# Set status: pending (from clickup-lifecycle.md)
# Set custom fields if they exist (reporter_source, affected_repos)
# Print: "Created ClickUp task <ID>: https://app.clickup.com/t/<ID>"
```

**`/bug-reporter --capture` → evidence folder**

ID generation: run `openssl rand -hex 4` to get an 8-char hex ID.
Folder: `bugs-evidence/bug-<id>/`

Write:
- `meta.md` (use schema from `evidence-schema.md`; populate all captured optional fields under `user:`, `screen:`, `env:`)
- `screenshots/01-viewport.png`
- `screenshots/02-full-page.png`
- `console.log` (if captured)
- `network.har` (if captured; JSON format)

Print: "Captured → `bugs-evidence/bug-<id>/`"

**`/bug-reporter --from-evidence <slug>` → ClickUp task + update folder**

Create ClickUp task (same as default flow). Then update `meta.md`:
- Set `ticket: <new-task-id>`
- Set `last-updated: <ISO timestamp>`

Print: "Created ClickUp task <ID>. Evidence folder updated with ticket reference."

---

### Step 6 — Regenerate browse page

After any write that touched an evidence folder:

```bash
BLUEPRINT_ROOT="<resolve from skill location>"
node "$BLUEPRINT_ROOT/.claude/skills/_bug-shared/generate-bug-list.mjs"
```

Print: "Browse page updated → open `bugs-evidence/index.html`"

---

### Step 7 — Return

**ClickUp case:** task ID + URL
**Evidence capture case:** folder path + "Run `/bug-fixer --evidence <slug>` to fix."
**From-evidence case:** both the new ticket URL and the updated folder path.

---

## What this skill does NOT do

- Does NOT fix anything — pass the output to `/bug-fixer`
- Does NOT commit or push
- Does NOT create branches
- Does NOT run tests
- Does NOT auto-close or update ClickUp status (only sets initial `pending`)

---

## Error handling

| Situation | Action |
|---|---|
| ClickUp API 401 | Stop — "Check your CLICKUP_API_TOKEN" |
| ClickUp API 404 | Stop — "CLICKUP_BUGS_LIST_ID not found. Check the value." |
| MCP / Playwright not available | Print: "MCP browser capture unavailable. See Appendix A for setup. Falling back to interactive mode." |
| Chrome DevTools MCP tools missing despite config | Chrome wasn't running on port 9222 when session started. Start Chrome with `--remote-debugging-port=9222`, then reload VSCode window. |
| Playwright browser on blank tab | Navigate to the app URL. Check for running dev servers on ports 3000, 5173, 8080 and navigate there automatically if no URL provided. |
| Config changed but MCP not connecting | MCP servers only connect at session start — reload VSCode window after any `~/.claude.json` change. |
| Evidence folder not found for `--from-evidence <slug>` | Stop — "No evidence folder found matching `<slug>`" |
| Can't write to evidence folder (permissions) | Stop — "Can't write to `bugs-evidence/`. Check directory exists and has write permission." |
| Classification ambiguous | Ask the dev before writing |
| Custom fields missing in ClickUp | Print setup instructions (from `clickup-lifecycle.md`); proceed with built-in fields only |

---

## Appendix A — MCP Browser Setup

**Recommended: Option 1 (Chrome DevTools MCP).** It connects to your existing logged-in Chrome session, so user context (auth, tenant, feature flags) is captured automatically. Option 2 (Playwright) is a fallback when Chrome isn't available — it starts a fresh Chromium with no session.

Do not configure both at the same time — when both are present, the skill may use either one, making behaviour unpredictable.

After editing `~/.claude.json`, **reload the VSCode window** (`Cmd+Shift+P` → Reload Window) for the new MCP server to connect.

---

### Option 1: Chrome DevTools MCP (recommended)

**Step 1 — Launch Chrome with remote debugging:**

If no Chrome session is open yet:
```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug
```

If Chrome is already open, you must use `--user-data-dir` with a separate profile to avoid the flag being ignored:
```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug
```

Then navigate to your app and log in as normal.

**Step 2 — Add to `~/.claude.json`:**
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

**Package:** `chrome-devtools-mcp` (maintained by Google/ChromeDevTools team). Verified working: v0.23.0+.

**Advantage:** Captures real user session — auth, tenant, feature flags are all present.  
**Caveat:** Chrome must be running on port 9222 *before* the Claude Code session starts. If Chrome isn't running when the session loads, the server fails to connect silently and capture won't work until you reload.

---

### Option 2: Playwright MCP (fallback)

Add to `~/.claude.json`:
```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp"]
    }
  }
}
```

**Package:** `@playwright/mcp`. Verified working: v0.0.70+.

**Advantage:** No Chrome required — always available.  
**Disadvantage:** Launches a fresh Chromium with no session. You must navigate to the app and log in before capture. User context (`user:` fields in meta.md) will be empty unless you log in first.

When using Playwright, the skill will check for running local dev servers on common ports (3000, 5173, 8080) and navigate there automatically if no URL is provided.

---

### Tool preference when both are configured

If both `chrome-devtools` and `playwright` are in `mcpServers`, the skill prefers Chrome DevTools tools (`mcp__chrome_devtools__*`) over Playwright tools (`mcp__playwright__*`). Remove `playwright` from `mcpServers` if you want to avoid ambiguity.

---

### Screenshot file handling

When using Playwright, `browser_take_screenshot` saves files relative to the blueprint root. The skill moves them into `bugs-evidence/<date>-<slug>/screenshots/` after capture — the tmp files (`bugs-evidence-viewport-tmp.png`, `bugs-evidence-fullpage-tmp.png`) are cleaned up automatically.

---

### Graceful no-op

If neither MCP server is available, `bug-reporter --capture` prints:
```
MCP browser capture not available.
To enable it, set up one of the MCP servers listed in SKILL.md Appendix A.
Falling back to interactive mode — please describe the bug below.
```