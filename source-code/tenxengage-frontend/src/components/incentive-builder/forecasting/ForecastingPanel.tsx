import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useBuilder } from "@/contexts/BuilderContext";
import { useLocationHierarchy } from "@/hooks/useLocationApi";
import {
  useCreateIncentive,
  useUpdateIncentive,
  useUploadIncentiveDocuments,
} from "@/hooks/useIncentiveApi";
import {
  buildCreateRequest,
  buildUpdateRequest,
} from "@/utils/builderRequestMapper";
import type {
  IncentiveForecast,
  ForecastLocationBreakdown,
  ForecastInsight,
} from "@/types/incentive.types";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
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
import {
  Sparkles,
  Settings,
  Globe,
  DollarSign,
  CheckCircle,
  Users,
  TrendingUp,
  ChevronDown,
  Bookmark,
  Loader2,
  AlertTriangle,
  Lightbulb,
  Shield,
  Zap,
  Info,
  Crosshair,
} from "lucide-react";
import {
  DocumentUploadDialog,
  type PendingUpload,
} from "@/components/incentive-builder/DocumentUploadDialog";
import { ForecastingLoader } from "./ForecastingLoader";
import { getNarrowingDescriptor } from "./narrowingDescriptor";
import { SlotDropNumber } from "@/components/SlotDropNumber";
import type { DocumentInput } from "@/types/incentive.types";

/** Compact currency formatter — uses M for ≥$1M, K otherwise. */
function formatCompactCurrency(value: number): string {
  if (value >= 1_000_000) {
    return `$${(value / 1_000_000).toFixed(1)}M`;
  }
  return `$${(value / 1000).toFixed(0)}K`;
}

// --- Location data (dynamic from forecast response) ---

type ViewMode = "GLOBAL" | string;

// Each top-level location is assigned a hue; its descendants get shades within
// the same hue family so a segmented bar at any level visually ties children
// to their parent. Classes are listed statically so Tailwind retains all of
// them at build time (dynamic `bg-${hue}-${shade}` would otherwise be purged).
const HUE_PALETTE = [
  "blue",
  "emerald",
  "amber",
  "violet",
  "rose",
  "cyan",
  "orange",
  "teal",
] as const;
type LocationHue = (typeof HUE_PALETTE)[number];

const HUE_SHADE_CLASSES: Record<LocationHue, Record<number, string>> = {
  blue:    { 300: "bg-blue-300",    400: "bg-blue-400",    500: "bg-blue-500",    600: "bg-blue-600",    700: "bg-blue-700"    },
  emerald: { 300: "bg-emerald-300", 400: "bg-emerald-400", 500: "bg-emerald-500", 600: "bg-emerald-600", 700: "bg-emerald-700" },
  amber:   { 300: "bg-amber-300",   400: "bg-amber-400",   500: "bg-amber-500",   600: "bg-amber-600",   700: "bg-amber-700"   },
  violet:  { 300: "bg-violet-300",  400: "bg-violet-400",  500: "bg-violet-500",  600: "bg-violet-600",  700: "bg-violet-700"  },
  rose:    { 300: "bg-rose-300",    400: "bg-rose-400",    500: "bg-rose-500",    600: "bg-rose-600",    700: "bg-rose-700"    },
  cyan:    { 300: "bg-cyan-300",    400: "bg-cyan-400",    500: "bg-cyan-500",    600: "bg-cyan-600",    700: "bg-cyan-700"    },
  orange:  { 300: "bg-orange-300",  400: "bg-orange-400",  500: "bg-orange-500",  600: "bg-orange-600",  700: "bg-orange-700"  },
  teal:    { 300: "bg-teal-300",    400: "bg-teal-400",    500: "bg-teal-500",    600: "bg-teal-600",    700: "bg-teal-700"    },
};

const SHADE_SCALE = [300, 400, 500, 600, 700];

/** Walk the location tree and assign each node a `bg-{hue}-{shade}` class.
 *  Top-level locations get `shade=500`; descendants get adjacent shades from
 *  their root's hue (skipping the parent shade) so children read as distinct
 *  but visually related to their parent. */
function buildLocationColors(
  breakdown: ForecastLocationBreakdown[] | undefined,
): Record<string, string> {
  const colors: Record<string, string> = {};
  if (!breakdown || breakdown.length === 0) return colors;

  const childrenByParent = new Map<string | null, ForecastLocationBreakdown[]>();
  for (const lb of breakdown) {
    const key = lb.parentId ?? null;
    const arr = childrenByParent.get(key) ?? [];
    arr.push(lb);
    childrenByParent.set(key, arr);
  }

  function assignDescendants(
    parent: ForecastLocationBreakdown,
    hue: LocationHue,
    parentShade: number,
  ) {
    const children = childrenByParent.get(parent.locationValueId) ?? [];
    if (children.length === 0) return;
    const available = SHADE_SCALE.filter((s) => s !== parentShade);
    children.forEach((child, i) => {
      const shade = available[i % available.length] ?? 500;
      colors[child.name] = HUE_SHADE_CLASSES[hue][shade] ?? "bg-blue-500";
      assignDescendants(child, hue, shade);
    });
  }

  const topLevel = childrenByParent.get(null) ?? [];
  topLevel.forEach((lb, i) => {
    const hue = HUE_PALETTE[i % HUE_PALETTE.length] ?? "blue";
    colors[lb.name] = HUE_SHADE_CLASSES[hue][500] ?? "bg-blue-500";
    assignDescendants(lb, hue, 500);
  });

  return colors;
}

const insightIcons: Record<string, typeof AlertTriangle> = {
  strength: Shield,
  risk: AlertTriangle,
  opportunity: Lightbulb,
  warning: Zap,
};

const insightColors: Record<string, string> = {
  strength: "text-[hsl(var(--success))] bg-[hsl(var(--success)/0.08)] border-[hsl(var(--success)/0.3)]",
  risk: "text-destructive bg-destructive/5 border-destructive/20",
  opportunity: "text-primary bg-primary/5 border-primary/20",
  warning: "text-[hsl(var(--warning))] bg-[hsl(var(--warning)/0.08)] border-[hsl(var(--warning)/0.3)]",
};

// --- Animated AI Insights section ---

const INSIGHT_STAGGER_MS = 80;
const INSIGHT_ANIM_MS = 300;

