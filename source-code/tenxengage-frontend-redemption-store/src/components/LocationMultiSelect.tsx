import { useMemo, useState } from "react";
import {
  Check,
  ChevronDown,
  ChevronRight,
  MapPin,
  Search,
  X,
} from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import type { LocationValueResponse } from "@/types/location.types";

interface LocationMultiSelectProps {
  /** The location hierarchy tree (LocationHierarchyResponse.tree). */
  tree: LocationValueResponse[];
  /** Currently selected location value IDs. */
  value: string[];
  /** Fired whenever the selection changes. */
  onChange: (locationValueIds: string[]) => void;
  isLoading?: boolean;
  placeholder?: string;
  className?: string;
}

const TRIGGER =
  "flex w-full items-center gap-2 rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50";

function depthPaddingClass(depth: number): string {
  if (depth <= 0) return "pl-2";
  if (depth === 1) return "pl-8";
  if (depth === 2) return "pl-14";
  if (depth === 3) return "pl-20";
  return "pl-24";
}

/** A node is visible during search if it or any descendant matches the query. */
function nodeMatches(node: LocationValueResponse, q: string): boolean {
  if (!q) return true;
  if (node.name.toLowerCase().includes(q)) return true;
  return (node.children ?? []).some((c) => nodeMatches(c, q));
}

export function LocationMultiSelect({
  tree,
  value,
  onChange,
  isLoading,
  placeholder = "Select locations…",
  className,
}: LocationMultiSelectProps) {
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");

  const selectedSet = useMemo(() => new Set(value), [value]);

  const nameById = useMemo(() => {
    const map = new Map<string, string>();
    const walk = (nodes: LocationValueResponse[]) => {
      for (const node of nodes) {
        map.set(node.id, node.name);
        walk(node.children ?? []);
      }
    };
    walk(tree);
    return map;
  }, [tree]);

  const q = search.trim().toLowerCase();

  function toggleSelect(id: string) {
    if (selectedSet.has(id)) {
      onChange(value.filter((v) => v !== id));
    } else {
      onChange([...value, id]);
    }
  }

  function toggleExpanded(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function renderNode(node: LocationValueResponse, depth: number) {
    if (!nodeMatches(node, q)) return null;
    const children = node.children ?? [];
    const hasChildren = children.length > 0;
    const isExpanded = expanded.has(node.id) || !!q;
    const isSelected = selectedSet.has(node.id);

    return (
      <div key={node.id}>
        <div
          className={cn(
            "group flex items-center gap-2 pr-3 py-2 text-sm rounded-md cursor-pointer hover:bg-accent hover:text-accent-foreground",
            depthPaddingClass(depth),
          )}
          onClick={() =>
            hasChildren ? toggleExpanded(node.id) : toggleSelect(node.id)
          }
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
              toggleSelect(node.id);
            }}
            aria-label={isSelected ? "Deselect" : "Select"}
            aria-checked={isSelected}
            role="checkbox"
            className={cn(
              "shrink-0 flex items-center justify-center h-4 w-4 rounded-sm border transition-colors",
              isSelected
                ? "bg-primary border-primary text-primary-foreground"
                : "border-input bg-background hover:border-primary",
            )}
          >
            {isSelected && <Check className="h-3 w-3" strokeWidth={3} />}
          </button>
          <span className={cn("flex-1 truncate", depth === 0 && "font-semibold")}>
            {node.name}
          </span>
        </div>
        {hasChildren && isExpanded && (
          <div>{children.map((c) => renderNode(c, depth + 1))}</div>
        )}
      </div>
    );
  }

  if (isLoading) {
    return (
      <button
        type="button"
        disabled
        className={cn(TRIGGER, "opacity-50", className)}
      >
        <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" />
        <span className="text-muted-foreground">Loading locations…</span>
        <ChevronDown className="h-4 w-4 opacity-50 shrink-0 ml-auto" />
      </button>
    );
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(TRIGGER, className)}
          aria-label="Select locations"
        >
          <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="flex-1 min-w-0 truncate text-left">
            {value.length === 0 ? (
              <span className="text-muted-foreground">{placeholder}</span>
            ) : (
              value
                .map((id) => nameById.get(id))
                .filter(Boolean)
                .join(", ")
            )}
          </span>
          {value.length > 0 && (
            <span className="inline-flex items-center justify-center rounded-full px-1.5 py-0 min-w-[20px] h-5 text-xs font-medium tabular-nums bg-primary/15 text-primary shrink-0">
              {value.length}
            </span>
          )}
          <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[360px] p-0 shadow-lg" align="start" sideOffset={8}>
        <div className="p-3 border-b">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search locations…"
              className="pl-9 h-10 rounded-full bg-muted/40 border-transparent focus-visible:ring-1"
            />
          </div>
        </div>

        <div className="px-4 py-2 flex items-center justify-between border-b text-sm">
          <span className="text-muted-foreground">
            {value.length > 0
              ? `${value.length} selected`
              : "No locations selected"}
          </span>
          <button
            type="button"
            onClick={() => onChange([])}
            disabled={value.length === 0}
            className="inline-flex items-center gap-1 text-sm text-foreground hover:underline disabled:text-muted-foreground disabled:no-underline disabled:cursor-not-allowed"
          >
            <X className="h-3 w-3" />
            Clear all
          </button>
        </div>

        <div className="max-h-[360px] overflow-y-auto p-1">
          {tree.length === 0 ? (
            <div className="px-4 py-6 text-sm text-muted-foreground text-center">
              No locations available
            </div>
          ) : (
            tree.map((node) => renderNode(node, 0))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
