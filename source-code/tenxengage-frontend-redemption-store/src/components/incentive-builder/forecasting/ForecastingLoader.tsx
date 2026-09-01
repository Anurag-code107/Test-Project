import { useEffect, useRef, useState } from "react";
import {
  Brain,
  Database,
  TrendingUp,
  Sparkles,
  Target,
  BarChart3,
  Calculator,
  CheckCircle2,
  Compass,
} from "lucide-react";

// Single combined list of phrases. They cycle on their own clock at a steady
// pace, decoupled from the progress bar — the bar gives a "rapid initial
// confidence" feel while the text gives a "let me explain what I'm doing"
// feel. Tone: domain-specific with a light playful edge.
const loadingPhrases = [
  // Initial setup phrases — only shown once per loader run.
  { text: "Analyzing partner performance data...", icon: Database },
  { text: "Evaluating historical trends...", icon: TrendingUp },
  { text: "Optimizing budget allocation...", icon: Target },
  { text: "Generating initial recommendations...", icon: Sparkles },
  // Extended phrases — these are looped if Claude is still working after the
  // initial run, so the screen never feels frozen on a single phrase.
  { text: "Crunching historical purchase orders...", icon: Database },
  { text: "Reading 39 months of seasonal patterns...", icon: TrendingUp },
  { text: "Cross-referencing similar past programs...", icon: Compass },
  { text: "Modeling cohort-level lift potential...", icon: BarChart3 },
  { text: "Stress-testing budget allocation across locations...", icon: Target },
  { text: "Validating training correlation signals...", icon: Brain },
  { text: "Drafting per-location insights...", icon: Sparkles },
  { text: "Double-checking the math...", icon: Calculator },
  { text: "Polishing the final forecast...", icon: CheckCircle2 },
];
const INITIAL_PHRASE_COUNT = 4; // first N phrases are "setup" — not included in the loop

const PHRASE_INTERVAL_MS = 3000; // steady dwell time per phrase (independent of progress bar)
const RUNWAY_MS = 6000; // progress bar reaches CAP over this window
const CAP = 90; // progress reaches this at end of the runway
const CEILING = 98; // asymptotic creep target past the cap (never reaches this)
const TAU_MS = 8000; // creep time constant — higher = slower creep
const COMPLETION_MS = 350; // smooth jump to 100% when isComplete fires

interface ForecastingLoaderProps {
  /** Live status text from the SSE stream — displayed below the step text when available */
  status?: string;
  /** When true, the progress bar animates to 100% and holds. Parent should delay
   *  unmounting the loader by ~COMPLETION_MS so the fill is visible. */
  isComplete?: boolean;
  /** Additional CSS classes (e.g. "flex-1" to fill parent height) */
  className?: string;
}

