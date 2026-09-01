import { useState, useMemo, useRef, useCallback } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Card } from "@/components/ui/card";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Search,
  X,
  GripVertical,
  Send,
  GraduationCap,
  ClipboardList,
  ArrowDown,
  Shuffle,
  ListOrdered,
  ChevronDown,
  Plus,
} from "lucide-react";
import { useIncentives } from "@/hooks/useIncentiveApi";
import { mockIncentives } from "@/data/mockIncentives";
import { INCENTIVE_TYPE_LABELS } from "@/types/incentive.types";
import type {
  JourneyStage,
  IncentiveResponse,
  IncentiveStatus,
} from "@/types/incentive.types";
import {
  statusLabels,
  statusColors,
} from "@/components/view-incentives/incentive-detail-shared";
import { cn } from "@/lib/utils";

const typeIcon: Record<string, React.ReactNode> = {
  SALES: <Send className="h-4 w-4" />,
  TRAINING: <GraduationCap className="h-4 w-4" />,
  ACTIVITY: <ClipboardList className="h-4 w-4" />,
};

const typeColor: Record<string, string> = {
  SALES: "text-primary",
  TRAINING: "text-amber-500",
  ACTIVITY: "text-blue-500",
};

const categoryLabels: Record<string, string> = {
  SALES: "Sales",
  TRAINING: "Training",
  ACTIVITY: "Activity",
};

