// Adapted from: none — no production reference
import axios from "axios";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useApproveRedemption } from "@/hooks/redemption/useRedemptionApproval";

interface ApproveConfirmDialogProps {
  redemptionId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ApproveConfirmDialog({
  redemptionId,
  open,
  onOpenChange,
}: ApproveConfirmDialogProps) {
  const { mutate, isPending } = useApproveRedemption();

  const handleApprove = () => {
    mutate(redemptionId, {
      onSuccess: () => {
        toast.success("Redemption approved");
        onOpenChange(false);
      },
      onError: (error) => {
        if (axios.isAxiosError(error)) {
          const status = error.response?.status;
          if (status === 409) {
            toast.error(
              "This redemption was just actioned by another approver. Please refresh the queue.",
            );
            onOpenChange(false);
            return;
          }
          if (status === 404) {
            toast.error("Redemption not found");
            onOpenChange(false);
            return;
          }
        }
        toast.error("Something went wrong. Please try again.");
      },
    });
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!isPending) onOpenChange(o); }}>
      <DialogContent className="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>Approve this redemption?</DialogTitle>
          <DialogDescription>
            This will approve the redemption and initiate vendor processing.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button onClick={handleApprove} disabled={isPending}>
            {isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
            Approve
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
