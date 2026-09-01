import {
  Megaphone,
  GraduationCap,
  Route,
  ArrowLeft,
  ArrowRight,
} from "lucide-react";
import type { IncentiveType } from "@/types/incentive.types";
import { FeatureGate } from "@/components/FeatureGate";

interface TypeSelectorProps {
  onSelect: (type: IncentiveType) => void;
  onEnablement: () => void;
  onBack: () => void;
}

/* ── SVG mini-illustrations for each card ─────────────────────────────────── */

function SalesIllustration() {
  return (
    <svg viewBox="0 0 200 100" fill="none" className="w-full h-full">
      {/* Flowing curve */}
      <path
        d="M-10 70 C40 45, 90 80, 140 50 S200 30, 220 55"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
      />
      {/* Rising bar chart */}
      <rect
        x="40"
        y="55"
        width="14"
        height="30"
        rx="3"
        fill="hsl(217 91% 60% / 0.08)"
        stroke="hsl(217 91% 60% / 0.15)"
        strokeWidth="0.8"
      />
      <rect
        x="62"
        y="40"
        width="14"
        height="45"
        rx="3"
        fill="hsl(217 91% 60% / 0.10)"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="0.8"
      />
      <rect
        x="84"
        y="25"
        width="14"
        height="60"
        rx="3"
        fill="hsl(217 91% 60% / 0.12)"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="0.8"
      />
      {/* Megaphone accent */}
      <circle
        cx="145"
        cy="35"
        r="16"
        stroke="hsl(217 91% 60% / 0.14)"
        strokeWidth="0.8"
      />
      <path
        d="M140 30 l12 5 -12 5Z"
        fill="hsl(217 91% 60% / 0.10)"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="0.6"
      />
      {/* Dots */}
      <circle cx="30" cy="40" r="2" fill="hsl(217 91% 60% / 0.15)" />
      <circle cx="170" cy="65" r="1.5" fill="hsl(217 91% 60% / 0.12)" />
      <circle cx="120" cy="18" r="1.5" fill="hsl(217 91% 60% / 0.10)" />
    </svg>
  );
}

function EnablementIllustration() {
  return (
    <svg viewBox="0 0 200 100" fill="none" className="w-full h-full">
      {/* Flowing curve */}
      <path
        d="M-10 60 C50 40, 100 75, 160 45 S210 30, 220 50"
        stroke="hsl(147 50% 42% / 0.16)"
        strokeWidth="1"
      />
      {/* Graduation cap */}
      <path
        d="M80 40 l30 -15 30 15 -30 15Z"
        fill="hsl(147 50% 42% / 0.06)"
        stroke="hsl(147 50% 42% / 0.18)"
        strokeWidth="0.8"
      />
      <line
        x1="110"
        y1="40"
        x2="110"
        y2="62"
        stroke="hsl(147 50% 42% / 0.14)"
        strokeWidth="0.8"
      />
      <path
        d="M90 47 v12 c0 6 10 10 20 10 s20 -4 20 -10 v-12"
        stroke="hsl(147 50% 42% / 0.14)"
        strokeWidth="0.8"
        fill="none"
      />
      {/* Achievement star */}
      <circle
        cx="160"
        cy="60"
        r="10"
        stroke="hsl(147 50% 42% / 0.12)"
        strokeWidth="0.8"
      />
      <path
        d="M160 53 l2 4 4.4 0.6 -3.2 3.1 0.8 4.3 -4 -2.1 -4 2.1 0.8 -4.3 -3.2 -3.1 4.4 -0.6Z"
        fill="hsl(147 50% 42% / 0.08)"
        stroke="hsl(147 50% 42% / 0.14)"
        strokeWidth="0.5"
      />
      {/* Dots */}
      <circle cx="40" cy="50" r="2" fill="hsl(147 50% 42% / 0.14)" />
      <circle cx="55" cy="30" r="1.5" fill="hsl(147 50% 42% / 0.10)" />
      <circle cx="175" cy="35" r="1.5" fill="hsl(147 50% 42% / 0.10)" />
    </svg>
  );
}

