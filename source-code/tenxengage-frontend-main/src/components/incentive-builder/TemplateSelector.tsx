import {
  Megaphone,
  GraduationCap,
  ClipboardList,
  Route,
  Upload,
  ArrowLeft,
  ArrowRight,
  FileSpreadsheet,
} from "lucide-react";
import type { IncentiveType } from "@/types/incentive.types";
import { FeatureGate } from "@/components/FeatureGate";

interface TemplateSelectorProps {
  onSelectType: (type: IncentiveType) => void;
  onBack: () => void;
}

/* ── SVG mini-illustrations for each card ─────────────────────────────── */

function SalesTemplateIllustration() {
  return (
    <svg viewBox="0 0 200 90" fill="none" className="w-full h-full">
      {/* Spreadsheet grid */}
      <rect
        x="50"
        y="18"
        width="60"
        height="54"
        rx="4"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.8"
      />
      <line
        x1="50"
        y1="32"
        x2="110"
        y2="32"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="46"
        x2="110"
        y2="46"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="58"
        x2="110"
        y2="58"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="72"
        y1="18"
        x2="72"
        y2="72"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="92"
        y1="18"
        x2="92"
        y2="72"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      {/* Upload arrow overlay */}
      <g>
        <circle
          cx="145"
          cy="42"
          r="16"
          fill="hsl(217 91% 60% / 0.15)"
          stroke="hsl(217 91% 60%)"
          strokeWidth="0.8"
        />
        <path
          d="M145 50 v-14 m-5 5 l5 -6 5 6"
          stroke="hsl(217 91% 60%)"
          strokeWidth="1"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>
      {/* Dots */}
      <circle cx="35" cy="35" r="2" fill="hsl(217 91% 60%)" />
      <circle cx="170" cy="65" r="1.5" fill="hsl(217 91% 60%)" />
    </svg>
  );
}

function TrainingTemplateIllustration() {
  return (
    <svg viewBox="0 0 200 90" fill="none" className="w-full h-full">
      {/* Spreadsheet grid */}
      <rect
        x="50"
        y="18"
        width="60"
        height="54"
        rx="4"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.8"
      />
      <line
        x1="50"
        y1="32"
        x2="110"
        y2="32"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="46"
        x2="110"
        y2="46"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="58"
        x2="110"
        y2="58"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="72"
        y1="18"
        x2="72"
        y2="72"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="92"
        y1="18"
        x2="92"
        y2="72"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
      />
      {/* Graduation cap accent */}
      <path
        d="M140 32 l18 -9 18 9 -18 9Z"
        fill="hsl(38 80% 50% / 0.15)"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.7"
      />
      <line
        x1="158"
        y1="32"
        x2="158"
        y2="46"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.7"
      />
      <path
        d="M146 37 v8 c0 4 6 6 12 6 s12 -2 12 -6 v-8"
        stroke="hsl(38 80% 50%)"
        strokeWidth="0.6"
        fill="none"
      />
      {/* Dots */}
      <circle cx="35" cy="40" r="2" fill="hsl(38 80% 50%)" />
      <circle cx="180" cy="58" r="1.5" fill="hsl(38 80% 50%)" />
    </svg>
  );
}

