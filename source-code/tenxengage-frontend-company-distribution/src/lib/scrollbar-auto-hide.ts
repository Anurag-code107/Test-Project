/**
 * Auto-hiding scrollbar utility.
 *
 * Shows a thin scrollbar thumb only while the user is actively scrolling,
 * then fades it away after a short idle period.
 *
 * Uses inline `scrollbar-color` (supported in Firefox and Chromium 121+)
 * which reliably triggers a scrollbar repaint, unlike class-based
 * ::-webkit-scrollbar approaches.
 *
 * A single document-level listener with `capture: true` catches scroll
 * events on every scrollable element without per-element setup.
 */

const IDLE_MS = 800; // ms after last scroll event before hiding thumb
const THUMB_COLOR = "hsl(200 10% 78% / 0.5)";
const HIDDEN = "transparent transparent";
const VISIBLE = `${THUMB_COLOR} transparent`;

const timers = new WeakMap<EventTarget, ReturnType<typeof setTimeout>>();

function handleScroll(e: Event) {
  const target = e.target;
  if (!(target instanceof HTMLElement)) return;

  // Show scrollbar thumb immediately
  target.style.scrollbarColor = VISIBLE;

  // Clear any pending hide timer for this element
  const existing = timers.get(target);
  if (existing) clearTimeout(existing);

  // Schedule hiding the thumb after idle
  timers.set(
    target,
    setTimeout(() => {
      target.style.scrollbarColor = HIDDEN;
      timers.delete(target);
    }, IDLE_MS),
  );
}

export function initScrollbarAutoHide() {
  document.addEventListener("scroll", handleScroll, {
    capture: true,
    passive: true,
  });
}
