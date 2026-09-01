// Adapted from: src/pages/DashboardPage.tsx (production analog from Mirror)
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import type { CurrencyTypeRateDto } from "@/types/redemption-analytics.types";

interface RedemptionRateCardProps {
  data: CurrencyTypeRateDto;
}

export function RedemptionRateCard({ data }: RedemptionRateCardProps) {
  const currency = getCurrency(data.currencyId.toLowerCase());

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">
          {currency.label} Redemption Rate
        </CardTitle>
        <div className={currency.iconClass}>
          <currency.icon className="h-4 w-4" aria-hidden />
        </div>
      </CardHeader>
      <CardContent>
        {data.hasActivity ? (
          <>
            <div className="text-2xl font-semibold">
              {data.ratePercentage ?? "0.00"}%
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Redeemed: {currency.rewardFormat(data.numerator)} /{" "}
              Earned: {currency.rewardFormat(data.denominator)}
            </p>
          </>
        ) : (
          <div
            role="status"
            className="text-sm text-muted-foreground py-2"
          >
            No program activity yet
          </div>
        )}
      </CardContent>
    </Card>
  );
}
