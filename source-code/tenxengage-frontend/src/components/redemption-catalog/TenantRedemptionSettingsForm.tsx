import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "sonner";
import { useTenantRedemptionSettings, useUpdateTenantSettings } from "@/hooks/useRedemptionCatalog";
import type { BatchCadence } from "@/types/redemption-catalog.types";

const tenantRedemptionSettingsSchema = z.object({
  batchCadence: z.enum(["DAILY", "WEEKLY"] as const),
  maxInFlightRedemptions: z.coerce.number().int().min(1).max(50),
});

type FormValues = z.infer<typeof tenantRedemptionSettingsSchema>;

export function TenantRedemptionSettingsForm() {
  const { data, isLoading } = useTenantRedemptionSettings();
  const updateSettings = useUpdateTenantSettings();

  const { handleSubmit, reset, setValue, watch, register, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(tenantRedemptionSettingsSchema),
    defaultValues: { batchCadence: "DAILY", maxInFlightRedemptions: 10 },
  });

  const batchCadence = watch("batchCadence");

  useEffect(() => {
    if (data) {
      reset({
        batchCadence: data.batchCadence as BatchCadence,
        maxInFlightRedemptions: data.maxInFlightRedemptions ?? 10,
      });
    }
  }, [data, reset]);

  function onSubmit(values: FormValues) {
    updateSettings.mutate(values, {
      onSuccess: () => {
        toast.success("Settings saved");
      },
      onError: () => {
        toast.error("Could not save settings");
      },
    });
  }

  if (isLoading) {
    return <Skeleton className="h-24 w-full" data-testid="settings-skeleton" />;
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-4"
      data-testid="tenant-settings-form"
    >
      <div className="space-y-2">
        <Label id="batch-cadence-label">Batch Cadence</Label>
        <RadioGroup
          aria-labelledby="batch-cadence-label"
          value={batchCadence}
          onValueChange={(v) => setValue("batchCadence", v as BatchCadence)}
          className="flex gap-6"
          data-testid="batch-cadence-radio"
        >
          <div className="flex items-center gap-2">
            <RadioGroupItem value="DAILY" id="cadence-daily" data-testid="cadence-daily" />
            <Label htmlFor="cadence-daily">Daily</Label>
          </div>
          <div className="flex items-center gap-2">
            <RadioGroupItem value="WEEKLY" id="cadence-weekly" data-testid="cadence-weekly" />
            <Label htmlFor="cadence-weekly">Weekly</Label>
          </div>
        </RadioGroup>
      </div>

      <div className="space-y-2">
        <Label htmlFor="max-in-flight">Max In-Flight Redemptions</Label>
        <p className="text-xs text-muted-foreground">
          Maximum concurrent pending redemptions per user (1–50). Default: 10.
        </p>
        <Input
          id="max-in-flight"
          type="number"
          min={1}
          max={50}
          data-testid="max-in-flight-input"
          {...register("maxInFlightRedemptions")}
        />
        {errors.maxInFlightRedemptions && (
          <p className="text-xs text-destructive" data-testid="max-in-flight-error">
            {errors.maxInFlightRedemptions.message}
          </p>
        )}
      </div>

      <Button type="submit" disabled={updateSettings.isPending}>
        {updateSettings.isPending ? "Saving…" : "Save Settings"}
      </Button>
    </form>
  );
}
