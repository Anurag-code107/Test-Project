import type { BugUserContext, BugScreenContext, BugEnvContext } from './payload'

declare global {
  interface Window {
    __APP__?: {
      user?: Record<string, unknown>
      auth?: { user?: Record<string, unknown> }
      buildSha?: string
      version?: string
      environment?: string
      currentRoute?: string
      screenName?: string
    }
    __QUERY_CLIENT__?: { getQueryData: (key: unknown[]) => unknown }
    __BUG_REPORTER_USER__?: Record<string, unknown> | null
  }
}

function tryParse(raw: string | null): Record<string, unknown> | null {
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}

function probeUser(): BugUserContext | undefined {
  let user: Record<string, unknown> | null = null

  // Source 1: AuthContext exposes the logged-in AuthUser here in DEV mode
  try {
    if (window.__BUG_REPORTER_USER__) user = window.__BUG_REPORTER_USER__
  } catch { /* ignore */ }

  // Source 2: TanStack Query cache (fallback for other apps / future query-based auth)
  if (!user) {
    try {
      const qc = window.__QUERY_CLIENT__
      if (qc) user = qc.getQueryData(['auth', 'me']) as Record<string, unknown> | null
    } catch { /* ignore */ }
  }

  // Source 3: localStorage
  if (!user) user = tryParse(localStorage.getItem('tenx_user'))
  if (!user) user = tryParse(localStorage.getItem('user'))

  // Source 4: window.__APP__
  if (!user) user = (window.__APP__?.user ?? window.__APP__?.auth?.user ?? null) as Record<string, unknown> | null

  // Source 5: sessionStorage
  if (!user) user = tryParse(sessionStorage.getItem('tenx_user'))

  if (!user) return undefined

  const str = (v: unknown) => (v != null ? String(v) : undefined)
  const ctx: BugUserContext = {}

  if (user['email']) ctx.email = str(user['email'])
  if (user['id']) ctx.id = str(user['id'])

  // AuthUser has firstName + lastName; generic fallback to name/displayName
  const firstName = str(user['firstName'])
  const lastName = str(user['lastName'])
  if (firstName || lastName) ctx.name = [firstName, lastName].filter(Boolean).join(' ')
  else if (user['name']) ctx.name = str(user['name'])
  else if (user['displayName']) ctx.name = str(user['displayName'])

  // AuthUser uses clientRoleName for role; fall back to roles array
  if (user['clientRoleName']) ctx.roles = [String(user['clientRoleName'])]
  else {
    const roles = user['roles'] ?? user['authorities']
    if (Array.isArray(roles)) ctx.roles = roles.map(String)
  }

  // Tenant: AuthUser uses clientId/clientName; fall back to generic tenantId/tenantName
  const tenantId = user['clientId'] ?? user['organizationId'] ?? user['tenantId']
  const tenantName = user['clientName'] ?? user['partnerCompanyName'] ?? user['tenantName']
  if (tenantId) ctx.tenantId = str(tenantId)
  if (tenantName) ctx.tenantName = str(tenantName)

  // Feature flags
  const flags = user['featureFlags'] ?? user['flags']
  if (Array.isArray(flags)) ctx.featureFlags = flags.map(String)

  return Object.keys(ctx).length ? ctx : undefined
}

function probeScreen(): BugScreenContext | undefined {
  const ctx: BugScreenContext = {
    url: window.location.href,
    pathname: window.location.pathname,
    query: window.location.search || undefined,
    pageTitle: document.title || undefined,
  }

  const routeEl = document.querySelector('[data-route-pattern]')
  if (routeEl) ctx.routePattern = routeEl.getAttribute('data-route-pattern') ?? undefined
  else if (window.__APP__?.currentRoute) ctx.routePattern = window.__APP__.currentRoute

  const screenEl = document.querySelector('[data-screen-name]')
  if (screenEl) ctx.screenName = screenEl.getAttribute('data-screen-name') ?? undefined
  else if (window.__APP__?.screenName) ctx.screenName = window.__APP__.screenName

  return ctx
}

function probeEnv(): BugEnvContext | undefined {
  const ctx: BugEnvContext = {}

  const buildSha =
    document.querySelector('meta[name="build-sha"]')?.getAttribute('content')
    ?? window.__APP__?.buildSha
    ?? (import.meta.env as Record<string, string>)['VITE_BUILD_SHA']
  if (buildSha) ctx.buildSha = buildSha

  const appVersion =
    document.querySelector('meta[name="app-version"]')?.getAttribute('content')
    ?? window.__APP__?.version
    ?? (import.meta.env as Record<string, string>)['VITE_APP_VERSION']
  if (appVersion) ctx.appVersion = appVersion

  const environment =
    window.__APP__?.environment
    ?? (import.meta.env as Record<string, string>)['VITE_APP_ENV']
    ?? 'local'
  ctx.environment = environment

  ctx.browser = navigator.userAgent
  ctx.viewport = `${window.innerWidth}x${window.innerHeight}`

  return ctx
}

export interface ProbeResult {
  user?: BugUserContext
  screen?: BugScreenContext
  env?: BugEnvContext
}

export function probeContext(): ProbeResult {
  return {
    user: probeUser(),
    screen: probeScreen(),
    env: probeEnv(),
  }
}
