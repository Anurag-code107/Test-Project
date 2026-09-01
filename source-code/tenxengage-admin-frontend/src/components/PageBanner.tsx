interface PageBannerProps {
  title: string;
  subtitle: string;
  actions?: React.ReactNode;
  theme?: "default";
}

export function PageBanner({ title, subtitle, actions }: PageBannerProps) {
  return (
    <div className="relative overflow-hidden border-b bg-gradient-to-r from-[hsl(210_20%_99%)] via-[hsl(217_40%_97%)] to-[hsl(210_20%_99%)] px-6 py-6">
      {/* Decorative SVG art */}
      <svg
        className="pointer-events-none absolute inset-0 h-full w-full opacity-60"
        viewBox="0 0 1060 160"
        preserveAspectRatio="xMidYMid slice"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M-40 110 C120 80, 280 140, 440 100 S700 50, 860 90 S1040 130, 1060 100"
          stroke="hsl(217 91% 60% / 0.28)"
          strokeWidth="1.5"
        />
        <path
          d="M-20 130 C140 95, 320 155, 480 120 S740 60, 900 110 S1060 145, 1080 120"
          stroke="hsl(199 89% 48% / 0.22)"
          strokeWidth="1"
        />
        <path
          d="M-60 45 C100 70, 260 25, 400 55 S620 95, 800 45 S960 30, 1060 55"
          stroke="hsl(95 55% 50% / 0.20)"
          strokeWidth="1"
        />
        <circle cx="840" cy="40" r="28" stroke="hsl(217 91% 60% / 0.22)" strokeWidth="1" />
        <rect x="900" y="95" width="38" height="38" rx="8" stroke="hsl(199 89% 48% / 0.18)" strokeWidth="1" transform="rotate(12 919 114)" />
        <circle cx="760" cy="130" r="15" stroke="hsl(95 55% 50% / 0.18)" strokeWidth="1" />
        <circle cx="700" cy="35" r="2.5" fill="hsl(217 91% 60% / 0.30)" />
        <circle cx="960" cy="65" r="2" fill="hsl(95 55% 50% / 0.28)" />
        <circle cx="800" cy="90" r="2.5" fill="hsl(199 89% 48% / 0.28)" />
      </svg>

      {/* Content */}
      <div className="relative z-10 mx-auto flex w-full max-w-7xl items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
        </div>
        {actions && <div className="flex items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}
