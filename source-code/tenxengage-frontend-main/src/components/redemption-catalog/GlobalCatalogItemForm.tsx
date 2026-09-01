import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MultiSelect } from "@/components/ui/multi-select";
import type {
  RedemptionCatalogItemDetailResponse,
  CatalogCategory,
  ProcessingMode,
} from "@/types/redemption-catalog.types";
import { useCreateCatalogItem, useUpdateCatalogItem } from "@/hooks/useRedemptionCatalog";

const COUNTRY_OPTIONS = [
  { value: "US", label: "United States" },
  { value: "GB", label: "United Kingdom" },
  { value: "CA", label: "Canada" },
  { value: "AU", label: "Australia" },
  { value: "IN", label: "India" },
  { value: "DE", label: "Germany" },
  { value: "FR", label: "France" },
  { value: "SG", label: "Singapore" },
];

export const createCatalogItemSchema = z
  .object({
    name: z.string().min(1, "Name is required").max(255),
    description: z.string().max(2000).optional(),
    category: z.enum(["CASH", "NON_CASH"] as const),
    currencyId: z.string().min(1, "Currency is required"),
    defaultMinRedemptionAmount: z
      .string()
      .min(1, "Min redemption amount is required")
      .refine((v) => parseFloat(v) > 0, "Must be greater than 0"),
    defaultProcessingMode: z.enum(["INSTANT", "BATCH", "APPROVAL_REQUIRED"] as const),
    geographicScope: z.array(z.string()),
    providerItemId: z.string().max(255).optional(),
    isReturnable: z.boolean(),
    defaultReturnWindowDays: z.coerce.number().min(0),
  })
  .refine(
    (data) => !(data.category === "CASH" && data.isReturnable),
    { message: "CASH items cannot be returnable", path: ["isReturnable"] },
  );

type FormValues = z.infer<typeof createCatalogItemSchema>;

interface Props {
  item?: RedemptionCatalogItemDetailResponse;
  onSave: () => void;
}

export function GlobalCatalogItemForm({ item, onSave }: Props) {
  const isEdit = !!item;
  const createMutation = useCreateCatalogItem();
  const updateMutation = useUpdateCatalogItem();
  const isPending = createMutation.isPending || updateMutation.isPending;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
    reset,
  } = useForm<FormValues>({
    resolver: zodResolver(createCatalogItemSchema),
    defaultValues: {
      name: item?.name ?? "",
      description: item?.description ?? "",
      category: (item?.category as CatalogCategory) ?? "NON_CASH",
      currencyId: item?.currencyId ?? "",
      defaultMinRedemptionAmount: item?.defaultMinRedemptionAmount ?? "",
      defaultProcessingMode: (item?.defaultProcessingMode as ProcessingMode) ?? "INSTANT",
      geographicScope: item?.geographicScope ?? [],
      providerItemId: item?.providerItemId ?? "",
      isReturnable: item?.isReturnable ?? false,
      defaultReturnWindowDays: item?.defaultReturnWindowDays ?? 0,
    },
  });

  const category = watch("category");
  const geographicScope = watch("geographicScope");

  useEffect(() => {
    if (category === "CASH") {
      setValue("isReturnable", false);
    }
  }, [category, setValue]);

  useEffect(() => {
    if (item) reset({ ...item, description: item.description ?? "", providerItemId: item.providerItemId ?? "" });
  }, [item, reset]);

  async function onSubmit(values: FormValues) {
    if (isEdit && item) {
      await updateMutation.mutateAsync({ id: item.id, request: values });
    } else {
      await createMutation.mutateAsync(values);
    }
    onSave();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" data-testid="catalog-item-form">
      <div className="space-y-1">
        <Label htmlFor="name">Name *</Label>
        <Input id="name" {...register("name")} placeholder="Item name" />
        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
      </div>

      <div className="space-y-1">
        <Label htmlFor="description">Description</Label>
        <Textarea id="description" {...register("description")} placeholder="Optional description" />
        {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <Label htmlFor="category">Category *</Label>
          <Select
            value={category}
            onValueChange={(v) => setValue("category", v as CatalogCategory)}
          >
            <SelectTrigger id="category">
              <SelectValue placeholder="Select category" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="CASH">Cash</SelectItem>
              <SelectItem value="NON_CASH">Non-Cash</SelectItem>
            </SelectContent>
          </Select>
          {errors.category && <p className="text-sm text-destructive">{errors.category.message}</p>}
        </div>

        <div className="space-y-1">
          <Label htmlFor="currencyId">Currency *</Label>
          <Input id="currencyId" {...register("currencyId")} placeholder="e.g. cash, points" />
          {errors.currencyId && <p className="text-sm text-destructive">{errors.currencyId.message}</p>}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <Label htmlFor="defaultMinRedemptionAmount">Min Redemption Amount *</Label>
          <Input
            id="defaultMinRedemptionAmount"
            {...register("defaultMinRedemptionAmount")}
            placeholder="e.g. 10.00"
          />
          {errors.defaultMinRedemptionAmount && (
            <p className="text-sm text-destructive">{errors.defaultMinRedemptionAmount.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="defaultProcessingMode">Processing Mode</Label>
          <Select
            value={watch("defaultProcessingMode")}
            onValueChange={(v) => setValue("defaultProcessingMode", v as ProcessingMode)}
          >
            <SelectTrigger id="defaultProcessingMode">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="INSTANT">Instant</SelectItem>
              <SelectItem value="BATCH">Batch</SelectItem>
              <SelectItem value="APPROVAL_REQUIRED">Approval Required</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {category === "NON_CASH" && (
        <div className="space-y-1">
          <Label htmlFor="providerItemId">Provider Item ID</Label>
          <Input
            id="providerItemId"
            {...register("providerItemId")}
            placeholder="Xoxoday product ID or XTRM payout type"
          />
          {errors.providerItemId && (
            <p className="text-sm text-destructive">{errors.providerItemId.message}</p>
          )}
        </div>
      )}

      <div className="space-y-1">
        <Label>Geographic Scope</Label>
        <MultiSelect
          options={COUNTRY_OPTIONS}
          selected={geographicScope}
          onChange={(v) => setValue("geographicScope", v)}
          placeholder="Select countries (empty = global)"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="flex items-center gap-2 pt-2">
          <Checkbox
            id="isReturnable"
            checked={watch("isReturnable")}
            onCheckedChange={(v) => setValue("isReturnable", !!v)}
            disabled={category === "CASH"}
            data-testid="isReturnable-checkbox"
          />
          <Label htmlFor="isReturnable" className={category === "CASH" ? "opacity-50 cursor-not-allowed" : ""}>
            Returnable
          </Label>
          {errors.isReturnable && (
            <p className="text-sm text-destructive">{errors.isReturnable.message}</p>
          )}
        </div>

        <div className="space-y-1">
          <Label htmlFor="defaultReturnWindowDays">Return Window (days)</Label>
          <Input
            id="defaultReturnWindowDays"
            type="number"
            min={0}
            {...register("defaultReturnWindowDays")}
          />
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onSave} disabled={isPending}>
          Cancel
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending ? "Saving…" : isEdit ? "Update Item" : "Create Item"}
        </Button>
      </div>
    </form>
  );
}
