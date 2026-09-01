// Adapted from: src/components/redemption/RejectDialog.tsx (production analog from Mirror)
import { useRef } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useRejectReturn } from "@/hooks/useRejectReturn";

// shape: contracts/endpoints/redemption-returns.yaml (RejectReturnRequest)
const rejectSchema = z.object({
  rejectionReason: z.string().min(1, "Rejection reason is required").max(1000),
});

type RejectFormValues = z.infer<typeof rejectSchema>;

const MAX_REASON_LENGTH = 1000;

interface RejectReturnDialogProps {
  returnId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function RejectReturnDialog({
  returnId,
  open,
  onOpenChange,
  onSuccess,
}: RejectReturnDialogProps) {
  const { mutateAsync, isPending } = useRejectReturn();
  const submittingRef = useRef(false);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isValid },
  } = useForm<RejectFormValues>({
    resolver: zodResolver(rejectSchema),
    mode: "onChange",
    defaultValues: { rejectionReason: "" },
  });

  const reasonValue = watch("rejectionReason");
  const charCount = reasonValue?.length ?? 0;

  const handleClose = (nextOpen: boolean) => {
    if (isPending) return;
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  };

  const onSubmit = async (values: RejectFormValues) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    try {
      await mutateAsync({ id: returnId, rejectionReason: values.rejectionReason });
      reset();
      onOpenChange(false);
      onSuccess?.();
    } finally {
      submittingRef.current = false;
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Reject Return Request?</DialogTitle>
          <DialogDescription>
            The partner will be notified and the return will be closed with your reason.
            This action cannot be undone.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="rejectionReason">Rejection reason</Label>
            <Textarea
              id="rejectionReason"
              placeholder="Explain why this return request is being rejected…"
              className="resize-none min-h-[120px]"
              disabled={isPending}
              {...register("rejectionReason")}
            />
            <div className="flex items-center justify-between">
              {errors.rejectionReason ? (
                <p
                  className="text-xs text-destructive"
                  role="alert"
                  aria-live="assertive"
                >
                  {errors.rejectionReason.message}
                </p>
              ) : (
                <span />
              )}
              <span className="text-xs text-muted-foreground tabular-nums ml-auto">
                {charCount}/{MAX_REASON_LENGTH}
              </span>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
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
              variant="destructive"
              disabled={!isValid || isPending}
            >
              {isPending ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              ) : null}
              Reject Request
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
