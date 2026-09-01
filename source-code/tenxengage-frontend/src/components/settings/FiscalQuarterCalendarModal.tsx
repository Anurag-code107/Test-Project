import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  format,
  eachDayOfInterval,
  startOfMonth,
  endOfMonth,
  getDay,
  addMonths,
  isBefore,
  isAfter,
  parseISO,
} from "date-fns";

interface QuarterRange {
  startDate: string;
  endDate: string;
}

interface FiscalQuarterCalendarModalProps {
  quarters: QuarterRange[];
  fyStartDate: string;
  fyEndDate: string;
  open: boolean;
  onClose: () => void;
}

const quarterColors = [
  { bg: "bg-blue-100", text: "text-blue-700", ring: "ring-blue-300" },
  { bg: "bg-emerald-100", text: "text-emerald-700", ring: "ring-emerald-300" },
  { bg: "bg-amber-100", text: "text-amber-700", ring: "ring-amber-300" },
  { bg: "bg-purple-100", text: "text-purple-700", ring: "ring-purple-300" },
];

const DAY_LABELS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

function getQuarterIndex(date: Date, quarters: QuarterRange[]): number {
  for (let i = 0; i < quarters.length; i++) {
    const q = quarters[i]!;
    if (!q.startDate || !q.endDate) continue;
    const qStart = parseISO(q.startDate);
    const qEnd = parseISO(q.endDate);
    if (!isBefore(date, qStart) && !isAfter(date, qEnd)) return i;
  }
  return -1;
}

export function FiscalQuarterCalendarModal({
  quarters,
  fyStartDate,
  fyEndDate,
  open,
  onClose,
}: FiscalQuarterCalendarModalProps) {
  if (!fyStartDate || !fyEndDate) return null;

  const fyStart = parseISO(fyStartDate);
  const fyEnd = parseISO(fyEndDate);

  // Generate months to display (from FY start month to FY end month)
  const months: Date[] = [];
  let current = startOfMonth(fyStart);
  const lastMonth = startOfMonth(fyEnd);
  while (!isAfter(current, lastMonth)) {
    months.push(current);
    current = addMonths(current, 1);
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-w-4xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Fiscal Year Calendar</DialogTitle>
        </DialogHeader>

        {/* Legend */}
        <div className="flex items-center gap-4 text-xs mb-2">
          {quarters.map((_, i) => (
            <div key={i} className="flex items-center gap-1.5">
              <span
                className={`inline-block w-3 h-3 rounded-sm ${quarterColors[i]!.bg}`}
              />
              <span className="text-[hsl(200_10%_46%)]">Q{i + 1}</span>
            </div>
          ))}
        </div>

        {/* Month grid */}
        <div className="grid grid-cols-3 md:grid-cols-4 gap-4">
          {months.map((month) => {
            const monthStart = startOfMonth(month);
            const monthEnd = endOfMonth(month);
            const days = eachDayOfInterval({
              start: monthStart,
              end: monthEnd,
            });
            const startDow = getDay(monthStart);

            return (
              <div key={format(month, "yyyy-MM")} className="space-y-1">
                <p className="text-xs font-semibold text-[hsl(200_20%_18%)] text-center">
                  {format(month, "MMMM yyyy")}
                </p>
                <div className="grid grid-cols-7 gap-px text-center">
                  {DAY_LABELS.map((d) => (
                    <span
                      key={d}
                      className="text-[10px] text-[hsl(200_10%_60%)] font-medium"
                    >
                      {d}
                    </span>
                  ))}
                  {Array.from({ length: startDow }).map((_, i) => (
                    <span key={`pad-${i}`} />
                  ))}
                  {days.map((day) => {
                    const qi = getQuarterIndex(day, quarters);
                    const color = qi >= 0 ? quarterColors[qi] : null;
                    return (
                      <span
                        key={day.toISOString()}
                        className={`text-[10px] leading-5 rounded-sm ${
                          color
                            ? `${color.bg} ${color.text} font-medium`
                            : "text-[hsl(200_10%_70%)]"
                        }`}
                      >
                        {format(day, "d")}
                      </span>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
