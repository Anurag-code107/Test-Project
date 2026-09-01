import { useState, useCallback, useEffect, useRef } from 'react'
import { capturePayload, submitPayload } from './capture'
import { getLastNavigationTime } from './network-interceptor'
import type { SubmitResult } from './capture'
import type { AdditionalScreenshot } from './payload'

type State = 'idle' | 'open' | 'capturing' | 'submitting' | 'success' | 'error'

function Spinner({ size = 18, color = '#dc2626' }: { size?: number; color?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" style={{ display: 'block', flexShrink: 0 }}>
      <circle cx="12" cy="12" r="9" stroke="#e5e7eb" strokeWidth="2.5" />
      <path d="M12 3a9 9 0 0 1 9 9" stroke={color} strokeWidth="2.5" strokeLinecap="round">
        <animateTransform
          attributeName="transform"
          type="rotate"
          from="0 12 12"
          to="360 12 12"
          dur="0.75s"
          repeatCount="indefinite"
        />
      </path>
    </svg>
  )
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      style={{
        position: 'relative', width: 36, height: 20, borderRadius: 10,
        border: 'none', background: checked ? '#3b82f6' : '#d1d5db',
        cursor: 'pointer', transition: 'background 0.15s', flexShrink: 0, padding: 0,
      }}
    >
      <span style={{
        position: 'absolute', top: 2, left: checked ? 18 : 2,
        width: 16, height: 16, borderRadius: '50%', background: '#fff',
        transition: 'left 0.15s', display: 'block',
      }} />
    </button>
  )
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function DevBugReporter() {
  const [state, setState] = useState<State>('idle')
  const [description, setDescription] = useState('')
  const [expectedBehaviour, setExpectedBehaviour] = useState('')
  const [reporterName, setReporterName] = useState('')
  const [result, setResult] = useState<SubmitResult | null>(null)
  const [errorMsg, setErrorMsg] = useState('')
  const [sessionStartTime, setSessionStartTime] = useState<number | null>(null)
  // Preserved when the user clicks "Stop & Capture" — cleared after successful submit or discard
  const [frozenSince, setFrozenSince] = useState<number | null>(null)
  const [descriptionError, setDescriptionError] = useState(false)
  const [autoTicket, setAutoTicket] = useState(false)
  const [extraScreenshots, setExtraScreenshots] = useState<AdditionalScreenshot[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  const modalRef = useRef<HTMLDivElement>(null)

  // Load server-side defaults (reads BUG_REPORTER_AUTO_TICKET and BUG_REPORTER_NAME)
  useEffect(() => {
    fetch('/__dev__/bug-capture')
      .then(r => r.ok ? r.json() : null)
      .then((cfg: { autoTicket?: boolean; reporterName?: string } | null) => {
        if (cfg && typeof cfg.autoTicket === 'boolean') setAutoTicket(cfg.autoTicket)
        if (cfg && typeof cfg.reporterName === 'string') setReporterName(cfg.reporterName)
      })
      .catch(() => {})
  }, [])

  const open = useCallback(() => {
    setState('open')
    setDescription('')
    setExpectedBehaviour('')
    setErrorMsg('')
    setDescriptionError(false)
    // Don't clear frozenSince — may be set from a just-stopped session
  }, [])

  const close = useCallback(() => {
    setState('idle')
    setFrozenSince(null)
    setExtraScreenshots([])
  }, [])

  const startSession = useCallback(() => {
    setSessionStartTime(Date.now())
    setState('idle')
  }, [])

  const discardSession = useCallback(() => {
    setSessionStartTime(null)
    setFrozenSince(null)
    setExtraScreenshots([])
    setState('idle')
  }, [])

  // Called by the regular "Capture Bug" button (no active session)
  const submit = useCallback(async () => {
    if (!description.trim()) {
      setDescriptionError(true)
      return
    }
    setDescriptionError(false)
    try {
      setState('capturing')
      await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
      const payload = await capturePayload(
        description.trim(),
        expectedBehaviour.trim() || undefined,
        { since: frozenSince ?? getLastNavigationTime(), autoTicket, reporter: reporterName.trim() || 'Anonymous', additionalScreenshots: extraScreenshots },
      )
      setState('submitting')
      const submitResult = await submitPayload(payload)
      setResult(submitResult)
      setFrozenSince(null)
      setExtraScreenshots([])
      setState('success')
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : String(e))
      setState('error')
    }
  }, [description, expectedBehaviour, frozenSince, autoTicket, reporterName, extraScreenshots])

  // Called by "Stop & Capture Bug" — always stops the session first, then validates + submits
  const stopAndCapture = useCallback(async () => {
    // Always stop the session
    const since = sessionStartTime
    setSessionStartTime(null)
    if (since !== null) setFrozenSince(since)

    if (!description.trim()) {
      setDescriptionError(true)
      return // session stopped; user fills description and submits via normal "Capture Bug"
    }
    setDescriptionError(false)
    try {
      setState('capturing')
      await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
      const payload = await capturePayload(
        description.trim(),
        expectedBehaviour.trim() || undefined,
        { since: since ?? undefined, autoTicket, reporter: reporterName.trim() || 'Anonymous', additionalScreenshots: extraScreenshots },
      )
      setState('submitting')
      const submitResult = await submitPayload(payload)
      setResult(submitResult)
      setFrozenSince(null)
      setExtraScreenshots([])
      setState('success')
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : String(e))
      setState('error')
    }
  }, [description, expectedBehaviour, sessionStartTime, autoTicket, reporterName, extraScreenshots])

  // When a Radix Dialog/Sheet is open underneath, its FocusScope listens on document
  // for focusin/focusout and forces focus back inside the Sheet — which prevents typing
  // into our textareas. Intercept at capture phase for events targeting our modal and
  // stopImmediatePropagation so Radix's document-level listener never runs.
  useEffect(() => {
    if (state === 'idle') return
    const modal = modalRef.current
    if (!modal) return

    const handleFocusIn = (event: FocusEvent) => {
      const target = event.target as Node | null
      if (target && modal.contains(target)) event.stopImmediatePropagation()
    }
    const handleFocusOut = (event: FocusEvent) => {
      const relatedTarget = event.relatedTarget as Node | null
      if (relatedTarget && modal.contains(relatedTarget)) event.stopImmediatePropagation()
    }
    document.addEventListener('focusin', handleFocusIn, true)
    document.addEventListener('focusout', handleFocusOut, true)
    return () => {
      document.removeEventListener('focusin', handleFocusIn, true)
      document.removeEventListener('focusout', handleFocusOut, true)
    }
  }, [state])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const isMac = navigator.platform.toUpperCase().includes('MAC')
      const mod = isMac ? e.metaKey : e.ctrlKey
      if (mod && e.shiftKey && e.key === 'B') {
        e.preventDefault()
        setState(s => s === 'idle' ? 'open' : 'idle')
      }
      if (e.key === 'Escape') setState(s => s !== 'idle' ? 'idle' : s)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  const isRecording = sessionStartTime !== null

  return (
    <>
      {/* Floating trigger button — excluded from screenshots via data-html2canvas-ignore */}
      {/* pointerEvents: 'auto' keeps the button clickable when a Radix Dialog/Sheet sets pointer-events: none on <body>. */}
      {/* stopPropagation on pointerdown prevents Radix's outside-click detector from closing that Sheet when we click here. */}
      <div
        data-html2canvas-ignore="true"
        onPointerDown={e => e.stopPropagation()}
        style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 99999, pointerEvents: 'auto' }}
      >
        <button
          onClick={open}
          title={isRecording ? 'Recording session — click to capture bug (Cmd+Shift+B)' : 'Report bug (Cmd+Shift+B)'}
          style={{
            position: 'relative',
            background: '#fff',
            color: '#dc2626',
            border: '1.5px solid #fca5a5',
            borderRadius: '50%',
            width: 44,
            height: 44,
            fontSize: 22,
            cursor: 'pointer',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          aria-label={isRecording ? 'Recording session — capture bug' : 'Report a bug'}
        >
          🐞
          {isRecording && (
            <span style={{
              position: 'absolute',
              top: 0,
              right: 0,
              width: 12,
              height: 12,
              background: '#dc2626',
              borderRadius: '50%',
              border: '2px solid #fff',
              animation: 'bugRecordPulse 1.2s ease-in-out infinite',
            }} />
          )}
        </button>
        <style>{`
          @keyframes bugRecordPulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.75); }
          }
        `}</style>
      </div>

      {/* Non-modal capture indicator */}
      {state === 'capturing' && (
        <div
          data-html2canvas-ignore="true"
          style={{
            position: 'fixed',
            bottom: 72,
            right: 20,
            zIndex: 100001,
            background: '#1f2937',
            color: '#fff',
            padding: '8px 14px',
            borderRadius: 8,
            fontSize: 13,
            fontWeight: 500,
            boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            pointerEvents: 'auto',
          }}
        >
          <Spinner size={16} color="#fff" />
          Taking screenshot...
        </div>
      )}

      {/* Modal overlay */}
      {state !== 'idle' && state !== 'capturing' && (
        <div
          ref={modalRef}
          onClick={close}
          onPointerDown={e => e.stopPropagation()}
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 100000,
            background: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            pointerEvents: 'auto',
          }}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              background: '#fff',
              borderRadius: 12,
              padding: 24,
              width: 480,
              maxWidth: '95vw',
              boxShadow: '0 8px 32px rgba(0,0,0,0.2)',
            }}
          >
            <h2 style={{ margin: '0 0 4px', fontSize: 18, fontWeight: 700 }}>Report a Bug</h2>

            {/* Session recording indicator */}
            {isRecording && state === 'open' && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                margin: '6px 0 12px',
                padding: '6px 10px',
                background: '#fef2f2',
                border: '1px solid #fecaca',
                borderRadius: 6,
                fontSize: 12,
                color: '#dc2626',
                fontWeight: 500,
              }}>
                <span style={{
                  width: 8,
                  height: 8,
                  background: '#dc2626',
                  borderRadius: '50%',
                  flexShrink: 0,
                  animation: 'bugRecordPulse 1.2s ease-in-out infinite',
                }} />
                Session recording since {formatTime(sessionStartTime!)}
              </div>
            )}

            {!isRecording && (
              <p style={{ margin: '0 0 16px', fontSize: 13, color: '#6b7280' }}>
                Screenshot, console logs, network errors, and user context will be captured automatically.
              </p>
            )}

            {state === 'open' && (
              <>
                <p style={{ margin: '0 0 6px', fontSize: 13, fontWeight: 600, color: '#374151' }}>
                  What went wrong? <span style={{ color: '#dc2626' }}>*</span>
                </p>
                <textarea
                  autoFocus
                  placeholder="Describe what went wrong..."
                  value={description}
                  onChange={e => { setDescription(e.target.value); if (descriptionError) setDescriptionError(false) }}
                  onKeyDown={e => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { if (isRecording) { stopAndCapture() } else { submit() } } }}
                  style={{
                    width: '100%',
                    minHeight: 80,
                    padding: 10,
                    border: `1px solid ${descriptionError ? '#dc2626' : '#d1d5db'}`,
                    borderRadius: 8,
                    fontSize: 14,
                    resize: 'vertical',
                    boxSizing: 'border-box',
                    fontFamily: 'inherit',
                  }}
                />
                {descriptionError && (
                  <p style={{ margin: '4px 0 0', fontSize: 12, color: '#dc2626' }}>Please describe what went wrong.</p>
                )}
                <p style={{ margin: '12px 0 6px', fontSize: 13, fontWeight: 600, color: '#374151' }}>
                  What should have happened? <span style={{ color: '#9ca3af', fontWeight: 400 }}>(optional)</span>
                </p>
                <textarea
                  placeholder="Describe the expected behaviour..."
                  value={expectedBehaviour}
                  onChange={e => setExpectedBehaviour(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { if (isRecording) { stopAndCapture() } else { submit() } } }}
                  style={{
                    width: '100%',
                    minHeight: 60,
                    padding: 10,
                    border: '1px solid #d1d5db',
                    borderRadius: 8,
                    fontSize: 14,
                    resize: 'vertical',
                    boxSizing: 'border-box',
                    fontFamily: 'inherit',
                  }}
                />

                {/* Attachments */}
                <div style={{ marginTop: 12 }}>
                  <p style={{ margin: '0 0 2px', fontSize: 13, fontWeight: 600, color: '#374151' }}>
                    Attachments
                    <span style={{ color: '#9ca3af', fontWeight: 400, marginLeft: 6 }}>(optional, up to 5)</span>
                  </p>
                  <p style={{ margin: '0 0 8px', fontSize: 11, color: '#9ca3af' }}>
                    Screenshots, screen recordings, log files, or anything that helps explain the bug
                  </p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    style={{ display: 'none' }}
                    onChange={e => {
                      const files = Array.from(e.target.files ?? [])
                      const remaining = 5 - extraScreenshots.length
                      files.slice(0, remaining).forEach(file => {
                        const reader = new FileReader()
                        reader.onload = ev => {
                          const dataUrl = ev.target?.result as string
                          if (!dataUrl) return
                          const base64 = dataUrl.split(',')[1] ?? dataUrl
                          setExtraScreenshots(prev => prev.length < 5
                            ? [...prev, { filename: file.name, data: base64, mimeType: file.type || 'application/octet-stream' }]
                            : prev
                          )
                        }
                        reader.readAsDataURL(file)
                      })
                      e.target.value = ''
                    }}
                  />
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'flex-start' }}>
                    {extraScreenshots.map((s, i) => (
                      <div key={i} style={{ position: 'relative', flexShrink: 0 }}>
                        {s.mimeType.startsWith('image/') ? (
                          <img
                            src={`data:${s.mimeType};base64,${s.data}`}
                            alt={s.filename}
                            title={s.filename}
                            style={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 4, border: '1px solid #e5e7eb', display: 'block' }}
                          />
                        ) : (
                          <div
                            title={s.filename}
                            style={{
                              width: 48, height: 48, borderRadius: 4,
                              border: '1px solid #e5e7eb', background: '#f3f4f6',
                              display: 'flex', flexDirection: 'column',
                              alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
                            }}
                          >
                            <span style={{ fontSize: 18, lineHeight: 1 }}>📄</span>
                            <span style={{
                              fontSize: 9, color: '#6b7280', marginTop: 2,
                              maxWidth: 44, overflow: 'hidden', textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap', padding: '0 2px',
                            }}>
                              {(s.filename.split('.').pop() ?? 'file').toUpperCase()}
                            </span>
                          </div>
                        )}
                        <button
                          onClick={() => setExtraScreenshots(prev => prev.filter((_, j) => j !== i))}
                          title="Remove"
                          style={{
                            position: 'absolute', top: -6, right: -6,
                            width: 16, height: 16, borderRadius: '50%',
                            background: '#6b7280', color: '#fff',
                            border: 'none', cursor: 'pointer',
                            fontSize: 10, lineHeight: '16px', padding: 0,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                          }}
                        >×</button>
                      </div>
                    ))}
                    {extraScreenshots.length < 5 && (
                      <button
                        onClick={() => fileInputRef.current?.click()}
                        style={{
                          width: 48, height: 48, borderRadius: 4,
                          border: '1px dashed #d1d5db', background: '#f9fafb',
                          cursor: 'pointer', fontSize: 20, color: '#9ca3af',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          flexShrink: 0,
                        }}
                        title="Add file"
                      >+</button>
                    )}
                  </div>
                </div>

                {/* Reporter name + ClickUp toggle row */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
                  <span style={{ fontSize: 12, color: '#6b7280', flexShrink: 0 }}>Reported by</span>
                  <input
                    type="text"
                    placeholder="Anonymous"
                    value={reporterName}
                    onChange={e => setReporterName(e.target.value)}
                    style={{
                      flex: 1,
                      padding: '4px 8px',
                      border: '1px solid #d1d5db',
                      borderRadius: 6,
                      fontSize: 13,
                      fontFamily: 'inherit',
                      color: '#374151',
                      minWidth: 0,
                    }}
                  />
                </div>

                {/* Action row: ClickUp toggle on left, buttons on right */}
                <div style={{ display: 'flex', gap: 8, marginTop: 10, alignItems: 'center' }}>
                  <label style={{
                    display: 'flex', alignItems: 'center', gap: 6,
                    fontSize: 12, color: '#6b7280', cursor: 'pointer', marginRight: 'auto',
                  }}>
                    <Toggle checked={autoTicket} onChange={setAutoTicket} />
                    ClickUp ticket
                  </label>

                  {isRecording ? (
                    <>
                      <button
                        onClick={discardSession}
                        style={{
                          padding: '8px 12px', borderRadius: 6,
                          border: '1px solid #fca5a5', background: '#fff',
                          cursor: 'pointer', fontSize: 13, color: '#dc2626',
                        }}
                      >
                        Discard
                      </button>
                      <button
                        onClick={stopAndCapture}
                        style={{
                          padding: '8px 16px', borderRadius: 6, border: 'none',
                          background: '#dc2626', color: '#fff',
                          cursor: 'pointer', fontSize: 14, fontWeight: 600,
                        }}
                      >
                        ■ Stop & Capture Bug
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        onClick={close}
                        style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #d1d5db', background: '#fff', cursor: 'pointer', fontSize: 14 }}
                      >
                        Cancel
                      </button>
                      <button
                        onClick={submit}
                        style={{
                          padding: '8px 16px', borderRadius: 6, border: 'none',
                          background: '#dc2626', color: '#fff',
                          cursor: 'pointer', fontSize: 14, fontWeight: 600,
                        }}
                      >
                        Capture Bug
                      </button>
                    </>
                  )}
                </div>

                {/* Session recording option — only when no session is active */}
                {!isRecording && (
                  <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #f3f4f6' }}>
                    <button
                      onClick={startSession}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        borderRadius: 6,
                        border: '1px solid #e5e7eb',
                        background: '#f9fafb',
                        cursor: 'pointer',
                        fontSize: 13,
                        color: '#374151',
                        fontWeight: 500,
                        textAlign: 'left',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <span style={{ fontSize: 16 }}>▶</span>
                      <span>
                        <span style={{ display: 'block' }}>Start session recording instead</span>
                        <span style={{ fontSize: 11, color: '#9ca3af', fontWeight: 400 }}>
                          Tracks errors precisely from now until you stop
                        </span>
                      </span>
                    </button>
                  </div>
                )}

                <p style={{ margin: '8px 0 0', fontSize: 11, color: '#9ca3af' }}>Cmd+Enter to submit · Esc to cancel</p>
              </>
            )}

            {state === 'submitting' && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0' }}>
                <Spinner />
                <p style={{ margin: 0, color: '#6b7280', fontSize: 14 }}>Saving evidence folder...</p>
              </div>
            )}

            {state === 'success' && result && (
              <>
                <p style={{ margin: '0 0 8px', color: '#059669', fontSize: 14, fontWeight: 600 }}>Bug captured!</p>
                <div style={{ display: 'grid', gap: 8, marginBottom: 16 }}>
                  {result.folderPath ? (
                    <div style={{ fontSize: 13, color: '#374151' }}>
                      <span style={{ color: '#6b7280' }}>Evidence folder: </span>
                      <code style={{ background: '#f3f4f6', padding: '2px 6px', borderRadius: 4, wordBreak: 'break-all' }}>
                        {result.folderPath}
                      </code>
                    </div>
                  ) : (
                    <div style={{ fontSize: 12, color: '#9ca3af' }}>
                      Evidence saved to server (no local folder path available)
                    </div>
                  )}
                  {result.ticketUrl ? (
                    <div style={{ fontSize: 13, color: '#374151' }}>
                      <span style={{ color: '#6b7280' }}>ClickUp ticket: </span>
                      <a
                        href={result.ticketUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{ color: '#3b82f6', fontWeight: 600 }}
                      >
                        #{result.ticketId} ↗
                      </a>
                    </div>
                  ) : (
                    <div style={{ fontSize: 12, color: '#9ca3af' }}>
                      No ClickUp ticket (enable the toggle or set <code>BUG_REPORTER_AUTO_TICKET=true</code>)
                    </div>
                  )}
                </div>
                {result.folderPath && (
                  <p style={{ margin: '0 0 16px', fontSize: 13, color: '#6b7280' }}>
                    Run <code>/bug-fixer --evidence {result.folderPath.split('/').pop()}</code> to fix it.
                  </p>
                )}
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button onClick={close} style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #d1d5db', background: '#fff', cursor: 'pointer', fontSize: 14 }}>
                    Close
                  </button>
                </div>
              </>
            )}

            {state === 'error' && (
              <>
                <p style={{ margin: '0 0 8px', color: '#dc2626', fontSize: 14, fontWeight: 600 }}>Capture failed</p>
                <p style={{ margin: '0 0 16px', fontSize: 13, color: '#374151' }}>{errorMsg}</p>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button onClick={close} style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #d1d5db', background: '#fff', cursor: 'pointer', fontSize: 14 }}>
                    Close
                  </button>
                  <button onClick={() => setState('open')} style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: '#dc2626', color: '#fff', cursor: 'pointer', fontSize: 14 }}>
                    Try again
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </>
  )
}
