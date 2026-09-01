// Adapted from: src/pages/DashboardPage.tsx (production analog from Mirror)
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import type { CurrencyTypeBalanceDto } from "@/types/redemption-analytics.types";

interface UnredeemedBalanceCardProps {
  data: CurrencyTypeBalanceDto;
}

export function UnredeemedBalanceCard({ data }: UnredeemedBalanceCardProps) {
  const currency = getCurrency(data.currencyId.toLowerCase());

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">
          {currency.label} Outstanding Liability
        </CardTitle>
        <div className={currency.iconClass}>
          <currency.icon className="h-4 w-4" aria-hidden />
        </div>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-semibold">
          {currency.rewardFormat(data.totalOutstanding)}
        </div>
        <p className="text-xs text-muted-foreground mt-1">
          Available: {currency.rewardFormat(data.availableBalance)}
          {" · "}
          Reserved: {currency.rewardFormat(data.reservedBalance)}
        </p>
      </CardContent>
    </Card>
  );
}
