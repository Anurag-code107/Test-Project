const filterNode = (node: HTMLElement) => !node.hasAttribute?.('data-html2canvas-ignore')

export async function captureViewportScreenshot(): Promise<string | undefined> {
  try {
    const { toPng } = await import('html-to-image')
    const dataUrl = await toPng(document.body, {
      filter: filterNode,
      pixelRatio: window.devicePixelRatio || 1,
    })
    return dataUrl.replace('data:image/png;base64,', '')
  } catch (e) {
    console.warn('[bug-reporter] viewport screenshot failed:', e)
    return undefined
  }
}

export async function captureFullPageScreenshot(): Promise<string | undefined> {
  // In SPA layouts (flex h-screen), document never scrolls — <main> does.
  // Temporarily expand <main> to its full scroll height and override body/html
  // overflow so html-to-image captures everything including the sidebar.
  const main = document.querySelector<HTMLElement>('main') ??
    document.querySelector<HTMLElement>('[role="main"]')

  const fullScrollHeight = main?.scrollHeight ?? 0

  const prevMain = main ? {
    overflow: main.style.overflow,
    height: main.style.height,
    maxHeight: main.style.maxHeight,
  } : null
  const prevBody = { overflow: document.body.style.overflow, height: document.body.style.height }
  const prevHtml = { overflow: document.documentElement.style.overflow, height: document.documentElement.style.height }

  try {
    const { toPng } = await import('html-to-image')

    if (main) {
      main.style.overflow = 'visible'
      main.style.height = `${fullScrollHeight}px`
      main.style.maxHeight = 'none'
    }
    document.body.style.height = 'auto'
    document.body.style.overflow = 'visible'
    document.documentElement.style.height = 'auto'
    document.documentElement.style.overflow = 'visible'

    const dataUrl = await toPng(document.body, {
      filter: filterNode,
      pixelRatio: window.devicePixelRatio || 1,
    })
    return dataUrl.replace('data:image/png;base64,', '')
  } catch (e) {
    console.warn('[bug-reporter] full-page screenshot failed:', e)
    return undefined
  } finally {
    if (main && prevMain) {
      main.style.overflow = prevMain.overflow
      main.style.height = prevMain.height
      main.style.maxHeight = prevMain.maxHeight
    }
    document.body.style.overflow = prevBody.overflow
    document.body.style.height = prevBody.height
    document.documentElement.style.overflow = prevHtml.overflow
    document.documentElement.style.height = prevHtml.height
  }
}
