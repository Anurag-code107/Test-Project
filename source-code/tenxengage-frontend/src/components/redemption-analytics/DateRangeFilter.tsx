// Adapted from: none — no production reference for date range filter with preset buttons
import { useState } from "react";
import type { DateRange as DayPickerDateRange } from "react-day-picker";
import { Calendar } from "@/components/ui/calendar";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { CalendarIcon } from "lucide-react";
import type { DateRange } from "@/types/redemption-analytics.types";

const MAX_RANGE_DAYS = 730;
const VALIDATION_MESSAGE = "Date range cannot exceed 24 months";

function formatLocalDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function subDays(d: Date, days: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() - days);
  return result;
}

function subMonths(d: Date, months: number): Date {
  const result = new Date(d);
  result.setMonth(result.getMonth() - months);
  return result;
}

function daysBetween(from: Date, to: Date): number {
  return Math.round((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24));
}

interface DateRangeFilterProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
}

type Preset = "7d" | "30d" | "90d" | "12mo" | "custom";

const PRESETS: { key: Exclude<Preset, "custom">; label: string }[] = [
  { key: "7d", label: "Last 7 days" },
  { key: "30d", label: "Last 30 days" },
  { key: "90d", label: "Last 90 days" },
  { key: "12mo", label: "Last 12 months" },
];

export function DateRangeFilter({ value, onChange }: DateRangeFilterProps) {
  const [activePreset, setActivePreset] = useState<Preset>("30d");
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [calendarRange, setCalendarRange] = useState<DayPickerDateRange | undefined>(undefined);
  const [validationError, setValidationError] = useState<string | null>(null);

  function applyPreset(preset: Exclude<Preset, "custom">) {
    const today = new Date();
    let from: Date;
    switch (preset) {
      case "7d":
        from = subDays(today, 7);
        break;
      case "30d":
        from = subDays(today, 30);
        break;
      case "90d":
        from = subDays(today, 90);
        break;
      case "12mo":
        from = subMonths(today, 12);
        break;
    }
    setActivePreset(preset);
    setValidationError(null);
    onChange({ from: formatLocalDate(from), to: formatLocalDate(today) });
  }

  function handleCalendarSelect(range: DayPickerDateRange | undefined) {
    setCalendarRange(range);
    if (range?.from && range?.to) {
      const days = daysBetween(range.from, range.to);
      if (days > MAX_RANGE_DAYS) {
        setValidationError(VALIDATION_MESSAGE);
        return;
      }
      setValidationError(null);
      setCalendarOpen(false);
      onChange({
        from: formatLocalDate(range.from),
        to: formatLocalDate(range.to),
      });
    } else {
      setValidationError(null);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {PRESETS.map(({ key, label }) => (
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
          {validationError && (
            <p
              role="alert"
              aria-live="polite"
              className="px-3 pb-3 text-xs text-destructive"
            >
              {validationError}
            </p>
          )}
        </PopoverContent>
      </Popover>

      <span
        className="text-xs text-muted-foreground"
        aria-label={`Active date range: ${value.from} to ${value.to}`}
      >
        {value.from} – {value.to}
      </span>
    </div>
  );
}
