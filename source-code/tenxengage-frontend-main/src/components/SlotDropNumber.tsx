import { useEffect, useMemo, useRef } from "react";
import { cn } from "@/lib/utils";

/**
 * SlotDropNumber
 * --------------
 * Renders a pre-formatted numeric / currency string (e.g. "$934,059") with a
 * left → right slot-machine drop animation. Each digit is its own short
 * vertical reel that decelerates into place; non-digit characters ($, commas,
 * spaces, decimals) render statically.
 *
 * The animation re-runs every time `value` changes. Respects
 * prefers-reduced-motion by snapping to the final state without animating.
 * Defers running the animation until the element enters the viewport (via
 * IntersectionObserver) so many off-screen amounts don't all animate at once.
 * `will-change: transform` is applied transiently — only while the reels are
 * rolling — and released once the landing pulse has completed, so the
 * compositor doesn't hold a layer per digit after the animation is done.
 */

interface SlotDropNumberProps {
  /** Pre-formatted value to display, e.g. "$934,059". */
  value: string;
  /** Base duration of each reel's spin in ms. */
  durationMs?: number;
  /** Stagger between reels in ms (left → right). */
  staggerMs?: number;
  /** Number of full digit cycles before landing on the final digit. */
  spins?: number;
  /** Optional class names applied to the wrapper. */
  className?: string;
}

const EASING = "cubic-bezier(.16,.84,.24,1)";

interface PlanEntry {
  char: string;
  isDigit: boolean;
  digits: string[];
}

function buildStripDigits(finalDigit: string, spins: number): string[] {
  const target = parseInt(finalDigit, 10);
  const total = spins * 10 + (target + 1);
  const out: string[] = [];
  for (let i = 0; i < total; i++) {
    if (i === 0) {
      // Every reel starts visually at "0" — so the container paints as
      // $000,000 before the drop animation begins.
      out.push("0");
    } else if (i === total - 1) {
      out.push(finalDigit);
    } else {
      out.push(String(Math.floor(Math.random() * 10)));
    }
  }
  return out;
}

