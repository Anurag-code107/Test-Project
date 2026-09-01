import { useMemo } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { usePartnerCatalog } from "@/hooks/useRedemptionCatalog";
import { CatalogItemCard } from "./CatalogItemCard";
import type { CatalogBrowseFilters } from "@/types/redemption-catalog.types";

interface Props {
  currencyId?: string;
  region?: string;
  onItemClick?: (itemId: string) => void;
  /**
   * When set, every card renders inactive with this string as its tooltip. The caller owns the
   * policy (e.g. "payout profile not set up") — the grid only presents it.
   */
  disabledReason?: string | null;
}

export function CatalogBrowseGrid({ currencyId, region, onItemClick, disabledReason }: Props) {
  const filters = useMemo<CatalogBrowseFilters>(() => ({ currencyId, region }), [currencyId, region]);
  const { data, isLoading, isError, refetch } = usePartnerCatalog(filters);
  const items = data?.data ?? [];
  const grouped = useMemo(() => {
    const src = data?.data ?? [];
    return src.reduce<Record<string, typeof src>>((acc, item) => {
      (acc[item.currencyId] ??= []).push(item);
      return acc;
    }, {});
  }, [data?.data]);

  if (isLoading) {
    return (
      <div className="space-y-8" data-testid="catalog-browse-grid-skeleton">
        {Array.from({ length: 2 }).map((_, sectionIdx) => (
          <div key={sectionIdx}>
            <Skeleton className="h-4 w-16 mb-3" />
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex overflow-hidden rounded-lg border h-24">
                  <Skeleton className="w-32 flex-shrink-0 rounded-none" />
                  <div className="flex-1 p-3 space-y-2">
                    <Skeleton className="h-4 w-3/4" />
                    <Skeleton className="h-3 w-1/3" />
                    <Skeleton className="h-3 w-1/2" />
                    <Skeleton className="h-3 w-2/3" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-md border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
        Could not load catalog items.
        <Button variant="link" className="ml-2 text-destructive underline p-0 h-auto" onClick={() => void refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <p className="text-sm text-muted-foreground py-12 text-center">
        No rewards available yet. Check back soon.
      </p>
    );
  }

  return (
    <div className="space-y-8" data-testid="catalog-browse-grid">
      {Object.entries(grouped).map(([currency, currencyItems]) => (
        <section key={currency}>
          <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-3">
            {currency}
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {currencyItems.map((item) => (
              <CatalogItemCard
                key={item.id}
                item={item}
                disabled={!!disabledReason}
                disabledReason={disabledReason ?? undefined}
                onClick={() => onItemClick?.(item.id)}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
