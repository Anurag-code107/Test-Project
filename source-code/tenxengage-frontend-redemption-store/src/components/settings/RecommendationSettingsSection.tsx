import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import {
  useRecommendationConfig,
  useUpdateRecommendationConfig,
} from "@/hooks/useRecommendationApi";
import { useRewardCurrencies } from "@/hooks/useRewardCurrencyApi";

const configSchema = z.object({
  trainingEnabled: z.boolean(),
  incentiveEnabled: z.boolean(),
  maxTrainingRecommendations: z.coerce.number().min(1).max(20),
  maxIncentiveRecommendations: z.coerce.number().min(1).max(20),
  rewardCurrencyId: z.string().nullable(),
  trainingCompletionReward: z.coerce.number().min(0),
  incentiveCompletionReward: z.coerce.number().min(0),
});

type ConfigFormValues = z.infer<typeof configSchema>;

export function RecommendationSettingsSection() {
  const { data: config, isLoading } = useRecommendationConfig();
  const updateMutation = useUpdateRecommendationConfig();
  const { data: currencies } = useRewardCurrencies();

  const form = useForm<ConfigFormValues>({
    resolver: zodResolver(configSchema),
    defaultValues: {
      trainingEnabled: true,
      incentiveEnabled: true,
      maxTrainingRecommendations: 5,
      maxIncentiveRecommendations: 5,
      rewardCurrencyId: null,
      trainingCompletionReward: 0,
      incentiveCompletionReward: 0,
    },
  });

  useEffect(() => {
    if (config) {
      form.reset({
        trainingEnabled: config.trainingEnabled,
        incentiveEnabled: config.incentiveEnabled,
        maxTrainingRecommendations: config.maxTrainingRecommendations,
        maxIncentiveRecommendations: config.maxIncentiveRecommendations,
        rewardCurrencyId: config.rewardCurrencyId,
        trainingCompletionReward: config.trainingCompletionReward,
        incentiveCompletionReward: config.incentiveCompletionReward,
      });
    }
  }, [config, form]);

  const watchedCurrencyId = form.watch("rewardCurrencyId");
  const selectedCurrency = currencies?.find(
    (c) => c.code === watchedCurrencyId,
  );
  const isMonetary = selectedCurrency?.type === "MONETARY";

  const onSubmit = (values: ConfigFormValues) => {
    updateMutation.mutate(values, {
      onSuccess: () => toast.success("Recommendation settings saved"),
      onError: () => toast.error("Failed to save recommendation settings"),
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
      {/* Enable/Disable Toggles */}
      <div className="space-y-4">
        <h4 className="text-sm font-semibold text-foreground">
          Recommendation Types
        </h4>
        <div className="flex items-center justify-between">
          <div>
            <Label>Recommended Training</Label>
            <p className="text-xs text-muted-foreground">
              Show training course recommendations on partner home pages
            </p>
          </div>
          <Switch
            checked={form.watch("trainingEnabled")}
            onCheckedChange={(checked) =>
              form.setValue("trainingEnabled", checked)
            }
          />
        </div>
        <div className="flex items-center justify-between">
          <div>
            <Label>Incentive Recommendations</Label>
            <p className="text-xs text-muted-foreground">
              Show incentive recommendations on partner home pages
            </p>
          </div>
          <Switch
            checked={form.watch("incentiveEnabled")}
            onCheckedChange={(checked) =>
              form.setValue("incentiveEnabled", checked)
            }
          />
        </div>
      </div>

      <Separator />

      {/* Max recommendations */}
      <div className="space-y-4">
        <h4 className="text-sm font-semibold text-foreground">
          Display Limits
        </h4>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>Max Training Recommendations</Label>
            <Input
              type="number"
              min={1}
              max={20}
              {...form.register("maxTrainingRecommendations")}
            />
          </div>
          <div className="space-y-2">
            <Label>Max Incentive Recommendations</Label>
            <Input
              type="number"
              min={1}
              max={20}
              {...form.register("maxIncentiveRecommendations")}
            />
          </div>
        </div>
      </div>

      <Separator />

      {/* Reward Configuration */}
      <div className="space-y-4">
        <h4 className="text-sm font-semibold text-foreground">
          Completion Rewards
        </h4>
        <p className="text-xs text-muted-foreground">
          Configure rewards for partners who complete recommended training or
          participate in suggested incentives.
          {selectedCurrency?.type === "MONETARY"
            ? " Enter the dollar value — for non-cash currencies, the equivalent amount will be calculated automatically."
            : selectedCurrency
              ? ` Enter the number of ${selectedCurrency.name.toLowerCase()} to reward.`
              : ""}
        </p>
        <div className="space-y-2">
          <Label>Reward Currency</Label>
          <Select
            value={form.watch("rewardCurrencyId") || ""}
            onValueChange={(value) =>
              form.setValue("rewardCurrencyId", value || null)
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select currency" />
            </SelectTrigger>
            <SelectContent>
              {currencies?.map((c) => (
                <SelectItem key={c.id} value={c.code}>
                  {c.name} (
                  {c.type === "MONETARY" ? "Monetary" : "Non-Monetary"})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>
              Training Completion Reward
              {isMonetary
                ? " (USD)"
                : selectedCurrency
                  ? ` (${selectedCurrency.name})`
                  : ""}
            </Label>
            <div className="relative">
              {isMonetary && (
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                  $
                </span>
              )}
              <Input
                type="number"
                min={0}
                step={isMonetary ? "1" : "1"}
                className={isMonetary ? "pl-7" : ""}
                {...form.register("trainingCompletionReward")}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label>
              Incentive Completion Reward
              {isMonetary
                ? " (USD)"
                : selectedCurrency
                  ? ` (${selectedCurrency.name})`
                  : ""}
            </Label>
            <div className="relative">
              {isMonetary && (
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                  $
                </span>
              )}
              <Input
                type="number"
                min={0}
                step={isMonetary ? "1" : "1"}
                className={isMonetary ? "pl-7" : ""}
                {...form.register("incentiveCompletionReward")}
              />
            </div>
          </div>
        </div>
      </div>

      <Button type="submit" disabled={updateMutation.isPending}>
        {updateMutation.isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin mr-2" />
            Saving...
          </>
        ) : (
          "Save Settings"
        )}
      </Button>
    </form>
  );
}
