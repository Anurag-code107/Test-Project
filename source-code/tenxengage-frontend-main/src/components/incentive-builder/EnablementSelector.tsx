import {
  GraduationCap,
  ClipboardList,
  ArrowLeft,
  ArrowRight,
} from "lucide-react";
import type { IncentiveType } from "@/types/incentive.types";

interface EnablementSelectorProps {
  onSelect: (type: IncentiveType) => void;
  onBack: () => void;
}

/* ── SVG mini-illustrations ───────────────────────────────────────────────── */

function TrainingIllustration() {
  return (
    <svg viewBox="0 0 200 100" fill="none" className="w-full h-full">
      {/* Open book */}
      <path
        d="M70 65 C85 55, 95 55, 100 58"
        stroke="hsl(38 80% 50% / 0.20)"
        strokeWidth="1"
        fill="hsl(38 80% 50% / 0.04)"
      />
      <path
        d="M130 65 C115 55, 105 55, 100 58"
        stroke="hsl(38 80% 50% / 0.20)"
        strokeWidth="1"
        fill="hsl(38 80% 50% / 0.04)"
      />
      <line
        x1="100"
        y1="58"
        x2="100"
        y2="78"
        stroke="hsl(38 80% 50% / 0.14)"
        strokeWidth="0.8"
      />
      {/* Graduation cap floating above */}
      <path
        d="M85 35 l15 -8 15 8 -15 8Z"
        fill="hsl(38 80% 50% / 0.08)"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="0.8"
      />
      <line
        x1="100"
        y1="35"
        x2="100"
        y2="48"
        stroke="hsl(38 80% 50% / 0.14)"
        strokeWidth="0.8"
      />
      <path
        d="M90 39 v8 c0 4 5 6 10 6 s10 -2 10 -6 v-8"
        stroke="hsl(38 80% 50% / 0.12)"
        strokeWidth="0.7"
        fill="none"
      />
      {/* Certificate */}
      <rect
        x="140"
        y="40"
        width="28"
        height="22"
        rx="3"
        stroke="hsl(38 80% 50% / 0.14)"
        strokeWidth="0.8"
      />
      <line
        x1="146"
        y1="48"
        x2="162"
        y2="48"
        stroke="hsl(38 80% 50% / 0.10)"
        strokeWidth="0.6"
      />
      <line
        x1="146"
        y1="53"
        x2="158"
        y2="53"
        stroke="hsl(38 80% 50% / 0.08)"
        strokeWidth="0.6"
      />
      <circle
        cx="154"
        cy="58"
        r="2"
        stroke="hsl(38 80% 50% / 0.12)"
        strokeWidth="0.6"
      />
      {/* Dots */}
      <circle cx="50" cy="40" r="2" fill="hsl(38 80% 50% / 0.14)" />
      <circle cx="170" cy="30" r="1.5" fill="hsl(38 80% 50% / 0.10)" />
      <circle cx="65" cy="80" r="1.5" fill="hsl(38 80% 50% / 0.10)" />
    </svg>
  );
}

function ActivityIllustration() {
  return (
    <svg viewBox="0 0 200 100" fill="none" className="w-full h-full">
      {/* Clipboard */}
      <rect
        x="75"
        y="28"
        width="36"
        height="48"
        rx="4"
        stroke="hsl(200 80% 50% / 0.20)"
        strokeWidth="1"
      />
      <rect
        x="85"
        y="24"
        width="16"
        height="8"
        rx="2.5"
        stroke="hsl(200 80% 50% / 0.16)"
        strokeWidth="0.8"
      />
      {/* Checklist items */}
      <rect
        x="83"
        y="40"
        width="6"
        height="6"
        rx="1"
        stroke="hsl(200 80% 50% / 0.18)"
        strokeWidth="0.7"
      />
      <path
        d="M84.5 43 l1.5 1.5 3 -3"
        stroke="hsl(160 60% 40% / 0.30)"
        strokeWidth="0.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <line
        x1="93"
        y1="43"
        x2="104"
        y2="43"
        stroke="hsl(200 80% 50% / 0.12)"
        strokeWidth="0.7"
      />
      <rect
        x="83"
        y="52"
        width="6"
        height="6"
        rx="1"
        stroke="hsl(200 80% 50% / 0.18)"
        strokeWidth="0.7"
      />
      <path
        d="M84.5 55 l1.5 1.5 3 -3"
        stroke="hsl(160 60% 40% / 0.30)"
        strokeWidth="0.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <line
        x1="93"
        y1="55"
        x2="104"
        y2="55"
        stroke="hsl(200 80% 50% / 0.12)"
        strokeWidth="0.7"
      />
      <rect
        x="83"
        y="64"
        width="6"
        height="6"
        rx="1"
        stroke="hsl(200 80% 50% / 0.14)"
        strokeWidth="0.7"
      />
      <line
        x1="93"
        y1="67"
        x2="104"
        y2="67"
        stroke="hsl(200 80% 50% / 0.10)"
        strokeWidth="0.7"
      />
      {/* Camera / proof icon */}
      <rect
        x="130"
        y="50"
        width="24"
        height="18"
        rx="3"
        stroke="hsl(200 80% 50% / 0.14)"
        strokeWidth="0.8"
      />
      <circle
        cx="142"
        cy="59"
        r="5"
        stroke="hsl(200 80% 50% / 0.16)"
        strokeWidth="0.8"
      />
      <circle cx="142" cy="59" r="2" fill="hsl(200 80% 50% / 0.08)" />
      <rect
        x="137"
        y="48"
        width="10"
        height="4"
        rx="1.5"
        stroke="hsl(200 80% 50% / 0.12)"
        strokeWidth="0.6"
      />
      {/* Dots */}
      <circle cx="50" cy="50" r="2" fill="hsl(200 80% 50% / 0.14)" />
      <circle cx="165" cy="38" r="1.5" fill="hsl(200 80% 50% / 0.10)" />
      <circle cx="55" cy="75" r="1.5" fill="hsl(200 80% 50% / 0.10)" />
    </svg>
  );
}

