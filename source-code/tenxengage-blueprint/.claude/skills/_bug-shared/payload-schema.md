---
name: payload-schema
description: Canonical ClickUp ticket shape, reporter_source enum, and required field definitions for bug-reporter and bug-fixer
type: reference
---

# ClickUp Bug Ticket — Canonical Payload Schema

Advisory contract for **bug-reporter** (produces tickets) and **bug-fixer** (consumes tickets). Missing or malformed fields never block bug-fixer — Step 0 normalizes whatever arrives.

---

## Title format

```
<short imperative description>
```

- No repo prefix in the title — affected repo(s) live in the `affected_repos` custom field.
- Keep under 80 characters.
- Imperative present tense: "Course progress not saved" not "Course progress was not being saved".

**Examples:**
- `Course progress not saved after quiz completion`
- `Rules table shows wrong tenant data`
- `Import CSV: frontend sends wrong field name, backend rejects silently`

---

## Description sections (markdown body of ClickUp task)

```markdown
## Observed
<What actually happens. Be specific: which screen, which action, what error message, what data.>

## Expected
<What should happen instead.>

## Reproduction steps
1. Log in as <role> on <tenant>
2. Navigate to <screen>
3. <Action>
4. Observe: <result>

## Environment
- App version / build SHA: <value or "unknown">
- Browser / OS: <value or "unknown">
- User: <email or "unknown">
- Tenant: <name or "unknown">
- Feature flags: <list or "unknown">

## Evidence
- Screenshots: <count or "none">
- Console log: <attached / not available>
- Network HAR: <attached / not available>
- Stack trace: <inline or "none">
```

All sections are expected but any may be omitted if not available. bug-fixer Step 0 will reconstruct missing sections from context.

---

## Custom fields (ClickUp)

> If these fields don't exist in the workspace, bug-reporter prints setup instructions and continues with only built-in fields. It does NOT attempt to auto-create custom fields at the workspace level.

| Field name | Type | Values / Notes |
|---|---|---|
| `base_branch` | text | Branch the fix should target (e.g., `main`, `features/enablement-courses`) |
| `affected_repos` | multi-label | `backend`, `frontend`, `admin-backend`, `admin-frontend`, `contracts` |
| `reporter_source` | dropdown | See enum below |
| `fix_mrs` | text | Comma-separated MR URLs, populated by bug-fixer after Step 7 |
| `fix_branch` | text | Shared branch name across repos (e.g., `bug/clickup-abc123-course-progress`) |
| `linked_duplicates` | text | Comma-separated ticket IDs / evidence folder slugs |

---

## `reporter_source` enum

| Value | Set by |
|---|---|
| `manual` | Human filed directly in ClickUp UI |
| `browser-inapp` | DevBugReporter component in tenxengage-frontend dev build |
| `mcp-capture` | `/bug-reporter --capture` via Playwright/Chrome DevTools MCP |
| `bug-channel-space` | Google Chat bug channel → GCP Cloud Run → ClickUp webhook |
| `bug-reporter` | `/bug-reporter "<text>"` skill invocation |
| `other` | Unknown / legacy |

---

## Status values

See `clickup-lifecycle.md` — that file is the **single source of truth** for the unified status vocabulary. Never define or override status names here.