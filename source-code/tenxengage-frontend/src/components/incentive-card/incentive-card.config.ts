/**
 * Shared configuration for all incentive card components.
 * Centralises icon maps, colour tokens, accent styling, and helpers
 * that were previously duplicated across PartnerIncentiveCard,
 * ManagedIncentiveCard, and JourneyIncentiveCard.
 */

import { Megaphone, GraduationCap, FileCheck, Layers } from "lucide-react";
import type { IncentiveType } from "@/types/incentive.types";

/* ── Type → icon mapping ─────────────────────────────────────────────── */

export const engagementIconMap: Record<IncentiveType, typeof Megaphone> = {
  SALES: Megaphone,
  TRAINING: GraduationCap,
  ACTIVITY: FileCheck,
  JOURNEY: Layers,
};

/* ── Type → Tailwind text colour class ───────────────────────────────── */

export const engagementColors: Record<IncentiveType, string> = {
  SALES: "text-primary",
  TRAINING: "text-warning",
  ACTIVITY: "text-blue-500",
  JOURNEY: "text-indigo-500",
};

/* ── Stage pill colours (used by journey stage buttons in both cards) ── */

export const stageTypeColors: Record<string, string> = {
  SALES: "bg-primary/20 text-primary border-primary/30",
  TRAINING: "bg-warning/20 text-warning border-warning/30",
  ACTIVITY: "bg-blue-500/20 text-blue-500 border-blue-500/30",
  JOURNEY: "bg-indigo-500/20 text-indigo-500 border-indigo-500/20",
};

/* ── Option B: Header band + hover colour flood per incentive type ──── */

export interface CardAccent {
  /** Header band background gradient (subtle, 135deg diagonal) */
  bandGradient: string;
  /** Header band bottom-border colour (fades to transparent on hover) */
  bandBorder: string;
  /** Top-to-bottom wash that floods down on hover */
  hoverWash: string;
  /** Card border colour on hover */
  hoverBorder: string;
  /** Card box-shadow on hover */
  hoverShadow: string;
}

export const cardAccent: Record<IncentiveType, CardAccent> = {
  SALES: {
    bandGradient:
      "linear-gradient(135deg, hsl(217 91% 60% / 0.10) 0%, hsl(217 91% 60% / 0.04) 100%)",
    bandBorder: "hsl(217 91% 60% / 0.10)",
    hoverWash:
      "linear-gradient(180deg, hsl(217 91% 60% / 0.09) 0%, hsl(217 91% 60% / 0.04) 70%, transparent 100%)",
    hoverBorder: "hover:border-[hsl(217_91%_60%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(217_60%_55%/0.10)]",
  },
  TRAINING: {
    bandGradient:
      "linear-gradient(135deg, hsl(38 92% 50% / 0.10) 0%, hsl(38 92% 50% / 0.04) 100%)",
    bandBorder: "hsl(38 92% 50% / 0.10)",
    hoverWash:
      "linear-gradient(180deg, hsl(38 92% 50% / 0.09) 0%, hsl(38 92% 50% / 0.04) 70%, transparent 100%)",
    hoverBorder: "hover:border-[hsl(38_80%_50%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(38_60%_50%/0.10)]",
  },
  ACTIVITY: {
    bandGradient:
      "linear-gradient(135deg, hsl(200 80% 50% / 0.10) 0%, hsl(200 80% 50% / 0.04) 100%)",
    bandBorder: "hsl(200 80% 50% / 0.10)",
    hoverWash:
      "linear-gradient(180deg, hsl(200 80% 50% / 0.09) 0%, hsl(200 80% 50% / 0.04) 70%, transparent 100%)",
    hoverBorder: "hover:border-[hsl(200_80%_50%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(200_60%_50%/0.10)]",
  },
  JOURNEY: {
    bandGradient:
      "linear-gradient(135deg, hsl(245 58% 58% / 0.10) 0%, hsl(245 58% 58% / 0.04) 100%)",
    bandBorder: "hsl(245 58% 58% / 0.10)",
    hoverWash:
      "linear-gradient(180deg, hsl(245 58% 58% / 0.09) 0%, hsl(245 58% 58% / 0.04) 70%, transparent 100%)",
    hoverBorder: "hover:border-[hsl(245_58%_58%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(245_45%_50%/0.10)]",
  },
};

/** Convenience lookup — same data as cardAccent but keyed by string for stage sub-cards. */
export function getAccentForType(type?: string): CardAccent {
  return (
    (type ? cardAccent[type as IncentiveType] : undefined) ?? cardAccent.JOURNEY
  );
}

/* ── Shared helpers ──────────────────────────────────────────────────── */

/** Format a Date for card display (e.g. "Nov 30, 2026"). */
export function formatIncentiveDate(date: Date): string {
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/** Strip the "Earn up to" / "Earn" prefix from a reward message. */
export function parseRewardMessage(message: string | null | undefined): string {
  return (message ?? "").replace(/^earn\s+(?:up\s+to\s+)?/i, "").trim();
}
