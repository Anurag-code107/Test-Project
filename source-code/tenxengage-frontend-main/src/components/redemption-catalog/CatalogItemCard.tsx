import { Clock } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import { ShortfallBadge } from "./ShortfallBadge";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

interface Props {
  item: CatalogBrowseItemResponse;
  onClick?: () => void;
}

export function CatalogItemCard({ item, onClick }: Props) {
  const formatted = getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount);

  return (
    <Card
      className="cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
      data-testid={`catalog-item-card-${item.id}`}
    >
      <CardHeader className="pb-2">
        <CardTitle className="text-base" data-testid={`item-name-${item.id}`}>
          {item.name}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-sm text-muted-foreground font-medium">{formatted}</p>
        <div
          className="flex items-center gap-1 text-xs text-muted-foreground"
          data-testid={`payout-timeline-${item.id}`}
        >
          <Clock className="w-3 h-3" />
          {item.estimatedPayoutTimeline}
        </div>
        {!item.canAfford && (
          <ShortfallBadge
            shortfallAmount={item.shortfallAmount}
            currencyId={item.currencyId}
          />
        )}
      </CardContent>
    </Card>
  );
}
