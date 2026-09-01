interface SuggestionChipsProps {
  suggestions: string[];
  onSelect: (suggestion: string) => void;
  disabled?: boolean;
}

export function SuggestionChips({
  suggestions,
  onSelect,
  disabled,
}: SuggestionChipsProps) {
  if (suggestions.length === 0) return null;

  return (
    <div className="flex gap-1.5 flex-wrap">
      {suggestions.map((s, idx) => (
        <button
          key={idx}
          type="button"
          disabled={disabled}
          className="inline-flex items-center px-2.5 py-1 rounded-md border border-border text-xs font-medium text-muted-foreground hover:border-primary/30 hover:bg-primary/5 hover:text-primary transition-[border-color,background-color,color] cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:border-border disabled:hover:bg-transparent disabled:hover:text-muted-foreground"
          onClick={() => onSelect(s)}
        >
          {s}
        </button>
      ))}
    </div>
  );
}
