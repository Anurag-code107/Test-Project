import { useBrandingContext } from "@/contexts/BrandingContext";

/* ─── Floating background art (same visual language as builder screens) ── */
function BackgroundArt() {
  return (
    <svg
      className="absolute inset-0 w-full h-full pointer-events-none"
      viewBox="0 0 1440 900"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
    >
      {/* Flowing curves */}
      <path
        d="M-60 320 C200 260,400 400,720 340 S1100 220,1500 300"
        fill="none"
        stroke="hsl(217 91% 60% / 0.07)"
        strokeWidth="2"
        className="app-loading-line-1"
      />
      <path
        d="M-40 520 C240 460,520 580,800 500 S1160 420,1520 480"
        fill="none"
        stroke="hsl(95 55% 50% / 0.06)"
        strokeWidth="2"
        className="app-loading-line-2"
      />
      <path
        d="M-80 680 C160 640,440 740,760 680 S1120 600,1500 660"
        fill="none"
        stroke="hsl(199 89% 48% / 0.05)"
        strokeWidth="1.5"
        className="app-loading-line-3"
      />

      {/* Scattered shapes */}
      <circle
        cx="180"
        cy="200"
        r="32"
        fill="none"
        stroke="hsl(217 91% 60% / 0.06)"
        strokeWidth="1.5"
        className="app-loading-shape-1"
      />
      <rect
        x="1180"
        y="160"
        width="48"
        height="48"
        rx="12"
        fill="none"
        stroke="hsl(38 80% 50% / 0.06)"
        strokeWidth="1.5"
        className="app-loading-shape-2"
        transform="rotate(15 1204 184)"
      />
      <polygon
        points="1300,680 1320,640 1340,680"
        fill="none"
        stroke="hsl(200 80% 50% / 0.06)"
        strokeWidth="1.5"
        className="app-loading-shape-3"
      />
      <circle
        cx="120"
        cy="640"
        r="24"
        fill="none"
        stroke="hsl(260 50% 55% / 0.06)"
        strokeWidth="1.5"
        className="app-loading-shape-1"
      />

      {/* Dots */}
      <circle
        cx="400"
        cy="160"
        r="3"
        fill="hsl(217 91% 60% / 0.10)"
        className="app-loading-dot"
      />
      <circle
        cx="1050"
        cy="280"
        r="2.5"
        fill="hsl(95 55% 50% / 0.10)"
        className="app-loading-dot app-loading-dot-2"
      />
      <circle
        cx="680"
        cy="740"
        r="3"
        fill="hsl(199 89% 48% / 0.08)"
        className="app-loading-dot app-loading-dot-3"
      />
      <circle
        cx="260"
        cy="480"
        r="2"
        fill="hsl(38 80% 50% / 0.08)"
        className="app-loading-dot app-loading-dot-2"
      />
      <circle
        cx="1260"
        cy="520"
        r="2.5"
        fill="hsl(260 50% 55% / 0.08)"
        className="app-loading-dot app-loading-dot-3"
      />
    </svg>
  );
}

/* ─── Orbiting dots around the logo ─────────────────────────────────── */
function OrbitDots() {
  return (
    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
      <div className="relative w-32 h-32">
        {/* Orbit ring */}
        <div className="absolute inset-0 rounded-full border border-[hsl(217_91%_60%/0.08)]" />

        {/* Dot 1 — blue */}
        <div className="absolute inset-0 app-loading-orbit-1">
          <div className="absolute -top-1 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full bg-[hsl(217_91%_60%/0.35)]" />
        </div>

        {/* Dot 2 — green */}
        <div className="absolute inset-0 app-loading-orbit-2">
          <div className="absolute -top-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-[hsl(95_55%_50%/0.30)]" />
        </div>

        {/* Dot 3 — cyan */}
        <div className="absolute inset-0 app-loading-orbit-3">
          <div className="absolute -top-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-[hsl(199_89%_48%/0.30)]" />
        </div>
      </div>
    </div>
  );
}

export function AppLoadingScreen() {
  const { logoSrc } = useBrandingContext();
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center overflow-hidden"
      style={{
        background:
          "radial-gradient(ellipse 70% 60% at 50% 45%, hsl(210 30% 99%) 0%, hsl(210 20% 96.5%) 55%, hsl(210 18% 94.5%) 100%)",
      }}
    >
      <BackgroundArt />

      {/* Soft glow behind logo */}
      <div
        className="absolute rounded-full app-loading-glow"
        style={{
          width: 200,
          height: 200,
          background:
            "radial-gradient(circle, hsl(217 91% 60% / 0.06) 0%, transparent 70%)",
        }}
      />

      {/* Orbiting dots */}
      <OrbitDots />

      {/* Logo with breathe */}
      <div className="relative z-10 app-loading-logo">
        <img
          src={logoSrc}
          alt="Logo"
          className="block w-[266px] sm:w-[310px] h-auto min-h-12 sm:min-h-14 max-h-[120px] sm:max-h-[140px] object-contain"
        />
      </div>

      {/* Progress bar */}
      <div className="relative z-10 mt-8">
        <div className="app-loading-track">
          <div className="app-loading-bar" />
        </div>
      </div>

      {/* Subtle label */}
      <p className="relative z-10 mt-4 text-sm text-[hsl(200_10%_56%)] font-medium tracking-wide app-loading-text">
        Loading your workspace…
      </p>
    </div>
  );
}
