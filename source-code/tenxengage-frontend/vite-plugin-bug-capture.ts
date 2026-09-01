import { loadEnv } from 'vite'
import type { Plugin } from 'vite'
import { writeFileSync, readFileSync, mkdirSync, existsSync } from 'fs'
import { join, resolve, dirname } from 'path'
import { fileURLToPath } from 'url'
import { execFileSync } from 'child_process'
import { randomBytes } from 'crypto'
import type { IncomingMessage, ServerResponse } from 'http'
import https from 'https'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BLUEPRINT_ROOT = resolve(__dirname, '..', 'tenxengage-blueprint')
const EVIDENCE_DIR = join(BLUEPRINT_ROOT, 'bugs-evidence')
const GENERATE_SCRIPT = join(BLUEPRINT_ROOT, '.claude', 'skills', '_bug-shared', 'generate-bug-list.mjs')

function getCurrentGitBranch(): string {
  try {
    return execFileSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { encoding: 'utf8' }).trim()
  } catch { /* git unavailable — try CI env vars */ }
  return process.env['GIT_BRANCH'] || process.env['BRANCH_NAME'] || 'main'
}

function buildClickUpTitle(description: string): string {
  return description.slice(0, 80)
}

function buildClickUpDescription(payload: Record<string, unknown>): string {
  const screen = payload['screen'] as Record<string, unknown> | undefined
  const user = payload['user'] as Record<string, unknown> | undefined
  const env = payload['env'] as Record<string, unknown> | undefined
  const expectedBehaviour = payload['expectedBehaviour'] as string | undefined

  const kv = (label: string, value: unknown) =>
    value ? `  ${label}: ${String(value)}` : ''

  const screenLines = [
    kv('URL', screen?.['url']),
    kv('Pathname', screen?.['pathname']),
    kv('Query', screen?.['query']),
    kv('Route', screen?.['routePattern']),
    kv('Page title', screen?.['pageTitle']),
    kv('Screen name', screen?.['screenName']),
  ].filter(Boolean).join('\n')

  const reporter = (payload['reporter'] as string | undefined)?.trim() || 'Anonymous'

  const userLines = [
    kv('Reporter', reporter),
    kv('Email', user?.['email']),
    kv('User ID', user?.['id']),
    kv('Name', user?.['name']),
    user?.['roles'] && Array.isArray(user['roles'])
      ? `  Roles: ${(user['roles'] as string[]).join(', ')}` : '',
    kv('Tenant', user?.['tenantName']),
    kv('Tenant ID', user?.['tenantId']),
    user?.['featureFlags'] && Array.isArray(user['featureFlags'])
      ? `  Feature flags: ${(user['featureFlags'] as string[]).join(', ')}` : '',
  ].filter(Boolean).join('\n')

  const envLines = [
    kv('Build SHA', env?.['buildSha']),
    kv('App version', env?.['appVersion']),
    kv('Environment', env?.['environment']),
    kv('Browser', env?.['browser'] ? String(env['browser']).slice(0, 120) : undefined),
    kv('Viewport', env?.['viewport']),
  ].filter(Boolean).join('\n')

  const sep = '─────────────────────────────────'

  return `OBSERVED
${String(payload['description'] || '')}

EXPECTED
${expectedBehaviour || '(fill in expected behaviour)'}

REPRODUCTION STEPS
1. ${screen?.['url'] ? `Navigate to ${screen['url']}` : '(fill in repro steps)'}
2. (describe the action)
3. Observe: (describe what happened)

${sep}

SCREEN
${screenLines || '  (not captured)'}

USER
${userLines || '  (not logged in or not captured)'}

ENVIRONMENT
${envLines || '  (not captured)'}

${sep}
Source: in-app-reporter (browser)  |  Screenshots: attached to this task
`
}

/** POST to ClickUp API from Node.js (never touches browser bundle). */
function createClickUpTask(
  title: string,
  description: string,
  apiToken: string,
  listId: string,
): Promise<{ id: string; url: string }> {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      name: title,
      description,
      status: 'pending',
      custom_fields: [],
    })

    const options = {
      hostname: 'api.clickup.com',
      path: `/api/v2/list/${listId}/task`,
      method: 'POST',
      headers: {
        'Authorization': apiToken,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
      },
    }

    const req = https.request(options, (res) => {
      let data = ''
      res.on('data', (chunk) => { data += chunk })
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data) as Record<string, unknown>
          if (res.statusCode && res.statusCode >= 400) {
            reject(new Error(`ClickUp API ${res.statusCode}: ${data.slice(0, 200)}`))
            return
          }
          const id = String(parsed['id'] ?? '')
          resolve({ id, url: `https://app.clickup.com/t/${id}` })
        } catch (e) {
          reject(e)
        }
      })
    })

    req.on('error', reject)
    req.write(body)
    req.end()
  })
}

