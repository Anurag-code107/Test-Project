import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Plus, X, CheckCircle2 } from "lucide-react";
import type {
  PayoutConfig,
  PayoutType,
  PayoutAgainst,
  PayoutBand,
} from "@/types/incentive.types";
import { getCurrency, getAllCurrencies } from "@/config/currencies";

const blockInvalidChars = (e: React.KeyboardEvent) => {
  if (
    e.key === "." ||
    e.key === "," ||
    e.key === "-" ||
    e.key === "e" ||
    e.key === "E"
  )
    e.preventDefault();
};

const blockNegative = (e: React.KeyboardEvent) => {
  if (e.key === "-" || e.key === "e" || e.key === "E") e.preventDefault();
};

let _uid = 0;
const uid = () => `band-${Date.now()}-${++_uid}`;

const makeBand = (): PayoutBand => ({
  id: uid(),
  minAmount: "",
  maxAmount: "",
  payoutValue: "",
});

function getCurrencyColors(id: string): { iconBg: string; text: string } {
  const cfg = getCurrency(id);
  return {
    iconBg: cfg.iconBgClass.split(" ")[0] || "bg-emerald-100",
    text: cfg.iconClass.split(" ")[0] || "text-emerald-600",
  };
}

interface PayoutCriteriaSectionProps {
  payouts: PayoutConfig[];
  activeCurrencies: string[];
  onChange: (payouts: PayoutConfig[]) => void;
}

