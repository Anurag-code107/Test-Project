/* eslint-disable react-refresh/only-export-components */
import {
  Megaphone,
  GraduationCap,
  FileCheck,
  Layers,
  FileText,
  DollarSign,
  Loader2,
  Download,
  Eye,
  FileSpreadsheet,
  File,
  CircleCheck,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  getCurrency,
  currencies as currencyConfig,
  monetaryCurrencyIds,
  nonMonetaryCurrencyIds,
} from "@/config/currencies";
import type {
  IncentiveType,
  IncentiveStatus,
  IncentiveDetailResponse,
  ActivityDefinition,
  TrainingCourseAssignment,
  DocumentSummary,
} from "@/types/incentive.types";

// --- Icon/color maps ---

export const engagementIcons: Record<IncentiveType, React.ReactNode> = {
  SALES: <Megaphone className="h-5 w-5" />,
  TRAINING: <GraduationCap className="h-5 w-5" />,
  ACTIVITY: <FileCheck className="h-5 w-5" />,
  JOURNEY: <Layers className="h-5 w-5" />,
};

export const engagementColors: Record<IncentiveType, string> = {
  SALES: "text-primary",
  TRAINING: "text-warning",
  ACTIVITY: "text-blue-500",
  JOURNEY: "text-indigo-500",
};

export const statusColors: Record<IncentiveStatus, string> = {
  DRAFT: "bg-muted text-muted-foreground border-muted-foreground/20",
  PENDING_APPROVAL: "bg-warning/10 text-warning border-warning/20",
  DENIED: "bg-destructive/10 text-destructive border-destructive/20",
  ACTIVE: "bg-success/10 text-success border-success/20",
  INACTIVE: "bg-destructive/10 text-destructive border-destructive/20",
};

export const statusLabels: Record<IncentiveStatus, string> = {
  DRAFT: "Draft",
  PENDING_APPROVAL: "Pending Approval",
  DENIED: "Denied",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
};

export const typeLabels: Record<IncentiveType, string> = {
  SALES: "Sales Incentive",
  TRAINING: "Training Incentive",
  ACTIVITY: "Activity Incentive",
  JOURNEY: "Journey Incentive",
};

/** @deprecated Use getCurrency(id) from @/config/currencies instead */
export function getCurrencyIcon(id: string, size = "h-4 w-4") {
  const Icon = getCurrency(id).icon;
  return <Icon className={size} />;
}

/**
 * Currency color config derived from centralized config.
 * Uses the static Tailwind classes from currencies.ts (bgClass, borderClass,
 * iconBgClass) so Tailwind's JIT scanner can detect them — avoids the
 * previous approach of dynamically constructing class names via template
 * literals which Tailwind's purge/JIT could not find.
 */
export function getCurrencyColors(id: string) {
  const cfg = getCurrency(id);
  return {
    text: cfg.iconClass,
    bg: cfg.bgClass,
    border: cfg.borderClass,
    iconBg: cfg.iconBgClass,
  };
}

/** @deprecated Kept for backward compat — use getCurrencyColors() instead */
export const currencyIcons = Object.fromEntries(
  Object.keys(currencyConfig).map((id) => [id, getCurrencyIcon(id)]),
) as Record<string, React.ReactNode>;

/** @deprecated Kept for backward compat — use getCurrencyColors() instead */
export const currencyColors = Object.fromEntries(
  Object.keys(currencyConfig).map((id) => [id, getCurrencyColors(id)]),
) as Record<
  string,
  { text: string; bg: string; border: string; iconBg: string }
>;

/** @deprecated Use getCurrencyColors() instead */
export const defaultCurrencyColors = getCurrencyColors("cash");

// --- Helpers ---