function InsightsSection({
  insights,
  scopeLabel,
  swapKey,
  open,
  onToggle,
}: {
  insights: ForecastInsight[];
  /** Optional scope hint (e.g. "Insights scoped to AMERICAS") shown when the
   *  user is viewing a child of a top-level location and we're falling back
   *  to that ancestor's insights. */
  scopeLabel?: string | null;
  /** Stable identifier for the active *insight scope*. Changing it triggers
   *  an exit-then-enter swap animation. Sibling-children of the same parent
   *  should share a swap key (they show the same insights). */
  swapKey: string;
  open: boolean;
  onToggle: () => void;
}) {
  // Phase: "closed" | "entering" | "open" | "exiting"
  const [phase, setPhase] = useState<"closed" | "entering" | "open" | "exiting">("closed");
  // Snapshot of what's currently rendered. While swapping (exit → enter), this
  // stays on the OUTGOING insights so they animate out cleanly even after the
  // props.insights have already updated to the new content.
  const [renderedInsights, setRenderedInsights] = useState(insights);
  const [renderedScopeLabel, setRenderedScopeLabel] = useState<string | null | undefined>(scopeLabel);
  const swapKeyRef = useRef(swapKey);
  // Latest "should display next" content; the in-flight exit timer reads this
  // when it fires, so a second pill click during an exit just updates the
  // pending content without restarting the animation.
  const pendingRef = useRef<{
    insights: ForecastInsight[];
    scopeLabel: string | null | undefined;
    swapKey: string;
  } | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;
  const renderedLengthRef = useRef(renderedInsights.length);
  renderedLengthRef.current = renderedInsights.length;

  const animDuration = (n: number) =>
    Math.max(1, n) * INSIGHT_STAGGER_MS + INSIGHT_ANIM_MS;

  useEffect(() => {
    clearTimeout(timeoutRef.current);
    const currentPhase = phaseRef.current;

    if (!open) {
      // Closing — cancel any pending swap and exit the visible items.
      pendingRef.current = null;
      if (currentPhase === "open" || currentPhase === "entering") {
        setPhase("exiting");
        timeoutRef.current = setTimeout(
          () => setPhase("closed"),
          animDuration(renderedLengthRef.current),
        );
      }
      return;
    }

    const swapKeyChanged = swapKey !== swapKeyRef.current;

    if (currentPhase === "closed") {
      // First open after being hidden — show new content and animate in.
      setRenderedInsights(insights);
      setRenderedScopeLabel(scopeLabel);
      swapKeyRef.current = swapKey;
      pendingRef.current = null;
      setPhase("entering");
      timeoutRef.current = setTimeout(
        () => setPhase("open"),
        animDuration(insights.length),
      );
    } else if (swapKeyChanged) {
      // Visible and the active scope changed — exit current set, then swap.
      pendingRef.current = { insights, scopeLabel, swapKey };
      if (currentPhase === "open" || currentPhase === "entering") {
        setPhase("exiting");
        timeoutRef.current = setTimeout(() => {
          const next = pendingRef.current;
          if (!next) return;
          setRenderedInsights(next.insights);
          setRenderedScopeLabel(next.scopeLabel);
          swapKeyRef.current = next.swapKey;
          pendingRef.current = null;
          setPhase("entering");
          timeoutRef.current = setTimeout(
            () => setPhase("open"),
            animDuration(next.insights.length),
          );
        }, animDuration(renderedLengthRef.current));
      }
      // If already in "exiting" from a previous swap, the in-flight timer
      // will pick up the latest pendingRef when it fires.
    }

    return () => clearTimeout(timeoutRef.current);
  }, [open, swapKey, insights, scopeLabel]);

  const showItems = phase !== "closed";

  return (
    <div>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="ai-insights-trigger flex items-center gap-1.5 text-sm font-medium text-foreground hover:text-primary transition-colors w-full px-3 py-2 rounded-lg border border-transparent"
      >
        <Sparkles className="h-3.5 w-3.5 ai-insights-sparkle" />
        <span className="ai-insights-label" data-text={`AI Insights (${insights.length})`}>
          {`AI Insights (${insights.length})`.split("").map((char, i) => (
            <span
              key={i}
              className="ai-insights-letter"
              style={{ animationDelay: `${i * 30}ms` }}
            >
              {char === " " ? " " : char}
            </span>
          ))}
        </span>
        <ChevronDown
          className={`h-3.5 w-3.5 ml-auto transition-transform duration-300 ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {showItems && (
        <div className="pt-2 space-y-2">
          {renderedScopeLabel && (
            <div className="text-xs text-muted-foreground italic px-1">
              {renderedScopeLabel}
            </div>
          )}
          {renderedInsights.map((insight, i) => {
            const Icon = insightIcons[insight.type] || Info;
            const colors =
              insightColors[insight.type] || "text-muted-foreground bg-muted border-border";

            // Entering: stagger top→bottom. Exiting: stagger bottom→top (reverse).
            const enterDelay = i * INSIGHT_STAGGER_MS;
            const exitDelay = (renderedInsights.length - 1 - i) * INSIGHT_STAGGER_MS;

            const isEntering = phase === "entering";
            const isExiting = phase === "exiting";

            const animStyle: React.CSSProperties =
              isEntering
                ? {
                    animation: `insight-enter ${INSIGHT_ANIM_MS}ms cubic-bezier(0.22,0.61,0.36,1) ${enterDelay}ms backwards`,
                  }
                : isExiting
                  ? {
                      animation: `insight-exit ${INSIGHT_ANIM_MS}ms cubic-bezier(0.32,0,0.67,0) ${exitDelay}ms forwards`,
                    }
                  : {};

            // Confidence dot — small visual cue for self-rated reliability.
            // 80+ = high (filled), 60-79 = medium, <60 = low (outline). Tooltip
            // exposes the exact number for users who want to dig in.
            const confidence = insight.confidence ?? 50;
            const dotClass =
              confidence >= 80
                ? "bg-current"
                : confidence >= 60
                  ? "bg-current opacity-60"
                  : "bg-transparent border border-current opacity-50";

            return (
              <div
                key={i}
                className={`flex items-start gap-2.5 p-2.5 rounded-lg border text-xs ${colors}`}
                style={animStyle}
              >
                <Icon className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0">
                  <span className="font-semibold">{insight.title}</span>
                  <span className="opacity-80"> — {insight.detail}</span>
                </div>
                <span
                  title={`Confidence: ${confidence}/100`}
                  className={`shrink-0 mt-1.5 h-2 w-2 rounded-full ${dotClass}`}
                  aria-label={`Confidence ${confidence} out of 100`}
                />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// --- Component ---

interface ForecastingPanelProps {
  onEditSetup: () => void;
  onCreateIncentive: () => void;
  navigateTo?: string;
}

export function ForecastingPanel({
  onEditSetup,
  onCreateIncentive,
  navigateTo,
}: ForecastingPanelProps) {
  const navigate = useNavigate();
  const { state } = useBuilder();
  const { data: locationHierarchy } = useLocationHierarchy();
  const createMutation = useCreateIncentive();
  const updateMutation = useUpdateIncentive();
  const uploadDocsMutation = useUploadIncentiveDocuments();
  const [viewMode, setViewMode] = useState<ViewMode>("GLOBAL");
  // locationValueId of the parent whose sub-location popover is currently open;
  // null when no popover is open. Tracked here (not inside each Popover) so
  // selecting a child can dismiss the popover synchronously.
  const [openPopoverParentId, setOpenPopoverParentId] = useState<string | null>(null);
  const [showDocUploadDialog, setShowDocUploadDialog] = useState(false);
  const [pendingDocs, setPendingDocs] = useState<DocumentInput[]>([]);
  const [pendingFiles, setPendingFiles] = useState<PendingUpload[]>([]);
  const [showSaveTemplateDialog, setShowSaveTemplateDialog] = useState(false);
  const [templateName, setTemplateName] = useState("");
  const [templateDescription, setTemplateDescription] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [showInsights, setShowInsights] = useState(false);
  const [showReasoning, setShowReasoning] = useState(false);

  // Forecast state
  const [forecast, setForecast] = useState<IncentiveForecast | null>(null);
  const [forecastStatus, setForecastStatus] = useState<string>("");
  const [isForecasting, setIsForecasting] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  const totalBudget = useMemo(() => {
    const gb = state.budgetData.globalBudgets || {};
    const MONETARY_CURRENCIES = ["cash", "points"];
    let sum = 0;
    for (const [currency, amount] of Object.entries(gb)) {
      if (MONETARY_CURRENCIES.includes(currency) && amount) {
        sum += parseInt(amount) || 0;
      }
    }
    if (sum === 0 && state.budgetData.budget?.totalBudget) {
      sum = parseInt(state.budgetData.budget.totalBudget) || 0;
    }
    return sum || 0;
  }, [state.budgetData.globalBudgets, state.budgetData.budget?.totalBudget]);
  const isEditMode = !!state.editingIncentiveId;
  const incentiveName = state.basics.name || "Untitled Incentive";

  const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

  // Shared SSE reader — parses event stream from any POST endpoint
  const readSSEStream = useCallback(async (response: Response) => {
    if (!response.body) return;
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    // Persist across chunks so split reads don't lose event context
    let eventName = "";
    let eventData = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.replace(/\r$/, ""); // handle \r\n
        if (trimmed.startsWith("event:")) {
          eventName = trimmed.slice(6).trim();
        } else if (trimmed.startsWith("data:")) {
          eventData = trimmed.slice(5).trim();
        } else if (trimmed === "" && eventData) {
          try {
            const parsed = JSON.parse(eventData);
            if (import.meta.env.DEV) console.log(
              `[SSE] Event: ${eventName}`,
              eventName === "forecast" ? "(forecast data received)" : parsed,
            );
            if (eventName === "status") {
              setForecastStatus(parsed.message || "Processing...");
            } else if (eventName === "forecast") {
              const forecastData = parsed?.data || parsed;
              if (import.meta.env.DEV) console.log("[SSE] Setting forecast:", Object.keys(forecastData));
              setForecast(forecastData);
            } else if (eventName === "done") {
              // Keep the loader visible briefly so its progress bar can animate
              // from its hold-point to 100% before we unmount it.
              setTimeout(() => {
                setIsForecasting(false);
                setForecastStatus("");
              }, 400);
            } else if (eventName === "error") {
              setForecastStatus("");
              setIsForecasting(false);
              toast.error("Forecast failed", { description: parsed.message });
            }
          } catch {
            // Skip unparseable events
          }
          eventName = "";
          eventData = "";
        }
      }
    }
    setIsForecasting(false);
  }, []);

  // Trigger forecast for a saved incentive (edit mode)
  const generateForecast = useCallback(
    async (incentiveId: string) => {
      // Clear stale data so the loader doesn't think this run is already complete
      setForecast(null);
      setIsForecasting(true);
      setForecastStatus("Connecting...");
      try {
        const response = await fetch(
          `${baseURL}/incentives/${incentiveId}/forecast`,
          {
            method: "POST",
            credentials: "include",
          },
        );
        if (!response.ok) throw new Error(`Request failed: ${response.status}`);
        await readSSEStream(response);
      } catch {
        setIsForecasting(false);
        setForecastStatus("");
      }
    },
    [baseURL, readSSEStream],
  );

  // Trigger forecast preview for creation flows (no saved incentive)
  const generateForecastPreview = useCallback(async () => {
    // Clear stale data so the loader doesn't think this run is already complete
    setForecast(null);
    setIsForecasting(true);
    setForecastStatus("Connecting...");

    // Build preview request from builder state with full sales criteria
    const salesReqs = state.criteria?.salesRequirements || [];

    // Extract product categories and payout info from sales requirements
    const productSkus: string[] = [];
    let payoutType: string | null = null;
    let payoutAgainst: string | null = null;
    let maxPerDeal: string | null = null;
    const payoutBands: Array<{
      minAmount: string;
      maxAmount: string | null;
      payoutValue: string;
    }> = [];

    for (const req of salesReqs) {
      // Extract product SKUs from eligibility rules
      for (const group of req.eligibilityGroups || []) {
        for (const rule of group.rules || []) {
          if (rule.selectedProducts && rule.selectedProducts.length > 0) {
            productSkus.push(...rule.selectedProducts);
          }
        }
      }
      // Extract payout config from first requirement
      for (const payout of req.payouts || []) {
        if (!payoutType && payout.payoutType) payoutType = payout.payoutType;
        if (!payoutAgainst && payout.against) payoutAgainst = payout.against;
        if (!maxPerDeal && payout.maxPerDeal) maxPerDeal = payout.maxPerDeal;
        for (const band of payout.bands || []) {
          payoutBands.push({
            minAmount: band.minAmount || "0",
            maxAmount: band.maxAmount || null,
            payoutValue: band.payoutValue || "0",
          });
        }
      }
    }

    // Sum monetary budgets for the total
    const gb = state.budgetData.globalBudgets || {};
    const cashBudget =
      gb["cash"] || state.budgetData.budget?.totalBudget || "0";

    // Ship the full level-keyed eligibility scope so the backend resolver can
    // walk parent_id up to depth-0 ancestors and narrow the forecast to the
    // actual targeted population (Region → Country → State → ...). The flat
    // `regions` field is still sent for backward compatibility — when
    // `locationSelections` is present, the backend prefers it. The collision
    // hazard ("Georgia" the state vs the country) is handled by matching on
    // (level UUID, name) pairs rather than name-only.
    const topLevelId = locationHierarchy?.levels?.[0]?.id ?? null;
    const topLevelSelections = topLevelId
      ? (state.audience.locationSelections?.[topLevelId] ?? [])
      : [];
    const locationSelections: Record<string, string[]> = {};
    for (const [levelId, names] of Object.entries(
      state.audience.locationSelections ?? {},
    )) {
      if (names && names.length > 0) {
        locationSelections[levelId] = names;
      }
    }

    const previewBody = {
      incentiveType: state.basics.incentiveType || "SALES",
      regions: topLevelSelections,
      locationSelections:
        Object.keys(locationSelections).length > 0
          ? locationSelections
          : null,
      startDate: state.schedule.startDate || null,
      endDate: state.schedule.endDate || null,
      totalBudget: String(totalBudget || cashBudget),
      budgetCurrency: state.budgetData.budget?.currency || "cash",
      budgetMode:
        state.budgetData.budgetMode === "per-region" ? "PER_REGION" : "GLOBAL",
      selectedCurrencies: state.budgetData.selectedCurrencies || [],
      partnerTypes: state.audience.partnerTypes || [],
      maxPerPartner: state.budgetData.maxPerPartner || null,
      maxPerUser: state.budgetData.maxPerUser || null,
      productSkus: productSkus.length > 0 ? productSkus : null,
      payoutType,
      payoutAgainst,
      payoutBands: payoutBands.length > 0 ? payoutBands : null,
      maxPerDeal,
      regionBudgets:
        Object.keys(state.budgetData.regionBudgets || {}).length > 0
          ? state.budgetData.regionBudgets
          : null,
    };

    if (import.meta.env.DEV) console.log(
      "[ForecastPreview] Sending:",
      JSON.stringify(previewBody, null, 2),
    );
    try {
      const response = await fetch(`${baseURL}/incentives/forecast-preview`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(previewBody),
      });
      if (import.meta.env.DEV) console.log("[ForecastPreview] Response status:", response.status);
      if (!response.ok) {
        const errorText = await response.text();
        if (import.meta.env.DEV) console.error("[ForecastPreview] Error body:", errorText);
        throw new Error(`Request failed: ${response.status} - ${errorText}`);
      }
      await readSSEStream(response);
    } catch (err) {
      setIsForecasting(false);
      setForecastStatus("");
      if (import.meta.env.DEV) console.error("[ForecastPreview] Failed:", err);
    }
  }, [
    baseURL,
    readSSEStream,
    state.basics,
    state.audience,
    state.schedule,
    state.budgetData,
    state.criteria?.salesRequirements,
    totalBudget,
    locationHierarchy?.levels,
  ]);

  // Auto-trigger forecast on mount
  useEffect(() => {
    const incentiveId = state.editingIncentiveId;

    if (incentiveId) {
      // Edit mode — use saved incentive endpoint
      generateForecast(incentiveId);
    } else {
      // Creation mode — use preview endpoint with builder state
      generateForecastPreview();
    }

    const currentEventSource = eventSourceRef.current;
    return () => {
      currentEventSource?.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const locationBreakdownMap = useMemo(() => {
    const map = new Map<string, ForecastLocationBreakdown>();
    if (forecast?.locationBreakdown) {
      for (const lb of forecast.locationBreakdown) {
        map.set(lb.name, lb);
      }
    }
    return map;
  }, [forecast?.locationBreakdown]);

  // Top-level locations (parentId == null) get a pill in the toggle row.
  // Children are grouped by parent UUID so each top-level pill can expand
  // into a popover listing its descendants — keeps the pill row from
  // sprawling once Country / State / City data lands.
  const topLevelLocations = useMemo<ForecastLocationBreakdown[]>(() => {
    if (!forecast?.locationBreakdown) return [];
    return forecast.locationBreakdown.filter((lb) => !lb.parentId);
  }, [forecast]);

  const childrenByParent = useMemo<Map<string, ForecastLocationBreakdown[]>>(() => {
    const map = new Map<string, ForecastLocationBreakdown[]>();
    if (!forecast?.locationBreakdown) return map;
    for (const lb of forecast.locationBreakdown) {
      if (!lb.parentId) continue;
      const bucket = map.get(lb.parentId) ?? [];
      bucket.push(lb);
      map.set(lb.parentId, bucket);
    }
    return map;
  }, [forecast]);

  // When a child node is the active viewMode, surface its parent so the
  // "Viewing:" header can render the breadcrumb (e.g. "AMERICAS › United
  // States") and the parent pill can show a subtle has-selected-child mark.
  const activeBreakdown = locationBreakdownMap.get(viewMode);
  const activeParent = activeBreakdown?.parentId
    ? forecast?.locationBreakdown?.find(
        (lb) => lb.locationValueId === activeBreakdown.parentId,
      )
    : undefined;

  // Walk up to the depth-0 ancestor of the active node so we can pull its
  // top-level insights when the user has drilled into a child pill (e.g.
  // viewing United States → fall back to AMERICAS's insights).
  const activeTopLevel = useMemo<ForecastLocationBreakdown | undefined>(() => {
    if (!forecast?.locationBreakdown || !activeBreakdown) return undefined;
    let node: ForecastLocationBreakdown | undefined = activeBreakdown;
    while (node?.parentId) {
      const parent: ForecastLocationBreakdown | undefined =
        forecast.locationBreakdown.find(
          (lb) => lb.locationValueId === node!.parentId,
        );
      if (!parent) break;
      node = parent;
    }
    return node;
  }, [forecast, activeBreakdown]);

  // Pick the right insights for the current viewMode:
  // - GLOBAL → forecast.insights
  // - top-level pill → forecast.topLevelInsights[name]
  // - child pill → forecast.topLevelInsights[ancestor.name] with a scope label
  // The `insightSwapKey` identifies the *effective scope* — siblings sharing a
  // parent share a swap key (no animation when clicking US → Canada under the
  // same AMERICAS); only an actual scope change triggers exit-then-enter.
  const { displayedInsights, insightScopeLabel, insightSwapKey } = useMemo<{
    displayedInsights: ForecastInsight[];
    insightScopeLabel: string | null;
    insightSwapKey: string;
  }>(() => {
    if (!forecast) {
      return { displayedInsights: [], insightScopeLabel: null, insightSwapKey: "empty" };
    }
    if (viewMode === "GLOBAL") {
      return {
        displayedInsights: forecast.insights ?? [],
        insightScopeLabel: null,
        insightSwapKey: "GLOBAL",
      };
    }
    const tl = forecast.topLevelInsights;
    if (!tl) {
      return { displayedInsights: [], insightScopeLabel: null, insightSwapKey: "empty" };
    }
    if (tl[viewMode]) {
      return {
        displayedInsights: tl[viewMode] ?? [],
        insightScopeLabel: null,
        insightSwapKey: viewMode,
      };
    }
    if (activeTopLevel && tl[activeTopLevel.name]) {
      return {
        displayedInsights: tl[activeTopLevel.name] ?? [],
        insightScopeLabel: `Insights scoped to ${activeTopLevel.name}`,
        insightSwapKey: activeTopLevel.name,
      };
    }
    return { displayedInsights: [], insightScopeLabel: null, insightSwapKey: "empty" };
  }, [forecast, viewMode, activeTopLevel]);

  // Narrowing chip — surfaces deeper-than-Region eligibility picks so the user
  // can see that a region-grain forecast does account for their sub-region
  // selections (the resolver walks parent_id up to the depth-0 ancestor and
  // the AI prompt scales the regional baseline accordingly).
  const narrowing = useMemo(
    () =>
      getNarrowingDescriptor(
        viewMode,
        state.audience.locationSelections ?? {},
        locationHierarchy,
      ),
    [viewMode, state.audience.locationSelections, locationHierarchy],
  );

  // Color map for every node in the breakdown — top-level hues + descendant
  // shades within the same hue family.
  const locationColors = useMemo(
    () => buildLocationColors(forecast?.locationBreakdown),
    [forecast],
  );

  // Each location's share of GLOBAL bookings. Used for the "% of global" label
  // next to the active location. Denominator is the top-level total (children
  // sum to parents per the contract aggregation invariant, so any deeper level
  // still shares this baseline).
  const globalShareWeights = useMemo(() => {
    const weights: Record<string, number> = {};
    if (!forecast?.locationBreakdown) return weights;
    const topLevelTotal = forecast.locationBreakdown
      .filter((lb) => !lb.parentId)
      .reduce((sum, lb) => sum + (parseFloat(lb.netNewBookings || "0") || 0), 0);
    if (topLevelTotal <= 0) return weights;
    for (const lb of forecast.locationBreakdown) {
      weights[lb.name] =
        (parseFloat(lb.netNewBookings || "0") || 0) / topLevelTotal;
    }
    return weights;
  }, [forecast]);

  // Direct children of the currently-active node. These become the segments
  // for each metric card's bar; an empty array collapses the bar to a solid
  // fill (leaf node, or no children returned by the backend).
  const activeNodeChildren = useMemo<ForecastLocationBreakdown[]>(() => {
    if (!forecast?.locationBreakdown) return [];
    if (viewMode === "GLOBAL") {
      return forecast.locationBreakdown.filter((lb) => !lb.parentId);
    }
    const activeNode = forecast.locationBreakdown.find(
      (lb) => lb.name === viewMode,
    );
    if (!activeNode) return [];
    return forecast.locationBreakdown.filter(
      (lb) => lb.parentId === activeNode.locationValueId,
    );
  }, [forecast, viewMode]);

  // Per-metric segment weights. The contract guarantees children sum to parent
  // for each of these fields, so each card's segments naturally sum to 100%
  // of that card's headline number — Budget bar reflects budget distribution,
  // Deals bar reflects deal distribution, Bookings bar reflects bookings.
  const segmentWeights = useMemo(() => {
    const empty = { budget: {}, deals: {}, bookings: {} };
    if (activeNodeChildren.length === 0) return empty;

    function distribute(values: number[]): number[] {
      const total = values.reduce(
        (s, v) => s + (Number.isFinite(v) ? v : 0),
        0,
      );
      if (total <= 0) return values.map(() => 1 / values.length);
      return values.map((v) => (Number.isFinite(v) ? v : 0) / total);
    }

    const names = activeNodeChildren.map((c) => c.name);
    const budgetVals = activeNodeChildren.map(
      (c) => parseFloat(c.budgetPredictedSpend || "0") || 0,
    );
    const dealsVals = activeNodeChildren.map((c) => c.netNewDeals ?? 0);
    const bookingsVals = activeNodeChildren.map(
      (c) => parseFloat(c.netNewBookings || "0") || 0,
    );

    const budgetW = distribute(budgetVals);
    const dealsW = distribute(dealsVals);
    const bookingsW = distribute(bookingsVals);

    const budget: Record<string, number> = {};
    const deals: Record<string, number> = {};
    const bookings: Record<string, number> = {};
    names.forEach((n, i) => {
      budget[n] = budgetW[i] ?? 0;
      deals[n] = dealsW[i] ?? 0;
      bookings[n] = bookingsW[i] ?? 0;
    });
    return { budget, deals, bookings };
  }, [activeNodeChildren]);

  const currentData = useMemo(() => {
    if (forecast) {
      const globalUtilPct =
        forecast.locationBreakdown?.length > 0
          ? forecast.locationBreakdown.reduce(
              (sum, r) => sum + parseFloat(r.budgetUtilizedPct || "0"),
              0,
            ) / forecast.locationBreakdown.length
          : (parseFloat(forecast.estimatedTotalCost || "0") /
              Math.max(totalBudget, 1)) *
            100;

      const globalBudgetUtilized = (totalBudget * globalUtilPct) / 100;

      if (viewMode === "GLOBAL") {
        const cost = parseFloat(forecast.estimatedTotalCost || "0");
        const bookings = parseFloat(forecast.estimatedNetNewBookings || "0");
        const computedRoi = cost > 0 ? bookings / cost : 0;
        return {
          budgetUtilized: globalBudgetUtilized,
          netNewDeals: forecast.estimatedNetNewDeals || 0,
          netNewBookings: bookings,
          label: "Global",
          roi: computedRoi,
          utilizationPct: globalUtilPct,
        };
      }
      const lb = locationBreakdownMap.get(viewMode);
      if (lb) {
        const spend = parseFloat(lb.budgetPredictedSpend || "0");
        const bookings = parseFloat(lb.netNewBookings || "0");
        const computedRoi = spend > 0 ? bookings / spend : 0;
        return {
          budgetUtilized: spend,
          netNewDeals: lb.netNewDeals,
          netNewBookings: bookings,
          label: viewMode as string,
          roi: computedRoi,
          utilizationPct: parseFloat(lb.budgetUtilizedPct || "0"),
        };
      }
    }
    return {
      budgetUtilized: 0,
      netNewDeals: 0,
      netNewBookings: 0,
      label: viewMode === "GLOBAL" ? "Global" : (viewMode as string),
      roi: 0,
      utilizationPct: 0,
    };
  }, [forecast, totalBudget, viewMode, locationBreakdownMap]);
  const confidenceScore = forecast
    ? parseFloat(forecast.confidenceScore || "0")
    : 0;
  const confidenceColor =
    confidenceScore >= 70
      ? "text-[hsl(var(--success))]"
      : confidenceScore >= 50
        ? "text-[hsl(var(--warning))]"
        : "text-destructive";

  async function handleActivate(
    documents?: DocumentInput[],
    files?: PendingUpload[],
  ) {
    setIsSaving(true);
    try {
      const docs = documents ?? pendingDocs;
      const uploadFiles = files ?? pendingFiles;
      let incentiveId: string;
      const hasFilesToUpload = uploadFiles.length > 0;

      if (isEditMode) {
        const request = buildUpdateRequest(state, locationHierarchy);
        if (docs.length > 0 && !hasFilesToUpload) request.documents = docs;
        await updateMutation.mutateAsync({
          id: state.editingIncentiveId!,
          data: request,
        });
        incentiveId = state.editingIncentiveId!;
        toast.success("Incentive Updated!", {
          description: `"${incentiveName}" has been saved with your changes.`,
        });
      } else {
        const request = buildCreateRequest(state, locationHierarchy);
        if (docs.length > 0 && !hasFilesToUpload) request.documents = docs;
        const result = await createMutation.mutateAsync(request);
        incentiveId = result.id;
        toast.success("Incentive Created!", {
          description: `"${incentiveName}" has been created successfully.`,
        });
        setPendingDocs([]);
        setPendingFiles([]);
      }

      if (uploadFiles.length > 0) {
        await uploadDocsMutation.mutateAsync({
          incentiveId,
          files: uploadFiles,
        });
      }
      setShowDocUploadDialog(false);

      if (navigateTo) {
        navigate(navigateTo, { replace: true });
      }
      onCreateIncentive();
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Please try again.";
      toast.error("Failed to save incentive", { description: message });
    } finally {
      setIsSaving(false);
    }
  }

  function handleSaveTemplate() {
    if (!templateName.trim()) {
      toast.error("Template name required", {
        description: "Please enter a name for your template.",
      });
      return;
    }
    toast.success("Template Saved", {
      description: `"${templateName}" has been saved to your template library.`,
    });
    setShowSaveTemplateDialog(false);
    setTemplateName("");
    setTemplateDescription("");
  }

  return (
    <div className="flex flex-col h-full">
      {/* Full-panel loading overlay — replaces all content while forecasting */}
      {isForecasting ? (
        <ForecastingLoader
          status={forecastStatus}
          isComplete={forecast !== null}
          className="flex-1"
        />
      ) : (
        <Card className="border-primary/30 bg-gradient-to-br from-primary/5 to-primary/10 animate-fade-in flex-1 flex flex-col overflow-hidden">
          <CardHeader className="pb-4">
            <div className="flex items-center justify-between">
              <div className="space-y-1.5">
                <CardTitle className="text-foreground flex items-center gap-2">
                  <Sparkles className="h-5 w-5 text-primary" />
                  AI Forecasting
                  {forecast && (
                    <span
                      className={`text-xs font-medium px-2 py-0.5 rounded-full ${confidenceColor} bg-opacity-10`}
                    >
                      {confidenceScore.toFixed(0)}% confidence
                    </span>
                  )}
                </CardTitle>
                <CardDescription>
                  Predicted performance metrics based on your incentive setup
                </CardDescription>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={onEditSetup}
                className="shrink-0"
              >
                <Settings className="h-4 w-4 mr-1" />
                Edit Setup
              </Button>
            </div>

            {/* Location toggle row — only top-level (depth-0) locations get a
                pill; children expand from each pill via a popover so the row
                stays compact even when many countries / states are forecast. */}
            <div className="flex flex-wrap items-center gap-1.5 pt-3">
              <button
                onClick={() => setViewMode("GLOBAL")}
                aria-pressed={viewMode === "GLOBAL"}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-[background-color,color,box-shadow] ${
                  viewMode === "GLOBAL"
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "bg-background/60 text-muted-foreground hover:text-foreground hover:bg-muted/50 border border-border/50"
                }`}
              >
                <Globe className="h-3.5 w-3.5" />
                <span>Global</span>
              </button>
              {topLevelLocations.map((parent) => {
                const children = childrenByParent.get(parent.locationValueId) ?? [];
                const isSelected = viewMode === parent.name;
                const hasSelectedChild = children.some((c) => c.name === viewMode);
                const baseClasses =
                  "flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-[background-color,color,box-shadow]";
                const selectedClasses = "bg-primary text-primary-foreground shadow-sm";
                const childSelectedClasses =
                  "bg-background/60 text-foreground border border-primary/40 ring-1 ring-primary/20";
                const idleClasses =
                  "bg-background/60 text-muted-foreground hover:text-foreground hover:bg-muted/50 border border-border/50";
                const stateClasses = isSelected
                  ? selectedClasses
                  : hasSelectedChild
                    ? childSelectedClasses
                    : idleClasses;

                if (children.length === 0) {
                  return (
                    <button
                      key={parent.locationValueId}
                      onClick={() => setViewMode(parent.name)}
                      aria-pressed={isSelected}
                      className={`${baseClasses} ${stateClasses}`}
                    >
                      <span className={`h-2 w-2 rounded-full ${locationColors[parent.name]}`} />
                      <span>{parent.name}</span>
                    </button>
                  );
                }

                return (
                  <div key={parent.locationValueId} className="flex items-stretch">
                    <button
                      onClick={() => setViewMode(parent.name)}
                      aria-pressed={isSelected || hasSelectedChild}
                      className={`${baseClasses} ${stateClasses} rounded-r-none pr-2`}
                    >
                      <span className={`h-2 w-2 rounded-full ${locationColors[parent.name]}`} />
                      <span>{parent.name}</span>
                    </button>
                    <Popover
                      open={openPopoverParentId === parent.locationValueId}
                      onOpenChange={(open) =>
                        setOpenPopoverParentId(open ? parent.locationValueId : null)
                      }
                    >
                      <PopoverTrigger asChild>
                        <button
                          aria-label={`Show ${parent.name} sub-locations`}
                          className={`${baseClasses} ${stateClasses} rounded-l-none border-l-0 pl-1.5 pr-2`}
                        >
                          <ChevronDown className="h-3.5 w-3.5" />
                        </button>
                      </PopoverTrigger>
                      <PopoverContent
                        align="start"
                        className="w-56 p-1"
                      >
                        <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
                          {parent.name} sub-locations
                        </div>
                        <div className="flex flex-col">
                          {children.map((child) => {
                            const childSelected = viewMode === child.name;
                            return (
                              <button
                                key={child.locationValueId}
                                onClick={() => {
                                  setViewMode(child.name);
                                  setOpenPopoverParentId(null);
                                }}
                                className={`flex items-center gap-2 px-2 py-1.5 rounded-sm text-sm text-left transition-colors ${
                                  childSelected
                                    ? "bg-primary/10 text-foreground font-medium"
                                    : "text-muted-foreground hover:bg-muted hover:text-foreground"
                                }`}
                              >
                                <span
                                  className={`h-1.5 w-1.5 rounded-full ${
                                    locationColors[child.name] ?? "bg-muted-foreground/40"
                                  }`}
                                />
                                <span className="flex-1 truncate">{child.name}</span>
                              </button>
                            );
                          })}
                        </div>
                      </PopoverContent>
                    </Popover>
                  </div>
                );
              })}
            </div>
          </CardHeader>

          <CardContent className="space-y-5 flex-1 overflow-y-auto min-h-0">
            <>
              {/* Current view label — shows a breadcrumb when a child node is
                  selected (e.g. "AMERICAS › United States") so users always know
                  where the metrics below are scoped. */}
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">Viewing:</span>
                <span className="font-semibold text-foreground flex items-center gap-1.5">
                  {viewMode === "GLOBAL" ? (
                    <Globe className="h-4 w-4 text-primary" />
                  ) : (
                    <span
                      className={`h-2.5 w-2.5 rounded-full ${locationColors[viewMode]}`}
                    />
                  )}
                  {activeParent ? (
                    <>
                      <span className="text-muted-foreground font-normal">
                        {activeParent.name}
                      </span>
                      <span className="text-muted-foreground font-normal">›</span>
                      <span>{currentData.label}</span>
                    </>
                  ) : (
                    <span>{currentData.label}</span>
                  )}
                  {viewMode !== "GLOBAL" && (
                    <span className="text-muted-foreground font-normal">
                      ({Math.round((globalShareWeights[viewMode] || 0) * 100)}%
                      of global)
                    </span>
                  )}
                </span>
              </div>

              {/* Narrowing chip — visible when Participant Eligibility includes
                  picks deeper than Region. Tells the user the region-grain
                  metrics below already account for their sub-region scope. */}
              {narrowing && (
                <NarrowingChip narrowing={narrowing} />
              )}

              {/* 3 Metric Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {/* Card 1: Budget Utilized */}
                <div className="rounded-xl border bg-card p-4 min-w-0 flex flex-col">
                  <div className="flex flex-col">
                    <div className="flex items-center justify-center h-12 w-12 rounded-xl bg-primary/10 mb-3">
                      <DollarSign className="h-6 w-6 text-primary" />
                    </div>
                    <p className="text-xs font-medium text-muted-foreground mb-1">
                      Budget Utilized
                    </p>
                    <p className="text-3xl font-bold tracking-tight mb-1 truncate">
                      <SlotDropNumber
                        value={formatCompactCurrency(currentData.budgetUtilized)}
                      />
                    </p>
                    <p className="text-xs text-muted-foreground mb-3">
                      of {formatCompactCurrency(totalBudget)} budget
                    </p>
                    <RegionProgressBar
                      segments={activeNodeChildren}
                      weights={segmentWeights.budget}
                      locationColors={locationColors}
                      fallbackColor={
                        viewMode === "GLOBAL"
                          ? "bg-primary"
                          : (locationColors[viewMode] ?? "bg-primary")
                      }
                    />
                    <div className="flex items-center gap-1 text-xs text-[hsl(var(--success))] mt-2">
                      <TrendingUp className="h-3 w-3" />
                      <span>
                        {currentData.utilizationPct.toFixed(0)}% utilization
                      </span>
                    </div>
                  </div>
                </div>

                {/* Card 2: Net New Deals */}
                <div className="rounded-xl border bg-card p-4 min-w-0 flex flex-col">
                  <div className="flex flex-col">
                    <div className="flex items-center justify-center h-12 w-12 rounded-xl bg-[hsl(var(--success)/0.1)] mb-3">
                      <CheckCircle className="h-6 w-6 text-[hsl(var(--success))]" />
                    </div>
                    <p className="text-xs font-medium text-muted-foreground mb-1">
                      Net New Deals
                    </p>
                    <p className="text-3xl font-bold tracking-tight mb-1 truncate">
                      <SlotDropNumber value={String(currentData.netNewDeals)} />
                    </p>
                    <p className="text-xs text-muted-foreground mb-3">
                      predicted closed deals
                    </p>
                    <RegionProgressBar
                      segments={activeNodeChildren}
                      weights={segmentWeights.deals}
                      locationColors={locationColors}
                      fallbackColor={
                        viewMode === "GLOBAL"
                          ? "bg-primary"
                          : (locationColors[viewMode] ?? "bg-primary")
                      }
                    />
                    <div className="text-xs text-muted-foreground mt-2">
                      from this incentive
                    </div>
                  </div>
                </div>

                {/* Card 3: Net New Bookings */}
                <div className="rounded-xl border bg-card p-4 min-w-0 flex flex-col">
                  <div className="flex flex-col">
                    <div className="flex items-center justify-center h-12 w-12 rounded-xl bg-primary/10 mb-3">
                      <Users className="h-6 w-6 text-primary" />
                    </div>
                    <p className="text-xs font-medium text-muted-foreground mb-1">
                      Net New Bookings
                    </p>
                    <p className="text-3xl font-bold tracking-tight mb-1 truncate">
                      <SlotDropNumber
                        value={formatCompactCurrency(currentData.netNewBookings)}
                      />
                    </p>
                    <p className="text-xs text-muted-foreground mb-3">
                      predicted revenue
                    </p>
                    <RegionProgressBar
                      segments={activeNodeChildren}
                      weights={segmentWeights.bookings}
                      locationColors={locationColors}
                      fallbackColor={
                        viewMode === "GLOBAL"
                          ? "bg-primary"
                          : (locationColors[viewMode] ?? "bg-primary")
                      }
                    />
                    <div className="flex items-center gap-1 text-xs text-[hsl(var(--success))] mt-2">
                      <TrendingUp className="h-3 w-3" />
                      <span>
                        {currentData.roi
                          ? `${currentData.roi.toFixed(1)}x`
                          : "0x"}{" "}
                        ROI
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* AI Insights — swap based on active pill (GLOBAL / top-level / child) */}
              {displayedInsights.length > 0 && (
                <InsightsSection
                  insights={displayedInsights}
                  scopeLabel={insightScopeLabel}
                  swapKey={insightSwapKey}
                  open={showInsights}
                  onToggle={() => setShowInsights((v) => !v)}
                />
              )}

              {/* Reasoning */}
              {forecast?.reasoning && (
                <Collapsible
                  open={showReasoning}
                  onOpenChange={setShowReasoning}
                >
                  <CollapsibleTrigger className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors w-full py-1">
                    <Info className="h-3 w-3" />
                    How this was calculated
                    <ChevronDown
                      className={`h-3 w-3 ml-auto transition-transform ${showReasoning ? "rotate-180" : ""}`}
                    />
                  </CollapsibleTrigger>
                  <CollapsibleContent className="pt-1">
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {forecast.reasoning}
                    </p>
                  </CollapsibleContent>
                </Collapsible>
              )}

              {/* Region Legend (Global view only) — top-level locations with
                  their share of global bookings. */}
              {viewMode === "GLOBAL" && topLevelLocations.length > 0 && (
                <div className="flex flex-wrap gap-3 justify-center pt-1">
                  {topLevelLocations.map((parent) => (
                    <button
                      key={parent.locationValueId}
                      onClick={() => setViewMode(parent.name)}
                      className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors px-3 py-1.5 rounded-md hover:bg-muted/50"
                    >
                      <span
                        className={`h-2.5 w-2.5 rounded-full ${locationColors[parent.name]}`}
                      />
                      <span>{parent.name}</span>
                      <span className="text-xs opacity-70">
                        (
                        {Math.round(
                          (globalShareWeights[parent.name] || 0) * 100,
                        )}
                        %)
                      </span>
                    </button>
                  ))}
                </div>
              )}

              {/* Footer note */}
              <div className="text-xs text-muted-foreground text-center pt-2 border-t border-border/50">
                Predictions based on historical data and incentive parameters
              </div>
            </>
          </CardContent>
        </Card>
      )}

      {/* Action buttons below the card */}
      <div className="flex gap-3 pt-4">
        <Button
          className="flex-1 bg-gradient-to-r from-primary to-primary-light hover:opacity-90 transition-opacity"
          onClick={() => setShowDocUploadDialog(true)}
          disabled={isSaving}
        >
          {isSaving ? (
            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
          ) : (
            <CheckCircle className="h-4 w-4 mr-2" />
          )}
          {isSaving
            ? "Saving..."
            : isEditMode
              ? "Update Incentive"
              : "Create Incentive"}
        </Button>
        <Button
          variant="outline"
          onClick={() => setShowSaveTemplateDialog(true)}
        >
          <Bookmark className="h-4 w-4 mr-2" />
          Save as Template
        </Button>
      </div>

      {/* Document Upload Dialog */}
      <DocumentUploadDialog
        open={showDocUploadDialog}
        onOpenChange={setShowDocUploadDialog}
        engagementType={state.basics.incentiveType || undefined}
        isSubmitting={isSaving}
        isEditMode={isEditMode}
        existingDocuments={state.existingDocuments}
        onComplete={(documents, files) => {
          handleActivate(documents, files);
        }}
      />

      {/* Save Template Dialog */}
      <AlertDialog
        open={showSaveTemplateDialog}
        onOpenChange={setShowSaveTemplateDialog}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Save as Template</AlertDialogTitle>
            <AlertDialogDescription>
              Save this incentive configuration for future use
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="template-name">Template Name</Label>
              <Input
                id="template-name"
                placeholder="e.g., Q2 APAC Growth Program"
                value={templateName}
                onChange={(e) => setTemplateName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="template-description">
                Description (Optional)
              </Label>
              <Textarea
                id="template-description"
                placeholder="Brief description of this template..."
                value={templateDescription}
                onChange={(e) => setTemplateDescription(e.target.value)}
                rows={3}
              />
            </div>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleSaveTemplate}>
              Save Template
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/** Segmented progress bar — splits into one segment per direct child of the
 *  active node (any depth in the location hierarchy). Falls back to a solid
 *  bar in `fallbackColor` when the active node is a leaf. */