function JourneyIllustration() {
  return (
    <svg viewBox="0 0 500 80" fill="none" className="w-full h-full">
      {/* Connected path with stops */}
      <path
        d="M40 40 C100 40, 100 20, 160 20 S220 40, 280 40 S340 20, 400 20 S460 40, 480 40"
        stroke="hsl(260 50% 55% / 0.18)"
        strokeWidth="1.5"
        strokeDasharray="6 4"
      />
      {/* Stop circles */}
      <circle
        cx="40"
        cy="40"
        r="8"
        fill="hsl(217 91% 60% / 0.08)"
        stroke="hsl(217 91% 60% / 0.22)"
        strokeWidth="1"
      />
      <circle cx="40" cy="40" r="3" fill="hsl(217 91% 60% / 0.20)" />
      <circle
        cx="160"
        cy="20"
        r="8"
        fill="hsl(147 50% 42% / 0.08)"
        stroke="hsl(147 50% 42% / 0.22)"
        strokeWidth="1"
      />
      <circle cx="160" cy="20" r="3" fill="hsl(147 50% 42% / 0.20)" />
      <circle
        cx="280"
        cy="40"
        r="8"
        fill="hsl(38 80% 50% / 0.08)"
        stroke="hsl(38 80% 50% / 0.22)"
        strokeWidth="1"
      />
      <circle cx="280" cy="40" r="3" fill="hsl(38 80% 50% / 0.20)" />
      <circle
        cx="400"
        cy="20"
        r="8"
        fill="hsl(263 50% 55% / 0.08)"
        stroke="hsl(263 50% 55% / 0.22)"
        strokeWidth="1"
      />
      <circle cx="400" cy="20" r="3" fill="hsl(263 50% 55% / 0.20)" />
      {/* Finish flag */}
      <line
        x1="470"
        y1="28"
        x2="470"
        y2="55"
        stroke="hsl(260 50% 55% / 0.18)"
        strokeWidth="1"
      />
      <path
        d="M470 28 l18 6 -18 6Z"
        fill="hsl(260 50% 55% / 0.10)"
        stroke="hsl(260 50% 55% / 0.16)"
        strokeWidth="0.6"
      />
      {/* Step labels */}
      <text
        x="40"
        y="62"
        textAnchor="middle"
        fontSize="8"
        fill="hsl(217 91% 60% / 0.35)"
        fontWeight="500"
      >
        Sales
      </text>
      <text
        x="160"
        y="42"
        textAnchor="middle"
        fontSize="8"
        fill="hsl(147 50% 42% / 0.35)"
        fontWeight="500"
      >
        Training
      </text>
      <text
        x="280"
        y="62"
        textAnchor="middle"
        fontSize="8"
        fill="hsl(38 80% 50% / 0.35)"
        fontWeight="500"
      >
        Activity
      </text>
      <text
        x="400"
        y="42"
        textAnchor="middle"
        fontSize="8"
        fill="hsl(263 50% 55% / 0.35)"
        fontWeight="500"
      >
        Reward
      </text>
      {/* Floating dots */}
      <circle cx="100" cy="55" r="1.5" fill="hsl(217 91% 60% / 0.12)" />
      <circle cx="220" cy="15" r="1.5" fill="hsl(147 50% 42% / 0.10)" />
      <circle cx="340" cy="55" r="1.5" fill="hsl(38 80% 50% / 0.10)" />
      <circle cx="450" cy="15" r="1.5" fill="hsl(260 50% 55% / 0.10)" />
    </svg>
  );
}

/* ── Component ────────────────────────────────────────────────────────────── */

