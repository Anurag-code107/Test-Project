import { useEffect, useRef, useMemo } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MultiSelect } from "@/components/ui/multi-select";
import { FISCAL_QUARTERS } from "@/types/builder-state.types";
import { useFiscalYearLabels } from "@/hooks/useFiscalYearApi";
import { AlertCircle } from "lucide-react";

const fiscalQuarterOptions = FISCAL_QUARTERS.map((q) => ({
  value: q,
  label: q,
}));

/**
 * Parse a "YYYY-MM-DD" string into year/month/day WITHOUT timezone shifting.
 * new Date("2026-07-01") is parsed as UTC midnight, which in western timezones
 * rolls back to June 30 — causing wrong quarter/year derivation.
 */
function parseDateParts(dateStr: string): { year: number; month: number } {
  const parts = dateStr.split("-").map(Number);
  return { year: parts[0] ?? 0, month: parts[1] ?? 1 };
}

/** Derive fiscal years from a date range. */
function deriveFiscalYears(startDate: string, endDate: string): string[] {
  if (!startDate || !endDate) return [];
  const start = parseDateParts(startDate);
  const end = parseDateParts(endDate);
  const years: string[] = [];
  for (let y = start.year; y <= end.year; y++) years.push(`FY${y}`);
  return years;
}

/** Derive fiscal quarters from a date range. */
function deriveFiscalQuarters(startDate: string, endDate: string): string[] {
  if (!startDate || !endDate) return [];
  const start = parseDateParts(startDate);
  const end = parseDateParts(endDate);
  const startQ = Math.ceil(start.month / 3);
  const endQ = Math.ceil(end.month / 3);
  const quarters: string[] = [];
  if (start.year === end.year) {
    for (let q = startQ; q <= endQ; q++) quarters.push(`Q${q}`);
  } else {
    for (let q = 1; q <= 4; q++) quarters.push(`Q${q}`);
  }
  return quarters;
}

export function Step2Schedule() {
  const { state, dispatch } = useBuilder();
  const { schedule } = state;
  const { data: fiscalLabels } = useFiscalYearLabels();

  const fiscalYearOptions = useMemo(() => {
    if (fiscalLabels && fiscalLabels.length > 0) {
      return fiscalLabels.map((fy) => ({ value: fy.label, label: fy.label }));
    }
    // Fallback if API hasn't loaded yet
    return ["FY2024", "FY2025", "FY2026"].map((y) => ({ value: y, label: y }));
  }, [fiscalLabels]);

  // Track whether the user manually set fiscal year/quarter so we can show a mismatch warning
  const userEditedFY = useRef(false);
  const userEditedFQ = useRef(false);

  // Auto-derive fiscal years & quarters when dates change
  useEffect(() => {
    if (!schedule.startDate || !schedule.endDate) return;
    const derivedYears = deriveFiscalYears(
      schedule.startDate,
      schedule.endDate,
    );
    const derivedQuarters = deriveFiscalQuarters(
      schedule.startDate,
      schedule.endDate,
    );

    const yearsMatch =
      derivedYears.length === schedule.fiscalYears.length &&
      derivedYears.every((y) => schedule.fiscalYears.includes(y));
    const quartersMatch =
      derivedQuarters.length === schedule.fiscalQuarters.length &&
      derivedQuarters.every((q) => schedule.fiscalQuarters.includes(q));

    if (!yearsMatch || !quartersMatch) {
      dispatch({
        type: "UPDATE_SCHEDULE",
        payload: {
          fiscalYears: derivedYears,
          fiscalQuarters: derivedQuarters,
        },
      });
      userEditedFY.current = false;
      userEditedFQ.current = false;
    }
  }, [schedule.startDate, schedule.endDate]); // eslint-disable-line react-hooks/exhaustive-deps

  // Check if manually selected FY/Q disagrees with dates
  const hasDates = !!schedule.startDate && !!schedule.endDate;
  const derivedYears = hasDates
    ? deriveFiscalYears(schedule.startDate, schedule.endDate)
    : [];
  const derivedQuarters = hasDates
    ? deriveFiscalQuarters(schedule.startDate, schedule.endDate)
    : [];
  const fyMismatch =
    hasDates &&
    userEditedFY.current &&
    (derivedYears.length !== schedule.fiscalYears.length ||
      !derivedYears.every((y) => schedule.fiscalYears.includes(y)));
  const fqMismatch =
    hasDates &&
    userEditedFQ.current &&
    (derivedQuarters.length !== schedule.fiscalQuarters.length ||
      !derivedQuarters.every((q) => schedule.fiscalQuarters.includes(q)));

  useEffect(() => {
    const isComplete =
      schedule.fiscalYears.length > 0 &&
      schedule.fiscalQuarters.length > 0 &&
      schedule.startDate.length > 0 &&
      schedule.endDate.length > 0;
    if (isComplete && !state.completedSteps.includes("schedule")) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "schedule" });
    } else if (!isComplete && state.completedSteps.includes("schedule")) {
      dispatch({ type: "MARK_STEP_INCOMPLETE", payload: "schedule" });
    }
  }, [
    schedule.fiscalYears,
    schedule.fiscalQuarters,
    schedule.startDate,
    schedule.endDate,
    state.completedSteps,
    dispatch,
  ]);

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label>
          Fiscal Year(s) <span className="text-destructive">*</span>
        </Label>
        <MultiSelect
          options={fiscalYearOptions}
          selected={schedule.fiscalYears}
          onChange={(v) => {
            userEditedFY.current = true;
            dispatch({ type: "UPDATE_SCHEDULE", payload: { fiscalYears: v } });
          }}
          placeholder="Select fiscal year(s)"
        />
        {fyMismatch && (
          <p className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
            <AlertCircle className="h-3.5 w-3.5 shrink-0" />
            Fiscal year doesn't match the selected dates — it will be
            auto-corrected when dates change.
          </p>
        )}
      </div>

      <div className="space-y-2">
        <Label>
          Fiscal Quarter(s) <span className="text-destructive">*</span>
        </Label>
        <MultiSelect
          options={fiscalQuarterOptions}
          selected={schedule.fiscalQuarters}
          onChange={(v) => {
            userEditedFQ.current = true;
            dispatch({
              type: "UPDATE_SCHEDULE",
              payload: { fiscalQuarters: v },
            });
          }}
          placeholder="Select quarter(s)"
        />
        {fqMismatch && (
          <p className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
            <AlertCircle className="h-3.5 w-3.5 shrink-0" />
            Fiscal quarter doesn't match the selected dates — it will be
            auto-corrected when dates change.
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="step2-start">
            Start Date <span className="text-destructive">*</span>
          </Label>
          <Input
            id="step2-start"
            type="date"
            value={schedule.startDate ? schedule.startDate.split("T")[0] : ""}
            onChange={(e) =>
              dispatch({
                type: "UPDATE_SCHEDULE",
                payload: {
                  startDate: e.target.value
                    ? `${e.target.value}T00:00:00Z`
                    : "",
                },
              })
            }
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="step2-end">
            End Date <span className="text-destructive">*</span>
          </Label>
          <Input
            id="step2-end"
            type="date"
            value={schedule.endDate ? schedule.endDate.split("T")[0] : ""}
            min={
              schedule.startDate ? schedule.startDate.split("T")[0] : undefined
            }
            onChange={(e) =>
              dispatch({
                type: "UPDATE_SCHEDULE",
                payload: {
                  endDate: e.target.value ? `${e.target.value}T23:59:59Z` : "",
                },
              })
            }
          />
        </div>
      </div>
    </div>
  );
}