function RegionProgressBar({
  segments,
  weights,
  locationColors,
  fallbackColor,
}: {
  segments: ForecastLocationBreakdown[];
  weights: Record<string, number>;
  locationColors: Record<string, string>;
  fallbackColor: string;
}) {
  return (
    <div className="w-full h-2 bg-muted rounded-full overflow-hidden">
      {segments.length > 0 ? (
        <div className="flex h-full w-full">
          {segments.map((seg) => (
            <div
              key={seg.locationValueId}
              className={`${locationColors[seg.name] ?? "bg-blue-500"} h-full`}
              style={{ width: `${(weights[seg.name] || 0) * 100}%` }}
            />
          ))}
        </div>
      ) : (
        <div className={`h-full w-full ${fallbackColor}`} />
      )}
    </div>
  );
}

/**
 * Sub-line under the "Viewing:" label that tells the user the region-grain
 * forecast already accounts for their deeper-than-Region picks (e.g.
 * Country/State narrowing).
 */
function NarrowingChip({
  narrowing,
}: {
  narrowing: NonNullable<ReturnType<typeof getNarrowingDescriptor>>;
}) {
  let text: string;
  if (narrowing.kind === "region-narrowed") {
    text =
      "Narrowed to - " +
      narrowing.deeperPicks
        .map((p) => `${p.levelName}: ${p.names.join(", ")}`)
        .join(" · ");
  } else {
    text =
      "Targeting narrowed within: " + narrowing.narrowedRegions.join(", ");
  }
  return (
    <div className="flex items-center gap-1.5 -mt-3 text-xs text-muted-foreground">
      <Crosshair className="h-3.5 w-3.5 shrink-0" />
      <span className="truncate" title={text}>
        {text}
      </span>
    </div>
  );
}
