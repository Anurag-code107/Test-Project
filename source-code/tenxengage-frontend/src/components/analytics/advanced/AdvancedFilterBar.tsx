// Adapted from: src/components/redemption-analytics/DateRangeFilter.tsx (date picker pattern)
// No mockup — no production analog for this exact filter bar shape.
import { useState } from "react";
import type { DateRange as DayPickerDateRange } from "react-day-picker";
import { Calendar } from "@/components/ui/calendar";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { MultiSelect, type MultiSelectOption } from "@/components/ui/multi-select";
import { CalendarIcon } from "lucide-react";
import type { AdvancedAnalyticsFilters } from "@/types/redemption-analytics-advanced.types";

const MAX_RANGE_DAYS = 365;
const DATE_RANGE_ERROR = "Date range cannot exceed 365 days";

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function subDays(d: Date, days: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() - days);
  return result;
}

function daysBetween(from: Date, to: Date): number {
  return Math.round((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24));
}

export interface AdvancedFilterBarProps {
  onFilterChange: (filters: AdvancedAnalyticsFilters) => void;
  isSegmentDataEmpty: boolean;
  /** Current filter state — exposed for controlled usage in tests and parent */
  currentFilters: AdvancedAnalyticsFilters;
  /** Distinct region values from the segment-breakdown response (FR-08.6 multi-select options) */
  regionOptions?: string[];
  /** Distinct role values from the segment-breakdown response (FR-08.6 multi-select options) */
  roleOptions?: string[];
}

type DatePreset = "7d" | "30d" | "90d" | "custom";

const DATE_PRESETS: { key: Exclude<DatePreset, "custom">; label: string; days: number }[] = [
  { key: "7d", label: "Last 7 days", days: 7 },
  { key: "30d", label: "Last 30 days", days: 30 },
  { key: "90d", label: "Last 90 days", days: 90 },
];

export function AdvancedFilterBar({
  onFilterChange,
  isSegmentDataEmpty,
  currentFilters,
  regionOptions = [],
  roleOptions = [],
}: AdvancedFilterBarProps) {
  const [activePreset, setActivePreset] = useState<DatePreset>("30d");
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [calendarRange, setCalendarRange] = useState<DayPickerDateRange | undefined>(undefined);
  const [dateError, setDateError] = useState<string | null>(null);
  const [pendingFrom, setPendingFrom] = useState<string>(currentFilters.dateFrom);
  const [pendingTo, setPendingTo] = useState<string>(currentFilters.dateTo);

  function applyPreset(preset: Exclude<DatePreset, "custom">) {
    const days = DATE_PRESETS.find((p) => p.key === preset)!.days;
    const today = new Date();
    const from = subDays(today, days);
    setActivePreset(preset);
    setDateError(null);
    const dateFrom = formatLocalDate(from);
    const dateTo = formatLocalDate(today);
    setPendingFrom(dateFrom);
    setPendingTo(dateTo);
    onFilterChange({ ...currentFilters, dateFrom, dateTo });
  }

  function handleCalendarSelect(range: DayPickerDateRange | undefined) {
    setCalendarRange(range);
    if (range?.from && range?.to) {
      const days = daysBetween(range.from, range.to);
      if (days > MAX_RANGE_DAYS) {
        setDateError(DATE_RANGE_ERROR);
        setPendingFrom(formatLocalDate(range.from));
        setPendingTo(formatLocalDate(range.to));
        return;
      }
      setDateError(null);
      setPendingFrom(formatLocalDate(range.from));
      setPendingTo(formatLocalDate(range.to));
    } else if (range?.from) {
      setDateError(null);
      setPendingFrom(formatLocalDate(range.from));
      setPendingTo(formatLocalDate(range.from));
    } else {
      setDateError(null);
    }
  }

  function handleApply() {
    if (dateError) return;
    setCalendarOpen(false);
    setActivePreset("custom");
    onFilterChange({ ...currentFilters, dateFrom: pendingFrom, dateTo: pendingTo });
  }

  const applyDisabled = !!dateError || !calendarRange?.from || !calendarRange?.to;

  const selectedRegions = currentFilters.region
    ? currentFilters.region.split(",").map((s) => s.trim()).filter(Boolean)
    : [];
  const selectedRoles = currentFilters.role
    ? currentFilters.role.split(",").map((s) => s.trim()).filter(Boolean)
    : [];

  const regionItems: MultiSelectOption[] = regionOptions.map((r) => ({ value: r, label: r }));
  const roleItems: MultiSelectOption[] = roleOptions.map((r) => ({ value: r, label: r }));

  function handleRegionChange(values: string[]) {
    onFilterChange({
      ...currentFilters,
      region: values.length ? values.join(",") : undefined,
    });
  }

  function handleRoleChange(values: string[]) {
    onFilterChange({
      ...currentFilters,
      role: values.length ? values.join(",") : undefined,
    });
  }

  return (
    <div className="flex flex-wrap items-start gap-3">
      {/* Date range presets */}
      <div className="flex flex-wrap items-center gap-2">
        {DATE_PRESETS.map(({ key, label }) => (
          <Button
            key={key}
            variant={activePreset === key ? "default" : "outline"}
            size="sm"
            onClick={() => applyPreset(key)}
            aria-pressed={activePreset === key}
          >
            {label}
          </Button>
        ))}

        {/* Custom date range picker */}
        <Popover open={calendarOpen} onOpenChange={setCalendarOpen}>
          <PopoverTrigger asChild>
            <Button
              variant={activePreset === "custom" ? "default" : "outline"}
              size="sm"
              aria-pressed={activePreset === "custom"}
            >
              <CalendarIcon className="mr-2 h-4 w-4" aria-hidden />
              Custom range
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto p-0" align="start">
            <Calendar
              mode="range"
              selected={calendarRange}
              onSelect={handleCalendarSelect}
              numberOfMonths={2}
              initialFocus
            />
            {dateError && (
              <p
                role="alert"
                aria-live="polite"
                className="px-3 pb-2 text-xs text-destructive"
              >
                {dateError}
              </p>
            )}
            <div className="flex justify-end px-3 pb-3 pt-1">
              <Button
                size="sm"
                aria-label="Apply custom date range"
                onClick={handleApply}
                disabled={applyDisabled}
              >
                Apply
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {/* Region filter — multi-select; options from the segment-breakdown response (FR-08.6).
          Disabled until segment data is loaded (AC-4). */}
      <MultiSelect
        ariaLabel="Region filter"
        disabled={isSegmentDataEmpty}
        options={regionItems}
        selected={selectedRegions}
        onChange={handleRegionChange}
        placeholder={isSegmentDataEmpty ? "No data available" : "All regions"}
        className="w-[160px]"
      />

      {/* Role filter — multi-select; options from the segment-breakdown response (FR-08.6).
          Disabled until segment data is loaded (AC-4). */}
      <MultiSelect
        ariaLabel="Role filter"
        disabled={isSegmentDataEmpty}
        options={roleItems}
        selected={selectedRoles}
        onChange={handleRoleChange}
        placeholder={isSegmentDataEmpty ? "No data available" : "All roles"}
        className="w-[160px]"
      />
    </div>
  );
}
