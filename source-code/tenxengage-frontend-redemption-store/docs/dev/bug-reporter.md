# Dev Bug Reporter

A dev-only tool for capturing and filing bugs directly from the running frontend. It takes a full-page screenshot, console errors, failed network requests, and user/screen context — then saves everything to a local evidence folder (and optionally files a ClickUp ticket automatically).

Visible only in `import.meta.env.DEV` builds. Zero code ships to production.

---

## Quick start

1. Run `npm run dev` as normal.
2. Click the **🐛 button** in the bottom-right corner (or press `Cmd+Shift+B` on Mac / `Ctrl+Shift+B` on other OS).
3. Type a one-line description of what went wrong.
4. Press **Capture Bug** (or `Cmd+Enter`).
5. The tool captures everything automatically and saves an evidence folder in `tenxengage-blueprint/bugs-evidence/`.

After capture, the success modal shows:

- The evidence folder path (`bugs-evidence/YYYY-MM-DD-<slug>/`)
- A ClickUp ticket link (if auto-ticket is enabled — see below)
- The Claude Code command to fix it: `/bug-fixer --evidence <slug>`

---

## What gets captured automatically

All fields are **best-effort and optional** — if the app isn't logged in or a value isn't available, that field is simply omitted. The capture never hard-fails.

| Category | What |
|---|---|
| **Screenshots** | Viewport screenshot + full-page screenshot (via `html2canvas`) |
| **Screen context** | Current URL, pathname, query string, page title, React Router route pattern, human-readable screen name |
| **User context** | Email, user ID, name, roles, tenant ID + name, active feature flags (read from TanStack Query cache) |
| **Environment** | Build SHA (from `VITE_BUILD_SHA` env var or `<meta name="build-sha">`), app version, browser user-agent, viewport size |
| **Console logs** | Last 100 `console.error` and `console.warn` entries (captured via patched console + `window.onerror` + `unhandledrejection`, installed at app boot) |
| **Network errors** | Last 50 failed requests — method, URL, HTTP status, response snippet (captured via axios interceptor, installed at app boot) |

---

## Evidence folder structure

```
tenxengage-blueprint/bugs-evidence/
  index.html                          ← auto-regenerated browse UI (open in browser)
  2026-04-23-login-typo/
    meta.md                           ← frontmatter: status, user, screen, env, ticket
    screenshots/
      01-viewport.png
      02-full-page.png
    console.log                       ← if any errors/warnings were captured
    network.har                       ← if any failed requests were captured
```

The entire `bugs-evidence/` folder is gitignored — nothing here gets committed.

To browse all captured bugs:

```bash
open tenxengage-blueprint/bugs-evidence/index.html
```

---

## ClickUp auto-ticket (optional)

When enabled, every bug capture also creates a ClickUp task automatically. The ticket is created server-side by the Vite plugin (Node.js) — your API token never touches the browser.

### Enable it

Copy `.env.example` to `.env.local` (gitignored) and set:

```bash
BUG_REPORTER_AUTO_TICKET=true
CLICKUP_API_TOKEN=pk_your_token_here
CLICKUP_BUGS_LIST_ID=your_list_id_here
```

**Where to get these values:**

| Variable | How to find it |
|---|---|
| `CLICKUP_API_TOKEN` | ClickUp → Settings → Apps → "API Token" → copy the `pk_...` value |
| `CLICKUP_BUGS_LIST_ID` | Open the bugs list in ClickUp. The numeric ID is in the URL: `https://app.clickup.com/<team>/<space>/list/<LIST_ID>` |

Restart `npm run dev` after editing `.env.local` for changes to take effect.

### What the ticket looks like

Title: `[frontend] <your description>`

Body sections: Observed, Expected, Reproduction steps, Environment (with URL, user email, tenant, build SHA, browser).

The ticket is created with status `pending`. The evidence folder's `meta.md` is updated with the ticket ID automatically.

### When auto-ticket is off

Evidence is still captured to `bugs-evidence/` as normal. You can escalate any folder to a ClickUp ticket manually at any time:

```
/bug-reporter --from-evidence <slug>
```

---

## Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Cmd+Shift+B` (Mac) / `Ctrl+Shift+B` | Toggle reporter open/closed |
| `Cmd+Enter` / `Ctrl+Enter` | Submit (while description textarea is focused) |
| `Esc` | Close modal |

---

## Fix a captured bug with Claude Code

After capture, run in the Claude Code terminal:

```
/bug-fixer --evidence <slug>
```