function ActivityTemplateIllustration() {
  return (
    <svg viewBox="0 0 200 90" fill="none" className="w-full h-full">
      {/* Spreadsheet grid */}
      <rect
        x="50"
        y="18"
        width="60"
        height="54"
        rx="4"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.8"
      />
      <line
        x1="50"
        y1="32"
        x2="110"
        y2="32"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="46"
        x2="110"
        y2="46"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="50"
        y1="58"
        x2="110"
        y2="58"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="72"
        y1="18"
        x2="72"
        y2="72"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <line
        x1="92"
        y1="18"
        x2="92"
        y2="72"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      {/* Clipboard with checkmarks */}
      <rect
        x="132"
        y="28"
        width="30"
        height="38"
        rx="3"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.8"
      />
      <rect
        x="140"
        y="24"
        width="14"
        height="7"
        rx="2"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <path
        d="M138 41 l2 2 4 -4"
        stroke="hsl(160 60% 40%)"
        strokeWidth="0.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <line
        x1="148"
        y1="41"
        x2="158"
        y2="41"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      <path
        d="M138 52 l2 2 4 -4"
        stroke="hsl(160 60% 40%)"
        strokeWidth="0.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <line
        x1="148"
        y1="52"
        x2="158"
        y2="52"
        stroke="hsl(200 80% 50%)"
        strokeWidth="0.6"
      />
      {/* Dots */}
      <circle cx="35" cy="45" r="2" fill="hsl(200 80% 50%)" />
      <circle cx="175" cy="60" r="1.5" fill="hsl(200 80% 50%)" />
    </svg>
  );
}

/* ── Step flow visualization — Download → Fill → Upload ──────────────── */

function StepFlowIllustration() {
  return (
    <svg viewBox="0 0 500 50" fill="none" className="w-full h-full">
      {/* Connecting dashed line */}
      <path
        d="M70 25 L220 25"
        stroke="hsl(263 50% 55%)"
        strokeWidth="1.2"
        strokeDasharray="5 4"
      />
      <path
        d="M280 25 L430 25"
        stroke="hsl(263 50% 55%)"
        strokeWidth="1.2"
        strokeDasharray="5 4"
      />

      {/* Step 1: Download */}
      <circle
        cx="50"
        cy="25"
        r="18"
        fill="hsl(263 50% 55% / 0.10)"
        stroke="hsl(263 50% 55%)"
        strokeWidth="1.2"
      />
      <path
        d="M50 19 v10 m-4 -4 l4 5 4 -5"
        stroke="hsl(263 50% 55%)"
        strokeWidth="1.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <text
        x="50"
        y="50"
        textAnchor="middle"
        fontSize="7"
        fill="hsl(263 50% 55%)"
        fontWeight="500"
      >
        Download
      </text>

      {/* Step 2: Fill out */}
      <circle
        cx="250"
        cy="25"
        r="18"
        fill="hsl(217 91% 60% / 0.10)"
        stroke="hsl(217 91% 60%)"
        strokeWidth="1.2"
      />
      <rect
        x="242"
        y="17"
        width="16"
        height="16"
        rx="2"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.8"
      />
      <line
        x1="246"
        y1="22"
        x2="254"
        y2="22"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="246"
        y1="26"
        x2="252"
        y2="26"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <line
        x1="246"
        y1="30"
        x2="256"
        y2="30"
        stroke="hsl(217 91% 60%)"
        strokeWidth="0.6"
      />
      <text
        x="250"
        y="50"
        textAnchor="middle"
        fontSize="7"
        fill="hsl(217 91% 60%)"
        fontWeight="500"
      >
        Fill Out
      </text>

      {/* Step 3: Upload */}
      <circle
        cx="450"
        cy="25"
        r="18"
        fill="hsl(160 60% 40% / 0.10)"
        stroke="hsl(160 60% 40%)"
        strokeWidth="1.2"
      />
      <path
        d="M450 31 v-10 m-4 4 l4 -5 4 5"
        stroke="hsl(160 60% 40%)"
        strokeWidth="1.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <text
        x="450"
        y="50"
        textAnchor="middle"
        fontSize="7"
        fill="hsl(160 60% 40%)"
        fontWeight="500"
      >
        Upload
      </text>

      {/* Arrow tips */}
      <path d="M216 22 l6 3 -6 3" fill="hsl(263 50% 55%)" />
      <path d="M426 22 l6 3 -6 3" fill="hsl(263 50% 55%)" />
    </svg>
  );
}

/* ── Template type cards config ───────────────────────────────────────── */

