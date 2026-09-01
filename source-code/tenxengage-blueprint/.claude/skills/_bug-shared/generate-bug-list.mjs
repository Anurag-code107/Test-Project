#!/usr/bin/env node
/**
 * Scans bugs-evidence/ and generates a self-contained bugs-evidence/index.html browse page.
 *
 * Usage:
 *   node .claude/skills/_bug-shared/generate-bug-list.mjs
 *
 * Run from the tenxengage-blueprint root, or from anywhere — the script resolves
 * the evidence folder relative to its own location.
 */

import { readFileSync, writeFileSync, readdirSync, existsSync } from 'fs'
import { join, dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const BLUEPRINT_ROOT = resolve(__dirname, '../../..')
const EVIDENCE_DIR = join(BLUEPRINT_ROOT, 'bugs-evidence')
const OUTPUT_FILE = join(EVIDENCE_DIR, 'index.html')

function parseFrontmatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---/)
  if (!match) return { meta: {}, body: content }
  const frontmatter = match[1]
  const body = content.slice(match[0].length).trim()
  const meta = {}
  for (const line of frontmatter.split('\n')) {
    const colonIdx = line.indexOf(':')
    if (colonIdx === -1) continue
    const key = line.slice(0, colonIdx).trim()
    let val = line.slice(colonIdx + 1).trim()
    if (val.startsWith('[') && val.endsWith(']')) {
      val = val.slice(1, -1).split(',').map(s => s.trim().replace(/^['"]|['"]$/g, '')).filter(Boolean)
    } else if (val === '-' || val === '') {
      val = null
    }
    meta[key] = val
  }
  return { meta, body }
}

function extractTitle(body) {
  const match = body.match(/^#\s+(.+)$/m)
  return match ? match[1].trim() : '(no title)'
}

function extractObserved(body) {
  const match = body.match(/##\s+Observed\n([\s\S]*?)(?=\n##|$)/)
  return match ? match[1].trim().slice(0, 300) : ''
}

function scanEvidence() {
  if (!existsSync(EVIDENCE_DIR)) return []
  const entries = readdirSync(EVIDENCE_DIR, { withFileTypes: true })
    .filter(e => e.isDirectory())
    .map(e => {
      const folder = e.name
      const metaPath = join(EVIDENCE_DIR, folder, 'meta.md')
      if (!existsSync(metaPath)) return null
      try {
        const content = readFileSync(metaPath, 'utf8')
        const { meta, body } = parseFrontmatter(content)
        const screenshots = (() => {
          const dir = join(EVIDENCE_DIR, folder, 'screenshots')
          if (!existsSync(dir)) return []
          return readdirSync(dir)
            .filter(f => /\.(png|jpg|jpeg|gif|webp)$/i.test(f))
            .map(f => `screenshots/${f}`)
        })()
        return {
          folder,
          title: extractTitle(body),
          observed: extractObserved(body),
          slug: meta.slug || folder,
          captured: meta.captured || '',
          reporter: meta.reporter || '',
          source: meta.source || '',
          status: meta.status || 'pending',
          modeHint: meta['mode-hint'] || '',
          affectedRepos: Array.isArray(meta['affected-repos']) ? meta['affected-repos'] : [],
          ticket: meta.ticket || null,
          fixMrs: Array.isArray(meta['fix-mrs']) ? meta['fix-mrs'] : [],
          linkedDuplicates: Array.isArray(meta['linked-duplicates']) ? meta['linked-duplicates'] : [],
          lastUpdated: meta['last-updated'] || '',
          screenshots,
        }
      } catch {
        return null
      }
    })
    .filter(Boolean)
  entries.sort((a, b) => b.captured.localeCompare(a.captured))
  return entries
}

const STATUS_ORDER = ['pending', 'in-progress', 'needs-review', 'fixed', 'duplicate', 'cant-reproduce', 'wont-fix']

const STATUS_BADGE = {
  'pending':        'bg-amber-400 text-white',
  'in-progress':    'bg-blue-500 text-white',
  'needs-review':   'bg-violet-500 text-white',
  'fixed':          'bg-emerald-500 text-white',
  'duplicate':      'bg-gray-400 text-white',
  'cant-reproduce': 'bg-red-500 text-white',
  'wont-fix':       'bg-gray-700 text-white',
}

const ACTIVE_STATUSES = new Set(['pending', 'in-progress', 'needs-review'])

function badge(status) {
  const cls = STATUS_BADGE[status] || 'bg-gray-400 text-white'
  return `<span class="inline-block px-2 py-0.5 rounded text-xs font-semibold ${cls}">${status}</span>`
}

function repoTags(repos) {
  return repos.map(r =>
    `<span class="inline-block px-1.5 py-0.5 rounded text-xs bg-indigo-100 text-indigo-700 font-medium">${r}</span>`
  ).join(' ')
}

function renderRow(e) {
  const screenshotsHtml = e.screenshots.map(s => {
    const src = `${e.folder}/${s}`
    const label = s.replace('screenshots/', '')
    return `<img src="${src}" class="h-28 rounded border border-gray-200 cursor-zoom-in hover:opacity-75 transition-opacity" onclick='openLightbox(${JSON.stringify(src)},${JSON.stringify(label)})'>`
  }).join('\n')

  const ticketLink = e.ticket && e.ticket !== '-'
    ? `<a href="https://app.clickup.com/t/${e.ticket}" target="_blank" class="text-blue-500 hover:underline">ClickUp #${e.ticket}</a>`
    : '<span class="text-gray-400">—</span>'

  const mrLinks = e.fixMrs.length
    ? e.fixMrs.map(mr => `<a href="${mr}" target="_blank" class="text-blue-500 hover:underline">${mr.split('/').pop()}</a>`).join(', ')
    : '<span class="text-gray-400">—</span>'

  return `
<details class="border border-gray-200 rounded-lg mb-2 bg-white shadow-sm overflow-hidden"
  data-status="${e.status}"
  data-repos="${e.affectedRepos.join(',')}"
  data-source="${e.source || ''}">
  <summary class="flex items-center gap-2 px-4 py-3 cursor-pointer select-none hover:bg-gray-50 transition-colors">
    <svg class="w-3.5 h-3.5 text-gray-400 shrink-0 transition-transform duration-150 details-arrow" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7"/>
    </svg>
    <span class="flex-1 font-medium text-sm text-gray-800 truncate">${e.title}</span>
    ${badge(e.status)}
    <span class="flex gap-1 items-center shrink-0">${repoTags(e.affectedRepos)}</span>
    <span class="text-xs text-gray-400 whitespace-nowrap shrink-0">${e.captured ? e.captured.slice(0, 10) : ''}</span>
    <span class="text-xs text-gray-400 whitespace-nowrap shrink-0 hidden sm:block">${e.source || ''}</span>
  </summary>
  <div class="px-4 py-4 border-t border-gray-100 space-y-3">
    <div class="grid grid-cols-2 gap-x-8 gap-y-1.5 text-sm">
      <div><span class="text-gray-400">Slug</span> <code class="text-xs text-gray-600 bg-gray-100 px-1 py-0.5 rounded">${e.slug}</code></div>
      <div><span class="text-gray-400">Reporter</span> <span class="text-gray-700 ml-1">${e.reporter || '—'}</span></div>
      <div><span class="text-gray-400">Ticket</span> <span class="ml-1">${ticketLink}</span></div>
      <div><span class="text-gray-400">MRs</span> <span class="ml-1">${mrLinks}</span></div>
      <div><span class="text-gray-400">Mode</span> <span class="text-gray-700 ml-1">${e.modeHint || '—'}</span></div>
      <div><span class="text-gray-400">Updated</span> <span class="text-gray-700 ml-1">${e.lastUpdated ? e.lastUpdated.slice(0, 16).replace('T', ' ') : '—'}</span></div>
    </div>
    ${e.observed ? `
    <div class="text-sm bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2.5 text-gray-700">
      <span class="font-semibold text-yellow-800 block mb-0.5">Observed</span>
      ${e.observed}
    </div>` : ''}
    ${e.screenshots.length ? `<div class="flex flex-wrap gap-2">${screenshotsHtml}</div>` : ''}
  </div>
</details>`
}

function generate() {
  const all = scanEvidence()
  const active = all.filter(e => ACTIVE_STATUSES.has(e.status))
  const closed = all.filter(e => !ACTIVE_STATUSES.has(e.status))

  const allRepos = [...new Set(all.flatMap(e => e.affectedRepos))].sort()
  const allSources = [...new Set(all.map(e => e.source).filter(Boolean))].sort()

  const now = new Date().toISOString().slice(0, 16).replace('T', ' ')

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TenXEngage — Bug Evidence</title>
<script src="https://cdn.tailwindcss.com"><\/script>
<style>
  details summary::-webkit-details-marker { display: none; }
  details[open] > summary { background-color: #eff6ff; }
  details[open] .details-arrow { transform: rotate(90deg); }
</style>
</head>
<body class="bg-gray-100 min-h-screen text-gray-900 font-sans antialiased">

<!-- Lightbox -->
<div id="lightbox"
  class="fixed inset-0 z-50 items-center justify-center p-6"
  style="display:none;background:rgba(0,0,0,0.88)"
  onclick="closeLightbox()">
  <div class="relative max-w-5xl w-full" onclick="event.stopPropagation()">
    <button onclick="closeLightbox()"
      class="absolute -top-3.5 -right-3.5 w-7 h-7 bg-white rounded-full text-base flex items-center justify-center shadow-lg cursor-pointer border-0 leading-none text-gray-600 hover:text-gray-900">
      &times;
    </button>
    <img id="lightbox-img" src="" alt=""
      class="block mx-auto max-w-full rounded-lg shadow-2xl"
      style="max-height:88vh;object-fit:contain">
    <p id="lightbox-label" class="mt-3 text-center text-gray-300 text-xs font-mono"></p>
  </div>
</div>

<div class="max-w-5xl mx-auto px-4 py-8">

  <!-- Header -->
  <div class="mb-6">
    <h1 class="text-2xl font-bold text-gray-900">Bug Evidence</h1>
    <p class="text-sm text-gray-400 mt-1">Generated ${now} UTC &bull; ${all.length} total &bull; ${active.length} active</p>
  </div>

  <!-- Filters -->
  <div class="flex flex-wrap gap-3 items-center mb-6 bg-white border border-gray-200 rounded-lg px-4 py-3 shadow-sm">
    <span class="text-xs font-semibold text-gray-400 uppercase tracking-wide mr-1">Filter</span>
    <label class="flex items-center gap-1.5 text-sm">
      <span class="text-gray-500 font-medium">Status</span>
      <select id="filter-status" onchange="applyFilters()"
        class="border border-gray-300 rounded-md px-2 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300">
        <option value="">All</option>
        ${STATUS_ORDER.map(s => `<option value="${s}">${s}</option>`).join('\n        ')}
      </select>
    </label>
    <label class="flex items-center gap-1.5 text-sm">
      <span class="text-gray-500 font-medium">Repo</span>
      <select id="filter-repo" onchange="applyFilters()"
        class="border border-gray-300 rounded-md px-2 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300">
        <option value="">All</option>
        ${allRepos.map(r => `<option value="${r}">${r}</option>`).join('\n        ')}
      </select>
    </label>
    <label class="flex items-center gap-1.5 text-sm">
      <span class="text-gray-500 font-medium">Source</span>
      <select id="filter-source" onchange="applyFilters()"
        class="border border-gray-300 rounded-md px-2 py-1 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-300">
        <option value="">All</option>
        ${allSources.map(s => `<option value="${s}">${s}</option>`).join('\n        ')}
      </select>
    </label>
  </div>

  <!-- Active bugs -->
  <div class="flex items-center gap-2 mb-3">
    <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Active</h2>
    <span class="text-xs text-gray-500 bg-gray-200 rounded-full px-2 py-0.5 font-medium">${active.length}</span>
  </div>
  <div id="active-list">
    ${active.length ? active.map(renderRow).join('') : '<p class="text-sm text-gray-400 py-6 text-center">No active bugs.</p>'}
  </div>

  <!-- Closed toggle -->
  <button id="closed-toggle" onclick="toggleClosed()"
    class="text-sm text-gray-400 hover:text-gray-600 underline mt-4 mb-2 block transition-colors">
    Show closed (${closed.length})
  </button>
  <div id="closed-section" class="hidden">
    <div class="flex items-center gap-2 mb-3 mt-2">
      <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Closed</h2>
      <span class="text-xs text-gray-500 bg-gray-200 rounded-full px-2 py-0.5 font-medium">${closed.length}</span>
    </div>
    ${closed.length ? closed.map(renderRow).join('') : '<p class="text-sm text-gray-400 py-6 text-center">No closed bugs.</p>'}
  </div>

</div>

<script>
const CLOSED_COUNT = ${closed.length}

function openLightbox(src, label) {
  document.getElementById('lightbox-img').src = src
  document.getElementById('lightbox-label').textContent = label || ''
  document.getElementById('lightbox').style.display = 'flex'
  document.body.style.overflow = 'hidden'
}
function closeLightbox() {
  document.getElementById('lightbox').style.display = 'none'
  document.getElementById('lightbox-img').src = ''
  document.body.style.overflow = ''
}
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeLightbox() })

function toggleClosed() {
  const el = document.getElementById('closed-section')
  const btn = document.getElementById('closed-toggle')
  const nowHidden = el.classList.toggle('hidden')
  btn.textContent = nowHidden ? \`Show closed (\${CLOSED_COUNT})\` : \`Hide closed (\${CLOSED_COUNT})\`
}

function applyFilters() {
  const status = document.getElementById('filter-status').value
  const repo   = document.getElementById('filter-repo').value
  const source = document.getElementById('filter-source').value
  document.querySelectorAll('details').forEach(d => {
    const matchStatus = !status || d.dataset.status === status
    const matchRepo   = !repo   || d.dataset.repos.split(',').includes(repo)
    const matchSource = !source || d.dataset.source === source
    d.classList.toggle('hidden', !(matchStatus && matchRepo && matchSource))
  })
}
<\/script>
</body>
</html>`

  writeFileSync(OUTPUT_FILE, html, 'utf8')
  console.log(`Generated ${OUTPUT_FILE} (${all.length} entries)`)
}

generate()