Where `<slug>` is the folder name suffix (e.g., `login-typo` from `2026-04-23-login-typo`). Bug-fixer will normalize the evidence, reproduce the bug, write a failing test, implement the fix, run a ready-check, and open an MR.

---

## MCP browser capture (Claude Code alternative)

Instead of the in-app button, you can trigger a capture directly from Claude Code using a browser MCP server. This is useful when you want Claude to capture the current state of the browser programmatically.

```
/bug-reporter --capture
```

Claude will take screenshots, read console + network state, ask for a one-line description, and write the evidence folder — same output as the in-app reporter.

**Use one option only.** Configuring both MCP servers at the same time leads to unpredictable behaviour — the skill may use either one.

After editing `~/.claude.json`, **reload the VSCode window** (`Cmd+Shift+P` → Reload Window) for the new server to connect.

---

### Setup: Option 1 — Chrome DevTools MCP (recommended)

Connects to your existing Chrome session — already logged in, user context captured automatically.

**Step 1:** Launch Chrome with remote debugging enabled:

```bash
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug
```

> The `--user-data-dir` flag is required. Without it, Chrome ignores `--remote-debugging-port` if a normal session is already running.

Navigate to the app in this Chrome window and log in as normal.

**Step 2:** Add to `~/.claude.json` under `mcpServers`:

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

**Package:** `chrome-devtools-mcp` (maintained by Google/ChromeDevTools). Verified working: v0.23.0+.

**Step 3:** Reload the VSCode window. The MCP server connects automatically at session start.

> **Important:** Chrome must be running on port 9222 *before* you open Claude Code. If Chrome isn't running when the session loads, the server fails to connect silently and `/bug-reporter --capture` will fall back to interactive mode until you reload.

---

### Setup: Option 2 — Playwright MCP (fallback)

Launches a fresh Chromium instance managed by Claude. No existing Chrome required, but you'll need to navigate to the app and log in each time — user context fields will be empty in `meta.md` unless you do.

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

---

### Verify MCP is working

Run `/bug-reporter --capture` in a Claude Code session. If MCP is connected, Claude will immediately start capturing (screenshots + console + network) without asking you to describe anything first. If it falls back to asking "Describe the bug", the MCP server isn't connected — check the troubleshooting table below.

If neither MCP server is configured, `/bug-reporter --capture` will print a graceful message and fall back to interactive text input.

---

## Screen name and route pattern (optional wiring)

For richer context in the captured `meta.md`, page components can advertise themselves via data attributes:

```tsx
// In a page or layout component root element
<div
  data-screen-name="Course Detail / Edit"
  data-route-pattern="/courses/:id/edit"
>
```

The bug reporter reads these attributes when present. Without them, the URL and `document.title` are still captured.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Bug button not visible | Only shown in dev mode (`npm run dev`). Not visible in `npm run build` previews. |
| "Capture failed" error | Check the Vite dev server terminal for the actual error. Common causes: blueprint path not found, screenshot timeout. |
| ClickUp ticket not created | Check `.env.local` has `BUG_REPORTER_AUTO_TICKET=true` and valid token/list ID. Restart the dev server after changes. Check Vite terminal for `[vite-plugin-bug-capture]` error logs. |
| Screenshots blank or black | Some pages with complex CSS or WebGL content may not render correctly in `html2canvas`. The evidence folder is still created without screenshots. |
| User context empty in meta.md | The app must be logged in when capture happens. The reporter reads from TanStack Query cache — if the `['auth','me']` query hasn't resolved, user fields are omitted. |
| Evidence folder missing | Check `tenxengage-blueprint/bugs-evidence/` exists. The Vite plugin creates subfolders automatically, but the parent dir must exist. Run: `mkdir -p ../tenxengage-blueprint/bugs-evidence` from the frontend root. |
| `/bug-reporter --capture` falls back to interactive mode | MCP server isn't connected. Most common cause: Chrome wasn't running on port 9222 when the Claude Code session started. Start Chrome with `--remote-debugging-port=9222 --user-data-dir=/tmp/chrome-debug`, then reload the VSCode window. |
| MCP config changed but capture still not working | MCP servers connect at session start only — reload the VSCode window (`Cmd+Shift+P` → Reload Window) after any `~/.claude.json` change. |
| MCP capture works but `user:` fields are empty in meta.md | Using Playwright MCP (fresh session). Log in to the app in the Playwright browser window before triggering capture, or switch to Chrome DevTools MCP (Option 1) which uses your existing logged-in session. |
