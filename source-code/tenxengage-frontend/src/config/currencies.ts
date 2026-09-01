import {
  DollarSign,
  Gift,
  Award,
  Ticket,
  Banknote,
  ArrowRightLeft,
  TicketIcon,
  GraduationCap,
  Coins,
  type LucideIcon,
} from "lucide-react";
import type { RewardCurrencyResponse } from "@/types/reward-currency.types";

/**
 * Central currency configuration for the platform.
 *
 * CURRENCY RULES:
 * - `format` — budget/admin context: cash & points use $ (USD, no decimals); credits & tickets use plain numbers
 * - `rewardFormat` — reward-facing context (balance cards, transaction history):
 *     cash = $, points = "X pts", credits = "X credits", tickets = "X tickets"
 * - Currency IDs in the DB: "cash", "points", "tickets", "credits" (defaults)
 * - Custom currencies are supported via hydration from the API
 */

export interface CurrencyConfig {
  id: string;
  label: string;
  icon: LucideIcon;
  iconClass: string;
  /** Color classes for the amount text (used in cards like My Balances) */
  amountClass: string;
  /** Card border color for balance cards */
  borderClass: string;
  /** Card background color for balance cards */
  bgClass: string;
  /** Icon badge background for balance cards */
  iconBgClass: string;
  /** Action button config for balance cards */
  action: { label: string; icon: LucideIcon };
  type: "monetary" | "non_monetary";
  /** Default format — $ for monetary, plain number for non-monetary */
  format: (value: string | number) => string;
  /** Reward-facing format — used on balance cards & transaction history (e.g. "1,000 pts", "3 tickets") */
  rewardFormat: (value: string | number) => string;
}

const fmtUsd = (value: string | number) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Number(value));

const fmtNum = (value: string | number) =>
  Math.round(Number(value)).toLocaleString();

const fmtUnit = (unit: string) => (value: string | number) =>
  `${Math.round(Number(value)).toLocaleString()} ${unit}`;

// ── Built-in defaults (used before API hydration completes) ──────────────────

const BUILT_IN_DEFAULTS: Record<string, CurrencyConfig> = {
  cash: {
    id: "cash",
    label: "Cash",
    icon: DollarSign,
    iconClass: "text-emerald-600 dark:text-emerald-400",
    amountClass: "text-emerald-600 dark:text-emerald-400",
    borderClass: "border-emerald-200 dark:border-emerald-800",
    bgClass: "bg-emerald-50/50 dark:bg-transparent",
    iconBgClass: "bg-emerald-100 dark:bg-emerald-900/50",
    action: { label: "Manage Payment Details", icon: Banknote },
    type: "monetary",
    format: fmtUsd,
    rewardFormat: fmtUsd,
  },
  points: {
    id: "points",
    label: "Points",
    icon: Gift,
    iconClass: "text-blue-500 dark:text-blue-400",
    amountClass: "text-blue-500 dark:text-blue-400",
    borderClass: "border-blue-200 dark:border-blue-800",
    bgClass: "bg-blue-50/50 dark:bg-transparent",
    iconBgClass: "bg-blue-100 dark:bg-blue-900/50",
    action: { label: "Redeem Points", icon: ArrowRightLeft },
    type: "monetary",
    format: fmtUsd,
    rewardFormat: fmtUnit("pts"),
  },
  credits: {
    id: "credits",
    label: "Credits",
    icon: Award,
    iconClass: "text-violet-500 dark:text-violet-400",
    amountClass: "text-violet-500 dark:text-violet-400",
    borderClass: "border-violet-200 dark:border-violet-800",
    bgClass: "bg-violet-50/50 dark:bg-transparent",
    iconBgClass: "bg-violet-100 dark:bg-violet-900/50",
    action: { label: "Browse Courses", icon: GraduationCap },
    type: "non_monetary",
    format: fmtNum,
    rewardFormat: fmtUnit("credits"),
  },
  tickets: {
    id: "tickets",
    label: "Tickets",
    icon: Ticket,
    iconClass: "text-orange-500 dark:text-orange-400",
    amountClass: "text-orange-500 dark:text-orange-400",
    borderClass: "border-orange-200 dark:border-orange-800",
    bgClass: "bg-orange-50/50 dark:bg-transparent",
    iconBgClass: "bg-orange-100 dark:bg-orange-900/50",
    action: { label: "Enter Raffles", icon: TicketIcon },
    type: "non_monetary",
    format: fmtNum,
    rewardFormat: fmtUnit("tickets"),
  },
};

// ── Color palette for custom currencies ──────────────────────────────────────

