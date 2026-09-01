import { useRef, useEffect, useState } from "react";
import { Pause } from "lucide-react";

interface ThinkingDividerProps {
  isActive: boolean;
  label: string;
  onPause: () => void;
  /** Show the "Take control" pause button. True only when the AI is actively filling builder fields. */
  showPause: boolean;
}

/** Height of the dome in pixels — exported so the panel can use it for scroll padding. */
export const HUMP_HEIGHT = 130;

/** Timing constants (ms) */
const RISE_MS = 900;
const DRAIN_MS = 600;
const CONTENT_DELAY_MS = 300;
const IDLE_DELAY_MS = RISE_MS;

/**
 * Morphing divider between the chat scroll area and the input area.
 *
 * The flat 1px border line acts as the "liquid surface".  When the AI
 * starts thinking the border glows, then the surface deforms upward —
 * a narrow bubble forms, widens with inertia, overshoots, bounces, and
 * settles into the dome.  Content (particles, label, pause button)
 * surfaces from within the liquid.
 *
 * Seamless border: when the dome is active, the flat border fades out
 * and a full-width 1px line at the dome's base connects the dome's
 * curved border to the panel edges — forming one continuous stroke.
 *
 * Layers (bottom → top):
 *   1. Side blur  — full-width backdrop-filter, fades in/out
 *   2. Border extension — full-width 1px line at dome base (connects
 *      dome curve to panel edges)
 *   3. Dome group — clip-path animated (liquid-rise / liquid-drain)
 *       3a. Background gradient
 *       3b. 1px curved border
 *       3c. Content (particles + label + button)
 */