export function formatDate(date: string) {
  return new Date(date).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatCurrencyAmount(
  currencyId: string,
  amount: string,
): string {
  const num = parseFloat(amount);
  if (isNaN(num)) return amount;
  return getCurrency(currencyId).format(num);
}

export const formatBudgetAmount = (amount: number) =>
  getCurrency("cash").format(amount);

// --- Document maps ---

export const fileTypeIcons: Record<string, React.ReactNode> = {
  pdf: <FileText className="h-4 w-4 text-destructive" />,
  xlsx: <FileSpreadsheet className="h-4 w-4 text-success" />,
  docx: <File className="h-4 w-4 text-blue-500" />,
};

export const documentTypeLabels: Record<string, string> = {
  "eligible-products": "Eligible Products",
  "terms-conditions": "Terms & Conditions",
  "program-rules": "Program Rules",
  faq: "FAQ",
};

// --- Sub-components ---

export function ActivityDefinitionsList({
  definitions,
}: {
  definitions: ActivityDefinition[];
}) {
  return (
    <div className="rounded-xl border border-border p-4 space-y-4">
      <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
        Required Activities ({definitions.length})
      </h3>
      <div className="space-y-3">
        {definitions.map((actDef) => (
          <div
            key={actDef.id ?? actDef.sortOrder}
            className="rounded-lg border bg-muted/30 p-3 space-y-2"
          >
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-muted-foreground">
                Activity {actDef.sortOrder}
              </span>
              <h4 className="text-sm font-semibold text-foreground">
                {actDef.name}
              </h4>
              {actDef.userCompleted && (
                <Badge
                  variant="outline"
                  className="text-xs bg-emerald-500/10 text-emerald-600 border-emerald-500/20 shrink-0"
                >
                  <CircleCheck className="h-3 w-3 mr-1" />
                  Completed
                </Badge>
              )}
            </div>
            {actDef.description && (
              <p className="text-xs text-muted-foreground leading-relaxed">
                {actDef.description}
              </p>
            )}
            {actDef.requiredDocuments.length > 0 && (
              <div className="pt-1 space-y-1.5">
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Required Documents
                </span>
                {actDef.requiredDocuments.map((doc, docIdx) => (
                  <div key={docIdx} className="flex items-start gap-2 pl-1">
                    <FileText className="h-3 w-3 mt-0.5 shrink-0 text-muted-foreground" />
                    <div className="min-w-0">
                      <span className="text-xs font-medium text-foreground">
                        {doc.name}
                      </span>
                      {doc.description && (
                        <p className="text-xs text-muted-foreground leading-relaxed">
                          {doc.description}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export function TrainingCoursesList({
  courses,
}: {
  courses: TrainingCourseAssignment[];
}) {
  return (
    <div className="rounded-xl border border-border p-4 space-y-4">
      <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
        Training Courses ({courses.length})
      </h3>
      <div className="space-y-3">
        {courses.map((course) => (
          <div
            key={course.id ?? course.courseId}
            className="rounded-lg border bg-muted/30 p-3 space-y-1.5"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 min-w-0">
                <h4 className="text-sm font-semibold text-foreground truncate">
                  {course.courseName}
                </h4>
                {course.userCompleted && (
                  <Badge
                    variant="outline"
                    className="text-xs bg-emerald-500/10 text-emerald-600 border-emerald-500/20 shrink-0"
                  >
                    <CircleCheck className="h-3 w-3 mr-1" />
                    Completed
                  </Badge>
                )}
              </div>
              {course.required && (
                <Badge
                  variant="outline"
                  className="text-xs bg-primary/10 text-primary border-primary/20 shrink-0"
                >
                  Required
                </Badge>
              )}
            </div>
            {course.courseCategory && (
              <p className="text-xs text-muted-foreground">
                {course.courseCategory}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export function BudgetBreakdownSection({
  incentive,
  label,
  amount,
}: {
  incentive: IncentiveDetailResponse;
  label: string;
  amount: number;
}) {
  const budgetCurrency = incentive.budget?.currency;

  const monetaryIds = [...monetaryCurrencyIds];
  const nonMonetaryIds = [...nonMonetaryCurrencyIds];

  // Canonical per-currency totals come from `incentive.budgets[]` — each entry
  // carries its own `totalBudget` for one `currencyId`. The legacy singular
  // `incentive.budget` is kept as a fallback for older payloads.
  const budgetByCurrency = new Map<string, number>();
  (incentive.budgets ?? []).forEach((b) => {
    const n = parseFloat(b.totalBudget);
    if (!isNaN(n)) budgetByCurrency.set(b.currencyId, n);
  });

  // Aggregate persisted total used to scale `amount` back across currencies.
  // For "Total Budget" callers `amount === aggregateTotal` (ratio = 1); for
  // "Budget Utilized" callers `amount = aggregateTotal * util%` (ratio < 1).
  const aggregateTotal =
    budgetByCurrency.size > 0
      ? Array.from(budgetByCurrency.values()).reduce((sum, n) => sum + n, 0)
      : incentive.budget
        ? parseFloat(incentive.budget.totalBudget)
        : 0;
  const ratio = aggregateTotal > 0 ? amount / aggregateTotal : 0;

  const getCurrencyAmount = (currencyId: string): string => {
    // Canonical: per-currency total from `budgets[]`, scaled to `amount`.
    const perCurrency = budgetByCurrency.get(currencyId);
    if (perCurrency !== undefined) {
      return getCurrency(currencyId).format(perCurrency * ratio);
    }
    // Legacy: singular `budget` whose `currency` matches this row.
    if (currencyId === budgetCurrency && incentive.budget) {
      return formatBudgetAmount(amount);
    }
    // No budget data for this currency — render zero. Never fall back to
    // `rewardAmounts`; reward figures don't belong in the Budget panel.
    return getCurrency(currencyId).format(0);
  };

  return (
    <div className="bg-muted/30 rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-muted-foreground">
          {label}
        </span>
        <span className="font-semibold text-foreground">
          {formatBudgetAmount(amount)}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {monetaryIds.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              <DollarSign className="h-3.5 w-3.5 text-emerald-500" />
              <span>Monetary</span>
            </div>
            <div className="space-y-1.5 pl-5">
              {monetaryIds.map((id) => {
                const cfg = getCurrency(id);
                const Icon = cfg.icon;
                return (
                  <div
                    key={id}
                    className="flex items-center justify-between text-sm"
                  >
                    <div className="flex items-center gap-1.5">
                      <Icon className={cn("h-3 w-3", cfg.iconClass)} />
                      <span className="text-muted-foreground">{cfg.label}</span>
                    </div>
                    <span className="font-medium">{getCurrencyAmount(id)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
        {nonMonetaryIds.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              {(() => {
                const Icon = getCurrency("credits").icon;
                return <Icon className="h-3.5 w-3.5 text-violet-500" />;
              })()}
              <span>Non-Monetary</span>
            </div>
            <div className="space-y-1.5 pl-5">
              {nonMonetaryIds.map((id) => {
                const cfg = getCurrency(id);
                const Icon = cfg.icon;
                return (
                  <div
                    key={id}
                    className="flex items-center justify-between text-sm"
                  >
                    <div className="flex items-center gap-1.5">
                      <Icon className={cn("h-3 w-3", cfg.iconClass)} />
                      <span className="text-muted-foreground">{cfg.label}</span>
                    </div>
                    <span className="font-medium">{getCurrencyAmount(id)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function DocumentRow({
  doc,
  onView,
  onDownload,
  isLoading,
  isDisabled,
}: {
  doc: DocumentSummary;
  onView: (doc: DocumentSummary) => void;
  onDownload: (doc: DocumentSummary) => void;
  isLoading: boolean;
  isDisabled: boolean;
}) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-lg border bg-card hover:bg-muted/50 transition-colors">
      <div className="shrink-0">
        {fileTypeIcons[doc.fileType] ?? (
          <FileText className="h-4 w-4 text-muted-foreground" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium truncate">{doc.name}</p>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span>
            {documentTypeLabels[doc.documentType] ?? doc.documentType}
          </span>
          <span>&middot;</span>
          <span>{doc.size}</span>
        </div>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {isLoading ? (
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground mx-1" />
        ) : (
          <>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => onView(doc)}
              disabled={isDisabled}
              title={doc.downloadUrl ? "View document" : "No file uploaded"}
            >
              <Eye className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => onDownload(doc)}
              disabled={isDisabled}
              title={doc.downloadUrl ? "Download document" : "No file uploaded"}
            >
              <Download className="h-4 w-4" />
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