export function PayoutCriteriaSection({
  payouts,
  activeCurrencies,
  onChange,
}: PayoutCriteriaSectionProps) {
  const [activeTab, setActiveTab] = useState(activeCurrencies[0] || "");

  // Auto-create default PayoutConfigs for any active currency missing one
  // (e.g. user added a new reward currency in the Budget step during edit)
  useEffect(() => {
    const missing = activeCurrencies.filter(
      (cid) => !payouts.some((p) => p.currencyId === cid),
    );
    if (missing.length > 0) {
      const newPayouts: PayoutConfig[] = missing.map((cid) => ({
        currencyId: cid,
        payoutType: "" as PayoutType,
        bands: [makeBand()],
      }));
      onChange([...payouts, ...newPayouts]);
    }
  }, [activeCurrencies]); // eslint-disable-line react-hooks/exhaustive-deps

  if (activeCurrencies.length === 0) {
    return (
      <p className="text-xs text-muted-foreground italic py-2">
        Select reward currencies in the Budget step to configure payouts.
      </p>
    );
  }

  const currentCurrencyId = activeCurrencies.includes(activeTab)
    ? activeTab
    : activeCurrencies[0]!;
  const allCurrencies = getAllCurrencies();
  const currentCurrencyCfg = getCurrency(currentCurrencyId);
  const currentCurrency = allCurrencies[currentCurrencyId]
    ? {
        id: currentCurrencyId,
        label: currentCurrencyCfg.label,
        type:
          currentCurrencyCfg.type === "monetary"
            ? ("MONETARY" as const)
            : ("NON_MONETARY" as const),
      }
    : null;
  const currentPayout = payouts.find((p) => p.currencyId === currentCurrencyId);

  function updatePayout(
    currencyId: string,
    updater: (p: PayoutConfig) => PayoutConfig,
  ) {
    onChange(
      payouts.map((p) => (p.currencyId === currencyId ? updater(p) : p)),
    );
  }

  return (
    <div className="space-y-3">
      {/* Currency switcher tabs */}
      {activeCurrencies.length > 1 && (
        <div className="flex items-center gap-1.5 flex-wrap">
          {activeCurrencies.map((currId) => {
            const currCfg = getCurrency(currId);
            if (!currCfg) return null;
            const cfg = getCurrency(currId);
            const Icon = cfg.icon;
            const colorsObj = getCurrencyColors(currId);
            const isActive = currId === currentCurrencyId;
            const isNonMonetary = cfg.type === "non_monetary";
            const payout = payouts.find((p) => p.currencyId === currId);
            const isConfigured =
              isNonMonetary ||
              (payout &&
                payout.payoutType &&
                payout.bands.length > 0 &&
                payout.bands.every(
                  (b) => !!b.minAmount && !!b.maxAmount && !!b.payoutValue,
                ));

            return (
              <button
                key={currId}
                type="button"
                onClick={() => setActiveTab(currId)}
                className={`
                  flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-[background-color,border-color,color,box-shadow] border
                  ${
                    isActive
                      ? "bg-background border-emerald-300 dark:border-emerald-700 shadow-sm text-foreground"
                      : "bg-transparent border-transparent text-muted-foreground hover:bg-background/50 hover:text-foreground"
                  }
                `}
              >
                <div
                  className={`flex h-5 w-5 items-center justify-center rounded-full ${colorsObj.iconBg}`}
                >
                  <Icon className={`h-3 w-3 ${colorsObj.text}`} />
                </div>
                {currCfg.label}
                {isConfigured && (
                  <span className="flex h-2 w-2 rounded-full bg-emerald-500" />
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* Active currency payout editor — key forces remount so Select resets per currency */}
      {currentCurrency &&
        currentPayout &&
        (() => {
          const cfg = getCurrency(currentCurrencyId);
          if (cfg.type === "non_monetary") {
            const CurrIcon = cfg.icon;
            return (
              <div className="rounded-lg border border-border bg-muted/30 p-5 flex flex-col items-center gap-3 text-center">
                <div
                  className={`flex h-10 w-10 items-center justify-center rounded-full ${getCurrencyColors(currentCurrencyId).iconBg}`}
                >
                  <CurrIcon
                    className={`h-5 w-5 ${getCurrencyColors(currentCurrencyId).text}`}
                  />
                </div>
                <div>
                  <p className="text-sm font-medium text-foreground">
                    {cfg.label} — No Payout Configuration Needed
                  </p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {cfg.label} is a non-monetary reward and does not require
                    budget-based payout tiers.
                  </p>
                </div>
                <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
                  <CheckCircle2 className="h-4 w-4" />
                  <span className="text-xs font-medium">Auto-completed</span>
                </div>
              </div>
            );
          }
          return (
            <PayoutEditor
              key={currentCurrencyId}
              currency={currentCurrency}
              currencyId={currentCurrencyId}
              payout={currentPayout}
              onUpdate={(updater) => updatePayout(currentCurrencyId, updater)}
            />
          );
        })()}
    </div>
  );
}

function PayoutEditor({
  currency,
  currencyId,
  payout,
  onUpdate,
}: {
  currency: { id: string; label: string; type: "MONETARY" | "NON_MONETARY" };
  currencyId: string;
  payout: PayoutConfig;
  onUpdate: (updater: (p: PayoutConfig) => PayoutConfig) => void;
}) {
  const Icon = getCurrency(currencyId).icon;
  const colorsObj = getCurrencyColors(currencyId);
  const isMoney = currency.type === "MONETARY";

  return (
    <div className="rounded-lg border border-emerald-100 dark:border-emerald-900/30 bg-background p-3 space-y-3 shadow-sm">
      {/* Currency header */}
      <div className="flex items-center gap-2">
        <div
          className={`flex h-6 w-6 items-center justify-center rounded-full ${colorsObj.iconBg}`}
        >
          <Icon className={`h-3.5 w-3.5 ${colorsObj.text}`} />
        </div>
        <span className="text-sm font-medium text-foreground">
          {currency.label}
        </span>
      </div>

      {/* Payout sentence builder */}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="text-muted-foreground">I want to pay</span>
        <Select
          value={payout.payoutType || undefined}
          onValueChange={(v) =>
            onUpdate((p) => ({ ...p, payoutType: v as PayoutType }))
          }
          required
        >
          <SelectTrigger className="w-[150px] h-8 text-sm">
            <SelectValue placeholder="choose type..." />
          </SelectTrigger>
          <SelectContent>
            {isMoney && (
              <SelectItem value="PERCENTAGE">a percentage</SelectItem>
            )}
            <SelectItem value="FLAT">a flat amount</SelectItem>
          </SelectContent>
        </Select>

        {payout.payoutType === "PERCENTAGE" && (
          <>
            <span className="text-muted-foreground">of the</span>
            <Select
              value={payout.against || undefined}
              onValueChange={(v) =>
                onUpdate((p) => ({ ...p, against: v as PayoutAgainst }))
              }
              required
            >
              <SelectTrigger className="w-[220px] h-8 text-sm">
                <SelectValue placeholder="booking basis..." />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="TOTAL_BOOKING">
                  total booking amount
                </SelectItem>
                <SelectItem value="ELIGIBLE_PRODUCTS">
                  eligible products booking
                </SelectItem>
              </SelectContent>
            </Select>
          </>
        )}

        {payout.payoutType === "FLAT" && (
          <span className="text-muted-foreground">per eligible deal</span>
        )}
      </div>

      {/* Payout bands */}
      {payout.payoutType && (
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground">
            {payout.payoutType === "PERCENTAGE"
              ? "Payout Tiers"
              : "Payout Bands"}
          </Label>
          {payout.bands.map((band, bIdx) => (
            <div
              key={band.id ?? bIdx}
              className="flex items-center gap-2 text-sm bg-muted/30 rounded-md border border-border p-2"
            >
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                If booking is
              </span>
              <div className="relative">
                <span className="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                  $
                </span>
                <Input
                  type="number"
                  min="0"
                  step="1"
                  required
                  className="h-7 w-[100px] pl-5 text-xs"
                  placeholder="min"
                  value={band.minAmount}
                  onKeyDown={blockInvalidChars}
                  onChange={(e) =>
                    onUpdate((p) => ({
                      ...p,
                      bands: p.bands.map((b) =>
                        b.id === band.id
                          ? { ...b, minAmount: e.target.value }
                          : b,
                      ),
                    }))
                  }
                />
              </div>
              <span className="text-muted-foreground text-xs">to</span>
              <div className="relative">
                <span className="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                  $
                </span>
                <Input
                  type="number"
                  min="0"
                  step="1"
                  required
                  className="h-7 w-[100px] pl-5 text-xs"
                  placeholder="max (or &infin;)"
                  value={band.maxAmount}
                  onKeyDown={blockInvalidChars}
                  onChange={(e) =>
                    onUpdate((p) => ({
                      ...p,
                      bands: p.bands.map((b) =>
                        b.id === band.id
                          ? { ...b, maxAmount: e.target.value }
                          : b,
                      ),
                    }))
                  }
                />
              </div>
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                then award
              </span>
              <div className="relative">
                {payout.payoutType === "FLAT" && isMoney && (
                  <span className="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                    $
                  </span>
                )}
                <Input
                  type="number"
                  min="0"
                  required
                  step={
                    payout.payoutType === "PERCENTAGE" && isMoney ? "any" : "1"
                  }
                  className={`h-7 w-[90px] text-xs ${payout.payoutType === "FLAT" && isMoney ? "pl-5" : "pl-2"} ${!isMoney ? "pr-14" : ""}`}
                  placeholder="0"
                  value={band.payoutValue}
                  onKeyDown={
                    !(payout.payoutType === "PERCENTAGE" && isMoney)
                      ? blockInvalidChars
                      : blockNegative
                  }
                  onBlur={
                    payout.payoutType === "PERCENTAGE" && isMoney
                      ? (e) => {
                          const v = e.target.value;
                          if (v && v.includes(".")) {
                            const cleaned = parseFloat(v).toString();
                            if (cleaned !== v) {
                              onUpdate((p) => ({
                                ...p,
                                bands: p.bands.map((b) =>
                                  b.id === band.id
                                    ? { ...b, payoutValue: cleaned }
                                    : b,
                                ),
                              }));
                            }
                          }
                        }
                      : undefined
                  }
                  onChange={(e) =>
                    onUpdate((p) => ({
                      ...p,
                      bands: p.bands.map((b) =>
                        b.id === band.id
                          ? { ...b, payoutValue: e.target.value }
                          : b,
                      ),
                    }))
                  }
                />
                {payout.payoutType === "PERCENTAGE" && isMoney && (
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                    %
                  </span>
                )}
                {!isMoney && (
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                    {getCurrency(currencyId).label.toLowerCase()}
                  </span>
                )}
              </div>
              {payout.bands.length > 1 && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 shrink-0 text-muted-foreground hover:text-destructive"
                  onClick={() =>
                    onUpdate((p) => ({
                      ...p,
                      bands: p.bands.filter((b) => b.id !== band.id),
                    }))
                  }
                >
                  <X className="h-3 w-3" />
                </Button>
              )}
            </div>
          ))}
          <Button
            variant="ghost"
            size="sm"
            className="text-xs h-7 text-emerald-600 dark:text-emerald-400"
            onClick={() =>
              onUpdate((p) => ({ ...p, bands: [...p.bands, makeBand()] }))
            }
          >
            <Plus className="h-3 w-3 mr-1" />
            Add Tier
          </Button>
        </div>
      )}

      {/* Max per deal */}
      {payout.payoutType && (
        <div className="flex items-center gap-2 text-sm pt-1 border-t border-border">
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            Max payout per deal (optional)
          </span>
          <div className="relative">
            {isMoney && (
              <span className="absolute left-2 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                $
              </span>
            )}
            <Input
              type="number"
              min="0"
              step="1"
              className={`h-7 w-[120px] text-xs ${isMoney ? "pl-5" : "pl-2"}`}
              placeholder="no limit"
              value={payout.maxPerDeal ?? ""}
              onKeyDown={blockInvalidChars}
              onChange={(e) =>
                onUpdate((p) => ({
                  ...p,
                  maxPerDeal: e.target.value || undefined,
                }))
              }
            />
          </div>
        </div>
      )}
    </div>
  );
}
