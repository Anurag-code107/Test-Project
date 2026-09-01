import type { BugCapturePayload } from './payload'
import { probeContext } from './context-probe'
import { getConsoleEntries } from './console-interceptor'
import { getNetworkCalls } from './network-interceptor'
import { captureViewportScreenshot, captureFullPageScreenshot } from './screenshot'

export async function capturePayload(
  description: string,
  expectedBehaviour?: string,
  opts?: { since?: number; autoTicket?: boolean; reporter?: string; additionalScreenshots?: BugCapturePayload['additionalScreenshots'] },
): Promise<BugCapturePayload> {
  const since = opts?.since
  const { user, screen, env } = probeContext()

  const [viewportScreenshot, fullPageScreenshot] = await Promise.all([
    captureViewportScreenshot(),
    captureFullPageScreenshot(),
  ])

  return {
    description,
    expectedBehaviour: expectedBehaviour?.trim() || undefined,
    reporter: opts?.reporter || undefined,
    user,
    screen,
    env,
    consoleLogs: getConsoleEntries(since),
    networkCalls: getNetworkCalls(since),
    viewportScreenshot,
    fullPageScreenshot,
    additionalScreenshots: opts?.additionalScreenshots?.length ? opts.additionalScreenshots : undefined,
    autoTicket: opts?.autoTicket,
  }
}

export interface SubmitResult {
  folderPath?: string   // only present in local dev (Vite plugin)
  ticketId?: string
  ticketUrl?: string
}

export async function submitPayload(payload: BugCapturePayload): Promise<SubmitResult> {
  const isDev = import.meta.env.DEV
  const endpoint = isDev
    ? '/__dev__/bug-capture'
    : (import.meta.env.VITE_BUG_CAPTURE_URL ?? '/__dev__/bug-capture')

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (!isDev && import.meta.env.VITE_BUG_CAPTURE_API_KEY) {
    headers['X-Bug-Capture-Key'] = import.meta.env.VITE_BUG_CAPTURE_API_KEY
  }

  const response = await fetch(endpoint, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const text = await response.text().catch(() => 'unknown error')
    throw new Error(`Bug capture server error (${response.status}): ${text}`)
  }

  return response.json()
}
