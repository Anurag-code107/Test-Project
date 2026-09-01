import { useMemo, useRef, useState } from "react";
import { Check, ChevronsUpDown, Gift, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { useGiftCardCatalog } from "@/hooks/useRedemptionCatalog";
import type { GiftCardSkuResponse } from "@/types/redemption-catalog.types";

interface Props {
  /** Currently-selected SKU (the form's providerItemId), or "" when none. */
  value: string;
  onSelect: (sku: GiftCardSkuResponse) => void;
  disabled?: boolean;
  /** Set on the trigger so an external <label htmlFor> associates with the picker. */
  id?: string;
  "aria-invalid"?: boolean;
}

/** `$10` for whole amounts, `$10.50` otherwise. Gift-card SKUs are all USD. */
function usd(amount: number | null | undefined): string {
  if (amount == null) return "—";
  const n = Number(amount);
  return `$${Number.isInteger(n) ? n : n.toFixed(2)}`;
}

/** The compact value descriptor shown on each option and the trigger. */
export function skuValueLabel(sku: GiftCardSkuResponse): string {
  return sku.valueType === "FIXED"
    ? usd(sku.faceValue)
    : `${usd(sku.minValue)}–${usd(sku.maxValue)}`;
}

/**
 * The SKU's brand logo — the same image the catalog card will show once the item is created, so the
 * admin recognizes what they picked. Falls back to a gift glyph when the SKU carries no image or the
 * vendor CDN link is dead. `object-contain` because brand logos are wide and transparent.
 */
function SkuThumbnail({
  sku,
  className = "h-7 w-7",
}: {
  sku: GiftCardSkuResponse;
  className?: string;
}) {
  // Keyed by URL so switching to another SKU on the same trigger gets a fresh attempt.
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);
  const url = sku.brandImageUrl;
  const box = `${className} shrink-0 rounded border bg-muted/40`;

  if (!url || brokenUrl === url) {
    return (
      <div
        className={`${box} flex items-center justify-center`}
        data-testid={`sku-thumb-placeholder-${sku.sku}`}
      >
        <Gift className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />
      </div>
    );
  }

  return (
    <img
      src={url}
      alt=""
      aria-hidden="true"
      loading="lazy"
      className={`${box} object-contain p-0.5`}
      onError={() => setBrokenUrl(url)}
      data-testid={`sku-thumb-${sku.sku}`}
    />
  );
}

export function GiftCardSkuCombobox({ value, onSelect, disabled, id, ...aria }: Props) {
  const [open, setOpen] = useState(false);
  // Defer the network call until the picker is first opened (server caches 6h).
  const [hasOpened, setHasOpened] = useState(false);
  const { data: skus, isLoading, isError, refetch } = useGiftCardCatalog(hasOpened);

  const triggerRef = useRef<HTMLButtonElement>(null);
  // When the picker is inside a modal dialog, portal the list INTO the dialog. The dialog's scroll-lock
  // (react-remove-scroll) only permits wheel scrolling within its own subtree, so a list portaled to
  // <body> (the default) can't be scrolled. Null when not in a dialog → portals to body as usual.
  const [portalContainer, setPortalContainer] = useState<HTMLElement | null>(null);

  const grouped = useMemo(() => {
    const map = new Map<string, GiftCardSkuResponse[]>();
    for (const sku of skus ?? []) {
      const key = sku.brandName || "Other";
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(sku);
    }
    return [...map.entries()];
  }, [skus]);

  const selected = useMemo(
    () => (skus ?? []).find((s) => s.sku === value),
    [skus, value],
  );

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setHasOpened(true);
          setPortalContainer((triggerRef.current?.closest('[role="dialog"]') as HTMLElement) ?? null);
        }
      }}
    >
      <PopoverTrigger asChild>
        <Button
          ref={triggerRef}
          type="button"
          variant="outline"
          role="combobox"
          id={id}
          aria-expanded={open}
          aria-invalid={aria["aria-invalid"]}
          disabled={disabled}
          className="w-full justify-between font-normal"
          data-testid="gift-card-sku-trigger"
        >
          <span className="flex min-w-0 items-center gap-2">
            {/* Selected SKU's brand logo, so the picked card is identifiable at a glance. */}
            {selected && <SkuThumbnail sku={selected} className="h-6 w-6" />}
            <span className={cn("truncate", !value && "text-muted-foreground")}>
              {value
                ? selected
                  ? `${selected.rewardName} · ${skuValueLabel(selected)}`
                  : value
                : "Select a gift-card SKU"}
            </span>
          </span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        container={portalContainer}
        className="w-[--radix-popover-trigger-width] p-0"
        align="start"
      >
        <Command
          filter={(itemValue, search) =>
            itemValue.toLowerCase().includes(search.toLowerCase()) ? 1 : 0
          }
        >
          <CommandInput placeholder="Search brand, name or SKU…" />
          <CommandList>
            {isLoading && (
              <div className="flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading gift cards…
              </div>
            )}
            {isError && (
              <div className="flex flex-col items-center gap-2 py-6 text-sm">
                <p className="text-destructive">Could not load gift-card catalog.</p>
                <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
                  Retry
                </Button>
              </div>
            )}
            {!isLoading && !isError && (
              <>
                <CommandEmpty>No matching gift cards.</CommandEmpty>
                {grouped.map(([brand, items]) => (
                  <CommandGroup key={brand} heading={brand}>
                    {items.map((sku) => (
                      <CommandItem
                        key={sku.sku}
                        value={`${sku.rewardName} ${sku.brandName} ${sku.sku}`}
                        onSelect={() => {
                          onSelect(sku);
                          setOpen(false);
                        }}
                        data-testid={`gift-card-sku-option-${sku.sku}`}
                      >
                        <Check
                          className={cn(
                            "mr-2 h-4 w-4 shrink-0",
                            value === sku.sku ? "opacity-100" : "opacity-0",
                          )}
                        />
                        <SkuThumbnail sku={sku} className="mr-2 h-7 w-7" />
                        <div className="flex min-w-0 flex-1 flex-col">
                          <span className="truncate">{sku.rewardName}</span>
                          <span className="text-xs text-muted-foreground">{sku.sku}</span>
                        </div>
                        <Badge variant="secondary" className="ml-2 shrink-0 text-[10px]">
                          {skuValueLabel(sku)}
                        </Badge>
                      </CommandItem>
                    ))}
                  </CommandGroup>
                ))}
              </>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
