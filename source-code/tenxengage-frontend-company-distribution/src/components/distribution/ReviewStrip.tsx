import { Button } from "@/components/ui/button";

interface Props {
  selectedCount: number;
  amountEach: string;
  total: number;
  remaining: number;
  currency: string;
  /** Null when ready. Otherwise shown next to a disabled button, so the block is never mysterious. */
  blockedReason: string | null;
  isSubmitting: boolean;
  onSubmit: () => void;
}

/**
 * Sticky review bar: the arithmetic and the send button.
 *
 * <p>The total is spelled out as `amount × N` rather than just a figure, because that multiplication is
 * the thing an admin most needs to sanity-check before moving company money.</p>
 */
export function ReviewStrip({
  selectedCount,
  amountEach,
  total,
  remaining,
  currency,
  blockedReason,
  isSubmitting,
  onSubmit,
}: Props) {
  return (
    <div
      className="fixed bottom-0 left-0 right-0 border-t bg-background/95 backdrop-blur px-6 py-4 z-10"
      data-testid="review-strip"
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4">
        <div className="text-sm">
          <span className="font-medium tabular-nums" data-testid="review-total">
            {amountEach || "0.00"} × {selectedCount} = {total.toFixed(2)} {currency}
          </span>
          <span className="text-muted-foreground ml-3 tabular-nums">
            {remaining.toFixed(2)} {currency} left
          </span>
        </div>

        <div className="ml-auto flex items-center gap-3">
          {blockedReason && (
            <span className="text-sm text-muted-foreground" data-testid="blocked-reason">
              {blockedReason}
            </span>
          )}
          <Button
            onClick={onSubmit}
            disabled={!!blockedReason || isSubmitting}
            data-testid="send-distribution"
          >
            {isSubmitting ? "Sending…" : "Send"}
          </Button>
        </div>
      </div>
    </div>
  );
}