export function SlotDropNumber({
  value,
  durationMs = 900,
  staggerMs = 160,
  spins = 2,
  className,
}: SlotDropNumberProps) {
  const plan = useMemo<PlanEntry[]>(() => {
    return Array.from(value).map((ch) => {
      const isDigit = /[0-9]/.test(ch);
      return {
        char: ch,
        isDigit,
        digits: isDigit ? buildStripDigits(ch, spins) : [],
      };
    });
  }, [value, spins]);

  const rootRef = useRef<HTMLSpanElement>(null);
  const stripRefs = useRef<Array<HTMLSpanElement | null>>([]);
  const cellRefs = useRef<Array<HTMLSpanElement | null>>([]);

  useEffect(() => {
    const rootEl = rootRef.current;
    if (!rootEl) return;

    // Build the reels once. `startAnimation` is idempotent — the
    // IntersectionObserver may fire more than once if the element flickers
    // across the viewport boundary during layout, but only the first call
    // actually runs.
    const runAnimation = (): (() => void) => {
      const prefersReducedMotion =
        typeof window !== "undefined" &&
        typeof window.matchMedia === "function" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;

      // First pass: snap every reel back to its "0" frame with no transition.
      // This guarantees the container paints as $000,000 before the drop begins.
      const reels: Array<{
        strip: HTMLSpanElement;
        cell: HTMLSpanElement | null;
        distance: number;
        idx: number;
      }> = [];
      let reelIndex = 0;
      for (const entry of plan) {
        if (!entry.isDigit) continue;
        const strip = stripRefs.current[reelIndex];
        const cell = cellRefs.current[reelIndex] ?? null;
        const localIndex = reelIndex;
        reelIndex++;
        if (!strip) continue;

        strip.style.transition = "none";
        strip.style.transform = "translateY(0)";
        reels.push({
          strip,
          cell,
          distance: entry.digits.length - 1,
          idx: localIndex,
        });
      }

      const firstReel = reels[0];
      if (!firstReel) return () => {};

      // Force reflow so the zeroed state actually paints.
      void firstReel.strip.offsetHeight;

      if (prefersReducedMotion) {
        for (const r of reels) {
          r.strip.style.transform = `translateY(-${r.distance}em)`;
        }
        return () => {};
      }

      // Kick off the drop on the next animation frame (after one paint) so the
      // user sees the "000..." starting state before the reels begin to roll.
      const pulseTimers: number[] = [];
      const pulseAnims: Animation[] = [];
      let rafA = 0;
      let rafB = 0;
      let willChangeReset = 0;

      // Time (from rafB dispatch) by which the last reel has landed. Used to
      // schedule the will-change release.
      const maxEnd = reels.reduce((max, r) => {
        const duration = durationMs + r.idx * 30;
        const delay = r.idx * staggerMs;
        return Math.max(max, duration + delay);
      }, 0);

      rafA = window.requestAnimationFrame(() => {
        rafB = window.requestAnimationFrame(() => {
          for (const r of reels) {
            // Promote to its own compositor layer just for the rolling window.
            // The permanent Tailwind `will-change-transform` class this used to
            // carry left ~1 layer per digit on the page forever, which saturated
            // the compositor and janked nearby height transitions.
            r.strip.style.willChange = "transform";

            const duration = durationMs + r.idx * 30;
            const delay = r.idx * staggerMs;
            r.strip.style.transition = `transform ${duration}ms ${EASING} ${delay}ms`;
            r.strip.style.transform = `translateY(-${r.distance}em)`;

            // Landing pulse — scale(1 → 1.18 → 1) on the digit cell, fired ~30ms
            // before the reel fully stops so it feels like a "tick" on landing.
            const landAt = duration + delay - 30;
            const timer = window.setTimeout(() => {
              const cell = r.cell;
              if (!cell || typeof cell.animate !== "function") return;
              const anim = cell.animate(
                [
                  { transform: "scale(1)" },
                  { transform: "scale(1.18)", offset: 0.4 },
                  { transform: "scale(1)" },
                ],
                { duration: 220, easing: "ease-out" },
              );
              pulseAnims.push(anim);
            }, Math.max(0, landAt));
            pulseTimers.push(timer);
          }

          // Once the slowest reel's landing pulse has had time to finish,
          // release the compositor layers. 220ms covers the pulse animation.
          willChangeReset = window.setTimeout(() => {
            for (const r of reels) {
              r.strip.style.willChange = "auto";
            }
          }, maxEnd + 220);
        });
      });

      return () => {
        if (rafA) window.cancelAnimationFrame(rafA);
        if (rafB) window.cancelAnimationFrame(rafB);
        if (willChangeReset) window.clearTimeout(willChangeReset);
        for (const t of pulseTimers) window.clearTimeout(t);
        for (const a of pulseAnims) {
          try {
            a.cancel();
          } catch {
            // ignore — animation may already be finished
          }
        }
        // Belt-and-suspenders: if the component unmounts mid-roll, make sure
        // no stray will-change layers are left on the DOM.
        for (const r of reels) {
          r.strip.style.willChange = "auto";
        }
      };
    };

    let animationCleanup: (() => void) | undefined;
    const startAnimation = () => {
      if (animationCleanup) return;
      animationCleanup = runAnimation();
    };

    // Fallback for environments without IntersectionObserver (jsdom, older
    // browsers): run the animation immediately.
    if (typeof IntersectionObserver === "undefined") {
      startAnimation();
      return () => {
        animationCleanup?.();
      };
    }

    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          startAnimation();
          io.disconnect();
        }
      },
      { rootMargin: "50px" },
    );
    io.observe(rootEl);

    return () => {
      io.disconnect();
      animationCleanup?.();
    };
  }, [plan, durationMs, staggerMs]);

  let reelCursor = 0;
  return (
    <span
      ref={rootRef}
      className={cn(
        "inline-flex items-baseline tabular-nums leading-none",
        className,
      )}
      aria-label={value}
    >
      {plan.map((entry, idx) => {
        if (!entry.isDigit) {
          return (
            <span key={`s-${idx}`} aria-hidden="true" className="inline-block">
              {entry.char === " " ? "\u00A0" : entry.char}
            </span>
          );
        }
        const myIndex = reelCursor++;
        return (
          <span
            key={`d-${idx}`}
            ref={(el) => {
              cellRefs.current[myIndex] = el;
            }}
            aria-hidden="true"
            className="inline-block overflow-hidden align-baseline"
            style={{ height: "1em", width: "1ch", transformOrigin: "center" }}
          >
            <span
              ref={(el) => {
                stripRefs.current[myIndex] = el;
              }}
              className="flex flex-col"
              style={{ lineHeight: "1em" }}
            >
              {entry.digits.map((d, i) => (
                <span
                  key={i}
                  className="block text-center"
                  style={{ height: "1em" }}
                >
                  {d}
                </span>
              ))}
            </span>
          </span>
        );
      })}
    </span>
  );
}
