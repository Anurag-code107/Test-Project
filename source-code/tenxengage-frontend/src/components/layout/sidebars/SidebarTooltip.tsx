import { useState, useRef, useCallback, useEffect } from "react";
import { createPortal } from "react-dom";

/* ------------------------------------------------------------------ */
/*  Shared "warm" state across all SidebarTooltip instances            */
/*                                                                     */
/*  When a tooltip is visible (or was visible very recently), moving   */
/*  to another item shows its tooltip instantly — no delay. Once the   */
/*  user leaves the sidebar for longer than COOLDOWN_MS the system     */
/*  resets and the next hover uses the full initial delay again.       */
/* ------------------------------------------------------------------ */
let activeCount = 0; // how many tooltips are currently shown
let lastHideTime = 0; // timestamp of the most recent hide
const COOLDOWN_MS = 400; // time before the system goes "cold"

function isWarm(): boolean {
  return activeCount > 0 || Date.now() - lastHideTime < COOLDOWN_MS;
}

interface SidebarTooltipProps {
  label: string;
  children: React.ReactNode;
  enabled?: boolean;
  /** Delay in ms before the first (cold) tooltip appears (default 500) */
  delay?: number;
}

/**
 * Custom hover tooltip for collapsed sidebar items.
 *
 * - First hover: waits `delay` ms before appearing.
 * - While warm (another tooltip was just shown): appears instantly.
 * - Uses a portal so the tooltip escapes overflow:hidden on the sidebar.
 */
export function SidebarTooltip({
  label,
  children,
  enabled = true,
  delay = 500,
}: SidebarTooltipProps) {
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(
    null,
  );
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);

  const show = useCallback(() => {
    if (wrapperRef.current) {
      const rect = wrapperRef.current.getBoundingClientRect();
      setCoords({
        top: rect.top + rect.height / 2,
        left: rect.right + 10,
      });
    }
    setVisible(true);
    activeCount++;
  }, []);

  const handleEnter = useCallback(() => {
    if (!enabled) return;
    // If the tooltip system is warm, show immediately
    const effectiveDelay = isWarm() ? 0 : delay;
    if (effectiveDelay === 0) {
      show();
    } else {
      timerRef.current = setTimeout(show, effectiveDelay);
    }
  }, [enabled, delay, show]);

  const handleLeave = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    if (visible) {
      activeCount = Math.max(0, activeCount - 1);
      lastHideTime = Date.now();
    }
    setVisible(false);
    setCoords(null);
  }, [visible]);

  // When `enabled` flips to false (e.g. flyout opens), tear down any
  // active tooltip so it doesn't linger when `enabled` returns to true.
  useEffect(() => {
    if (!enabled) {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      if (visible) {
        activeCount = Math.max(0, activeCount - 1);
        lastHideTime = Date.now();
        setVisible(false);
        setCoords(null);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  // Clean up on unmount (e.g. sidebar expands while tooltip is shown)
  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (visible) {
        activeCount = Math.max(0, activeCount - 1);
        lastHideTime = Date.now();
      }
    };
  }, [visible]);

  if (!enabled) return <>{children}</>;

  return (
    <div ref={wrapperRef} onMouseEnter={handleEnter} onMouseLeave={handleLeave}>
      {children}
      {visible &&
        coords &&
        createPortal(
          <div
            role="tooltip"
            className="sidebar-tooltip"
            style={{
              position: "fixed",
              top: coords.top,
              left: coords.left,
            }}
          >
            <span className="sidebar-tooltip-label">{label}</span>
          </div>,
          document.body,
        )}
    </div>
  );
}