const templateTypes = [
  {
    type: "SALES" as IncentiveType,
    icon: Megaphone,
    title: "Sales Incentive",
    description:
      "Give your partners something customized to strive for with targeted promotions.",
    illustration: SalesTemplateIllustration,
    gradientFrom: "hsl(217_91%_60%/0.04)",
    gradientTo: "hsl(217_91%_60%/0.12)",
    accentLine: "hsl(217_91%_60%/0.40)",
    iconBg: "bg-[hsl(217_91%_60%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(217_91%_60%/0.14)]",
    iconText: "text-[hsl(217_91%_55%)]",
    borderHover: "hover:border-[hsl(217_91%_60%/0.30)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(217_60%_55%/0.10),0_2px_8px_hsl(217_60%_55%/0.06)]",
    arrowHover: "group-hover:text-[hsl(217_91%_55%)]",
    downloadBg: "bg-[hsl(217_91%_60%)]",
    downloadHover: "hover:bg-[hsl(217_91%_52%)]",
  },
  {
    type: "TRAINING" as IncentiveType,
    icon: GraduationCap,
    title: "Training Incentive",
    description:
      "Reward your partners for completing training and learning new skills.",
    illustration: TrainingTemplateIllustration,
    gradientFrom: "hsl(38_80%_55%/0.04)",
    gradientTo: "hsl(38_80%_55%/0.12)",
    accentLine: "hsl(38_80%_50%/0.40)",
    iconBg: "bg-[hsl(38_80%_55%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(38_80%_55%/0.14)]",
    iconText: "text-[hsl(38_80%_45%)]",
    borderHover: "hover:border-[hsl(38_80%_50%/0.30)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(38_60%_50%/0.10),0_2px_8px_hsl(38_60%_50%/0.06)]",
    arrowHover: "group-hover:text-[hsl(38_80%_45%)]",
    downloadBg: "bg-[hsl(38_80%_50%)]",
    downloadHover: "hover:bg-[hsl(38_80%_42%)]",
  },
  {
    type: "ACTIVITY" as IncentiveType,
    icon: ClipboardList,
    title: "Activity Incentive",
    description:
      "Require proof of execution for partner activities and reward compliance.",
    illustration: ActivityTemplateIllustration,
    gradientFrom: "hsl(200_80%_50%/0.04)",
    gradientTo: "hsl(200_80%_50%/0.12)",
    accentLine: "hsl(200_80%_50%/0.40)",
    iconBg: "bg-[hsl(200_80%_50%/0.08)]",
    iconHoverBg: "group-hover:bg-[hsl(200_80%_50%/0.14)]",
    iconText: "text-[hsl(200_80%_45%)]",
    borderHover: "hover:border-[hsl(200_80%_50%/0.30)]",
    shadowHover:
      "hover:shadow-[0_8px_32px_hsl(200_60%_50%/0.10),0_2px_8px_hsl(200_60%_50%/0.06)]",
    arrowHover: "group-hover:text-[hsl(200_80%_45%)]",
    downloadBg: "bg-[hsl(200_80%_50%)]",
    downloadHover: "hover:bg-[hsl(200_80%_42%)]",
  },
];

/* ── Component ────────────────────────────────────────────────────────── */