export function ThinkingDivider({ isActive, label, onPause, showPause }: ThinkingDividerProps) {
  const [mounted, setMounted] = useState(false);
  const [visible, setVisible] = useState(false);
  const hasBeenVisible = useRef(false);
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (isActive) {
      if (exitTimer.current) {
        clearTimeout(exitTimer.current);
        exitTimer.current = null;
      }
      setMounted(true);
      requestAnimationFrame(() =>
        requestAnimationFrame(() => {
          setVisible(true);
          hasBeenVisible.current = true;
        }),
      );
    } else {
      setVisible(false);
      exitTimer.current = setTimeout(() => {
        setMounted(false);
        hasBeenVisible.current = false;
      }, DRAIN_MS + 50);
    }
    return () => {
      if (exitTimer.current) clearTimeout(exitTimer.current);
    };
  }, [isActive]);

  // ── animation helpers ─────────────────────────────────────────────
  const domeAnimation = (() => {
    if (visible) {
      return `liquid-rise ${RISE_MS}ms cubic-bezier(0.22, 0.61, 0.36, 1) forwards, liquid-idle 4s ease-in-out ${IDLE_DELAY_MS}ms infinite`;
    }
    if (hasBeenVisible.current) {
      return `liquid-drain ${DRAIN_MS}ms ease-in forwards`;
    }
    return "none";
  })();

  const blurAnimation = (() => {
    if (visible) return `liquid-blur-in 500ms ease-out 100ms forwards`;
    if (hasBeenVisible.current) return `liquid-blur-out ${DRAIN_MS * 0.7}ms ease-in forwards`;
    return "none";
  })();

  const contentAnimation = (() => {
    if (visible) return `liquid-content-in 500ms cubic-bezier(0.22, 0.61, 0.36, 1) ${CONTENT_DELAY_MS}ms forwards`;
    if (hasBeenVisible.current) return `liquid-content-out ${DRAIN_MS * 0.5}ms ease-in forwards`;
    return "none";
  })();

  // Border extension: appears with dome, connects curve to panel edges
  const borderExtAnimation = (() => {
    if (visible) return `liquid-blur-in 300ms ease-out 50ms forwards`;
    if (hasBeenVisible.current) return `liquid-blur-out ${DRAIN_MS * 0.5}ms ease-in forwards`;
    return "none";
  })();

  return (
    <div className="shrink-0 relative">
      {/* The flat border — fades out when dome is active so the dome's
          curved border + extension line take over seamlessly. */}
      <div
        className="border-t border-border"
        style={{
          opacity: mounted ? 0 : 1,
          transition: mounted
            ? "opacity 200ms ease-out"     // fade out quickly
            : "opacity 400ms 200ms ease-in", // fade back in (delayed so drain finishes first)
        }}
      />

      {/* Border glow — blue energy on the border before the dome rises */}
      {mounted && (
        <div
          className="absolute inset-x-0 bottom-0 h-px z-10"
          style={{
            background: "hsl(var(--primary))",
            animation: visible
              ? "liquid-border-glow 700ms ease-out forwards"
              : `liquid-blur-out ${DRAIN_MS * 0.6}ms ease-in forwards`,
            opacity: 0,
          }}
        />
      )}

      {/* ── Dome container ─────────────────────────────────────────── */}
      {mounted && (
        <div
          className="absolute bottom-0 inset-x-0 pointer-events-none"
          style={{ height: HUMP_HEIGHT, zIndex: 20 }}
        >
          {/* Layer 1 — Side blur (full-width frosted glass) */}
          <div
            className="absolute inset-0"
            style={{
              backdropFilter: "blur(7px)",
              WebkitBackdropFilter: "blur(7px)",
              background:
                "linear-gradient(to top, hsl(var(--background) / 0.55) 0%, hsl(var(--background) / 0.25) 50%, transparent 100%)",
              maskImage:
                "linear-gradient(to top, black 0%, black 40%, transparent 85%)",
              WebkitMaskImage:
                "linear-gradient(to top, black 0%, black 40%, transparent 85%)",
              animation: blurAnimation,
              opacity: 0,
            }}
          />

          {/* Layer 2 — Border extension: full-width 1px line at the
              dome's base, connecting the dome curve to the panel edges.
              Together with the dome's curved border, this creates one
              continuous stroke across the full width. */}
          <div
            className="absolute bottom-0 inset-x-0"
            style={{
              height: "1px",
              background: "hsl(var(--border))",
              animation: borderExtAnimation,
              opacity: 0,
            }}
          />

          {/* Layer 3 — Dome group (clip-path animated) */}
          <div
            className="absolute inset-0"
            style={{
              clipPath: "ellipse(18% 0% at 50% 100%)",
              animation: domeAnimation,
            }}
          >
            {/* 3a — Background gradient */}
            <div
              className="absolute inset-0"
              style={{
                background: "hsl(var(--background))",
              }}
            />

            {/* 3b — Pulsating top arc border: SVG half-ellipse so only
                the dome's upper curve renders — no bottom, no sides.
                Stroke color pulses via CSS animation on the SVG element. */}
            <svg
              className="absolute inset-0 w-full h-full pointer-events-none"
              viewBox="0 0 400 130"
              preserveAspectRatio="none"
              fill="none"
              style={{ overflow: "visible", animation: "liquid-border-pulse 3s ease-in-out infinite" }}
            >
              <path
                d="M 0 130 A 200 130 0 0 1 400 130"
                stroke="hsl(var(--border))"
                strokeWidth="1"
                vectorEffect="non-scaling-stroke"
              />
            </svg>

            {/* 3c — Content: particles + label + pause button */}
            <div
              className="relative h-full flex flex-col items-center justify-center gap-2 pointer-events-auto"
              style={{
                animation: contentAnimation,
                opacity: 0,
              }}
            >
              {/* Particle field */}
              <div className="relative w-10 h-10">
                <div className="absolute inset-2 rounded-full bg-primary/20 blur-md animate-pulse" />
                <div className="absolute inset-3 rounded-full bg-primary/30 blur-sm" />
                {PARTICLES.map((p, i) => (
                  <div
                    key={i}
                    className="absolute inset-0"
                    style={{
                      animation: `ai-orbit ${p.duration}s linear infinite`,
                      animationDelay: `${p.delay}s`,
                    }}
                  >
                    <span
                      className="absolute rounded-full bg-primary"
                      style={{
                        width: `${p.size}px`,
                        height: `${p.size}px`,
                        top: `${p.offset}px`,
                        left: "50%",
                        marginLeft: `${-p.size / 2}px`,
                        opacity: p.opacity,
                      }}
                    />
                  </div>
                ))}
              </div>

              {/* Label */}
              <span className="text-xs font-medium text-muted-foreground">
                {label}
              </span>

              {/* Pause button — only when AI is actively filling fields */}
              {showPause && (
                <button
                  onClick={onPause}
                  className="flex items-center gap-1.5 px-3.5 py-1 rounded-lg text-xs font-semibold text-foreground bg-muted hover:bg-muted/70 hover:text-foreground border border-border hover:border-muted-foreground/40 transition-all shadow-sm"
                  title="Pause AI and take control"
                >
                  <Pause className="h-3 w-3" />
                  Take control
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const PARTICLES = [
  { duration: 2.0, delay: 0.0, size: 3.5, offset: 0, opacity: 0.9 },
  { duration: 2.6, delay: 0.3, size: 2.5, offset: 2, opacity: 0.7 },
  { duration: 1.8, delay: 0.6, size: 4, offset: -1, opacity: 0.8 },
  { duration: 3.0, delay: 0.1, size: 2, offset: 3, opacity: 0.5 },
  { duration: 2.2, delay: 0.8, size: 3, offset: 1, opacity: 0.6 },
];
