/**
 * Persistent full-screen SVG background for the incentive builder flow.
 * Wavy lines and shapes drift gently via CSS animations (ambient only).
 * No navigation-triggered surge or burst effects.
 */
export function BuilderFlowBackground() {
  return (
    <div className="pointer-events-none absolute inset-0 select-none overflow-hidden">
      <svg
        className="absolute inset-0 h-full w-full"
        viewBox="0 0 1400 800"
        preserveAspectRatio="xMidYMid slice"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* ─── Layer 1: Primary flowing waves (deep undulation) ────────── */}
        <path
          d="M-80 140 C60 80, 180 240, 360 160 C540 80, 660 260, 840 180 C1020 100, 1140 250, 1320 170 C1420 130, 1460 180, 1500 150"
          stroke="hsl(217 91% 60% / 0.18)"
          strokeWidth="1.8"
          className="builder-flow-line builder-flow-line-1"
        />
        <path
          d="M-60 300 C100 220, 240 380, 420 290 C600 200, 740 390, 920 300 C1100 210, 1220 370, 1400 280 L1500 310"
          stroke="hsl(160 60% 40% / 0.15)"
          strokeWidth="1.5"
          className="builder-flow-line builder-flow-line-2"
        />
        <path
          d="M-40 460 C120 380, 280 540, 460 440 C640 340, 780 540, 960 450 C1140 360, 1260 520, 1440 430 L1500 460"
          stroke="hsl(263 50% 55% / 0.13)"
          strokeWidth="1.3"
          className="builder-flow-line builder-flow-line-3"
        />
        <path
          d="M-100 620 C80 550, 220 700, 400 610 C580 520, 720 700, 900 620 C1080 540, 1200 680, 1380 600 L1500 630"
          stroke="hsl(38 80% 50% / 0.12)"
          strokeWidth="1.2"
          className="builder-flow-line builder-flow-line-4"
        />

        {/* ─── Layer 2: Secondary waves (offset rhythm) ────────────────── */}
        <path
          d="M-60 60 C140 120, 280 20, 460 90 C640 160, 780 40, 960 100 C1140 160, 1280 50, 1460 110"
          stroke="hsl(199 89% 48% / 0.12)"
          strokeWidth="1"
          className="builder-flow-line builder-flow-line-5"
        />
        <path
          d="M-80 740 C100 680, 260 780, 440 720 C620 660, 780 770, 960 710 C1140 650, 1280 760, 1460 700"
          stroke="hsl(217 91% 60% / 0.10)"
          strokeWidth="1"
          className="builder-flow-line builder-flow-line-6"
        />

        {/* ─── Layer 3: Faint accent waves ─────────────────────────────── */}
        <path
          d="M-40 220 C160 280, 300 170, 480 240 C660 310, 800 190, 980 260 C1160 330, 1300 210, 1480 270"
          stroke="hsl(217 91% 60% / 0.07)"
          strokeWidth="0.8"
          className="builder-flow-line builder-flow-line-1"
        />
        <path
          d="M-60 560 C140 500, 300 610, 480 540 C660 470, 800 590, 980 520 C1160 450, 1300 570, 1480 510"
          stroke="hsl(160 60% 40% / 0.06)"
          strokeWidth="0.8"
          className="builder-flow-line builder-flow-line-3"
        />

        {/* ─── Scattered shapes ────────────────────────────────────────── */}
        <circle
          cx="120"
          cy="200"
          r="28"
          stroke="hsl(217 91% 60% / 0.12)"
          strokeWidth="1.2"
          className="builder-flow-shape builder-flow-shape-1"
        />
        <rect
          x="1200"
          y="140"
          width="40"
          height="40"
          rx="10"
          stroke="hsl(38 80% 50% / 0.12)"
          strokeWidth="1.2"
          className="builder-flow-shape builder-flow-shape-2"
          transform="rotate(12 1220 160)"
        />
        <polygon
          points="1280,620 1300,580 1320,620"
          stroke="hsl(200 80% 50% / 0.10)"
          strokeWidth="1.2"
          className="builder-flow-shape builder-flow-shape-3"
        />
        <circle
          cx="100"
          cy="580"
          r="20"
          stroke="hsl(260 50% 55% / 0.10)"
          strokeWidth="1.2"
          className="builder-flow-shape builder-flow-shape-1"
        />
        <rect
          x="680"
          y="50"
          width="32"
          height="32"
          rx="8"
          stroke="hsl(263 50% 55% / 0.08)"
          strokeWidth="1"
          className="builder-flow-shape builder-flow-shape-3"
          transform="rotate(-8 696 66)"
        />
        <circle
          cx="1350"
          cy="440"
          r="22"
          stroke="hsl(160 60% 40% / 0.08)"
          strokeWidth="1"
          className="builder-flow-shape builder-flow-shape-2"
        />

        {/* ─── Dots ────────────────────────────────────────────────────── */}
        <circle
          cx="300"
          cy="120"
          r="3"
          fill="hsl(217 91% 60% / 0.18)"
          className="builder-flow-dot builder-flow-dot-1"
        />
        <circle
          cx="900"
          cy="220"
          r="2.5"
          fill="hsl(160 60% 40% / 0.15)"
          className="builder-flow-dot builder-flow-dot-2"
        />
        <circle
          cx="600"
          cy="680"
          r="3"
          fill="hsl(263 50% 55% / 0.14)"
          className="builder-flow-dot builder-flow-dot-3"
        />
        <circle
          cx="200"
          cy="420"
          r="2.5"
          fill="hsl(38 80% 50% / 0.14)"
          className="builder-flow-dot builder-flow-dot-2"
        />
        <circle
          cx="1100"
          cy="480"
          r="2.5"
          fill="hsl(199 89% 48% / 0.14)"
          className="builder-flow-dot builder-flow-dot-1"
        />
        <circle
          cx="1300"
          cy="340"
          r="3"
          fill="hsl(217 91% 60% / 0.12)"
          className="builder-flow-dot builder-flow-dot-3"
        />
        <circle
          cx="500"
          cy="360"
          r="2"
          fill="hsl(263 50% 55% / 0.10)"
          className="builder-flow-dot builder-flow-dot-1"
        />
        <circle
          cx="1050"
          cy="100"
          r="2"
          fill="hsl(38 80% 50% / 0.12)"
          className="builder-flow-dot builder-flow-dot-2"
        />
      </svg>

      {/* Radial gradient overlay — keeps center clear for content */}
      <div
        className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 50% at 50% 40%, transparent 0%, hsl(var(--background) / 0.85) 100%)",
        }}
      />
    </div>
  );
}