/** Upload any file as an attachment to an existing ClickUp task. Non-fatal — caller should catch. */
function uploadClickUpAttachment(
  taskId: string,
  filename: string,
  content: Buffer,
  contentType: string,
  apiToken: string,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const boundary = `----BugReporterBoundary${Date.now()}`
    const header = Buffer.from(
      `--${boundary}\r\nContent-Disposition: form-data; name="attachment"; filename="${filename}"\r\nContent-Type: ${contentType}\r\n\r\n`,
    )
    const footer = Buffer.from(`\r\n--${boundary}--\r\n`)
    const body = Buffer.concat([header, content, footer])

    const options = {
      hostname: 'api.clickup.com',
      path: `/api/v2/task/${taskId}/attachment`,
      method: 'POST',
      headers: {
        'Authorization': apiToken,
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': body.length,
      },
    }

    const req = https.request(options, (res) => {
      let data = ''
      res.on('data', (chunk) => { data += chunk })
      res.on('end', () => {
        if (res.statusCode && res.statusCode >= 400) {
          reject(new Error(`ClickUp attachment ${res.statusCode}: ${data.slice(0, 200)}`))
        } else {
          resolve()
        }
      })
    })

    req.on('error', reject)
    req.write(body)
    req.end()
  })
}

/** Update the ticket: field in an already-written meta.md. */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function updateMetaTicket(metaPath: string, ticketId: string): void {
  const now = new Date().toISOString()
  let content = readFileSync(metaPath, 'utf8')
  content = content.replace(/^ticket: -$/m, `ticket: ${ticketId}`)
  content = content.replace(/^last-updated: .+$/m, `last-updated: ${now}`)
  writeFileSync(metaPath, content, 'utf8')
}

function buildMetaMd(
  payload: Record<string, unknown>,
  slug: string,
  ticketId: string | null,
): string {
  const now = new Date().toISOString()
  const user = payload['user'] as Record<string, unknown> | undefined
  const screen = payload['screen'] as Record<string, unknown> | undefined
  const env = payload['env'] as Record<string, unknown> | undefined

  const userBlock = user && Object.keys(user).length ? [
    'user:',
    user['email'] ? `  email: ${user['email']}` : null,
    user['id'] ? `  id: ${user['id']}` : null,
    user['name'] ? `  name: ${user['name']}` : null,
    user['roles'] && Array.isArray(user['roles']) ? `  roles: [${(user['roles'] as string[]).join(', ')}]` : null,
    user['tenantId'] ? `  tenant-id: ${user['tenantId']}` : null,
    user['tenantName'] ? `  tenant-name: ${user['tenantName']}` : null,
    user['featureFlags'] && Array.isArray(user['featureFlags']) ? `  feature-flags: [${(user['featureFlags'] as string[]).join(', ')}]` : null,
  ].filter(Boolean).join('\n') : null

  const screenBlock = screen && Object.keys(screen).length ? [
    'screen:',
    screen['url'] ? `  url: ${screen['url']}` : null,
    screen['pathname'] ? `  pathname: ${screen['pathname']}` : null,
    screen['query'] ? `  query: ${screen['query']}` : null,
    screen['routePattern'] ? `  route-pattern: ${screen['routePattern']}` : null,
    screen['pageTitle'] ? `  page-title: ${screen['pageTitle']}` : null,
    screen['screenName'] ? `  screen-name: ${screen['screenName']}` : null,
  ].filter(Boolean).join('\n') : null

  const envBlock = env && Object.keys(env).length ? [
    'env:',
    env['buildSha'] ? `  build-sha: ${env['buildSha']}` : null,
    env['appVersion'] ? `  app-version: ${env['appVersion']}` : null,
    env['environment'] ? `  environment: ${env['environment']}` : null,
    env['browser'] ? `  browser: ${env['browser']}` : null,
    env['viewport'] ? `  viewport: ${env['viewport']}` : null,
  ].filter(Boolean).join('\n') : null

  const optionalBlocks = [userBlock, screenBlock, envBlock].filter(Boolean).join('\n')

  const expectedBehaviour = payload['expectedBehaviour'] as string | undefined

  const reporter = (payload['reporter'] as string | undefined)?.trim() || 'Anonymous'

  return `---
slug: ${slug}
captured: ${now}
reporter: ${reporter}
source: in-app-reporter
status: pending
mode-hint: M2
affected-repos: []
base-branch: ${getCurrentGitBranch()}
ticket: ${ticketId ?? '-'}
fix-mrs: []
fix-commits: []
linked-duplicates: []
last-updated: ${now}
${optionalBlocks ? optionalBlocks + '\n' : ''}---

# ${String(payload['description'] || '').slice(0, 100)}

## Observed
${String(payload['description'] || '')}

## Expected
${expectedBehaviour || '(fill in expected behaviour)'}

## Reproduction steps
1. (fill in repro steps)

## Notes
(add any additional context)
`
}

