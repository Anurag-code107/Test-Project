import { useMemo, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import type { DistributionCatalogItem } from "@/types/company-distribution.types";

/**
 * How many cards to render before asking the admin to search.
 *
 * The provider catalogue runs to a few hundred SKUs, and a wall of them is slower to scan than a search
 * box — the admin almost always arrives knowing the brand they want.
 */
const VISIBLE_LIMIT = 12;

interface Props {
  items: DistributionCatalogItem[];
  isLoading: boolean;
  selectedId: string | null;
  onSelect: (id: string) => void;
}

/**
 * Gift cards available to distribute.
 *
 * <p>The provider's whole catalogue, which runs to a few hundred SKUs — so it is capped at
 * {@code VISIBLE_LIMIT} with a search box rather than rendered in full. An admin arriving to send
 * "Amazon" finds it faster by typing than by scrolling.</p>
 */
export function GiftCardPicker({
  items,
  isLoading,
  selectedId,
  onSelect,
}: Props) {
  const [query, setQuery] = useState("");

  const { visible, matchCount } = useMemo(() => {
    const q = query.trim().toLowerCase();
    const matches = q
      ? items.filter(
          (i) =>
            i.name.toLowerCase().includes(q) ||
            (i.description ?? "").toLowerCase().includes(q),
        )
      : items;

    const shown = matches.slice(0, VISIBLE_LIMIT);
    // Keep the chosen card on screen even when it falls outside the cap or the current search, so the
    // selection never silently disappears from under the admin while they look for something else.
    const selectedItem = items.find((i) => i.id === selectedId);
    if (selectedItem && !shown.some((i) => i.id === selectedItem.id)) {
      shown.unshift(selectedItem);
    }
    return { visible: shown, matchCount: matches.length };
  }, [items, query, selectedId]);

  if (isLoading) {
    return (
      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="catalog-loading"
      >
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-28 w-full" />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <Card data-testid="catalog-empty">
        <CardContent className="pt-6 text-sm text-muted-foreground">
          No gift cards are available to distribute right now — the provider
          catalogue came back empty.
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <Input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search gift cards…"
          aria-label="Search gift cards"
          className="max-w-xs"
          data-testid="gift-card-search"
        />
        <p
          className="text-xs text-muted-foreground"
          data-testid="gift-card-count"
        >
          {matchCount === 0
            ? `No cards match “${query.trim()}”`
            : matchCount > visible.length
              ? `Showing ${visible.length} of ${matchCount} — search to narrow it down`
              : `${matchCount} card${matchCount === 1 ? "" : "s"}`}
        </p>
      </div>

      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        role="radiogroup"
        aria-label="Gift card"
        data-testid="gift-card-picker"
      >
        {visible.map((item) => {
          const selected = item.id === selectedId;
          return (
            <button
              key={item.id}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => onSelect(item.id)}
              data-testid={`gift-card-${item.id}`}
              className={`text-left rounded-lg border p-4 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                selected
                  ? "border-primary ring-2 ring-primary/30"
                  : "hover:border-muted-foreground/40"
              }`}
            >
              <div className="flex items-start gap-3">
                {/* Uploaded image wins, then the vendor brand image; neither is guaranteed. */}
                {(item.imageUrl || item.providerImageUrl) && (
                  <img
                    src={item.imageUrl ?? item.providerImageUrl ?? ""}
                    alt=""
                    className="h-10 w-10 rounded object-contain shrink-0"
                    onError={(e) => {
                      (e.currentTarget as HTMLImageElement).style.display =
                        "none";
                    }}
                  />
                )}
                <div className="min-w-0">
                  <p className="font-medium truncate">{item.name}</p>
                  <p className="text-xs text-muted-foreground mt-0.5 tabular-nums">
                    {item.valueType === "FIXED"
                      ? `${item.minAmount} ${item.currencyId}`
                      : `${item.minAmount}${item.maxAmount ? `–${item.maxAmount}` : "+"} ${item.currencyId}`}
                  </p>
                </div>
                {item.valueType === "FIXED" && (
                  <Badge variant="secondary" className="ml-auto shrink-0">
                    Fixed
                  </Badge>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
