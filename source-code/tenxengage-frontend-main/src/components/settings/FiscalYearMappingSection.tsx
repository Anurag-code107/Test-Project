import { useState, useMemo, useCallback } from "react";
import { Calendar as CalendarIcon, Plus, Trash2, Loader2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { format, addMonths, addWeeks, addDays } from "date-fns";
import { toast } from "sonner";
import {
  useFiscalYearConfigs,
  useCreateFiscalYearConfig,
  useUpdateFiscalYearConfig,
  useDeleteFiscalYearConfig,
} from "@/hooks/useFiscalYearApi";
import { FiscalQuarterCalendarModal } from "./FiscalQuarterCalendarModal";
import type {
  QuarterMethod,
  FiscalYearConfigResponse,
  SaveFiscalYearConfigRequest,
} from "@/types/fiscal-year.types";

type LocalQuarterMethod = "months" | "weeks" | "days" | "custom";

interface Quarter {
  name: string;
  startDate: string;
  endDate: string;
}

const quarterColors = [
  {
    border: "border-[hsl(217_91%_60%/0.2)]",
    bg: "bg-[hsl(217_91%_60%/0.03)]",
    accent: "text-[hsl(217_91%_50%)]",
  },
  {
    border: "border-[hsl(160_55%_42%/0.2)]",
    bg: "bg-[hsl(160_55%_42%/0.03)]",
    accent: "text-[hsl(160_55%_38%)]",
  },
  {
    border: "border-[hsl(38_80%_50%/0.2)]",
    bg: "bg-[hsl(38_80%_50%/0.03)]",
    accent: "text-[hsl(38_80%_42%)]",
  },
  {
    border: "border-[hsl(270_55%_55%/0.2)]",
    bg: "bg-[hsl(270_55%_55%/0.03)]",
    accent: "text-[hsl(270_55%_45%)]",
  },
];

function toLocalMethod(m: QuarterMethod): LocalQuarterMethod {
  return m.toLowerCase() as LocalQuarterMethod;
}

function toApiMethod(m: LocalQuarterMethod): QuarterMethod {
  return m.toUpperCase() as QuarterMethod;
}

function deriveLabel(startDate: string): string {
  if (!startDate) return "";
  const year = new Date(startDate + "T00:00:00").getFullYear();
  return `FY${year}`;
}

function getQuarterSize(
  method: LocalQuarterMethod,
  months: number,
  weeks: number,
  days: number,
): number | null {
  if (method === "months") return months;
  if (method === "weeks") return weeks;
  if (method === "days") return days;
  return null;
}

export function FiscalYearMappingSection() {
  const { data: configs, isLoading, isError } = useFiscalYearConfigs();
  const createMutation = useCreateFiscalYearConfig();
  const updateMutation = useUpdateFiscalYearConfig();
  const deleteMutation = useDeleteFiscalYearConfig();

  // Editing state
  const [selectedConfigId, setSelectedConfigId] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteTarget, setDeleteTarget] =
    useState<FiscalYearConfigResponse | null>(null);
  const [calendarOpen, setCalendarOpen] = useState(false);

  // Form state
  const [fyStartDate, setFyStartDate] = useState("");
  const [fyEndDate, setFyEndDate] = useState("");
  const [quarterMethod, setQuarterMethod] =
    useState<LocalQuarterMethod>("months");
  const [monthsPerQuarter, setMonthsPerQuarter] = useState(3);
  const [weeksPerQuarter, setWeeksPerQuarter] = useState(13);
  const [daysPerQuarter, setDaysPerQuarter] = useState(91);
  const [customQ1Start, setCustomQ1Start] = useState("");
  const [customQ1End, setCustomQ1End] = useState("");
  const [customQ2Start, setCustomQ2Start] = useState("");
  const [customQ2End, setCustomQ2End] = useState("");
  const [customQ3Start, setCustomQ3Start] = useState("");
  const [customQ3End, setCustomQ3End] = useState("");
  const [customQ4Start, setCustomQ4Start] = useState("");
  const [customQ4End, setCustomQ4End] = useState("");

  const loadConfig = useCallback((config: FiscalYearConfigResponse) => {
    setSelectedConfigId(config.id);
    setIsNew(false);
    setFyStartDate(config.startDate);
    setFyEndDate(config.endDate);
    setQuarterMethod(toLocalMethod(config.quarterMethod));
    setMonthsPerQuarter(
      config.quarterMethod === "MONTHS" ? (config.quarterSize ?? 3) : 3,
    );
    setWeeksPerQuarter(
      config.quarterMethod === "WEEKS" ? (config.quarterSize ?? 13) : 13,
    );
    setDaysPerQuarter(
      config.quarterMethod === "DAYS" ? (config.quarterSize ?? 91) : 91,
    );
    setCustomQ1Start(config.q1StartDate);
    setCustomQ1End(config.q1EndDate);
    setCustomQ2Start(config.q2StartDate);
    setCustomQ2End(config.q2EndDate);
    setCustomQ3Start(config.q3StartDate);
    setCustomQ3End(config.q3EndDate);
    setCustomQ4Start(config.q4StartDate);
    setCustomQ4End(config.q4EndDate);
  }, []);

  const resetForm = useCallback(() => {
    setSelectedConfigId(null);
    setIsNew(true);
    setFyStartDate("");
    setFyEndDate("");
    setQuarterMethod("months");
    setMonthsPerQuarter(3);
    setWeeksPerQuarter(13);
    setDaysPerQuarter(91);
    setCustomQ1Start("");
    setCustomQ1End("");
    setCustomQ2Start("");
    setCustomQ2End("");
    setCustomQ3Start("");
    setCustomQ3End("");
    setCustomQ4Start("");
    setCustomQ4End("");
  }, []);

  // Auto-calculate quarters for non-custom methods
  const autoQuarters = useMemo<Quarter[]>(() => {
    if (!fyStartDate || quarterMethod === "custom") return [];
    const start = new Date(fyStartDate + "T00:00:00");
    const result: Quarter[] = [];
    for (let i = 0; i < 4; i++) {
      let qStart: Date;
      let qEnd: Date;
      if (quarterMethod === "months") {
        qStart = addMonths(start, i * monthsPerQuarter);
        qEnd = addDays(addMonths(start, (i + 1) * monthsPerQuarter), -1);
      } else if (quarterMethod === "weeks") {
        qStart = addWeeks(start, i * weeksPerQuarter);
        qEnd = addDays(addWeeks(start, (i + 1) * weeksPerQuarter), -1);
      } else {
        qStart = addDays(start, i * daysPerQuarter);
        qEnd = addDays(start, (i + 1) * daysPerQuarter - 1);
      }
      result.push({
        name: `Q${i + 1}`,
        startDate: format(qStart, "yyyy-MM-dd"),
        endDate: format(qEnd, "yyyy-MM-dd"),
      });
    }
    return result;
  }, [
    fyStartDate,
    quarterMethod,
    monthsPerQuarter,
    weeksPerQuarter,
    daysPerQuarter,
  ]);

  const quarters: Quarter[] =
    quarterMethod === "custom"
      ? [
          { name: "Q1", startDate: customQ1Start, endDate: customQ1End },
          { name: "Q2", startDate: customQ2Start, endDate: customQ2End },
          { name: "Q3", startDate: customQ3Start, endDate: customQ3End },
          { name: "Q4", startDate: customQ4Start, endDate: customQ4End },
        ]
      : autoQuarters;

  const isEditing = selectedConfigId !== null || isNew;
  const isSaving = createMutation.isPending || updateMutation.isPending;

  function buildRequest(): SaveFiscalYearConfigRequest {
    const q = quarterMethod === "custom" ? quarters : autoQuarters;
    return {
      label: deriveLabel(fyStartDate),
      startDate: fyStartDate,
      endDate: fyEndDate,
      quarterMethod: toApiMethod(quarterMethod),
      quarterSize: getQuarterSize(
        quarterMethod,
        monthsPerQuarter,
        weeksPerQuarter,
        daysPerQuarter,
      ),
      q1StartDate: q[0]?.startDate ?? "",
      q1EndDate: q[0]?.endDate ?? "",
      q2StartDate: q[1]?.startDate ?? "",
      q2EndDate: q[1]?.endDate ?? "",
      q3StartDate: q[2]?.startDate ?? "",
      q3EndDate: q[2]?.endDate ?? "",
      q4StartDate: q[3]?.startDate ?? "",
      q4EndDate: q[3]?.endDate ?? "",
    };
  }

  function handleSave() {
    const request = buildRequest();
    if (!request.startDate || !request.endDate) {
      toast.error("Please set fiscal year start and end dates.");
      return;
    }

    if (isNew) {
      createMutation.mutate(request, {
        onSuccess: (created) => {
          toast.success(`Fiscal year ${created.label} created.`);
          loadConfig(created);
        },
        onError: (err) => {
          toast.error(
            err instanceof Error
              ? err.message
              : "Failed to create fiscal year config.",
          );
        },
      });
    } else if (selectedConfigId) {
      updateMutation.mutate(
        { id: selectedConfigId, data: request },
        {
          onSuccess: (updated) => {
            toast.success(`Fiscal year ${updated.label} updated.`);
            loadConfig(updated);
          },
          onError: (err) => {
            toast.error(
              err instanceof Error
                ? err.message
                : "Failed to update fiscal year config.",
            );
          },
        },
      );
    }
  }

  function handleDelete() {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(`Fiscal year ${deleteTarget.label} deleted.`);
        if (selectedConfigId === deleteTarget.id) resetForm();
        setDeleteTarget(null);
      },
      onError: (err) => {
        toast.error(
          err instanceof Error
            ? err.message
            : "Failed to delete fiscal year config.",
        );
        setDeleteTarget(null);
      },
    });
  }

  // Custom quarter date setters
  const customSetters = [
    { setStart: setCustomQ1Start, setEnd: setCustomQ1End },
    { setStart: setCustomQ2Start, setEnd: setCustomQ2End },
    { setStart: setCustomQ3Start, setEnd: setCustomQ3End },
    { setStart: setCustomQ4Start, setEnd: setCustomQ4End },
  ];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">
          Loading fiscal year configs...
        </span>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="text-sm text-destructive py-4">
        Failed to load fiscal year configurations. Please try again.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Config Selector */}
      <div className="flex items-center gap-3 flex-wrap">
        {(configs ?? []).map((c) => (
          <button
            key={c.id}
            onClick={() => loadConfig(c)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors ${
              selectedConfigId === c.id
                ? "border-primary bg-primary/10 text-primary"
                : "border-border text-muted-foreground hover:border-muted-foreground/40"
            }`}
          >
            {c.label}
          </button>
        ))}
        <Button
          variant="outline"
          size="sm"
          onClick={resetForm}
          className="gap-1.5"
        >
          <Plus className="h-3.5 w-3.5" /> New Fiscal Year
        </Button>
      </div>

      {/* Form - shown when editing or creating */}
      {isEditing && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Fiscal Year Definition */}
            <div className="rounded-xl border border-border p-4 space-y-3">
              <div>
                <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
                  Fiscal Year Definition
                </span>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Set the start and end dates for your organization's fiscal
                  year.
                </p>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-foreground">
                    Start Date
                  </Label>
                  <Input
                    type="date"
                    value={fyStartDate}
                    onChange={(e) => setFyStartDate(e.target.value)}
                    className="h-8 text-sm border-border tabular-nums"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium text-foreground">
                    End Date
                  </Label>
                  <Input
                    type="date"
                    value={fyEndDate}
                    onChange={(e) => setFyEndDate(e.target.value)}
                    className="h-8 text-sm border-border tabular-nums"
                  />
                </div>
              </div>
              {fyStartDate && (
                <p className="text-xs text-muted-foreground">
                  Label:{" "}
                  <span className="font-semibold">
                    {deriveLabel(fyStartDate)}
                  </span>
                </p>
              )}
            </div>

            {/* Quarter Method */}
            <div className="rounded-xl border border-border p-4 space-y-3">
              <div>
                <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
                  Quarter Definition
                </span>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Choose how fiscal quarters are calculated.
                </p>
              </div>
              <RadioGroup
                value={quarterMethod}
                onValueChange={(v) => setQuarterMethod(v as LocalQuarterMethod)}
              >
                <div className="space-y-3">
                  <div className="space-y-1.5">
                    <div className="flex items-center space-x-2">
                      <RadioGroupItem value="months" id="q-months" />
                      <Label
                        htmlFor="q-months"
                        className="text-sm cursor-pointer text-foreground"
                      >
                        Fixed calendar months
                      </Label>
                    </div>
                    {quarterMethod === "months" && (
                      <div className="ml-6 space-y-1">
                        <Input
                          type="number"
                          value={monthsPerQuarter}
                          onChange={(e) =>
                            setMonthsPerQuarter(Number(e.target.value))
                          }
                          min={1}
                          max={6}
                          className="h-8 text-sm border-border tabular-nums"
                        />
                        <p className="text-xs text-muted-foreground">
                          Months per quarter
                        </p>
                      </div>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center space-x-2">
                      <RadioGroupItem value="weeks" id="q-weeks" />
                      <Label
                        htmlFor="q-weeks"
                        className="text-sm cursor-pointer text-foreground"
                      >
                        Fixed number of weeks
                      </Label>
                    </div>
                    {quarterMethod === "weeks" && (
                      <div className="ml-6 space-y-1">
                        <Input
                          type="number"
                          value={weeksPerQuarter}
                          onChange={(e) =>
                            setWeeksPerQuarter(Number(e.target.value))
                          }
                          min={1}
                          max={26}
                          className="h-8 text-sm border-border tabular-nums"
                        />
                        <p className="text-xs text-muted-foreground">
                          Weeks per quarter
                        </p>
                      </div>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center space-x-2">
                      <RadioGroupItem value="days" id="q-days" />
                      <Label
                        htmlFor="q-days"
                        className="text-sm cursor-pointer text-foreground"
                      >
                        Fixed number of days
                      </Label>
                    </div>
                    {quarterMethod === "days" && (
                      <div className="ml-6 space-y-1">
                        <Input
                          type="number"
                          value={daysPerQuarter}
                          onChange={(e) =>
                            setDaysPerQuarter(Number(e.target.value))
                          }
                          min={1}
                          max={183}
                          className="h-8 text-sm border-border tabular-nums"
                        />
                        <p className="text-xs text-muted-foreground">
                          Days per quarter
                        </p>
                      </div>
                    )}
                  </div>

                  <div className="flex items-center space-x-2">
                    <RadioGroupItem value="custom" id="q-custom" />
                    <Label
                      htmlFor="q-custom"
                      className="text-sm cursor-pointer text-foreground"
                    >
                      Custom dates per quarter
                    </Label>
                  </div>
                </div>
              </RadioGroup>
            </div>
          </div>

          {/* Quarters Grid */}
          <div className="rounded-xl border border-border p-4 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
                  Fiscal Quarters
                </span>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {quarterMethod === "custom"
                    ? "Enter custom start and end dates for each quarter."
                    : "Auto-calculated based on your fiscal year start and quarter method."}
                </p>
              </div>
              <button
                onClick={() => setCalendarOpen(true)}
                className="p-1 rounded hover:bg-muted transition-colors"
                title="View calendar"
              >
                <CalendarIcon className="h-4 w-4 text-muted-foreground" />
              </button>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
              {quarters.map((q, idx) => {
                const color = quarterColors[idx]!;
                return (
                  <div
                    key={q.name}
                    className={`rounded-xl border ${color.border} ${color.bg} p-4 space-y-3`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`text-sm font-semibold ${color.accent}`}>
                        {q.name}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {quarterMethod === "custom" ? "Custom" : "Auto"}
                      </span>
                    </div>
                    {quarterMethod === "custom" ? (
                      <div className="space-y-2">
                        <div className="space-y-1">
                          <Label className="text-xs text-muted-foreground">
                            Start Date
                          </Label>
                          <Input
                            type="date"
                            value={q.startDate}
                            onChange={(e) =>
                              customSetters[idx]!.setStart(e.target.value)
                            }
                            className="text-xs h-7 border-border tabular-nums"
                          />
                        </div>
                        <div className="space-y-1">
                          <Label className="text-xs text-muted-foreground">
                            End Date
                          </Label>
                          <Input
                            type="date"
                            value={q.endDate}
                            onChange={(e) =>
                              customSetters[idx]!.setEnd(e.target.value)
                            }
                            className="text-xs h-7 border-border tabular-nums"
                          />
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-1.5">
                        <div className="flex justify-between text-sm">
                          <span className="text-muted-foreground">Start</span>
                          <span className="font-medium text-foreground tabular-nums">
                            {q.startDate
                              ? format(
                                  new Date(q.startDate + "T00:00:00"),
                                  "MMM d, yyyy",
                                )
                              : "\u2014"}
                          </span>
                        </div>
                        <div className="flex justify-between text-sm">
                          <span className="text-muted-foreground">End</span>
                          <span className="font-medium text-foreground tabular-nums">
                            {q.endDate
                              ? format(
                                  new Date(q.endDate + "T00:00:00"),
                                  "MMM d, yyyy",
                                )
                              : "\u2014"}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-3">
            <Button onClick={handleSave} disabled={isSaving} size="sm">
              {isSaving && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {isNew ? "Create" : "Save Changes"}
            </Button>
            {!isNew && selectedConfigId && (
              <Button
                variant="outline"
                size="sm"
                className="text-destructive hover:text-destructive hover:bg-destructive/10"
                onClick={() => {
                  const target = configs?.find(
                    (c) => c.id === selectedConfigId,
                  );
                  if (target) setDeleteTarget(target);
                }}
              >
                <Trash2 className="h-3.5 w-3.5 mr-1.5" /> Delete
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSelectedConfigId(null);
                setIsNew(false);
              }}
            >
              Cancel
            </Button>
          </div>
        </>
      )}

      {/* Calendar Modal */}
      <FiscalQuarterCalendarModal
        quarters={quarters.map((q) => ({
          startDate: q.startDate,
          endDate: q.endDate,
        }))}
        fyStartDate={fyStartDate}
        fyEndDate={fyEndDate}
        open={calendarOpen}
        onClose={() => setCalendarOpen(false)}
      />

      {/* Delete Confirmation */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete {deleteTarget?.label}?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently delete the fiscal year configuration. This
              action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive hover:bg-destructive/90"
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
