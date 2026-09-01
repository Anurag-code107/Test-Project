import { Sparkles, Loader2 } from "lucide-react";

interface AiInsightsSectionProps {
  insight: string;
  isStreaming: boolean;
  error: string | null;
}

export function AiInsightsSection({
  insight,
  isStreaming,
  error,
}: AiInsightsSectionProps) {
  if (!isStreaming && !insight && !error) return null;

  return (
    <div className="rounded-xl border border-border p-4 space-y-3">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-amber-500" />
        <h4 className="text-sm font-semibold text-foreground">AI Insights</h4>
      </div>

      {isStreaming && !insight && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>Generating personalized insights...</span>
        </div>
      )}

      {error && <p className="text-sm text-destructive">{error}</p>}

      {insight && (
        <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-wrap">
          {insight}
        </p>
      )}
    </div>
  );
}
