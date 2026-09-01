import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "sonner";
import { useCatalogItemConfig, useUpsertItemConfig } from "@/hooks/useRedemptionCatalog";
import type { ProcessingMode } from "@/types/redemption-catalog.types";

const catalogItemConfigSchema = z.object({
  processingModeOverride: z.enum(["INSTANT", "BATCH", "APPROVAL_REQUIRED"] as const).optional(),
  minTransactionAmountOverride: z.string().optional(),
  minWalletBalanceOverride: z.string().optional(),
  returnWindowDaysOverride: z.preprocess(
    (v) => (v === "" || v == null ? undefined : v),
    z.coerce.number().min(0).optional(),
  ),
});

type FormValues = z.infer<typeof catalogItemConfigSchema>;

interface Props {
  catalogItemId: string;
  enabled: boolean;
  onClose: () => void;
}

export function ItemConfigPanel({ catalogItemId, enabled, onClose }: Props) {
  const { data, isLoading } = useCatalogItemConfig(catalogItemId);
  const item = data?.data.find((i) => i.id === catalogItemId);

  const upsert = useUpsertItemConfig();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
    reset,
    setError,
  } = useForm<FormValues>({
    resolver: zodResolver(catalogItemConfigSchema),
    defaultValues: {
      processingModeOverride: (item?.processingModeOverride as ProcessingMode) ?? undefined,
      minTransactionAmountOverride: item?.minTransactionAmountOverride ?? "",
      minWalletBalanceOverride: item?.minWalletBalanceOverride ?? "",
      returnWindowDaysOverride: item?.returnWindowDaysOverride ?? undefined,
    },
  });

  useEffect(() => {
    if (item) {
      reset({
        processingModeOverride: (item.processingModeOverride as ProcessingMode) ?? undefined,
        minTransactionAmountOverride: item.minTransactionAmountOverride ?? "",
        minWalletBalanceOverride: item.minWalletBalanceOverride ?? "",
        returnWindowDaysOverride: item.returnWindowDaysOverride ?? undefined,
      });
    }
  }, [item?.configId, item?.processingModeOverride, item?.minTransactionAmountOverride, item?.minWalletBalanceOverride, item?.returnWindowDaysOverride, reset]);

  async function onSubmit(values: FormValues) {
    const request = {
      enabled,
      processingModeOverride: values.processingModeOverride || undefined,
      minTransactionAmountOverride: values.minTransactionAmountOverride || undefined,
      minWalletBalanceOverride: values.minWalletBalanceOverride || undefined,
      ...(values.returnWindowDaysOverride !== undefined && { returnWindowDaysOverride: values.returnWindowDaysOverride }),
    };

    upsert.mutate(
      { catalogItemId, request },
      {
        onSuccess: () => {
          toast.success("Configuration saved");
          onClose();
        },
        onError: (err: unknown) => {
          const error = err as { response?: { status?: number; data?: { errorMessage?: string } } };
          const status = error?.response?.status;
          if (status === 422) {
            const msg =
              error?.response?.data?.errorMessage ??
              "Minimum transaction amount cannot be set below the platform minimum.";
            setError("minTransactionAmountOverride", { message: msg });
          } else if (status === 409) {
            toast.error("Configuration was updated concurrently. Refresh and retry.");
          } else {
            toast.error("Could not save configuration");
          }
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 mt-4" data-testid="item-config-panel">
      <div className="space-y-1">
        <Label htmlFor="processingModeOverride">Processing Mode Override</Label>
        <Select
          value={watch("processingModeOverride") ?? "inherit"}
          onValueChange={(v) =>
            setValue(
              "processingModeOverride",
              v === "inherit" ? undefined : (v as ProcessingMode),
            )
          }
        >
          <SelectTrigger id="processingModeOverride">
            <SelectValue placeholder="Inherit global default" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="inherit">Inherit global default</SelectItem>
            <SelectItem value="INSTANT">Instant</SelectItem>
            <SelectItem value="BATCH">Batch</SelectItem>
            <SelectItem value="APPROVAL_REQUIRED">Approval Required</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-1">
        <Label htmlFor="minTransactionAmountOverride">Min Transaction Amount Override</Label>
        <Input
          id="minTransactionAmountOverride"
          placeholder="e.g. 25.00 (leave blank to inherit)"
          {...register("minTransactionAmountOverride")}
        />
        {errors.minTransactionAmountOverride && (
          <p className="text-sm text-destructive" data-testid="min-amount-error">
            {errors.minTransactionAmountOverride.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <Label htmlFor="minWalletBalanceOverride">Min Wallet Balance Override</Label>
        <Input
          id="minWalletBalanceOverride"
          placeholder="e.g. 0.00 (leave blank for platform default)"
          {...register("minWalletBalanceOverride")}
        />
      </div>

      <div className="space-y-1">
        <Label htmlFor="returnWindowDaysOverride">Return Window Override (days)</Label>
        <Input
          id="returnWindowDaysOverride"
          type="number"
          min={0}
          placeholder="Leave blank to inherit"
          {...register("returnWindowDaysOverride")}
        />
      </div>

      <div className="flex gap-2 justify-end pt-2">
        <Button type="button" variant="outline" onClick={onClose} disabled={upsert.isPending}>
          Cancel
        </Button>
        <Button type="submit" disabled={upsert.isPending || isLoading || !item}>
          {upsert.isPending ? "Saving…" : "Save"}
        </Button>
      </div>
    </form>
  );
}
