import { useMemo } from "react";
import { Link } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ArrowUpRight, Gift } from "lucide-react";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import { useRewardBalances } from "@/hooks/useClaimApi";
import { useRewardCurrencies } from "@/hooks/useRewardCurrencyApi";

export function RewardsBalancesWidget() {
  const { data: tenantCurrencies } = useRewardCurrencies();
  const { data: balances } = useRewardBalances();

  const balanceByCurrencyId = useMemo(() => {
    const map = new Map<string, string>();
    (balances ?? []).forEach((b) => map.set(b.currencyId, b.balance));
    return map;
  }, [balances]);

  const entries = (tenantCurrencies ?? []).map((currency) => ({
    id: currency.code,
    label: currency.name,
    balance: balanceByCurrencyId.get(currency.code) ?? "0",
    config: getCurrency(currency.code),
  }));

  return (
    <Link
      to="/rewards?tab=rewards"
      className="group block h-full w-full"
      aria-label="Go to Manage Rewards"
    >
      <Card className="h-full flex flex-col transition-[box-shadow,border-color,transform] duration-300 group-hover:shadow-[0_4px_16px_hsl(210_20%_50%/0.08)] group-hover:-translate-y-0.5 group-hover:border-primary/40">
        <CardHeader className="pb-3 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-[hsl(217_91%_60%/0.08)]">
                <Gift className="h-3.5 w-3.5 text-[hsl(217_91%_55%)]" />
              </div>
              <CardTitle className="text-base">Rewards Balance</CardTitle>
            </div>
            <span className="flex items-center gap-1 text-xs font-medium text-primary opacity-0 -translate-x-1 transition-[opacity,transform] duration-200 group-hover:opacity-100 group-hover:translate-x-0">
              View details
              <ArrowUpRight className="h-3 w-3" />
            </span>
          </div>
        </CardHeader>
        <CardContent className="flex-1">
          {entries.length === 0 ? (
            <div className="py-4 text-center text-sm text-muted-foreground">
              No currencies configured for this tenant. Ask your administrator.
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {entries.map((entry) => {
                const Icon = entry.config.icon;
                return (
                  <div
                    key={entry.id}
                    className={cn(
                      "flex items-center justify-between gap-3 rounded-lg border p-3",
                      entry.config.borderClass,
                      entry.config.bgClass,
                    )}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div
                        className={cn(
                          "flex items-center justify-center w-7 h-7 rounded-md shrink-0",
                          entry.config.iconBgClass,
                        )}
                      >
                        <Icon
                          className={cn("h-3.5 w-3.5", entry.config.iconClass)}
                        />
                      </div>
                      <p className="text-sm font-medium text-foreground truncate">
                        {entry.label}
                      </p>
                    </div>
                    <p
                      className={cn(
                        "text-base font-semibold tabular-nums shrink-0",
                        entry.config.amountClass,
                      )}
                    >
                      {entry.config.rewardFormat(entry.balance)}
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}
