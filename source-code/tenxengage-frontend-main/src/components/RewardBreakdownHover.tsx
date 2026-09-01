import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { cn } from "@/lib/utils";
import {
  getCurrency,
  currencyIds,
  type CurrencyConfig,
} from "@/config/currencies";
import { formatCurrency } from "@/utils/formatters";
import { SlotDropNumber } from "@/components/SlotDropNumber";

// Re-export for consumers that were importing from here
// eslint-disable-next-line react-refresh/only-export-components
export { getCurrency as getCurrencyConfig, type CurrencyConfig };

// ─── Shared Hover Component ───────────────────────────────────────────────────

interface RewardBreakdownHoverProps {
  /** Section label — e.g. "Rewards", "Budget", "Earnings", "Used" */
  label: string;
  /** Currency entries: key = currency id (e.g. "cash"), value = raw amount (string or number) */
  entries: Record<string, string | number>;
  /** Monetary total — formatted as currency and displayed as the trigger text + in the footer */
  monetaryTotal?: string | number;
  /** Optional suffix after the formatted amount (e.g. "earned", "used", "total") */
  suffix?: string;
  /** HoverCard alignment */
  align?: "start" | "center" | "end";
  /**
   * Forwarded to the internal SlotDropNumber. Lets emphasis callers (e.g. the
   * Total Earnings banner) opt back into the longer reel animation while most
   * consumers stay on the cheap default.
   */
  spins?: number;
  /**
   * When false, render the formatted amount as a plain span instead of the
   * animated SlotDropNumber. Important for high-cardinality usages (tables
   * with dozens of rows, partner group headers) where the per-render cost of
   * tearing down and rebuilding the slot reels on filter / sort / data
   * changes becomes the dominant INP contributor. Defaults to true so
   * existing emphasis callers (Total Earnings banner, etc.) keep animating.
   */
  animate?: boolean;
}

export function RewardBreakdownHover({
  label,
  entries,
  monetaryTotal,
  suffix,
  align = "center",
  spins,
  animate = true,
}: RewardBreakdownHoverProps) {
  const allEntries = Object.entries(entries);
  const sortByDisplayOrder = (
    a: [string, string | number],
    b: [string, string | number],
  ) =>
    currencyIds.indexOf(a[0] as (typeof currencyIds)[number]) -
    currencyIds.indexOf(b[0] as (typeof currencyIds)[number]);
  const monetaryEntries = allEntries
    .filter(([k]) => getCurrency(k).type === "monetary")
    .sort(sortByDisplayOrder);
  const nonMonetaryEntries = allEntries
    .filter(([k]) => getCurrency(k).type === "non_monetary")
    .sort(sortByDisplayOrder);

  const formattedAmount =
    monetaryTotal != null ? formatCurrency(Number(monetaryTotal)) : "$0";
  const triggerContent = (
    <>
      {animate ? (
        <SlotDropNumber value={formattedAmount} spins={spins} />
      ) : (
        <span className="tabular-nums">{formattedAmount}</span>
      )}
      {suffix ? <span>{`\u00A0${suffix}`}</span> : null}
    </>
  );

  if (allEntries.length === 0)
    return <span className="inline-flex items-baseline">{triggerContent}</span>;

  return (
    <HoverCard openDelay={200} closeDelay={100}>
      <HoverCardTrigger asChild>
        <span className="cursor-help border-b border-dashed border-muted-foreground/50 hover:border-muted-foreground transition-colors inline-flex items-baseline">
          {triggerContent}
        </span>
      </HoverCardTrigger>
      <HoverCardContent
        className="w-[280px] p-0 rounded-xl shadow-lg border-border overflow-hidden"
        side="top"
        align={align}
        sideOffset={8}
        collisionPadding={16}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-4 py-2.5 bg-muted/40 border-b border-border">
          <span className="text-xs font-semibold tracking-[0.04em] uppercase text-muted-foreground">
            {label} Breakdown
          </span>
        </div>

        <div className="px-4 py-3 space-y-3">
          {/* Monetary section */}
          {monetaryEntries.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-semibold tracking-[0.06em] uppercase text-muted-foreground">
                Monetary
              </span>
              <div className="space-y-1.5">
                {monetaryEntries.map(([key, value]) => {
                  const config = getCurrency(key);
                  const Icon = config.icon;
                  return (
                    <div
                      key={key}
                      className="flex items-center justify-between"
                    >
                      <div className="flex items-center gap-2">
                        <div
                          className={cn(
                            "flex items-center justify-center w-5 h-5 rounded",
                            key === "cash"
                              ? "bg-emerald-500/8"
                              : "bg-blue-500/8",
                          )}
                        >
                          <Icon className={cn("h-3 w-3", config.iconClass)} />
                        </div>
                        <span className="text-xs text-muted-foreground">
                          {config.label}
                        </span>
                      </div>
                      <span className="text-xs font-semibold text-foreground tabular-nums">
                        {config.format(value)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Non-monetary section */}
          {nonMonetaryEntries.length > 0 && (
            <div className="space-y-2">
              <span className="text-xs font-semibold tracking-[0.06em] uppercase text-muted-foreground">
                Non-Monetary
              </span>
              <div className="space-y-1.5">
                {nonMonetaryEntries.map(([key, value]) => {
                  const config = getCurrency(key);
                  const Icon = config.icon;
                  return (
                    <div
                      key={key}
                      className="flex items-center justify-between"
                    >
                      <div className="flex items-center gap-2">
                        <div
                          className={cn(
                            "flex items-center justify-center w-5 h-5 rounded",
                            key === "credits"
                              ? "bg-violet-500/8"
                              : "bg-orange-500/8",
                          )}
                        >
                          <Icon className={cn("h-3 w-3", config.iconClass)} />
                        </div>
                        <span className="text-xs text-muted-foreground">
                          {config.label}
                        </span>
                      </div>
                      <span className="text-xs font-semibold text-foreground tabular-nums">
                        {config.format(value)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Footer total */}
        {monetaryTotal != null && (
          <div className="px-4 py-2.5 bg-muted/40 border-t border-border flex items-center justify-between">
            <span className="text-xs font-medium text-muted-foreground">
              Monetary Total
            </span>
            <span className="text-sm font-semibold text-foreground tabular-nums">
              {formattedAmount}
            </span>
          </div>
        )}
      </HoverCardContent>
    </HoverCard>
  );
}
