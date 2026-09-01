import { TrendingDown } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { getCurrency } from "@/config/currencies";

interface Props {
  shortfallAmount: string;
  currencyId: string;
}

export function ShortfallBadge({ shortfallAmount, currencyId }: Props) {
  const formatted = getCurrency(currencyId).rewardFormat(shortfallAmount);
  return (
    <Badge
      variant="outline"
      className="border-destructive/40 text-destructive gap-1"
      data-testid="shortfall-badge"
    >
      <TrendingDown className="w-3 h-3" />
      {formatted} short
    </Badge>
  );
}
