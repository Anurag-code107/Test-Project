import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { DateFilter } from "@/types/filters";

interface DateFilterControlProps {
  dateFilter: DateFilter;
  customStartDate?: Date;
  customEndDate?: Date;
  onDateFilterChange: (filter: DateFilter) => void;
  onStartDateChange: (date?: Date) => void;
  onEndDateChange: (date?: Date) => void;
}

const dateFilterLabels: Record<DateFilter, string> = {
  recent: "Recent",
  quarter: "Quarter",
  year: "Year",
  custom: "Custom",
};

export function DateFilterControl({
  dateFilter,
  customStartDate,
  customEndDate,
  onDateFilterChange,
  onStartDateChange,
  onEndDateChange,
}: DateFilterControlProps) {
  return (
    <div className="flex items-center gap-2">
      <Select
        value={dateFilter}
        onValueChange={(v) => {
          const filter = v as DateFilter;
          onDateFilterChange(filter);
          if (filter !== "custom") {
            onStartDateChange(undefined);
            onEndDateChange(undefined);
          }
        }}
      >
        <SelectTrigger className="h-8 w-[140px] text-sm border-border">
          <CalendarIcon className="h-3.5 w-3.5 mr-1.5 text-muted-foreground shrink-0" />
          <SelectValue>
            {dateFilter === "custom" && customStartDate && customEndDate
              ? `${format(customStartDate, "MMM d")} – ${format(customEndDate, "MMM d")}`
              : dateFilterLabels[dateFilter]}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          {(Object.keys(dateFilterLabels) as DateFilter[]).map((filter) => (
            <SelectItem key={filter} value={filter}>
              {dateFilterLabels[filter]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {dateFilter === "custom" && (
        <div className="flex items-center gap-1.5">
          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                className={cn(
                  "h-8 text-sm font-normal gap-1.5",
                  !customStartDate && "text-muted-foreground",
                )}
              >
                <CalendarIcon className="h-3.5 w-3.5" />
                {customStartDate ? format(customStartDate, "MMM d") : "Start"}
              </Button>
            </PopoverTrigger>
            <PopoverContent
              className="w-auto p-0 z-[60] bg-background border shadow-lg"
              align="end"
            >
              <Calendar
                mode="single"
                selected={customStartDate}
                onSelect={(date) => {
                  onStartDateChange(date);
                  onDateFilterChange("custom");
                }}
                initialFocus
                className="p-3 pointer-events-auto"
              />
            </PopoverContent>
          </Popover>
          <span className="text-xs text-muted-foreground">&ndash;</span>
          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                className={cn(
                  "h-8 text-sm font-normal gap-1.5",
                  !customEndDate && "text-muted-foreground",
                )}
              >
                <CalendarIcon className="h-3.5 w-3.5" />
                {customEndDate ? format(customEndDate, "MMM d") : "End"}
              </Button>
            </PopoverTrigger>
            <PopoverContent
              className="w-auto p-0 z-[60] bg-background border shadow-lg"
              align="end"
            >
              <Calendar
                mode="single"
                selected={customEndDate}
                onSelect={(date) => {
                  onEndDateChange(date);
                  onDateFilterChange("custom");
                }}
                initialFocus
                className="p-3 pointer-events-auto"
              />
            </PopoverContent>
          </Popover>
        </div>
      )}
    </div>
  );
}