export function JourneyStageEditor() {
  const { state, dispatch } = useBuilder();
  const stages = state.criteria.journeyStages;
  const isSequential = state.criteria.journeySequential;

  // Dropdown state
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("all");

  // Drag state
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const dragStartY = useRef(0);
  const [dragOffset, setDragOffset] = useState(0);
  const itemRects = useRef<DOMRect[]>([]);
  const listRef = useRef<HTMLDivElement>(null);

  // Fetch from API, fall back to mock data
  const { data: apiData, isError } = useIncentives({ pageSize: 100 });
  const allIncentives = apiData && !isError ? apiData.data : mockIncentives;

  const availableIncentives = useMemo(
    () =>
      allIncentives.filter(
        (inc) => inc.incentiveType !== "JOURNEY" && inc.status !== "INACTIVE",
      ),
    [allIncentives],
  );

  const selectedIds = useMemo(
    () => new Set(stages.map((s) => s.linkedIncentiveId)),
    [stages],
  );

  const categories = useMemo(() => {
    const cats = new Set(availableIncentives.map((inc) => inc.incentiveType));
    return Array.from(cats).sort();
  }, [availableIncentives]);

  const filteredIncentives = useMemo(
    () =>
      availableIncentives
        .filter((inc) => !selectedIds.has(inc.id))
        .filter(
          (inc) =>
            categoryFilter === "all" || inc.incentiveType === categoryFilter,
        )
        .filter(
          (inc) =>
            !searchQuery ||
            inc.name.toLowerCase().includes(searchQuery.toLowerCase()),
        ),
    [availableIncentives, selectedIds, searchQuery, categoryFilter],
  );

  const selectedIncentives = stages
    .map((s) => ({
      stage: s,
      incentive: availableIncentives.find(
        (inc) => inc.id === s.linkedIncentiveId,
      ),
    }))
    .filter((x) => x.incentive) as {
    stage: JourneyStage;
    incentive: IncentiveResponse;
  }[];

  // eslint-disable-next-line react-hooks/exhaustive-deps -- stable identity not needed; used only in drag handler
  function setStages(updater: (prev: JourneyStage[]) => JourneyStage[]) {
    dispatch({
      type: "UPDATE_CRITERIA",
      payload: { journeyStages: updater(stages) },
    });
  }

  function addStage(incentiveId: string) {
    setStages((prev) => [
      ...prev,
      { linkedIncentiveId: incentiveId, sortOrder: prev.length + 1 },
    ]);
  }

  function removeStage(incentiveId: string) {
    setStages((prev) =>
      prev
        .filter((s) => s.linkedIncentiveId !== incentiveId)
        .map((s, i) => ({ ...s, sortOrder: i + 1 })),
    );
  }

  // Pointer-based drag for live reordering
  const handlePointerDown = useCallback(
    (e: React.PointerEvent, index: number) => {
      if (!isSequential) return;
      e.preventDefault();
      (e.target as HTMLElement).setPointerCapture(e.pointerId);
      dragStartY.current = e.clientY;
      setDragIndex(index);
      setDragOffset(0);
      if (listRef.current) {
        const items = listRef.current.querySelectorAll("[data-stage-item]");
        itemRects.current = Array.from(items).map((el) =>
          el.getBoundingClientRect(),
        );
      }
    },
    [isSequential],
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      if (dragIndex === null) return;
      const offset = e.clientY - dragStartY.current;
      setDragOffset(offset);

      const currentCenter =
        (itemRects.current[dragIndex]?.top ?? 0) +
        (itemRects.current[dragIndex]?.height ?? 0) / 2 +
        offset;

      let targetIndex = dragIndex;
      for (let i = 0; i < itemRects.current.length; i++) {
        if (i === dragIndex) continue;
        const rect = itemRects.current[i];
        if (!rect) continue;
        const center = rect.top + rect.height / 2;
        if (dragIndex < i && currentCenter > center) {
          targetIndex = i;
        } else if (dragIndex > i && currentCenter < center) {
          targetIndex = i;
          break;
        }
      }

      if (targetIndex !== dragIndex) {
        setStages((prev) => {
          const next = [...prev];
          const removed = next.splice(dragIndex, 1)[0];
          if (!removed) return prev;
          next.splice(targetIndex, 0, removed);
          return next.map((s, i) => ({ ...s, sortOrder: i + 1 }));
        });
        dragStartY.current = e.clientY;
        setDragOffset(0);
        setDragIndex(targetIndex);
        requestAnimationFrame(() => {
          if (listRef.current) {
            const items = listRef.current.querySelectorAll("[data-stage-item]");
            itemRects.current = Array.from(items).map((el) =>
              el.getBoundingClientRect(),
            );
          }
        });
      }
    },
    [dragIndex, setStages],
  );

  const handlePointerUp = useCallback(() => {
    setDragIndex(null);
    setDragOffset(0);
  }, []);

  return (
    <div className="space-y-4">
      {/* Sequential toggle */}
      <Card className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {isSequential ? (
              <ListOrdered className="h-5 w-5 text-indigo-500" />
            ) : (
              <Shuffle className="h-5 w-5 text-emerald-500" />
            )}
            <div>
              <Label className="text-sm font-medium">
                Stage Completion Order
              </Label>
              <p className="text-xs text-muted-foreground">
                {isSequential
                  ? "Partners must complete stages in the defined order"
                  : "Partners can complete stages in any order"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <span
              className={
                isSequential
                  ? "text-foreground font-medium"
                  : "text-muted-foreground"
              }
            >
              Sequential
            </span>
            <Switch
              checked={!isSequential}
              onCheckedChange={(v) =>
                dispatch({
                  type: "UPDATE_CRITERIA",
                  payload: { journeySequential: !v },
                })
              }
            />
            <span
              className={
                !isSequential
                  ? "text-foreground font-medium"
                  : "text-muted-foreground"
              }
            >
              Any Order
            </span>
          </div>
        </div>
      </Card>

      {/* Selected stages - drag to reorder */}
      {selectedIncentives.length > 0 && (
        <div className="space-y-2">
          <Label className="text-sm font-medium">
            Selected Stages ({selectedIncentives.length})
            {isSequential && (
              <span className="text-xs text-muted-foreground ml-2">
                Drag to reorder
              </span>
            )}
          </Label>
          <div
            ref={listRef}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
          >
            {selectedIncentives.map(({ stage, incentive }, index) => (
              <div
                key={stage.linkedIncentiveId}
                data-stage-item
                style={{
                  transform:
                    dragIndex === index
                      ? `translateY(${dragOffset}px)`
                      : undefined,
                  transition:
                    dragIndex === index ? "none" : "transform 0.2s ease",
                  position: "relative",
                  zIndex: dragIndex === index ? 10 : 1,
                }}
              >
                <Card
                  className={cn(
                    "p-3 border-primary/20 bg-primary/5",
                    isSequential && "cursor-grab active:cursor-grabbing",
                    dragIndex === index && "shadow-lg scale-[1.02] opacity-90",
                    dragIndex !== null &&
                      dragIndex !== index &&
                      "transition-transform duration-200",
                  )}
                  onPointerDown={(e) => handlePointerDown(e, index)}
                >
                  <div className="flex items-center gap-3">
                    {isSequential && (
                      <GripVertical className="h-4 w-4 text-muted-foreground shrink-0" />
                    )}
                    <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-semibold shrink-0">
                      {isSequential ? stage.sortOrder : "\u2022"}
                    </div>
                    <div
                      className={`shrink-0 ${typeColor[incentive.incentiveType] ?? ""}`}
                    >
                      {typeIcon[incentive.incentiveType]}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground truncate">
                        {incentive.name}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {INCENTIVE_TYPE_LABELS[incentive.incentiveType]}
                        {incentive.startDate &&
                          ` \u00B7 ${incentive.startDate.slice(0, 10)}`}
                        {incentive.endDate &&
                          ` \u2192 ${incentive.endDate.slice(0, 10)}`}
                      </p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
                      onClick={() => removeStage(stage.linkedIncentiveId)}
                      onPointerDown={(e) => e.stopPropagation()}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                </Card>
                {isSequential && index < selectedIncentives.length - 1 && (
                  <div className="flex justify-center py-0.5">
                    <ArrowDown className="h-3.5 w-3.5 text-muted-foreground" />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add incentives dropdown */}
      <div className="space-y-2">
        <Label className="text-sm font-medium">Add Stages</Label>
        <Popover
          open={dropdownOpen}
          onOpenChange={(v) => {
            setDropdownOpen(v);
            if (!v) {
              setSearchQuery("");
              setCategoryFilter("all");
            }
          }}
        >
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              role="combobox"
              className="w-full justify-between font-normal text-muted-foreground"
            >
              <div className="flex items-center gap-2">
                <Plus className="h-4 w-4" />
                <span>Browse & add incentives...</span>
              </div>
              <ChevronDown className="h-4 w-4 shrink-0 opacity-50" />
            </Button>
          </PopoverTrigger>
          <PopoverContent
            className="w-[var(--radix-popover-trigger-width)] p-0"
            align="start"
          >
            {/* Search */}
            <div className="p-2 border-b">
              <div className="relative">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search incentives..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8 h-9"
                  autoFocus
                />
              </div>
            </div>
            {/* Category filter tabs */}
            <div className="flex gap-1 p-2 border-b flex-wrap">
              <Badge
                variant={categoryFilter === "all" ? "default" : "outline"}
                className="cursor-pointer text-xs"
                onClick={() => setCategoryFilter("all")}
              >
                All
              </Badge>
              {categories.map((cat) => (
                <Badge
                  key={cat}
                  variant={categoryFilter === cat ? "default" : "outline"}
                  className="cursor-pointer text-xs"
                  onClick={() => setCategoryFilter(cat)}
                >
                  {categoryLabels[cat] ?? cat}
                </Badge>
              ))}
            </div>
            {/* Results */}
            <div className="max-h-64 overflow-y-auto p-1">
              {filteredIncentives.length === 0 ? (
                <div className="py-6 text-center text-sm text-muted-foreground">
                  No matching incentives found.
                </div>
              ) : (
                filteredIncentives.map((inc) => (
                  <button
                    key={inc.id}
                    type="button"
                    onClick={() => addStage(inc.id)}
                    className="flex items-center gap-3 w-full rounded-sm px-2 py-2 text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer transition-colors"
                  >
                    <div
                      className={`shrink-0 ${typeColor[inc.incentiveType] ?? ""}`}
                    >
                      {typeIcon[inc.incentiveType]}
                    </div>
                    <div className="flex-1 min-w-0 text-left">
                      <p className="text-sm font-medium truncate">{inc.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {categoryLabels[inc.incentiveType] ?? inc.incentiveType}
                        {inc.endDate &&
                          ` \u00B7 Ends ${inc.endDate.slice(0, 10)}`}
                      </p>
                    </div>
                    <Badge
                      variant="outline"
                      className={cn(
                        "shrink-0 text-xs",
                        statusColors[inc.status as IncentiveStatus],
                      )}
                    >
                      {statusLabels[inc.status as IncentiveStatus] ??
                        inc.status}
                    </Badge>
                    <Plus className="h-4 w-4 text-muted-foreground shrink-0" />
                  </button>
                ))
              )}
            </div>
            {/* Footer count */}
            <div className="border-t p-2 text-xs text-muted-foreground text-center">
              {filteredIncentives.length} incentive
              {filteredIncentives.length !== 1 ? "s" : ""} available
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}