export function TemplateSelector({
  onSelectType,
  onBack,
}: TemplateSelectorProps) {
  return (
    <div className="relative min-h-[calc(100vh-64px)] overflow-hidden">
      <div className="relative z-10 max-w-3xl mx-auto pt-12">
        {/* Top bar: back */}
        <div className="flex items-center mb-4">
          <button
            type="button"
            onClick={onBack}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Back</span>
          </button>
        </div>

        {/* Hero heading */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-[hsl(263_50%_55%/0.15)] bg-[hsl(263_50%_55%/0.04)] mb-6">
            <FileSpreadsheet className="h-3.5 w-3.5 text-[hsl(263_50%_50%)]" />
            <span className="text-xs font-medium text-[hsl(263_50%_42%)]">
              Create From Template
            </span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            Upload a Template
          </h1>
          <p className="text-base text-muted-foreground mt-3 mx-auto">
            Choose an engagement type, download the template, fill it out, then
            upload
          </p>
        </div>

        {/* Step flow — Download → Fill → Upload */}
        <div className="mb-10 flex justify-center">
          <div className="rounded-full border border-border bg-background/60 backdrop-blur-sm px-6 py-3">
            <div className="h-12">
              <StepFlowIllustration />
            </div>
          </div>
        </div>

        {/* Type showcase cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
          {templateTypes.map((t) => (
            <button
              key={t.type}
              type="button"
              onClick={() => onSelectType(t.type)}
              className={`group relative flex flex-col rounded-2xl border border-border bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 ${t.borderHover} ${t.shadowHover}`}
            >
              {/* Illustration area */}
              <div
                className="relative h-24 overflow-hidden opacity-50 group-hover:opacity-100 transition-opacity duration-300"
                style={{
                  background: `linear-gradient(to bottom right, ${t.gradientFrom}, ${t.gradientTo})`,
                }}
              >
                <t.illustration />
                <div
                  className="absolute bottom-0 left-0 right-0 h-[2px]"
                  style={{
                    background: `linear-gradient(to right, transparent, ${t.accentLine}, transparent)`,
                  }}
                />
              </div>

              {/* Content */}
              <div className="p-4 flex-1 flex flex-col">
                <div className="flex items-center gap-2.5 mb-2">
                  <div
                    className={`flex items-center justify-center w-8 h-8 rounded-lg ${t.iconBg} ${t.iconHoverBg} transition-colors`}
                  >
                    <t.icon className={`h-4 w-4 ${t.iconText}`} />
                  </div>
                  <h3 className="font-semibold text-sm text-foreground">
                    {t.title}
                  </h3>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed mb-4">
                  {t.description}
                </p>
                <div
                  className={`mt-auto flex items-center gap-1.5 text-xs font-medium text-muted-foreground ${t.arrowHover} transition-colors`}
                >
                  <Upload className="h-3 w-3" />
                  <span>Upload template</span>
                  <ArrowRight className="h-3 w-3 ml-auto group-hover:translate-x-0.5 transition-transform" />
                </div>
              </div>
            </button>
          ))}
        </div>

        <FeatureGate feature="journey_incentives">
        {/* OR Divider */}
        <div className="flex items-center gap-4 my-8">
          <div className="flex-1 h-px bg-border" />
          <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
            or
          </span>
          <div className="flex-1 h-px bg-border" />
        </div>

        {/* Journey — full-width premium card */}
        <button
          type="button"
          onClick={() => onSelectType("JOURNEY")}
          className="group relative w-full rounded-2xl border border-dashed border-primary/40 bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-primary hover:shadow-[0_8px_32px_hsl(var(--primary)/0.10),0_2px_8px_hsl(var(--primary)/0.06)]"
        >
          <div className="px-6 py-5">
            <div className="flex items-center gap-3 mb-2">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-primary/10 group-hover:bg-primary/15 transition-colors">
                <Route className="h-5 w-5 text-primary" />
              </div>
              <h3 className="font-semibold text-base text-foreground">
                Journey
              </h3>
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-primary/10 text-xs font-medium text-primary">
                Multi-step
              </span>
              <div className="ml-auto flex items-center gap-1.5">
                <Upload className="h-3.5 w-3.5 text-muted-foreground group-hover:text-primary transition-colors" />
                <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-primary group-hover:translate-x-0.5 transition-[color,transform]" />
              </div>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed pl-[52px]">
              Create a multi-step journey combining different incentive types
              into one program.
            </p>
          </div>
        </button>
        </FeatureGate>
      </div>
    </div>
  );
}
