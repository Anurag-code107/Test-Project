import { useState, useRef, useEffect, useMemo } from "react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { useProducts } from "@/hooks/useProductApi";
import { ChevronDown, Search, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

interface ProductMultiSelectProps {
  selected: string[]; // SKU strings
  onSelectionChange: (skus: string[]) => void;
}

export function ProductMultiSelect({
  selected,
  onSelectionChange,
}: ProductMultiSelectProps) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);
  const { data: products, isLoading } = useProducts();

  useEffect(() => {
    if (open) {
      setTimeout(() => searchRef.current?.focus(), 100);
    } else {
      setSearch("");
    }
  }, [open]);

  const lowerSearch = search.toLowerCase();

  // Group products by category
  const groupedProducts = useMemo(() => {
    if (!products) return [];

    const groups: Record<string, typeof products> = {};
    for (const product of products) {
      const cat = product.category || "Other";
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(product);
    }

    return Object.entries(groups)
      .map(([name, items]) => ({
        name,
        products: items.filter(
          (p) =>
            p.name.toLowerCase().includes(lowerSearch) ||
            p.sku.toLowerCase().includes(lowerSearch) ||
            name.toLowerCase().includes(lowerSearch),
        ),
      }))
      .filter((g) => g.products.length > 0);
  }, [products, lowerSearch]);

  const toggleProduct = (sku: string) => {
    if (selected.includes(sku)) {
      onSelectionChange(selected.filter((s) => s !== sku));
    } else {
      onSelectionChange([...selected, sku]);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            "w-full justify-between font-normal min-h-10 h-10",
            selected.length === 0 && "text-muted-foreground",
          )}
        >
          <div className="flex items-center gap-1 flex-1 text-left overflow-hidden">
            {selected.length === 0 ? (
              <span>Select products...</span>
            ) : (
              <span className="text-sm truncate">
                {selected.length} product{selected.length !== 1 ? "s" : ""}{" "}
                selected
              </span>
            )}
          </div>
          <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
      >
        <div className="p-2 border-b">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              ref={searchRef}
              placeholder="Search products..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 h-9"
            />
          </div>
        </div>
        <div className="max-h-64 overflow-y-auto">
          <div className="p-1">
            {isLoading ? (
              <div className="py-6 flex items-center justify-center text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Loading products...
              </div>
            ) : groupedProducts.length === 0 ? (
              <div className="py-6 text-center text-sm text-muted-foreground">
                No products found.
              </div>
            ) : (
              groupedProducts.map((category) => (
                <div key={category.name} className="mb-1">
                  <div className="px-2 py-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {category.name}
                  </div>
                  {category.products.map((product) => (
                    <button
                      key={product.sku}
                      type="button"
                      onClick={() => toggleProduct(product.sku)}
                      className="flex items-center gap-2 w-full rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer"
                    >
                      <Checkbox
                        checked={selected.includes(product.sku)}
                        className="pointer-events-none"
                      />
                      <span>{product.name}</span>
                    </button>
                  ))}
                </div>
              ))
            )}
          </div>
        </div>
        {selected.length > 0 && (
          <div className="border-t p-2 flex justify-between items-center">
            <span className="text-xs text-muted-foreground">
              {selected.length} selected
            </span>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 text-xs"
              onClick={() => onSelectionChange([])}
            >
              Clear all
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
