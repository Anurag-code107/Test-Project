// Adapted from: src/pages/client-admin/ManageIncentivesPage.tsx (production analog from Mirror)
import { useState } from "react";
import { CalendarIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Calendar } from "@/components/ui/calendar";
import { cn } from "@/lib/utils";
import { useTenantCatalogConfig } from "@/hooks/useRedemptionCatalog";
import type { ApprovalQueueFilters } from "@/types/redemption/redemption.types";

interface ApprovalQueueFiltersProps {
  filters: ApprovalQueueFilters;
  onChange: (filters: ApprovalQueueFilters) => void;
}

const CURRENCY_OPTIONS = [
  { value: "cash", label: "Cash" },
  { value: "points", label: "Points" },
  { value: "credits", label: "Credits" },
  { value: "tickets", label: "Tickets" },
];

function formatDateLabel(iso?: string): string {
  if (!iso) return "Pick a date";
  return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function toIsoDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function ApprovalQueueFilters({ filters, onChange }: ApprovalQueueFiltersProps) {
  const [fromOpen, setFromOpen] = useState(false);
  const [toOpen, setToOpen] = useState(false);

  const { data: catalogData } = useTenantCatalogConfig({ enabled: true, pageSize: 50 });
  const catalogItems = catalogData?.data ?? [];

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* Date from */}
      <Popover open={fromOpen} onOpenChange={setFromOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className={cn(
              "h-9 px-3 gap-2 font-normal",
              filters.startDate ? "text-foreground" : "text-muted-foreground",
            )}
            aria-label="Date from filter"
          >
            <CalendarIcon className="h-3.5 w-3.5 shrink-0" />
            <span>{filters.startDate ? formatDateLabel(filters.startDate) : "Date from"}</span>
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0 rounded-xl" align="start">
          <Calendar
            mode="single"
            selected={filters.startDate ? new Date(filters.startDate) : undefined}
            onSelect={(d) => {
              onChange({ ...filters, startDate: d ? toIsoDate(d) : undefined });
              setFromOpen(false);
            }}
            initialFocus
          />
          {filters.startDate && (
            <div className="px-3 pb-3">
              <Button
                variant="ghost"
                size="sm"
                className="w-full h-7 text-xs text-muted-foreground hover:text-foreground"
                onClick={() => {
                  onChange({ ...filters, startDate: undefined });
                  setFromOpen(false);
                }}
              >
                Clear
              </Button>
            </div>
          )}
        </PopoverContent>
      </Popover>

      {/* Date to */}
      <Popover open={toOpen} onOpenChange={setToOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className={cn(
              "h-9 px-3 gap-2 font-normal",
              filters.endDate ? "text-foreground" : "text-muted-foreground",
            )}
            aria-label="Date to filter"
          >
            <CalendarIcon className="h-3.5 w-3.5 shrink-0" />
            <span>{filters.endDate ? formatDateLabel(filters.endDate) : "Date to"}</span>
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0 rounded-xl" align="start">
          <Calendar
            mode="single"
            selected={filters.endDate ? new Date(filters.endDate) : undefined}
            onSelect={(d) => {
              onChange({ ...filters, endDate: d ? toIsoDate(d) : undefined });
              setToOpen(false);
            }}
            initialFocus
          />
          {filters.endDate && (
            <div className="px-3 pb-3">
              <Button
                variant="ghost"
                size="sm"
                className="w-full h-7 text-xs text-muted-foreground hover:text-foreground"
                onClick={() => {
                  onChange({ ...filters, endDate: undefined });
                  setToOpen(false);
                }}
              >
                Clear
              </Button>
            </div>
          )}
        </PopoverContent>
      </Popover>

      {/* Currency */}
      <Select
        value={filters.currencyId ?? ""}
        onValueChange={(v) =>
          onChange({ ...filters, currencyId: v === "__all__" ? undefined : v })
        }
      >
        <SelectTrigger
          className="h-9 w-[140px] rounded-lg border border-border text-sm"
          aria-label="Currency filter"
        >
          <SelectValue placeholder="Currency" />
        </SelectTrigger>
        <SelectContent className="rounded-xl">
          <SelectItem value="__all__">All currencies</SelectItem>
          {CURRENCY_OPTIONS.map((c) => (
            <SelectItem key={c.value} value={c.value}>
              {c.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Catalog item */}
      <Select
        value={filters.catalogItemId ?? ""}
        onValueChange={(v) =>
          onChange({ ...filters, catalogItemId: v === "__all__" ? undefined : v })
        }
      >
        <SelectTrigger
          className="h-9 w-[180px] rounded-lg border border-border text-sm"
          aria-label="Catalog item filter"
        >
          <SelectValue placeholder="Catalog item" />
        </SelectTrigger>
        <SelectContent className="rounded-xl">
          <SelectItem value="__all__">All items</SelectItem>
          {catalogItems.map((item) => (
            <SelectItem key={item.id} value={item.id}>
              {item.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
