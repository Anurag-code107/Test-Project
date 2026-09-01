// Adapted from: src/components/course/CloneCourseDialog.tsx (Dialog pattern)
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";

interface ExportConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isPending: boolean;
}

export function ExportConfirmDialog({
  open,
  onClose,
  onConfirm,
  isPending,
}: ExportConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o && !isPending) onClose();
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Export unredeemed balances</DialogTitle>
          <DialogDescription>
            Download a CSV of all current unredeemed wallet balances for your program.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={onClose}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button
            onClick={onConfirm}
            disabled={isPending}
            aria-busy={isPending}
          >
            {isPending ? (
              <>
                <Loader2
                  className="mr-2 h-4 w-4 animate-spin motion-reduce:animate-none"
                  aria-hidden
                />
                Downloading…
              </>
            ) : (
              "Download CSV"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
