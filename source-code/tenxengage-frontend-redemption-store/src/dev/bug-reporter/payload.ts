export interface BugUserContext {
  email?: string
  id?: string
  name?: string
  roles?: string[]
  tenantId?: string
  tenantName?: string
  featureFlags?: string[]
}

export interface BugScreenContext {
  url?: string
  pathname?: string
  query?: string
  routePattern?: string
  pageTitle?: string
  screenName?: string
}

export interface BugEnvContext {
  buildSha?: string
  appVersion?: string
  environment?: string
  browser?: string
  viewport?: string
}

export interface ConsoleEntry {
  level: 'error' | 'warn'
  message: string
  timestamp: number
}

export interface NetworkEntry {
  method: string
  url: string
  status: number
  requestBodyDigest?: string
  responseSnippet?: string
  timestamp: number
}

export interface AdditionalScreenshot {
  filename: string
  data: string      // base64, no data: URI prefix
  mimeType: string
}

export interface BugCapturePayload {
  description: string
  expectedBehaviour?: string
  reporter?: string
  user?: BugUserContext
  screen?: BugScreenContext
  env?: BugEnvContext
  consoleLogs: ConsoleEntry[]
  networkCalls: NetworkEntry[]
  viewportScreenshot?: string  // base64 PNG
  fullPageScreenshot?: string  // base64 PNG
  additionalScreenshots?: AdditionalScreenshot[]
  autoTicket?: boolean
}