/* ── Component ────────────────────────────────────────────────────────────── */

export function EnablementSelector({
  onSelect,
  onBack,
}: EnablementSelectorProps) {
  return (
    <div className="max-w-3xl mx-auto pt-12">
      {/* Top bar: back */}
      <div className="mb-4">
        <button
          type="button"
          onClick={onBack}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back</span>
        </button>
      </div>

      {/* Heading */}
      <div className="text-center mb-10">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          Choose Enablement Type
        </h1>
        <p className="text-base text-muted-foreground mt-2">
          What kind of enablement incentive would you like to create?
        </p>
      </div>

      {/* Training + Activity showcase cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Training */}
        <button
          type="button"
          onClick={() => onSelect("TRAINING")}
          className="group relative flex flex-col rounded-2xl border border-border bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-[hsl(38_80%_50%/0.30)] hover:shadow-[0_8px_32px_hsl(38_60%_50%/0.10),0_2px_8px_hsl(38_60%_50%/0.06)]"
        >
          {/* Illustration area */}
          <div className="relative h-28 bg-gradient-to-br from-[hsl(38_80%_55%/0.03)] to-[hsl(38_80%_55%/0.08)] overflow-hidden transition-[background,filter] duration-300 group-hover:from-[hsl(38_80%_55%/0.08)] group-hover:to-[hsl(38_80%_55%/0.18)] [&_svg]:transition-all [&_svg]:duration-300 group-hover:[&_svg]:scale-105 group-hover:[&_svg]:[filter:contrast(2.5)_drop-shadow(0_0_3px_hsl(38_80%_50%/0.5))_drop-shadow(0_0_1px_hsl(38_80%_50%/0.4))]">
            <TrainingIllustration />
            <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[hsl(38_80%_50%/0.30)] to-transparent" />
          </div>

          {/* Content */}
          <div className="p-5 flex-1 flex flex-col">
            <div className="flex items-center gap-3 mb-2">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-[hsl(38_80%_55%/0.08)] group-hover:bg-[hsl(38_80%_55%/0.14)] transition-colors">
                <GraduationCap className="h-5 w-5 text-[hsl(38_80%_45%)]" />
              </div>
              <h3 className="font-semibold text-base text-foreground">
                Training Incentive
              </h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              Reward your partners for completing training and learning new
              skills.
            </p>
            <div className="mt-auto flex items-center gap-1.5 text-xs font-medium text-muted-foreground group-hover:text-[hsl(38_80%_45%)] transition-colors">
              <span>Select</span>
              <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
            </div>
          </div>
        </button>

        {/* Activity */}
        <button
          type="button"
          onClick={() => onSelect("ACTIVITY")}
          className="group relative flex flex-col rounded-2xl border border-border bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-[hsl(200_80%_50%/0.30)] hover:shadow-[0_8px_32px_hsl(200_60%_50%/0.10),0_2px_8px_hsl(200_60%_50%/0.06)]"
        >
          {/* Illustration area */}
          <div className="relative h-28 bg-gradient-to-br from-[hsl(200_80%_50%/0.03)] to-[hsl(200_80%_50%/0.08)] overflow-hidden transition-[background,filter] duration-300 group-hover:from-[hsl(200_80%_50%/0.08)] group-hover:to-[hsl(200_80%_50%/0.18)] [&_svg]:transition-all [&_svg]:duration-300 group-hover:[&_svg]:scale-105 group-hover:[&_svg]:[filter:contrast(2.5)_drop-shadow(0_0_3px_hsl(200_80%_50%/0.5))_drop-shadow(0_0_1px_hsl(200_80%_50%/0.4))]">
            <ActivityIllustration />
            <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[hsl(200_80%_50%/0.30)] to-transparent" />
          </div>

          {/* Content */}
          <div className="p-5 flex-1 flex flex-col">
            <div className="flex items-center gap-3 mb-2">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-[hsl(200_80%_50%/0.08)] group-hover:bg-[hsl(200_80%_50%/0.14)] transition-colors">
                <ClipboardList className="h-5 w-5 text-[hsl(200_80%_45%)]" />
              </div>
              <h3 className="font-semibold text-base text-foreground">
                Activity Incentive
              </h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              Require proof of execution for partner activities and reward
              compliance.
            </p>
            <div className="mt-auto flex items-center gap-1.5 text-xs font-medium text-muted-foreground group-hover:text-[hsl(200_80%_45%)] transition-colors">
              <span>Select</span>
              <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
            </div>
          </div>
        </button>
      </div>
    </div>
  );
}