const CUSTOM_PALETTES = [
  {
    text: "text-pink-500 dark:text-pink-400",
    border: "border-pink-200 dark:border-pink-800",
    bg: "bg-pink-50/50 dark:bg-transparent",
    iconBg: "bg-pink-100 dark:bg-pink-900/50",
  },
  {
    text: "text-cyan-500 dark:text-cyan-400",
    border: "border-cyan-200 dark:border-cyan-800",
    bg: "bg-cyan-50/50 dark:bg-transparent",
    iconBg: "bg-cyan-100 dark:bg-cyan-900/50",
  },
  {
    text: "text-amber-500 dark:text-amber-400",
    border: "border-amber-200 dark:border-amber-800",
    bg: "bg-amber-50/50 dark:bg-transparent",
    iconBg: "bg-amber-100 dark:bg-amber-900/50",
  },
  {
    text: "text-rose-500 dark:text-rose-400",
    border: "border-rose-200 dark:border-rose-800",
    bg: "bg-rose-50/50 dark:bg-transparent",
    iconBg: "bg-rose-100 dark:bg-rose-900/50",
  },
  {
    text: "text-teal-500 dark:text-teal-400",
    border: "border-teal-200 dark:border-teal-800",
    bg: "bg-teal-50/50 dark:bg-transparent",
    iconBg: "bg-teal-100 dark:bg-teal-900/50",
  },
  {
    text: "text-indigo-500 dark:text-indigo-400",
    border: "border-indigo-200 dark:border-indigo-800",
    bg: "bg-indigo-50/50 dark:bg-transparent",
    iconBg: "bg-indigo-100 dark:bg-indigo-900/50",
  },
];

// ── Mutable internal state ───────────────────────────────────────────────────

let _currencies: Record<string, CurrencyConfig> = { ...BUILT_IN_DEFAULTS };

// ES module live bindings: reassigning these in hydrateCurrencies() updates all importers
export let currencyIds: string[] = ["cash", "points", "credits", "tickets"];
export let monetaryCurrencyIds: string[] = ["cash", "points"];
export let nonMonetaryCurrencyIds: string[] = ["credits", "tickets"];

/**
 * Hydrate currency config from API response. Called after fetching currencies
 * from the backend. Merges API data into internal state, updates all derived arrays.
 */
export function hydrateCurrencies(
  apiCurrencies: RewardCurrencyResponse[],
): void {
  const updated: Record<string, CurrencyConfig> = {};
  let customIndex = 0;

  for (const ac of apiCurrencies) {
    const builtin = BUILT_IN_DEFAULTS[ac.code];
    const type = ac.type === "MONETARY" ? "monetary" : "non_monetary";

    if (builtin) {
      // Known currency: use built-in icon/color, override label/type/conversionRate from API
      updated[ac.code] = {
        ...builtin,
        label: ac.name,
        type,
        format: type === "monetary" ? fmtUsd : fmtNum,
        rewardFormat:
          type === "monetary"
            ? ac.isCurrencyFormatted
              ? fmtUsd
              : fmtUnit(ac.unit || ac.code)
            : fmtUnit(ac.unit || ac.code),
      };
    } else {
      // Custom currency: assign from palette
      const palette = CUSTOM_PALETTES[customIndex % CUSTOM_PALETTES.length]!;
      customIndex++;
      updated[ac.code] = {
        id: ac.code,
        label: ac.name,
        icon: type === "monetary" ? Coins : Award,
        iconClass: palette.text,
        amountClass: palette.text,
        borderClass: palette.border,
        bgClass: palette.bg,
        iconBgClass: palette.iconBg,
        action: { label: "View", icon: type === "monetary" ? Coins : Award },
        type,
        format: type === "monetary" ? fmtUsd : fmtNum,
        rewardFormat:
          type === "monetary"
            ? ac.isCurrencyFormatted
              ? fmtUsd
              : fmtUnit(ac.unit || ac.code)
            : fmtUnit(ac.unit || ac.code),
      };
    }
  }

  _currencies = updated;
  // Reassigning exported `let` bindings updates all ES module importers
  currencyIds = apiCurrencies.map((c) => c.code);
  monetaryCurrencyIds = apiCurrencies
    .filter((c) => c.type === "MONETARY")
    .map((c) => c.code);
  nonMonetaryCurrencyIds = apiCurrencies
    .filter((c) => c.type === "NON_MONETARY")
    .map((c) => c.code);
}

// ── Public API ───────────────────────────────────────────────────────────────

/** Get the full currencies record (snapshot of current state) */
export function getAllCurrencies(): Record<string, CurrencyConfig> {
  return _currencies;
}

/** Get config for a currency ID, with sensible fallback for unknown IDs */
export function getCurrency(key: string): CurrencyConfig {
  return (
    _currencies[key] ?? {
      id: key,
      label: key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()),
      icon: DollarSign,
      iconClass: "text-muted-foreground",
      amountClass: "text-foreground",
      borderClass: "border-border",
      bgClass: "bg-muted/30",
      iconBgClass: "bg-muted",
      action: { label: "View", icon: DollarSign },
      type: "monetary",
      format: fmtUsd,
      rewardFormat: fmtUsd,
    }
  );
}

/**
 * The `currencies` record — Proxy-backed for backward compatibility.
 * Consumers importing `currencies` directly will always get live data.
 */
export const currencies: Record<string, CurrencyConfig> = new Proxy(
  {} as Record<string, CurrencyConfig>,
  {
    get(_target, prop: string) {
      return _currencies[prop];
    },
    ownKeys() {
      return Object.keys(_currencies);
    },
    getOwnPropertyDescriptor(_target, prop: string) {
      if (prop in _currencies) {
        return {
          configurable: true,
          enumerable: true,
          value: _currencies[prop],
        };
      }
      return undefined;
    },
    has(_target, prop: string) {
      return prop in _currencies;
    },
  },
);
