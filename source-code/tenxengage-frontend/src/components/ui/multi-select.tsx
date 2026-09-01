import { useState, useRef, useEffect } from "react";
import { Check, ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";

export interface MultiSelectOption {
  value: string;
  label: string;
  icon?: React.ReactNode;
  meta?: string;
}

interface MultiSelectProps {
  options: MultiSelectOption[];
  selected: string[];
  onChange: (selected: string[]) => void;
  placeholder?: string;
  className?: string;
  displayCount?: number;
  disabled?: boolean;
  ariaLabel?: string;
}

export function MultiSelect({
  options,
  selected,
  onChange,
  placeholder = "Select...",
  className,
  displayCount,
  disabled = false,
  ariaLabel,
}: MultiSelectProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function toggle(value: string) {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  }

  function selectAll() {
    onChange(options.map((o) => o.value));
  }

  function unselectAll() {
    onChange([]);
  }

  const selectedLabels = selected
    .map((v) => options.find((o) => o.value === v)?.label)
    .filter(Boolean) as string[];

  const count = displayCount ?? selectedLabels.length;
  const displayText =
    count === 0
      ? placeholder
      : count > 2
        ? `${count} selected`
        : selectedLabels.slice(0, 2).join(", ");

  return (
    <div ref={containerRef} className={cn("relative", className)}>
      <button
        type="button"
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => {
          if (!disabled) setOpen(!open);
        }}
        className={cn(
          "flex items-center justify-between w-full rounded-md border border-input bg-background px-3 py-2 text-sm",
          "ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
          "hover:bg-primary hover:text-primary-foreground transition-colors",
          disabled &&
            "opacity-50 cursor-not-allowed hover:bg-background hover:text-muted-foreground",
          selected.length === 0 && "text-muted-foreground",
        )}
      >
        <span className="truncate text-left">{displayText}</span>
        <ChevronDown
          className={cn(
            "h-4 w-4 shrink-0 opacity-50 transition-transform",
            open && "rotate-180",
          )}
        />
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-full rounded-md border border-input bg-popover shadow-md animate-in fade-in-0 zoom-in-95">
          <div className="flex items-center justify-between px-3 py-2">
            <button
              type="button"
              className="text-xs text-primary hover:underline font-medium"
              onClick={selectAll}
            >
              Select All
            </button>
            <button
              type="button"
              className="text-xs text-muted-foreground hover:underline"
              onClick={unselectAll}
            >
              Unselect All
            </button>
          </div>
          <div className="h-px bg-border" />
          <div className="max-h-[200px] overflow-y-auto py-1">
            {options.map((option) => {
              const isSelected = selected.includes(option.value);
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => toggle(option.value)}
                  className={cn(
                    "group/item flex items-center w-full gap-2 px-3 py-2 text-sm transition-colors",
                    "hover:bg-primary hover:text-primary-foreground",
                  )}
                >
                  <div className="flex items-center justify-center h-4 w-4 shrink-0">
                    {isSelected && (
                      <Check className="h-4 w-4 text-primary group-hover/item:text-primary-foreground" />
                    )}
                  </div>
                  {option.icon && (
                    <span className="shrink-0">{option.icon}</span>
                  )}
                  <span className="flex-1 text-left">{option.label}</span>
                  {option.meta && (
                    <span className="text-xs text-muted-foreground">
                      {option.meta}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
