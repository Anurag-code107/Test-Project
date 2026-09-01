import { CheckCircle, Loader2, Rocket, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useBuilder } from "@/contexts/BuilderContext";
import { INCENTIVE_TYPE_LABELS } from "@/types/incentive.types";

interface CreateConfirmationCardProps {
  onConfirm: () => void;
  onCancel?: () => void;
  visible: boolean;
}

export function CreateConfirmationCard({
  onConfirm,
  onCancel,
  visible,
}: CreateConfirmationCardProps) {
  const { state } = useBuilder();
  const isCreating = state.isCreating;
  const isEditMode = !!state.editingIncentiveId;

  const typeName = state.basics.incentiveType
    ? INCENTIVE_TYPE_LABELS[state.basics.incentiveType]
    : "Incentive";

  return (
    <div
      className="overflow-hidden transition-all duration-300 ease-out"
      style={{
        maxHeight: visible ? 96 : 0,
        opacity: visible ? 1 : 0,
      }}
    >
      <div className="relative mx-4 mt-3 mb-3 rounded-xl border border-primary/25 bg-gradient-to-r from-primary/5 to-background shadow-sm overflow-hidden">
        {/* Shine sweep — subtle looped pass while idle */}
        {!isCreating && (
          <div
            className="absolute inset-0 pointer-events-none"
            style={{ animation: "confirm-shine 7s ease-in-out infinite" }}
          >
            <div
              className="absolute inset-y-0 w-[60%]"
              style={{
                background:
                  "linear-gradient(105deg, transparent 0%, transparent 35%, hsl(var(--primary) / 0.06) 42%, hsl(var(--primary) / 0.12) 50%, hsl(var(--primary) / 0.06) 58%, transparent 65%, transparent 100%)",
              }}
            />
          </div>
        )}

        <div className="relative flex items-center gap-3 px-4 py-3">
          {/* Icon */}
          <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-primary/10 shrink-0">
            <Rocket className="h-4 w-4 text-primary" />
          </div>

          {/* Label */}
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-foreground truncate">
              Ready to {isEditMode ? "update" : "create"} this{" "}
              {typeName.toLowerCase()}
            </p>
            <p className="text-xs text-muted-foreground truncate tabular-nums">
              {state.basics.name || "Untitled"}
            </p>
          </div>

          {/* Confirm */}
          <Button
            size="sm"
            onClick={onConfirm}
            disabled={isCreating}
            className="h-9 px-4 text-sm font-medium bg-primary hover:bg-primary/90 shadow-sm shrink-0"
          >
            {isCreating ? (
              <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />
            ) : (
              <CheckCircle className="h-4 w-4 mr-1.5" />
            )}
            {isCreating
              ? "Creating..."
              : isEditMode
                ? "Confirm & Update"
                : "Confirm & Create"}
          </Button>

          {/* Cancel — subtle X, keeps the clean pill look while preserving dismiss */}
          {onCancel && !isCreating && (
            <button
              type="button"
              onClick={onCancel}
              className="flex items-center justify-center w-8 h-8 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors shrink-0"
              title="Dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
