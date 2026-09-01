import { useEffect, useMemo, useRef, useState } from "react";
import {
  Check,
  ChevronDown,
  ChevronRight,
  MapPin,
  Search,
} from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useLocationFilterOptions } from "@/hooks/useLocationApi";
import {
  GLOBAL_VALUE,
  buildLeafCountMap,
  computeVisibleIds,
  flattenVisibleValues,
  getAncestorIds,
  getChildren,
  getValueName,
  type FlattenedValue,
} from "@/components/LocationFilter.helpers";

interface LocationFilterProps {
  /** Selected location value ID, or "GLOBAL" for no filter. */
  value: string;
  /** Fired immediately on row click or Clear all. */
  onChange: (locationValueId: string) => void;
  /** Overrides sizing on the trigger button. Structural classes always apply. */
  className?: string;
}

const TRIGGER_BASE =
  "inline-flex items-center gap-2 rounded-md border border-input bg-background px-3 py-1 ring-offset-background transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50";
const TRIGGER_SIZE_DEFAULT = "h-9 w-auto text-sm";

function depthPaddingClass(depth: number): string {
  if (depth <= 0) return "pl-2";
  if (depth === 1) return "pl-8";
  if (depth === 2) return "pl-14";
  if (depth === 3) return "pl-20";
  return "pl-24";
}

export function LocationFilter({
  value,
  onChange,
  className,
}: LocationFilterProps) {
  const { data: filterOptions, isLoading } = useLocationFilterOptions();

  const values = useMemo(
    () => flattenVisibleValues(filterOptions),
    [filterOptions],
  );
  const roots = useMemo(() => getChildren(null, values), [values]);
  const leafCounts = useMemo(() => buildLeafCountMap(values), [values]);

  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const searchInputRef = useRef<HTMLInputElement>(null);

  // On open: clear search, auto-expand ancestors of the current selection,
  // and focus the search input. Without the staged buffer there's nothing
  // else to initialize.
  useEffect(() => {
    if (!open) return;
    setSearch("");
    if (value !== GLOBAL_VALUE && value) {
      const ancestors = getAncestorIds(value, values);
      if (ancestors.length > 0) {
        setExpanded((prev) => {
          const next = new Set(prev);
          for (const id of ancestors) next.add(id);
          return next;
        });
      }
    }
    const t = window.setTimeout(() => searchInputRef.current?.focus(), 40);
    return () => window.clearTimeout(t);
  }, [open, value, values]);

  const searchVisibleIds = useMemo(
    () => computeVisibleIds(search, values),
    [search, values],
  );

  const isApplied = value !== GLOBAL_VALUE && !!value;
  const selectedName = isApplied ? getValueName(value, values) : null;
  const selectedLeafCount = isApplied
    ? (leafCounts.get(value) ?? 0)
    : 0;

  if (isLoading) {
    return (
      <button
        type="button"
        disabled
        className={cn(TRIGGER_BASE, className ?? TRIGGER_SIZE_DEFAULT, "opacity-50")}
      >
        <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" />
        <span className="text-muted-foreground">Loading...</span>
        <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
      </button>
    );
  }

  if (values.length === 0) {
    return null;
  }

  function toggleExpanded(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleSelectToggle(id: string) {
    // Single-select: clicking the already-selected value clears it; otherwise
    // select it. Commit closes the popover either way.
    onChange(id === value ? GLOBAL_VALUE : id);
    setOpen(false);
  }

  function handleRowClick(node: FlattenedValue, hasChildren: boolean) {
    if (hasChildren) {
      toggleExpanded(node.id);
    } else {
      handleSelectToggle(node.id);
    }
  }

  function handleClearAll() {
    onChange(GLOBAL_VALUE);
  }

  function renderNode(node: FlattenedValue, depth: number) {
    if (searchVisibleIds && !searchVisibleIds.has(node.id)) return null;
    const children = getChildren(node.id, values);
    const hasChildren = children.length > 0;
    const isExpanded = expanded.has(node.id) || !!searchVisibleIds;
    const isSelectedRow = node.id === value;
    const count = leafCounts.get(node.id) ?? 0;

    return (
      <div key={node.id}>
        <div
          className={cn(
            "group flex items-center gap-2 pr-3 py-2 text-sm rounded-md cursor-pointer hover:bg-accent hover:text-accent-foreground",
            depthPaddingClass(depth),
          )}
          onClick={() => handleRowClick(node, hasChildren)}
        >
          {hasChildren ? (
            isExpanded ? (
              <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-accent-foreground" />
            ) : (
              <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-accent-foreground" />
            )
          ) : (
            <span className="w-4 shrink-0" aria-hidden="true" />
          )}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              handleSelectToggle(node.id);
            }}
            aria-label={isSelectedRow ? "Deselect" : "Select"}
            aria-checked={isSelectedRow}
            role="checkbox"
            className={cn(
              "shrink-0 flex items-center justify-center h-4 w-4 rounded-sm border transition-colors",
              isSelectedRow
                ? "bg-primary border-primary text-primary-foreground"
                : "border-input bg-background hover:border-primary",
            )}
          >
            {isSelectedRow && <Check className="h-3 w-3" strokeWidth={3} />}
          </button>
          <span
            className={cn(
              "flex-1 truncate",
              depth === 0 && "font-semibold",
            )}
          >
            {node.name}
          </span>
          <span className="shrink-0 text-xs text-muted-foreground tabular-nums group-hover:text-accent-foreground">
            {count}
          </span>
        </div>
        {hasChildren && isExpanded && (
          <div>{children.map((c) => renderNode(c, depth + 1))}</div>
        )}
      </div>
    );
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(TRIGGER_BASE, className ?? TRIGGER_SIZE_DEFAULT)}
          aria-label="Location filter"
        >
          <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="font-medium truncate min-w-0">
            {isApplied && selectedName ? selectedName : "All Locations"}
          </span>
          {isApplied && (
            <span className="inline-flex items-center justify-center rounded-full px-1.5 py-0 min-w-[20px] h-5 text-xs font-medium tabular-nums bg-primary/15 text-primary shrink-0">
              {selectedLeafCount}
            </span>
          )}
          <ChevronDown className="h-4 w-4 opacity-50 shrink-0 ml-auto" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[360px] p-0 shadow-lg"
        align="start"
        sideOffset={8}
      >
        <div className="p-3 border-b">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              ref={searchInputRef}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search regions, countries, cities..."
              className="pl-9 h-10 rounded-full bg-muted/40 border-transparent focus-visible:ring-1"
            />
          </div>
        </div>

        <div className="px-4 py-2 flex items-center justify-between border-b text-sm">
          <span className="text-muted-foreground">
            {isApplied ? "1 selected" : "No locations selected"}
          </span>
          <button
            type="button"
            onClick={handleClearAll}
            disabled={!isApplied}
            className="text-sm text-foreground hover:underline disabled:text-muted-foreground disabled:no-underline disabled:cursor-not-allowed"
          >
            Clear all
          </button>
        </div>

        <div className="max-h-[360px] overflow-y-auto p-1">
          {searchVisibleIds && searchVisibleIds.size === 0 ? (
            <div className="px-4 py-6 text-sm text-muted-foreground text-center">
              No locations match "{search}"
            </div>
          ) : (
            roots.map((r) => renderNode(r, 0))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