export function ForecastingLoader({
  status,
  isComplete,
  className,
}: ForecastingLoaderProps) {
  const [phraseIndex, setPhraseIndex] = useState(0);
  const progressBarRef = useRef<HTMLDivElement>(null);

  // Phrase cycling — single steady clock at PHRASE_INTERVAL_MS regardless of
  // where the progress bar is. After we walk through every phrase once, loop
  // back to the start of the EXTENDED block (skip the "setup" phrases so we
  // don't show "Analyzing partner performance data..." again 30s in).
  useEffect(() => {
    const interval = setInterval(() => {
      setPhraseIndex((i) => {
        const next = i + 1;
        if (next >= loadingPhrases.length) return INITIAL_PHRASE_COUNT;
        return next;
      });
    }, PHRASE_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  // Progress curve: linear to CAP over RUNWAY_MS, then asymptotic creep toward
  // CEILING, jump to 100% on isComplete. Direct DOM writes avoid re-rendering
  // the whole component 60× per second.
  useEffect(() => {
    const bar = progressBarRef.current;
    if (!bar) return;

    if (isComplete) {
      bar.style.transition = `width ${COMPLETION_MS}ms ease-out`;
      bar.style.width = "100%";
      return;
    }

    // Live progress — cancel any prior "complete" transition
    bar.style.transition = "none";

    const startTime = performance.now();
    let rafId = 0;

    const tick = () => {
      const elapsed = performance.now() - startTime;
      let p: number;
      if (elapsed < RUNWAY_MS) {
        p = (elapsed / RUNWAY_MS) * CAP;
      } else {
        const creep = elapsed - RUNWAY_MS;
        p = CAP + (CEILING - CAP) * (1 - Math.exp(-creep / TAU_MS));
      }
      bar.style.width = `${Math.min(p, CEILING)}%`;
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);

    return () => cancelAnimationFrame(rafId);
  }, [isComplete]);

  // Active line: initial runway uses the fixed step list; afterwards we cycle
  // through extendedSteps. The render key combines phase + index so the
  // fade-in animation re-fires every time the line changes.
  const activeLine = loadingPhrases[phraseIndex];
  const CurrentIcon = activeLine?.icon ?? Brain;
  const activeKey = `phrase-${phraseIndex}`;

  return (
    <div
      className={`relative w-full overflow-hidden bg-gradient-to-br from-primary/5 via-background to-primary/5 flex items-center justify-center rounded-xl border border-border/50 py-16 ${className ?? ""}`}
    >
      {/* Background particles */}
      <div className="absolute inset-0 overflow-hidden" aria-hidden="true">
        {Array.from({ length: 20 }, (_, i) => (
          <div
            key={i}
            className="absolute w-1 h-1 bg-primary/20 rounded-full animate-pulse"
            style={{
              left: `${(i * 37) % 100}%`,
              top: `${(i * 53) % 100}%`,
              animationDelay: `${(i % 5) * 0.4}s`,
              animationDuration: `${2 + (i % 3)}s`,
            }}
          />
        ))}
      </div>

      {/* Content */}
      <div className="relative z-10 flex flex-col items-center gap-8 px-4">
        {/* AI icon */}
        <div className="relative">
          <div className="absolute inset-0 bg-primary/20 rounded-full blur-2xl animate-pulse" />
          <div className="relative w-24 h-24 bg-gradient-to-br from-primary to-primary/60 rounded-full flex items-center justify-center animate-fade-in">
            <Brain className="w-12 h-12 text-primary-foreground animate-pulse" />
          </div>
          <Sparkles
            className="absolute -top-2 -right-2 w-6 h-6 text-primary animate-pulse"
            style={{ animationDelay: "0.5s" }}
          />
          <Sparkles
            className="absolute -bottom-2 -left-2 w-5 h-5 text-primary animate-pulse"
            style={{ animationDelay: "1s" }}
          />
        </div>

        {/* Step indicator */}
        <div className="flex flex-col items-center gap-4 min-h-[80px]">
          <div
            className="flex items-center gap-3 animate-fade-in"
            key={activeKey}
          >
            <CurrentIcon className="w-5 h-5 text-primary" />
            <p className="text-lg font-medium text-foreground">
              {activeLine?.text}
            </p>
          </div>

          {/* Progress bar */}
          <div className="w-64 h-1.5 bg-muted rounded-full overflow-hidden">
            <div
              ref={progressBarRef}
              className="h-full bg-gradient-to-r from-primary to-primary/60 rounded-full"
              style={{ width: "0%" }}
            />
          </div>

          {/* Pulsing dots */}
          <div className="flex gap-1.5">
            {Array.from({ length: 3 }, (_, i) => (
              <div
                key={i}
                className="w-2 h-2 bg-primary/60 rounded-full animate-pulse"
                style={{ animationDelay: `${i * 0.2}s` }}
              />
            ))}
          </div>
        </div>

        {/* Status text */}
        <p className="text-sm text-muted-foreground animate-fade-in">
          {status || "Crafting the perfect incentive program for your partners"}
        </p>
      </div>
    </div>
  );
}
