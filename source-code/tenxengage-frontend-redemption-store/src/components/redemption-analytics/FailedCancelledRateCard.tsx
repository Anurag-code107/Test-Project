// Adapted from: src/pages/DashboardPage.tsx (production analog from Mirror)
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getCurrency } from "@/config/currencies";
import type { CurrencyTypeRateDto } from "@/types/redemption-analytics.types";

interface FailedCancelledRateCardProps {
  data: CurrencyTypeRateDto;
}

export function FailedCancelledRateCard({ data }: FailedCancelledRateCardProps) {
  const currency = getCurrency(data.currencyId.toLowerCase());

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">
          {currency.label} Failed &amp; Cancelled Rate
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
              Failed/Cancelled: {data.numerator.toLocaleString()} /{" "}
              Total: {data.denominator.toLocaleString()}
            </p>
          </>
        ) : (
          <div
            role="status"
            className="text-sm text-muted-foreground py-2"
          >
            No redemptions in this period
          </div>
        )}
      </CardContent>
    </Card>
  );
}
