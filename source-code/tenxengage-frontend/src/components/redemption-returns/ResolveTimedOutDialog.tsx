// Adapted from: src/components/redemption-returns/RejectReturnDialog.tsx (same domain, same mutation dialog shape)
import { useRef } from "react";
import { useForm, useWatch } from "react-hook-form";
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
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { useResolveTimedOutReturn } from "@/hooks/useResolveTimedOutReturn";

// shape: contracts/endpoints/redemption-returns.yaml (ResolveTimedOutReturnRequest)
const resolveSchema = z.object({
  resolution: z.enum(["CONFIRM", "REJECT"]),
  notes: z.string().max(1000).optional(),
});

type ResolveFormValues = z.infer<typeof resolveSchema>;

const MAX_NOTES_LENGTH = 1000;

interface ResolveTimedOutDialogProps {
  returnId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function ResolveTimedOutDialog({
  returnId,
  open,
  onOpenChange,
  onSuccess,
}: ResolveTimedOutDialogProps) {
  const { mutateAsync, isPending } = useResolveTimedOutReturn();
  const submittingRef = useRef(false);

  const {
    register,
    handleSubmit,
    setValue,
    control,
    reset,
    formState: { isValid },
  } = useForm<ResolveFormValues>({
    resolver: zodResolver(resolveSchema),
    mode: "onChange",
    defaultValues: { notes: "" },
  });

  const notesValue = useWatch({ control, name: "notes" });
  const charCount = notesValue?.length ?? 0;

  const handleClose = (nextOpen: boolean) => {
    if (isPending) return;
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  };

  const onSubmit = async (values: ResolveFormValues) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    try {
      await mutateAsync({
        id: returnId,
        dto: {
          resolution: values.resolution,
          notes: values.notes || undefined,
        },
      });
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
          <DialogTitle>Resolve Timed-Out Return</DialogTitle>
          <DialogDescription>
            This return has been waiting for Xoxoday confirmation for more than 7 days.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
          {/* Radio group — resolution choice */}
          <div className="flex flex-col gap-3">
            <Label id="resolution-group-label">Resolution</Label>
            <RadioGroup
              aria-labelledby="resolution-group-label"
              onValueChange={(value) =>
                setValue("resolution", value as "CONFIRM" | "REJECT", {
                  shouldValidate: true,
                  shouldDirty: true,
                })
              }
              disabled={isPending}
              className="flex flex-col gap-2"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem
                  value="CONFIRM"
                  id="resolution-confirm"
                  aria-label="Confirm return (credit wallet)"
                />
                <Label htmlFor="resolution-confirm" className="cursor-pointer font-normal">
                  Confirm return (credit wallet)
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem
                  value="REJECT"
                  id="resolution-reject"
                  aria-label="Reject return (no credit)"
                />
                <Label htmlFor="resolution-reject" className="cursor-pointer font-normal">
                  Reject return (no credit)
                </Label>
              </div>
            </RadioGroup>
          </div>

          {/* Optional notes textarea */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="resolveNotes">Notes (optional)</Label>
            <Textarea
              id="resolveNotes"
              placeholder="Add a note about your resolution decision…"
              className="resize-none min-h-[96px]"
              disabled={isPending}
              {...register("notes")}
            />
            <div className="flex items-center justify-end">
              <span className="text-xs text-muted-foreground tabular-nums">
                {charCount}/{MAX_NOTES_LENGTH}
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
              disabled={!isValid || isPending}
              aria-label="Resolve Return"
            >
              {isPending ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" aria-hidden="true" />
              ) : null}
              Resolve Return
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
