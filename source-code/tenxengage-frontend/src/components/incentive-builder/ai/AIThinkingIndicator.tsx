import { Pause } from "lucide-react";

interface AIThinkingIndicatorProps {
  label: string;
  onPause: () => void;
}

/**
 * Particle-based thinking animation for the AI copilot.
 * Renders orbiting dots around a central glow, with a status label and pause button.
 * Uses pure CSS animations — no JS timers or canvas.
 */
export function AIThinkingIndicator({ label, onPause }: AIThinkingIndicatorProps) {
  return (
    <div className="flex flex-col items-center gap-3 pt-4 animate-fade-in">
      {/* Particle field */}
      <div className="relative w-12 h-12">
        {/* Central glow */}
        <div className="absolute inset-2 rounded-full bg-primary/20 blur-md animate-pulse" />
        <div className="absolute inset-3 rounded-full bg-primary/30 blur-sm" />

        {/* Orbiting particles — each on its own orbit ring */}
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

      {/* Pause button — prominent so the user knows they can take control */}
      <button
        onClick={onPause}
        className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold text-foreground bg-muted hover:bg-muted/70 hover:text-foreground border border-border hover:border-muted-foreground/40 transition-all shadow-sm"
        title="Pause AI and take control"
      >
        <Pause className="h-3.5 w-3.5" />
        Take control
      </button>
    </div>
  );
}

/** Particle config — each particle orbits at a different speed, offset, and size */
const PARTICLES = [
  { duration: 2.0, delay: 0.0, size: 4, offset: 0, opacity: 0.9 },
  { duration: 2.6, delay: 0.3, size: 3, offset: 2, opacity: 0.7 },
  { duration: 1.8, delay: 0.6, size: 5, offset: -1, opacity: 0.8 },
  { duration: 3.0, delay: 0.1, size: 2.5, offset: 4, opacity: 0.5 },
  { duration: 2.2, delay: 0.8, size: 3.5, offset: 1, opacity: 0.6 },
  { duration: 2.8, delay: 0.4, size: 2, offset: 3, opacity: 0.4 },
];
