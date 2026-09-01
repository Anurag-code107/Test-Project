// Adapted from: none — no production reference
import { useState } from "react";
import { CalendarIcon, X } from "lucide-react";
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
import type { RedemptionHistoryFilters, RedemptionStatus, RedemptionCategory } from "@/types/redemption-history/redemption-history.types";

interface HistoryFilterBarProps {
  filters: RedemptionHistoryFilters;
  onFiltersChange: (filters: RedemptionHistoryFilters) => void;
}

const STATUS_OPTIONS: { value: RedemptionStatus; label: string }[] = [
  { value: "PENDING_APPROVAL", label: "Pending" },
  { value: "RESERVED", label: "Reserved" },
  { value: "PROCESSING", label: "Processing" },
  { value: "COMPLETED", label: "Completed" },
  { value: "FAILED", label: "Failed" },
  { value: "CANCELLED", label: "Cancelled" },
];

const CATEGORY_OPTIONS: { value: RedemptionCategory; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "NON_CASH", label: "Non-cash" },
];

function toIsoDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatDateLabel(iso?: string): string {
  if (!iso) return "";
  const [y, m, d] = iso.split("-");
  return new Date(Number(y), Number(m) - 1, Number(d)).toLocaleDateString(undefined, {
    year: "numeric", month: "short", day: "numeric",
  });
}

export function HistoryFilterBar({ filters, onFiltersChange }: HistoryFilterBarProps) {
  const [fromOpen, setFromOpen] = useState(false);
  const [toOpen, setToOpen] = useState(false);

  const dateRangeError =
    filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo
      ? "Start date must be before end date"
      : null;

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {/* Date from */}
        <Popover open={fromOpen} onOpenChange={setFromOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              className={cn(
                "h-9 px-3 gap-2 font-normal",
                filters.dateFrom ? "text-foreground" : "text-muted-foreground",
                dateRangeError && "border-destructive",
              )}
              aria-label="Date from filter"
            >
              <CalendarIcon className="h-3.5 w-3.5 shrink-0" />
              <span>{filters.dateFrom ? formatDateLabel(filters.dateFrom) : "Select date range"}</span>
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto p-0 rounded-xl" align="start">
            <Calendar
              mode="single"
              selected={filters.dateFrom ? new Date(filters.dateFrom + "T12:00:00") : undefined}
              onSelect={(d) => {
                onFiltersChange({ ...filters, dateFrom: d ? toIsoDate(d) : undefined });
                setFromOpen(false);
              }}
              initialFocus
            />
            {filters.dateFrom && (
              <div className="px-3 pb-3">
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-full h-7 text-xs text-muted-foreground hover:text-foreground"
                  onClick={() => { onFiltersChange({ ...filters, dateFrom: undefined }); setFromOpen(false); }}
                >
                  Clear
                </Button>
              </div>
            )}
          </PopoverContent>
        </Popover>

        {/* Date to */}
        {filters.dateFrom && (
          <Popover open={toOpen} onOpenChange={setToOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                className={cn(
                  "h-9 px-3 gap-2 font-normal",
                  filters.dateTo ? "text-foreground" : "text-muted-foreground",
                  dateRangeError && "border-destructive",
                )}
                aria-label="Date to filter"
              >
                <CalendarIcon className="h-3.5 w-3.5 shrink-0" />
                <span>{filters.dateTo ? formatDateLabel(filters.dateTo) : "End date"}</span>
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0 rounded-xl" align="start">
              <Calendar
                mode="single"
                selected={filters.dateTo ? new Date(filters.dateTo + "T12:00:00") : undefined}
                onSelect={(d) => {
                  onFiltersChange({ ...filters, dateTo: d ? toIsoDate(d) : undefined });
                  setToOpen(false);
                }}
                initialFocus
              />
              {filters.dateTo && (
                <div className="px-3 pb-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="w-full h-7 text-xs text-muted-foreground hover:text-foreground"
                    onClick={() => { onFiltersChange({ ...filters, dateTo: undefined }); setToOpen(false); }}
                  >
                    Clear
                  </Button>
                </div>
              )}
            </PopoverContent>
          </Popover>
        )}

        {/* Status */}
        <Select
          value={filters.status ?? ""}
          onValueChange={(v) =>
            onFiltersChange({ ...filters, status: v === "__all__" ? undefined : v as RedemptionStatus })
          }
        >
          <SelectTrigger
            className="h-9 w-[160px] rounded-lg text-sm"
            aria-label="Status filter"
          >
            <SelectValue placeholder="All statuses" />
          </SelectTrigger>
          <SelectContent className="rounded-xl">
            <SelectItem value="__all__">All statuses</SelectItem>
            {STATUS_OPTIONS.map((s) => (
              <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Category */}
        <Select
          value={filters.category ?? ""}
          onValueChange={(v) =>
            onFiltersChange({ ...filters, category: v === "__all__" ? undefined : v as RedemptionCategory })
          }
        >
          <SelectTrigger
            className="h-9 w-[140px] rounded-lg text-sm"
            aria-label="Category filter"
          >
            <SelectValue placeholder="All types" />
          </SelectTrigger>
          <SelectContent className="rounded-xl">
            <SelectItem value="__all__">All types</SelectItem>
            {CATEGORY_OPTIONS.map((c) => (
              <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Clear all */}
        {(filters.dateFrom || filters.dateTo || filters.status || filters.category) && (
          <Button
            variant="ghost"
            size="sm"
            className="h-9 px-2 text-muted-foreground hover:text-foreground gap-1"
            onClick={() => onFiltersChange({})}
          >
            <X className="h-3.5 w-3.5" />
            Clear
          </Button>
        )}
      </div>

      {/* Inline date validation error */}
      {dateRangeError && (
        <p className="text-xs text-destructive" role="alert">
          {dateRangeError}
        </p>
      )}
    </div>
  );
}
