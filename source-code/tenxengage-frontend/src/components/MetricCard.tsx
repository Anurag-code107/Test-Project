import React from "react";
import { TrendingUp, TrendingDown, ArrowUpRight } from "lucide-react";
import { cn } from "@/lib/utils";

export type MetricCardAccent = "blue" | "purple" | "teal" | "amber" | "green";

const accentConfig: Record<
  MetricCardAccent,
  {
    borderColor: string;
    washGradient: string;
    hoverShadow: string;
    iconBg: string;
    iconColor: string;
  }
> = {
  blue: {
    borderColor: "hsl(217 91% 60%)",
    washGradient:
      "linear-gradient(to right, hsl(217 91% 60% / 0.07) 0%, hsl(217 91% 60% / 0.035) 35%, transparent 80%)",
    hoverShadow: "hover:shadow-[0_4px_16px_hsl(217_50%_60%/0.08)]",
    iconBg: "bg-[hsl(217_91%_60%/0.08)]",
    iconColor: "text-[hsl(217_91%_55%)]",
  },
  purple: {
    borderColor: "hsl(260 60% 58%)",
    washGradient:
      "linear-gradient(to right, hsl(260 60% 58% / 0.07) 0%, hsl(260 60% 58% / 0.035) 35%, transparent 80%)",
    hoverShadow: "hover:shadow-[0_4px_16px_hsl(260_50%_50%/0.08)]",
    iconBg: "bg-[hsl(260_60%_55%/0.08)]",
    iconColor: "text-[hsl(260_60%_48%)]",
  },
  teal: {
    borderColor: "hsl(175 60% 42%)",
    washGradient:
      "linear-gradient(to right, hsl(175 60% 42% / 0.07) 0%, hsl(175 60% 42% / 0.035) 35%, transparent 80%)",
    hoverShadow: "hover:shadow-[0_4px_16px_hsl(175_50%_40%/0.08)]",
    iconBg: "bg-[hsl(175_60%_40%/0.08)]",
    iconColor: "text-[hsl(175_60%_35%)]",
  },
  amber: {
    borderColor: "hsl(30 80% 52%)",
    washGradient:
      "linear-gradient(to right, hsl(30 80% 52% / 0.07) 0%, hsl(30 80% 52% / 0.035) 35%, transparent 80%)",
    hoverShadow: "hover:shadow-[0_4px_16px_hsl(30_60%_50%/0.08)]",
    iconBg: "bg-[hsl(30_70%_50%/0.08)]",
    iconColor: "text-[hsl(30_70%_40%)]",
  },
  green: {
    borderColor: "hsl(152 56% 39%)",
    washGradient:
      "linear-gradient(to right, hsl(152 56% 39% / 0.07) 0%, hsl(152 56% 39% / 0.035) 35%, transparent 80%)",
    hoverShadow: "hover:shadow-[0_4px_16px_hsl(152_40%_35%/0.08)]",
    iconBg: "bg-[hsl(152_50%_40%/0.08)]",
    iconColor: "text-[hsl(152_50%_35%)]",
  },
};

interface MetricCardProps {
  title: string;
  value: string | number;
  subValue?: string;
  icon: React.ElementType;
  change?: number;
  changeLabel?: string;
  onClick?: () => void;
  extraContent?: React.ReactNode;
  showTrend?: boolean;
  accent?: MetricCardAccent;
}

export function MetricCard({
  title,
  value,
  subValue,
  icon: Icon,
  change,
  changeLabel = "vs last quarter",
  onClick,
  extraContent,
  showTrend = true,
  accent = "blue",
}: MetricCardProps) {
  const isPositive = (change ?? 0) >= 0;
  const isClickable = showTrend && onClick;
  const a = accentConfig[accent];

  return (
    <div
      className={cn(
        "relative flex flex-col rounded-2xl border border-[hsl(210_20%_90%)] bg-white p-5 transition-[border-color,box-shadow] duration-300 overflow-hidden",
        isClickable && cn("cursor-pointer group", a.hoverShadow),
      )}
      onClick={isClickable ? onClick : undefined}
    >
      {/* Left accent bar — fades out on hover */}
      <div
        className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl transition-opacity duration-300 group-hover:opacity-0"
        style={{ backgroundColor: a.borderColor }}
      />

      {/* Color wash overlay — fades in from left on hover */}
      <div
        className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
        style={{ background: a.washGradient }}
      />

      {/* Content — above overlays */}
      <div className="relative z-10 flex flex-col flex-1">
        {/* Header row */}
        <div className="flex items-center gap-2 mb-3">
          <div
            className={cn(
              "flex items-center justify-center w-7 h-7 rounded-lg",
              a.iconBg,
            )}
          >
            <Icon className={cn("h-3.5 w-3.5", a.iconColor)} />
          </div>
          <p className="text-sm font-medium text-muted-foreground">{title}</p>
        </div>

        {/* Value */}
        <div className="flex items-baseline gap-2">
          <p className="text-[clamp(1.5rem,2.5vw,1.875rem)] font-semibold tracking-[-0.02em] text-foreground tabular-nums">
            {value}
          </p>
          {subValue && (
            <p className="text-sm text-muted-foreground">{subValue}</p>
          )}
        </div>

        {/* Trend */}
        {change !== undefined && (
          <div className="mt-2">
            <span
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1",
                isPositive
                  ? "bg-[hsl(152_56%_39%/0.08)]"
                  : "bg-[hsl(0_65%_50%/0.08)]",
              )}
            >
              {isPositive ? (
                <TrendingUp className="h-3 w-3 text-[hsl(152_56%_39%)]" />
              ) : (
                <TrendingDown className="h-3 w-3 text-[hsl(0_65%_50%)]" />
              )}
              <span
                className={cn(
                  "text-xs font-semibold tabular-nums",
                  isPositive
                    ? "text-[hsl(152_56%_39%)]"
                    : "text-[hsl(0_65%_50%)]",
                )}
              >
                {isPositive ? "+" : ""}
                {change}%
              </span>
              <span className="text-xs text-muted-foreground">
                {changeLabel}
              </span>
            </span>
          </div>
        )}

        {extraContent}

        {/* Spacer */}
        <div className="flex-1" />

        {/* View Trend footer */}
        {showTrend && (
          <div className="mt-4 pt-3 border-t border-[hsl(210_20%_94%)] flex items-center text-xs font-medium text-primary">
            <span>View Trend</span>
            <ArrowUpRight className="h-3 w-3 ml-auto opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-[opacity,transform] duration-200" />
          </div>
        )}
      </div>
    </div>
  );
}