export function TypeSelector({
  onSelect,
  onEnablement,
  onBack,
}: TypeSelectorProps) {
  return (
    <div
      className="max-w-3xl mx-auto px-8 py-12"
      data-tour="builder-type-picker"
    >
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

      {/* Heading */}
      <div className="text-center mb-10">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          Choose Incentive Type
        </h1>
        <p className="text-base text-muted-foreground mt-2">
          What type of incentive would you like to create?
        </p>
      </div>

      {/* Sales + Enablement showcase cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
        {/* Sales card */}
        <button
          type="button"
          onClick={() => onSelect("SALES")}
          data-tour="type-sales-incentive"
          className="group relative flex flex-col rounded-2xl border border-border bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-[hsl(217_91%_60%/0.30)] hover:shadow-[0_8px_32px_hsl(217_60%_55%/0.10),0_2px_8px_hsl(217_60%_55%/0.06)]"
        >
          {/* Illustration area */}
          <div className="relative h-28 bg-gradient-to-br from-[hsl(217_91%_60%/0.03)] to-[hsl(217_91%_60%/0.08)] overflow-hidden transition-[filter] duration-300 group-hover:saturate-[3] group-hover:contrast-[1.3] group-hover:brightness-[0.8]">
            <SalesIllustration />
            {/* Colored accent line at bottom */}
            <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[hsl(217_91%_60%/0.30)] to-transparent" />
          </div>

          {/* Content */}
          <div className="p-5 flex-1 flex flex-col">
            <div className="flex items-center gap-3 mb-2">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-[hsl(217_91%_60%/0.08)] group-hover:bg-[hsl(217_91%_60%/0.14)] transition-colors">
                <Megaphone className="h-5 w-5 text-[hsl(217_91%_55%)]" />
              </div>
              <h3 className="font-semibold text-base text-foreground">
                Sales Incentive
              </h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              Give your partners something customized to strive for with
              targeted promotions.
            </p>
            <div className="mt-auto flex items-center gap-1.5 text-xs font-medium text-muted-foreground group-hover:text-[hsl(217_91%_55%)] transition-colors">
              <span>Select</span>
              <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
            </div>
          </div>
        </button>

        {/* Enablement card */}
        <button
          type="button"
          onClick={onEnablement}
          className="group relative flex flex-col rounded-2xl border border-border bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-[hsl(147_50%_42%/0.30)] hover:shadow-[0_8px_32px_hsl(147_40%_40%/0.10),0_2px_8px_hsl(147_40%_40%/0.06)]"
        >
          {/* Illustration area */}
          <div className="relative h-28 bg-gradient-to-br from-[hsl(147_50%_42%/0.03)] to-[hsl(147_50%_42%/0.08)] overflow-hidden transition-[filter] duration-300 group-hover:saturate-[3] group-hover:contrast-[1.3] group-hover:brightness-[0.8]">
            <EnablementIllustration />
            <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-[hsl(147_50%_42%/0.30)] to-transparent" />
          </div>

          {/* Content */}
          <div className="p-5 flex-1 flex flex-col">
            <div className="flex items-center gap-3 mb-2">
              <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-[hsl(147_50%_47%/0.08)] group-hover:bg-[hsl(147_50%_47%/0.14)] transition-colors">
                <GraduationCap className="h-5 w-5 text-[hsl(147_50%_42%)]" />
              </div>
              <h3 className="font-semibold text-base text-foreground">
                Enablement Incentive
              </h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed mb-4">
              Reward partners for training completion, proof of execution, and
              enablement activities.
            </p>
            <div className="mt-auto flex items-center gap-1.5 text-xs font-medium text-muted-foreground group-hover:text-[hsl(147_50%_42%)] transition-colors">
              <span>Choose type</span>
              <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
            </div>
          </div>
        </button>
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
          onClick={() => onSelect("JOURNEY")}
          className="group relative w-full rounded-2xl border border-dashed border-primary/40 bg-background overflow-hidden text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer hover:-translate-y-1 hover:border-primary hover:shadow-[0_8px_32px_hsl(var(--primary)/0.10),0_2px_8px_hsl(var(--primary)/0.06)]"
        >
        {/* Illustration area */}
        <div className="relative h-20 overflow-hidden px-8 pt-4 transition-[filter] duration-300 group-hover:saturate-[3] group-hover:contrast-[1.3] group-hover:brightness-[0.8]">
          <JourneyIllustration />
        </div>

        {/* Content */}
        <div className="px-6 pb-5">
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
            <ArrowRight className="h-4 w-4 ml-auto text-muted-foreground group-hover:text-primary group-hover:translate-x-0.5 transition-[color,transform]" />
          </div>
          <p className="text-sm text-muted-foreground leading-relaxed pl-[52px]">
            Create a multi-step journey combining different incentive types into
            one program.
          </p>
        </div>
        </button>
      </FeatureGate>
    </div>
  );
}
