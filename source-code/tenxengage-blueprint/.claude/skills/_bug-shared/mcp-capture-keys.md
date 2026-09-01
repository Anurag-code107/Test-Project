---
name: mcp-capture-keys
description: Configurable probe strategy for reading user/screen/env context from the running TenXEngage app — shared by in-app bug reporter and MCP-based capture
type: reference
---

# MCP Capture Keys — Context Probe Strategy

Used by both `DevBugReporter` (in-app) and `bug-reporter --capture` (MCP) to extract optional context from the running app. All probes are best-effort — if a source is unavailable, the field is omitted rather than failing the capture.

---

## User context probes

Try each source in order. Use the first one that returns a non-null result.

### Source 1: TanStack Query cache

```javascript
// Key pattern in tenxengage-frontend
const queryClient = window.__QUERY_CLIENT__  // set on window in main.tsx if DEV
const authData = queryClient?.getQueryData(['auth', 'me'])
// Expected shape: { id, email, name, roles, tenant: { id, name }, featureFlags }
```

### Source 2: localStorage

```javascript
const user = JSON.parse(localStorage.getItem('tenx_user') || 'null')
// Fallback key: 'user', 'currentUser', 'auth_user'
```

### Source 3: window.__APP__ global

```javascript
const user = window.__APP__?.user  // or window.__APP__?.auth?.user
```

### Source 4: sessionStorage

```javascript
const user = JSON.parse(sessionStorage.getItem('tenx_user') || 'null')
```

**Extracted fields (all optional):**

| Field | Path(s) to try |
|---|---|
| `user.email` | `authData.email` / `user.email` |
| `user.id` | `authData.id` / `authData.userId` / `user.id` |
| `user.name` | `authData.name` / `authData.displayName` / `user.name` |
| `user.roles` | `authData.roles` / `authData.authorities` / `user.roles` |
| `user.tenant-id` | `authData.tenant?.id` / `authData.tenantId` / `user.tenantId` |
| `user.tenant-name` | `authData.tenant?.name` / `authData.tenantName` / `user.tenantName` |
| `user.feature-flags` | `authData.featureFlags` / `authData.flags` / `user.featureFlags` |

---

## Screen context probes

### URL and route

```javascript
const url = window.location.href
const pathname = window.location.pathname
const query = window.location.search

// React Router route pattern — try data-route-pattern attribute first
const routePattern = document.querySelector('[data-route-pattern]')
  ?.getAttribute('data-route-pattern')
  // fallback: read from window.__APP__?.currentRoute
  ?? window.__APP__?.currentRoute
```

### Page title

```javascript
const pageTitle = document.title
```

### Screen name

```javascript
// Convention: root layout or page wrapper should carry data-screen-name attribute
const screenName =
  document.querySelector('[data-screen-name]')?.getAttribute('data-screen-name')
  ?? window.__APP__?.screenName
  ?? null  // omit if not found
```

**`data-screen-name` convention:** Each page/layout component in tenxengage-frontend should set this attribute on its root element. Example:
```tsx
<div data-screen-name="Course Detail / Edit" data-route-pattern="/courses/:id/edit">
```

---

## Environment context probes

```javascript
// Build SHA — try Vite meta tag, then env variable injected into window
const buildSha =
  document.querySelector('meta[name="build-sha"]')?.getAttribute('content')
  ?? window.__APP__?.buildSha
  ?? import.meta.env?.VITE_BUILD_SHA  // only accessible from within the app
  ?? 'unknown'

// App version
const appVersion =
  document.querySelector('meta[name="app-version"]')?.getAttribute('content')
  ?? window.__APP__?.version
  ?? 'unknown'

// Environment label
const environment =
  window.__APP__?.environment
  ?? import.meta.env?.VITE_APP_ENV  // 'local', 'staging', 'production'
  ?? 'unknown'

// Browser and viewport
const browser = navigator.userAgent
const viewport = `${window.innerWidth}x${window.innerHeight}`
```

---

## MCP-specific capture (bug-reporter --capture)

When using Playwright or Chrome DevTools MCP, these probes are executed via `page.evaluate()`:

```javascript
// Playwright example
const context = await page.evaluate(() => {
  return {
    url: window.location.href,
    pathname: window.location.pathname,
    query: window.location.search,
    title: document.title,
    screenName: document.querySelector('[data-screen-name]')?.getAttribute('data-screen-name') ?? null,
    routePattern: document.querySelector('[data-route-pattern]')?.getAttribute('data-route-pattern') ?? null,
    buildSha: document.querySelector('meta[name="build-sha"]')?.content ?? 'unknown',
    environment: window.__APP__?.environment ?? 'unknown',
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    userAgent: navigator.userAgent,
    // User context
    user: (() => {
      try {
        return JSON.parse(localStorage.getItem('tenx_user') || 'null')
          ?? window.__APP__?.user
          ?? null
      } catch { return null }
    })()
  }
})
```

---

## Adding new probe sources

When the app changes how it stores auth state, update this file to add new probe paths. The consumer code (`context-probe.ts`) reads the strategy from this file's conventions — keep them in sync.

**Never hardcode probe paths in the TypeScript files.** This file is the single source of truth for what to try and in what order.

---

## Admin frontend variants

For `tenxengage-admin-frontend`, the same strategy applies with these key differences:
- TanStack Query key: `['admin-auth', 'me']`
- localStorage key: `tenx_admin_user`
- `window.__APP__` path: same structure

V1.5: in-app reporter for admin-frontend will reuse the same `context-probe.ts` with an `appVariant: 'admin'` flag.