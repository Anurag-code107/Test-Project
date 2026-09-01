// Adapted from: none — no production reference
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import axios from "axios";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
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
import { useRejectRedemption } from "@/hooks/redemption/useRedemptionApproval";

const rejectRedemptionSchema = z.object({
  rejectionReason: z.string().min(1, "Required").max(1000),
});

type RejectFormValues = z.infer<typeof rejectRedemptionSchema>;

interface RejectDialogProps {
  redemptionId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function RejectDialog({ redemptionId, open, onOpenChange }: RejectDialogProps) {
  const { mutate, isPending } = useRejectRedemption();

  const {
    register,
    handleSubmit,
    watch,
    reset,
  } = useForm<RejectFormValues>({
    resolver: zodResolver(rejectRedemptionSchema),
    defaultValues: { rejectionReason: "" },
    mode: "onChange",
  });

  const reason = watch("rejectionReason");
  const trimmedLength = reason.trim().length;
  const charCount = reason.length;
  const submitDisabled = isPending || trimmedLength === 0 || charCount > 1000;

  const handleClose = (nextOpen: boolean) => {
    if (!isPending) {
      if (!nextOpen) reset();
      onOpenChange(nextOpen);
    }
  };

  const onSubmit = (values: RejectFormValues) => {
    mutate(
      { redemptionId, rejectionReason: values.rejectionReason },
      {
        onSuccess: () => {
          toast.success("Redemption rejected");
          reset();
          onOpenChange(false);
        },
        onError: (error) => {
          if (axios.isAxiosError(error)) {
            const status = error.response?.status;
            if (status === 409) {
              toast.error(
                "This redemption was just actioned by another approver. Please refresh the queue.",
              );
              reset();
              onOpenChange(false);
              return;
            }
            if (status === 404) {
              toast.error("Redemption not found");
              reset();
              onOpenChange(false);
              return;
            }
            if (status === 400) {
              toast.error("Rejection reason is required");
              return;
            }
          }
          toast.error("Something went wrong. Please try again.");
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Reject redemption</DialogTitle>
          <DialogDescription>
            Provide a reason for rejecting this redemption. The reserved balance will be released
            back to the partner wallet.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="rejectionReason">Rejection reason</Label>
            <Textarea
              id="rejectionReason"
              placeholder="Enter reason for rejection..."
              rows={4}
              disabled={isPending}
              {...register("rejectionReason")}
            />
            <div className="flex items-center justify-between">
              <p className="text-xs text-muted-foreground">Required. Max 1000 characters.</p>
              {charCount > 900 && (
                <p
                  className={cn("text-xs tabular-nums", charCount > 1000 ? "text-destructive" : "text-muted-foreground")}
                >
                  {charCount}/1000
                </p>
              )}
            </div>
          </div>

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
              variant="destructive"
              disabled={submitDisabled}
            >
              {isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
              Reject
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
