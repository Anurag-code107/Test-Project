// Adapted from: src/components/redemption/RejectDialog.tsx (production analog from Mirror)
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { isAxiosError } from "axios";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { useSubmitReturn } from "@/hooks/useSubmitReturn";
import { getCurrency } from "@/config/currencies";

const schema = z.object({
  reason: z.string().max(500, "Reason must be 500 characters or fewer").optional(),
});

type FormValues = z.infer<typeof schema>;

interface RequestReturnDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  redemptionId: string;
  amount: string;
  currencyId: string;
  catalogItemName: string;
  onSuccess?: () => void;
}

export function RequestReturnDialog({
  open,
  onOpenChange,
  redemptionId,
  amount,
  currencyId,
  catalogItemName,
  onSuccess,
}: RequestReturnDialogProps) {
  const [inlineError, setInlineError] = useState<string | null>(null);

  const { mutate, isPending } = useSubmitReturn();

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
    mode: "onChange",
  });

  const reason = watch("reason") ?? "";
  const charCount = reason.length;
  const showCounter = charCount >= 400;

  const handleClose = (nextOpen: boolean) => {
    if (!isPending) {
      if (!nextOpen) {
        reset();
        setInlineError(null);
      }
      onOpenChange(nextOpen);
    }
  };

  const onSubmit = (values: FormValues) => {
    setInlineError(null);
    mutate(
      { redemptionId, reason: values.reason || undefined },
      {
        onSuccess: () => {
          reset();
          setInlineError(null);
          onOpenChange(false);
          onSuccess?.();
        },
        onError: (error) => {
          if (isAxiosError(error)) {
            const status = error.response?.status;
            const data = error.response?.data as { errorCode?: string; errorMessage?: string } | undefined;
            const msg = data?.errorMessage ?? data?.errorCode;
            if (status === 422) {
              setInlineError(msg ?? "This redemption is not eligible for a return.");
              return;
            }
            if (status === 409) {
              setInlineError(msg ?? "A return request is already active for this redemption.");
              return;
            }
          }
          setInlineError("Something went wrong — please try again.");
        },
      },
    );
  };

  const formattedAmount = getCurrency(currencyId).rewardFormat(amount);

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Request Return</DialogTitle>
          <DialogDescription>
            {catalogItemName} &middot; {formattedAmount}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="reason">Reason (optional)</Label>
            <Textarea
              id="reason"
              placeholder="Describe why you're returning this item…"
              rows={4}
              disabled={isPending}
              {...register("reason")}
            />
            <div className="flex items-center justify-between">
              {errors.reason ? (
                <p className="text-xs text-destructive">{errors.reason.message}</p>
              ) : (
                <span />
              )}
              {showCounter && (
                <p
                  className={cn(
                    "text-xs tabular-nums",
                    charCount > 500 ? "text-destructive" : "text-muted-foreground",
                  )}
                >
                  {charCount}/500
                </p>
              )}
            </div>
          </div>

          {inlineError && (
            <p
              className="text-sm text-destructive rounded-lg bg-destructive/10 px-3 py-2"
              role="alert"
              aria-live="assertive"
            >
              {inlineError}
            </p>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleClose(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isPending || charCount > 500}
            >
              {isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" aria-hidden="true" /> : null}
              {isPending ? <span className="sr-only">Submitting return request…</span> : null}
              Submit Return Request
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
