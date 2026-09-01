import type { NetworkEntry } from './payload'

const MAX_ENTRIES = 200
let entries: NetworkEntry[] = []
let installed = false
let lastNavigationTime = Date.now()

// Endpoints that only appear in the HAR when they fail (status >= 400 or network error).
// Suppresses polling noise from high-frequency background calls.
// Extend via VITE_BUG_REPORTER_NOISE_ENDPOINTS (comma-separated substrings).
const NOISE_ENDPOINTS: string[] = [
  '/notifications/unread-count',
  ...((import.meta.env.VITE_BUG_REPORTER_NOISE_ENDPOINTS as string | undefined) ?? '')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean),
]

function isNoise(url: string, status: number): boolean {
  if (status >= 400 || status === 0) return false
  return NOISE_ENDPOINTS.some(pattern => url.includes(pattern))
}

function push(entry: NetworkEntry): void {
  if (isNoise(entry.url, entry.status)) return
  entries.push(entry)
  if (entries.length > MAX_ENTRIES) entries = entries.slice(-MAX_ENTRIES)
}

export function installNetworkInterceptor(): void {
  if (installed) return
  installed = true

  // Patch History API — update boundary on every React Router navigation
  const originalPushState = history.pushState.bind(history)
  const originalReplaceState = history.replaceState.bind(history)
  history.pushState = function (...args: Parameters<typeof history.pushState>) {
    lastNavigationTime = Date.now()
    return originalPushState(...args)
  }
  history.replaceState = function (...args: Parameters<typeof history.replaceState>) {
    lastNavigationTime = Date.now()
    return originalReplaceState(...args)
  }
  window.addEventListener('popstate', () => { lastNavigationTime = Date.now() })

  // Patch XHR prototype — covers all axios instances (the app's only HTTP client)
  const originalOpen = XMLHttpRequest.prototype.open
  const originalSend = XMLHttpRequest.prototype.send

  XMLHttpRequest.prototype.open = function (
    method: string,
    url: string | URL,
    async = true,
    username?: string | null,
    password?: string | null,
  ) {
    (this as XMLHttpRequest & { _bugMethod: string; _bugUrl: string })._bugMethod = method
    ;(this as XMLHttpRequest & { _bugUrl: string })._bugUrl =
      typeof url === 'string' ? url : url.toString()
    return originalOpen.call(this, method, url, async as boolean, username ?? null, password ?? null)
  }

  XMLHttpRequest.prototype.send = function (body?: Document | XMLHttpRequestBodyInit | null) {
    this.addEventListener('loadend', () => {
      const self = this as XMLHttpRequest & { _bugMethod: string; _bugUrl: string }
      if (!self._bugUrl) return
      const entry: NetworkEntry = {
        method: (self._bugMethod ?? 'UNKNOWN').toUpperCase(),
        url: self._bugUrl,
        status: this.status,
        timestamp: Date.now(),
      }
      try { entry.responseSnippet = this.responseText.slice(0, 300) || undefined } catch { /* ignore */ }
      push(entry)
    })
    return originalSend.call(this, body)
  }

}

export function getLastNavigationTime(): number {
  return lastNavigationTime
}

export function getNetworkCalls(since?: number): NetworkEntry[] {
  return entries.filter(e => e.timestamp >= (since ?? 0))
}

export function clearNetworkErrors(): void {
  entries = []
}
