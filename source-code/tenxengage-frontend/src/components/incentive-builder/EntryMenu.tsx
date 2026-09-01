import { Sparkles, Copy, Upload, ArrowRight } from "lucide-react";

interface EntryMenuProps {
  onCreateFromScratch: () => void;
  onCreateFromExisting: () => void;
  onCreateFromTemplate: () => void;
}

/* ── Card configurations ──────────────────────────────────────────────────── */

const entryOptions = [
  {
    id: "scratch" as const,
    icon: Sparkles,
    title: "Create From Scratch",
    description:
      "Start fresh and build a brand new incentive program with AI assistance or manual setup.",
    accentColor: "hsl(217 91% 60%)",
    glowColor: "hsl(217 91% 60% / 0.06)",
    hoverGlow: "hsl(217 91% 60% / 0.12)",
    borderHover: "hover:border-[hsl(217_91%_60%/0.35)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(217_60%_55%/0.12),0_2px_8px_hsl(217_60%_55%/0.08)]",
    iconBg: "bg-[hsl(217_91%_60%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(217_91%_60%/0.14)]",
    iconText: "text-[hsl(217_91%_55%)]",
    arrowHover: "group-hover:text-[hsl(217_91%_55%)]",
  },
  {
    id: "existing" as const,
    icon: Copy,
    title: "Create From Existing",
    description:
      "Use a previously created incentive as a template and customize it for a new program.",
    accentColor: "hsl(160 60% 40%)",
    glowColor: "hsl(160 60% 40% / 0.06)",
    hoverGlow: "hsl(160 60% 40% / 0.12)",
    borderHover: "hover:border-[hsl(160_60%_40%/0.35)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(160_40%_35%/0.12),0_2px_8px_hsl(160_40%_35%/0.08)]",
    iconBg: "bg-[hsl(160_60%_40%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(160_60%_40%/0.14)]",
    iconText: "text-[hsl(160_60%_38%)]",
    arrowHover: "group-hover:text-[hsl(160_60%_38%)]",
  },
  {
    id: "template" as const,
    icon: Upload,
    title: "Create From Template",
    description:
      "Download an Excel template, fill it out offline, then upload it to auto-populate your incentive setup.",
    accentColor: "hsl(263 50% 55%)",
    glowColor: "hsl(263 50% 55% / 0.06)",
    hoverGlow: "hsl(263 50% 55% / 0.12)",
    borderHover: "hover:border-[hsl(263_50%_55%/0.35)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(263_40%_45%/0.12),0_2px_8px_hsl(263_40%_45%/0.08)]",
    iconBg: "bg-[hsl(263_50%_55%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(263_50%_55%/0.14)]",
    iconText: "text-[hsl(263_50%_50%)]",
    arrowHover: "group-hover:text-[hsl(263_50%_50%)]",
  },
];

/* ── Component ────────────────────────────────────────────────────────────── */

export function EntryMenu({
  onCreateFromScratch,
  onCreateFromExisting,
  onCreateFromTemplate,
}: EntryMenuProps) {
  function handleClick(optionId: "scratch" | "existing" | "template") {
    switch (optionId) {
      case "scratch":
        onCreateFromScratch();
        break;
      case "existing":
        onCreateFromExisting();
        break;
      case "template":
        onCreateFromTemplate();
        break;
    }
  }

  return (
    <div
      className="relative min-h-[calc(100vh-64px)] flex items-center justify-center overflow-hidden"
      data-tour="builder-content"
    >
      {/* Content */}
      <div className="relative z-10 w-full max-w-5xl mx-auto px-6 pb-12">
        {/* Hero heading */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-[hsl(217_91%_60%/0.15)] bg-[hsl(217_91%_60%/0.04)] mb-6">
            <Sparkles className="h-3.5 w-3.5 text-[hsl(217_91%_55%)]" />
            <span className="text-xs font-medium text-[hsl(217_91%_48%)]">
              Incentive Builder
            </span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            Build Your Incentive
          </h1>
          <p className="text-base text-muted-foreground mt-3 mx-auto">
            Choose a path to create your next incentive program
          </p>
        </div>

        {/* Cards grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {entryOptions.map((option) => (
            <button
              key={option.id}
              type="button"
              onClick={() => handleClick(option.id)}
              data-tour={`create-from-${option.id}`}
              className={`group relative flex flex-col items-center text-center rounded-2xl border border-border bg-background/70 backdrop-blur-sm p-7 transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 ${option.borderHover} ${option.shadowHover}`}
            >
              {/* Glow overlay on hover */}
              <div
                className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
                style={{
                  background: `radial-gradient(ellipse at 50% 0%, ${option.hoverGlow} 0%, transparent 70%)`,
                }}
              />

              {/* Icon */}
              <div
                className={`relative z-10 flex items-center justify-center w-14 h-14 rounded-2xl ${option.iconBg} ${option.iconHoverBg} transition-[background-color] duration-300 mb-5`}
              >
                <option.icon className={`h-6 w-6 ${option.iconText}`} />
              </div>

              {/* Text */}
              <h3 className="relative z-10 font-semibold text-base text-foreground mb-2">
                {option.title}
              </h3>
              <p className="relative z-10 text-sm text-muted-foreground leading-relaxed mb-5">
                {option.description}
              </p>

              {/* Arrow indicator */}
              <div
                className={`relative z-10 mt-auto flex items-center gap-1.5 text-xs font-medium text-[hsl(200_10%_60%)] ${option.arrowHover} transition-colors`}
              >
                <span>Get started</span>
                <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
