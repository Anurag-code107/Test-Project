import type { ConsoleEntry } from './payload'

const MAX_ENTRIES = 100
let entries: ConsoleEntry[] = []
let installed = false

export function installConsoleInterceptor(): void {
  if (installed) return
  installed = true

  const patch = (level: 'error' | 'warn', original: (...args: unknown[]) => void) =>
    (...args: unknown[]) => {
      const message = args.map(a => {
        try { return typeof a === 'object' ? JSON.stringify(a) : String(a) } catch { return String(a) }
      }).join(' ')
      entries.push({ level, message, timestamp: Date.now() })
      if (entries.length > MAX_ENTRIES) entries = entries.slice(-MAX_ENTRIES)
      original.apply(console, args)
    }

  console.error = patch('error', console.error.bind(console))
  console.warn = patch('warn', console.warn.bind(console))

  window.addEventListener('error', (e) => {
    entries.push({ level: 'error', message: `Uncaught: ${e.message} (${e.filename}:${e.lineno})`, timestamp: Date.now() })
    if (entries.length > MAX_ENTRIES) entries = entries.slice(-MAX_ENTRIES)
  })

  window.addEventListener('unhandledrejection', (e) => {
    const msg = e.reason instanceof Error ? e.reason.message : String(e.reason)
    entries.push({ level: 'error', message: `UnhandledRejection: ${msg}`, timestamp: Date.now() })
    if (entries.length > MAX_ENTRIES) entries = entries.slice(-MAX_ENTRIES)
  })
}

export function getConsoleEntries(since?: number): ConsoleEntry[] {
  return entries.filter(e => e.timestamp >= (since ?? 0))
}

export function clearConsoleEntries(): void {
  entries = []
}
