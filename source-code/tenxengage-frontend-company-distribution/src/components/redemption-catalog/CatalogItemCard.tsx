import { Clock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { getCurrency } from "@/config/currencies";
import { ShortfallBadge } from "./ShortfallBadge";
import { CatalogCardIllustration } from "./CatalogCardIllustration";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

interface Props {
  item: CatalogBrowseItemResponse;
  onClick?: () => void;
  /**
   * Renders the card as inactive: dimmed + desaturated, not clickable, with `disabledReason` as a
   * tooltip. Used when the item can't be redeemed at all yet (e.g. the payout profile isn't set up),
   * as opposed to `!item.canAfford`, which is a per-item balance shortfall shown as a badge.
   */
  disabled?: boolean;
  disabledReason?: string;
}

export function CatalogItemCard({ item, onClick, disabled = false, disabledReason }: Props) {
  const formatted = getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount);

  const card = (
    <div
      className={
        disabled
          ? "flex overflow-hidden rounded-lg border bg-muted/40 opacity-60 grayscale cursor-not-allowed select-none"
          : "flex overflow-hidden rounded-lg border bg-card cursor-pointer hover:shadow-md transition-shadow"
      }
      // No handler at all when disabled — the detail drawer offers nothing actionable in this state.
      onClick={disabled ? undefined : onClick}
      aria-disabled={disabled || undefined}
      data-disabled={disabled || undefined}
      data-testid={`catalog-item-card-${item.id}`}
    >
      {/* Left: uploaded image, else the SKU's brand image, else the illustration — fixed width */}
      <div className="w-32 flex-shrink-0 overflow-hidden">
        <CatalogCardIllustration
          category={item.category}
          imageUrl={item.imageUrl}
          catalogItemId={item.id}
          providerImageUrl={item.providerImageUrl}
        />
      </div>

      {/* Right: item data */}
      <div className="flex-1 p-3 space-y-1.5 min-w-0">
        <p
          className="font-semibold text-sm leading-tight truncate"
          data-testid={`item-name-${item.id}`}
        >
          {item.name}
        </p>

        <div className="flex items-center gap-1.5">
          <Badge
            variant={item.category === "CASH" ? "default" : "secondary"}
            className="text-[10px] px-1.5 py-0 h-4 shrink-0"
          >
            {item.category === "CASH" ? "Cash" : "Non-Cash"}
          </Badge>
        </div>

        {/* Amount: FIXED shows the exact denomination; VARIABLE shows the min–max range;
            legacy items (no value type) fall back to "Starting at" the minimum. */}
        <p className="text-lg font-semibold text-foreground" data-testid={`item-amount-${item.id}`}>
          {item.valueType === "FIXED"
            ? formatted
            : item.valueType === "VARIABLE" && item.effectiveMaxTransactionAmount != null
              ? `${formatted}–${getCurrency(item.currencyId).rewardFormat(item.effectiveMaxTransactionAmount)}`
              : `Starting at ${formatted}`}
        </p>

        <div
          className="flex items-center gap-1 text-xs text-muted-foreground"
          data-testid={`payout-timeline-${item.id}`}
        >
          <Clock className="w-3 h-3 flex-shrink-0" />
          <span className="truncate">{item.estimatedPayoutTimeline}</span>
        </div>

        {!item.canAfford && (
          <ShortfallBadge
            shortfallAmount={item.shortfallAmount}
            currencyId={item.currencyId}
          />
        )}
      </div>
    </div>
  );

  // Hover reason only when there is one to give; the banner above the grid carries the CTA.
  if (!disabled || !disabledReason) return card;

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>{card}</TooltipTrigger>
        <TooltipContent>{disabledReason}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