export function bugCapturePlugin(): Plugin {
  let autoTicketEnabled = false
  let defaultAutoTicket = false
  let defaultReporterName = ''
  let clickupApiToken = ''
  let clickupListId = ''

  return {
    name: 'vite-plugin-bug-capture',
    apply: 'serve',

    configResolved(config) {
      // loadEnv with '' prefix loads ALL vars from .env / .env.local — not just VITE_ ones.
      // process.env does NOT include .env.local vars in Vite plugin context.
      const env = loadEnv(config.mode, config.root, '')
      clickupApiToken = env['CLICKUP_API_TOKEN'] ?? ''
      clickupListId = env['CLICKUP_BUGS_LIST_ID'] ?? ''
      autoTicketEnabled = !!clickupApiToken && !!clickupListId
      defaultAutoTicket =
        env['VITE_BUG_REPORTER_AUTO_TICKET'] === 'true' ||
        env['BUG_REPORTER_AUTO_TICKET'] === 'true'
      defaultReporterName = env['BUG_REPORTER_NAME'] ?? ''
      if (autoTicketEnabled) {
        config.logger.info('[vite-plugin-bug-capture] ClickUp integration available (token + list configured)')
      }
    },

    configureServer(server) {
      server.middlewares.use('/__dev__/bug-capture', (req: IncomingMessage, res: ServerResponse) => {
        if (req.method === 'GET') {
          res.writeHead(200, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ autoTicket: defaultAutoTicket, reporterName: defaultReporterName }))
          return
        }

        if (req.method !== 'POST') {
          res.writeHead(405)
          res.end('Method Not Allowed')
          return
        }

        let body = ''
        req.on('data', (chunk: Buffer) => { body += chunk.toString() })
        req.on('end', async () => {
          try {
            const payload = JSON.parse(body) as Record<string, unknown>
            const description = String(payload['description'] || 'untitled-bug')
            const folderName = `bug-${randomBytes(4).toString('hex')}`
            const folderPath = join(EVIDENCE_DIR, folderName)

            mkdirSync(join(folderPath, 'screenshots'), { recursive: true })

            // Create ClickUp ticket first (if requested) so we can embed the ID in meta.md
            let ticketId: string | null = null
            let ticketUrl: string | null = null
            const userWantsTicket = payload['autoTicket'] !== undefined
              ? payload['autoTicket'] === true
              : defaultAutoTicket
            if (autoTicketEnabled && userWantsTicket) {
              try {
                const result = await createClickUpTask(
                  buildClickUpTitle(description),
                  buildClickUpDescription(payload),
                  clickupApiToken,
                  clickupListId,
                )
                ticketId = result.id
                ticketUrl = result.url
                console.info(`[vite-plugin-bug-capture] ClickUp ticket created: ${ticketUrl}`)

                // Upload screenshots (non-fatal)
                const imgUploads: Array<[string, string]> = []
                if (payload['viewportScreenshot']) imgUploads.push(['01-viewport.png', String(payload['viewportScreenshot'])])
                if (payload['fullPageScreenshot']) imgUploads.push(['02-full-page.png', String(payload['fullPageScreenshot'])])
                for (const [fname, b64] of imgUploads) {
                  try {
                    await uploadClickUpAttachment(ticketId, fname, Buffer.from(b64, 'base64'), 'image/png', clickupApiToken)
                  } catch (e) {
                    console.warn(`[vite-plugin-bug-capture] Failed to attach ${fname}:`, e instanceof Error ? e.message : e)
                  }
                }

                // Upload network.har (non-fatal)
                const networkCalls = payload['networkCalls']
                if (Array.isArray(networkCalls) && networkCalls.length) {
                  try {
                    await uploadClickUpAttachment(
                      ticketId, 'network.har',
                      Buffer.from(JSON.stringify({ calls: networkCalls }, null, 2), 'utf8'),
                      'application/json', clickupApiToken,
                    )
                  } catch (e) {
                    console.warn('[vite-plugin-bug-capture] Failed to attach network.har:', e instanceof Error ? e.message : e)
                  }
                }

                // Upload additional screenshots (non-fatal)
                const additionalScreenshots = payload['additionalScreenshots']
                if (Array.isArray(additionalScreenshots)) {
                  for (let i = 0; i < additionalScreenshots.length; i++) {
                    const s = additionalScreenshots[i] as Record<string, unknown>
                    if (!s || !s['data']) continue
                    const origName = String(s['filename'] || '')
                    const ext = origName.includes('.') ? (origName.split('.').pop() ?? 'bin') : (String(s['mimeType'] || 'bin').split('/')[1] ?? 'bin')
                    const fname = `${String(i + 3).padStart(2, '0')}-extra-${i + 1}.${ext}`
                    try {
                      await uploadClickUpAttachment(
                        ticketId, fname,
                        Buffer.from(String(s['data']), 'base64'),
                        String(s['mimeType'] || 'image/png'), clickupApiToken,
                      )
                    } catch (e) {
                      console.warn(`[vite-plugin-bug-capture] Failed to attach ${fname}:`, e instanceof Error ? e.message : e)
                    }
                  }
                }

                // Upload console.log (non-fatal)
                const consoleLogs = payload['consoleLogs']
                if (Array.isArray(consoleLogs) && consoleLogs.length) {
                  try {
                    const logText = (consoleLogs as Array<Record<string, unknown>>)
                      .map(e => `[${new Date(Number(e['timestamp'])).toISOString()}] [${e['level']}] ${e['message']}`)
                      .join('\n')
                    await uploadClickUpAttachment(
                      ticketId, 'console.log',
                      Buffer.from(logText, 'utf8'),
                      'text/plain', clickupApiToken,
                    )
                  } catch (e) {
                    console.warn('[vite-plugin-bug-capture] Failed to attach console.log:', e instanceof Error ? e.message : e)
                  }
                }
              } catch (e) {
                // Non-fatal: still write evidence folder, just without a ticket reference
                console.error('[vite-plugin-bug-capture] ClickUp ticket creation FAILED:', e instanceof Error ? e.message : e)
              }
            }

            // Write meta.md (ticket ID embedded if available)
            writeFileSync(join(folderPath, 'meta.md'), buildMetaMd(payload, folderName, ticketId), 'utf8')

            // Write screenshots
            if (payload['viewportScreenshot']) {
              writeFileSync(
                join(folderPath, 'screenshots', '01-viewport.png'),
                Buffer.from(String(payload['viewportScreenshot']), 'base64'),
              )
            }
            if (payload['fullPageScreenshot']) {
              writeFileSync(
                join(folderPath, 'screenshots', '02-full-page.png'),
                Buffer.from(String(payload['fullPageScreenshot']), 'base64'),
              )
            }

            // Write console log
            const consoleLogs = payload['consoleLogs']
            if (Array.isArray(consoleLogs) && consoleLogs.length) {
              const logContent = (consoleLogs as Array<Record<string, unknown>>)
                .map(e => `[${new Date(Number(e['timestamp'])).toISOString()}] [${e['level']}] ${e['message']}`)
                .join('\n')
              writeFileSync(join(folderPath, 'console.log'), logContent, 'utf8')
            }

            // Write network calls as HAR-like JSON
            const networkCalls = payload['networkCalls']
            if (Array.isArray(networkCalls) && networkCalls.length) {
              writeFileSync(
                join(folderPath, 'network.har'),
                JSON.stringify({ calls: networkCalls }, null, 2),
                'utf8',
              )
            }

            // Write additional screenshots
            const additionalScreenshots = payload['additionalScreenshots']
            if (Array.isArray(additionalScreenshots)) {
              for (let i = 0; i < additionalScreenshots.length; i++) {
                const s = additionalScreenshots[i] as Record<string, unknown>
                if (!s || !s['data']) continue
                const origName = String(s['filename'] || '')
                const ext = origName.includes('.') ? (origName.split('.').pop() ?? 'bin') : (String(s['mimeType'] || 'bin').split('/')[1] ?? 'bin')
                const fname = `extra-${String(i + 1).padStart(2, '0')}.${ext}`
                writeFileSync(
                  join(folderPath, 'screenshots', fname),
                  Buffer.from(String(s['data']), 'base64'),
                )
              }
            }

            // Regenerate browse page
            if (existsSync(GENERATE_SCRIPT)) {
              try {
                execFileSync('node', [GENERATE_SCRIPT], { stdio: 'pipe' })
              } catch { /* non-fatal */ }
            }

            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({
              folderPath: `bugs-evidence/${folderName}`,
              ...(ticketId ? { ticketId, ticketUrl } : {}),
            }))
          } catch (e) {
            const msg = e instanceof Error ? e.message : String(e)
            console.error('[vite-plugin-bug-capture] error:', msg)
            res.writeHead(500, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ error: msg }))
          }
        })
      })
    },
  }
}
