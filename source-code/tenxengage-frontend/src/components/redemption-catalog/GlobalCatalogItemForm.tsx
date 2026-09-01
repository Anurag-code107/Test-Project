import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
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
  ClientCatalogItemConfigResponse,
  CatalogCategory,
  ProcessingMode,
} from "@/types/redemption-catalog.types";
import { useCreateCatalogItem, useUpdateCatalogItem, useUpsertItemConfig } from "@/hooks/useRedemptionCatalog";
import { uploadCatalogItemImage } from "@/services/redemption-catalog-admin.service";
import { CatalogImageUpload } from "./CatalogImageUpload";
import { GiftCardSkuCombobox, skuValueLabel } from "./GiftCardSkuCombobox";
import type { GiftCardSkuResponse } from "@/types/redemption-catalog.types";
import { toast } from "sonner";
import api from "@/lib/axios";
import { CATALOG_GEOGRAPHIC_SCOPE_ENABLED } from "@/config/redemptionFeatures";


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
    providerItemId: z.string().max(255),
    isReturnable: z.boolean(),
    defaultReturnWindowDays: z.coerce.number().min(0),
    minWalletBalance: z.string().optional(),
  })
  .refine(
    (data) => !(data.category === "CASH" && data.isReturnable),
    { message: "CASH items cannot be returnable", path: ["isReturnable"] },
  )
  // Required for both categories, but they name different things: CASH maps to an XTRM gift-card SKU,
  // NON_CASH to a Xoxoday provider item id. Message follows the category so it matches the field shown.
  .superRefine((data, ctx) => {
    if (!data.providerItemId?.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["providerItemId"],
        message: data.category === "CASH" ? "SKU is required" : "Provider item ID is required",
      });
    }
  });

type FormValues = z.infer<typeof createCatalogItemSchema>;

/**
 * Normalize an amount for the string-typed form fields. The API declares these as `string` but
 * serializes BigDecimal as a JSON number at runtime, so a raw value can be a number — which fails
 * the zod `z.string()` schema on the first submit. Returns "" for null/undefined.
 */
function toAmountString(value: string | number | null | undefined): string {
  return value != null ? String(value) : "";
}

/** Capitalize the first character of a catalog name auto-filled from a SKU (e.g. "adidas" → "Adidas"). */
function capitalizeFirst(value: string): string {
  return value ? value.charAt(0).toUpperCase() + value.slice(1) : value;
}

interface Props {
  item?: RedemptionCatalogItemDetailResponse;
  onSave: () => void;
  tenantConfig?: ClientCatalogItemConfigResponse | null;
}

export function GlobalCatalogItemForm({ item, onSave, tenantConfig }: Props) {
  const isEdit = !!item;
  const createMutation = useCreateCatalogItem();
  const updateMutation = useUpdateCatalogItem();
  const upsertConfig = useUpsertItemConfig();
  const isPending = createMutation.isPending || updateMutation.isPending || upsertConfig.isPending;
  const [pendingImageFile, setPendingImageFile] = useState<File | null>(null);
  // Default to the searchable picker in both create and edit. Legacy / non-catalog SKUs can still be
  // hand-edited via the "Enter SKU manually" toggle below.
  const [manualSku, setManualSku] = useState<boolean>(false);
  // The SKU chosen from the picker — drives the value-type helper line. Cleared in manual mode.
  const [selectedSku, setSelectedSku] = useState<GiftCardSkuResponse | null>(null);
  // Does the Name field hold an auto-filled (SKU-derived) value, or a title the admin typed? Starts true
  // in edit (the saved name is treated as in-sync, so switching SKU updates it) and false in create. A
  // manual edit to Name flips it off, preserving a deliberate title on subsequent SKU switches.
  const [nameAutofilled, setNameAutofilled] = useState<boolean>(isEdit);

  const { data: currenciesData } = useQuery({
    queryKey: ["currencies"],
    queryFn: async () => {
      const res = await api.get<{ data: Array<{ id: string; code: string; name: string; type: string }> }>(
        "/currencies",
      );
      return res.data.data;
    },
  });
  const currencyOptions = currenciesData ?? [];

  const { data: locationData } = useQuery({
    queryKey: ["location-levels"],
    // Only the (currently hidden) Geographic Scope field consumes this — skip the call while hidden.
    enabled: CATALOG_GEOGRAPHIC_SCOPE_ENABLED,
    queryFn: async () => {
      const res = await api.get<{
        data: {
          levels: Array<{ id: string; name: string; depth: number }>;
          tree: Array<{
            id: string; name: string; code: string | null; levelName: string;
            levelId: string; parentId: string | null;
            children: Array<{
              id: string; name: string; code: string | null;
              levelName: string; levelId: string; parentId: string | null; children: never[];
            }>;
          }>;
        };
      }>("/location-levels");
      return res.data.data;
    },
  });

  const locationOptions = (locationData?.tree ?? []).flatMap((region) => [
    ...(region.code ? [{ value: region.code, label: region.name, isRegion: true }] : []),
    ...(region.children ?? [])
      .filter((c) => c.code != null)
      .map((c) => ({ value: c.code!, label: c.name, isRegion: false })),
  ]);

  const regionToCountries = new Map<string, string[]>(
    (locationData?.tree ?? [])
      .filter((r) => r.code != null)
      .map((r) => [
        r.code!,
        (r.children ?? []).filter((c) => c.code != null).map((c) => c.code!),
      ]),
  );

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    getValues,
    formState: { errors },
    reset,
  } = useForm<FormValues>({
    resolver: zodResolver(createCatalogItemSchema),
    defaultValues: {
      name: item?.name ?? "",
      description: item?.description ?? "",
      category: (item?.category as CatalogCategory) ?? "NON_CASH",
      currencyId: item?.currencyId ?? "",
      defaultMinRedemptionAmount: toAmountString(item?.defaultMinRedemptionAmount),
      defaultProcessingMode: (item?.defaultProcessingMode as ProcessingMode) ?? "INSTANT",
      geographicScope: item?.geographicScope ?? [],
      providerItemId: item?.providerItemId ?? "",
      isReturnable: item?.isReturnable ?? false,
      defaultReturnWindowDays: item?.defaultReturnWindowDays ?? 0,
      minWalletBalance: toAmountString(tenantConfig?.minWalletBalanceOverride),
    },
  });

  const category = watch("category");

  // The brand image the card will fall back to when no upload exists. Once a SKU is picked it is the
  // sole source — including when that SKU has no image — so switching from a branded SKU to an
  // unbranded one can't leave the previous brand's logo on screen. Only with nothing picked (edit,
  // or manual SKU entry) does the item's stored value stand in.
  const skuImagePreview = selectedSku
    ? selectedSku.brandImageUrl
    : (item?.providerImageUrl ?? null);

  // Decision A: a CASH item may only use the `cash` currency; a NON_CASH item uses everything else.
  const filteredCurrencyOptions = currencyOptions.filter((c) =>
    category === "CASH" ? c.code === "cash" : c.code !== "cash",
  );

  const knownCodes = new Set(locationOptions.map((o) => o.value));
  const currentScope = watch("geographicScope") ?? [];
  const unmatchedCodes = currentScope.filter((code) => !knownCodes.has(code));

  const selectedSet = new Set(currentScope.filter((c) => knownCodes.has(c)));
  const derivedSelected = [
    ...selectedSet,
    ...(locationData?.tree ?? [])
      .filter((r) => r.code &&
        (r.children ?? []).filter((c) => c.code).length > 0 &&
        (r.children ?? []).filter((c) => c.code).every((c) => selectedSet.has(c.code!)))
      .map((r) => r.code!),
  ];

  useEffect(() => {
    if (category === "CASH") {
      setValue("isReturnable", false);
    }
  }, [category, setValue]);

  // The two categories map to different providers (CASH → XTRM gift-card SKU, NON_CASH → Xoxoday item
  // id), so an id carried across a switch is always wrong for the new category. Guarded on a real
  // change via the ref: firing on mount would wipe the saved id when opening the edit form.
  const prevCategoryRef = useRef<CatalogCategory | null>(null);
  useEffect(() => {
    const previous = prevCategoryRef.current;
    prevCategoryRef.current = category;
    if (previous && previous !== category) {
      setValue("providerItemId", "");
      setSelectedSku(null);
      setManualSku(false);
    }
  }, [category, setValue]);

  // Keep the selected currency valid for the category: CASH auto-selects `cash`;
  // NON_CASH clears `cash` if it was selected (Decision A).
  useEffect(() => {
    const cur = getValues("currencyId");
    if (category === "CASH") {
      if (cur !== "cash") setValue("currencyId", "cash");
    } else if (cur === "cash") {
      setValue("currencyId", "");
    }
  }, [category, setValue, getValues]);

  useEffect(() => {
    if (item) {
      reset({
        ...item,
        description: item.description ?? "",
        providerItemId: item.providerItemId ?? "",
        // The API serializes these BigDecimal fields as JSON numbers, but the form fields are
        // strings. Coerce here so the first submit validates instead of failing zod with
        // "Expected string, received number" (which self-heals on the 2nd click as RHF re-reads
        // the string value from the DOM).
        defaultMinRedemptionAmount: toAmountString(item.defaultMinRedemptionAmount),
        minWalletBalance: toAmountString(tenantConfig?.minWalletBalanceOverride),
      });
      // The loaded name is treated as in-sync with the item's SKU, so switching SKU updates it.
      setNameAutofilled(true);
    }
    setPendingImageFile(null);
  }, [item, tenantConfig, reset]);

  function handleSkuSelect(sku: GiftCardSkuResponse) {
    // Keep the name in sync when switching SKUs — unless the admin typed a custom title (nameAutofilled
    // flipped off). An empty name always fills.
    const currentName = getValues("name")?.trim() ?? "";
    setSelectedSku(sku);
    setValue("providerItemId", sku.sku, { shouldValidate: true });
    if (!currentName || nameAutofilled) {
      setValue("name", capitalizeFirst(sku.rewardName), { shouldValidate: true });
      setNameAutofilled(true);
    }
    // Mirror the SKU's min so the form preview matches: FIXED locks to face value; VARIABLE seeds the
    // low end. XTRM's VARIABLE floor is often 0 ("any amount") — never autofill 0 (the field requires
    // > 0), so the admin keeps/sets their own platform floor.
    const min = sku.valueType === "FIXED" ? sku.faceValue : sku.minValue;
    if (min != null && min > 0) {
      setValue("defaultMinRedemptionAmount", String(min), { shouldValidate: true });
    }
  }

  /**
   * Turn a save failure into something the admin can act on. A 409 is always the same thing on this
   * endpoint — the provider mapping is already taken by a live item — so it lands inline on that field
   * rather than as a bare toast the admin has to translate. Everything else surfaces the server's own
   * message, which is already written for a human ("CASH items cannot be returnable", range errors …).
   */
  function handleSaveError(err: unknown) {
    const error = err as { response?: { status?: number; data?: { errorMessage?: string } } };
    if (error?.response?.status === 409) {
      // Named for the field it lands on: CASH picks an XTRM SKU, NON_CASH a Xoxoday provider item id.
      const conflict = category === "CASH" ? "SKU already in use" : "Provider item ID already in use";
      setError("providerItemId", { message: conflict });
      // Same words as a toast: the submit button sits below the fold in this scrollable dialog, so the
      // inline message alone can go unseen.
      toast.error(conflict);
      return;
    }
    toast.error(
      error?.response?.data?.errorMessage ?? "Could not save the catalog item — please try again.",
    );
  }

  async function onSubmit(values: FormValues) {
    const { minWalletBalance, ...rest } = values;
    const payload = { ...rest, providerItemId: rest.providerItemId || undefined };
    try {
      if (isEdit && item) {
        await updateMutation.mutateAsync({ id: item.id, request: payload });
        // Only upsert tenant config when the current enabled state is known.
        // If tenantConfig is absent the enabled state is unknown — defaulting to
        // false would silently disable the item for all tenant partners.
        if (tenantConfig != null) {
          await upsertConfig.mutateAsync({
            catalogItemId: item.id,
            request: {
              enabled: tenantConfig.enabled,
              minWalletBalanceOverride: minWalletBalance || undefined,
            },
          });
        }
      } else {
        const created = await createMutation.mutateAsync(payload);
        if (pendingImageFile) {
          try {
            await uploadCatalogItemImage(created.id, pendingImageFile);
          } catch {
            toast.error("Item created but image upload failed. You can re-upload from the edit form.");
            onSave();
            return;
          }
        }
      }
    } catch (err) {
      // Keep the dialog open on failure so the entered values survive and can be corrected.
      handleSaveError(err);
      return;
    }
    onSave();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" data-testid="catalog-item-form">
      <div className="space-y-1">
        <Label htmlFor="name">Name *</Label>
        <Input
          id="name"
          {...register("name", { onChange: () => setNameAutofilled(false) })}
          placeholder="Item name"
        />
        {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
      </div>

      <div className="space-y-1">
        <Label htmlFor="description">Description</Label>
        <Textarea id="description" {...register("description")} placeholder="Optional description" />
        {errors.description && <p className="text-sm text-destructive">{errors.description.message}</p>}
      </div>

      <CatalogImageUpload
        itemId={item?.id ?? null}
        currentImageUrl={item?.imageUrl ?? null}
        fallbackImageUrl={skuImagePreview}
        onUploaded={() => {
          // edit mode: upload already done inside component
        }}
        onFilePicked={(file) => setPendingImageFile(file)}
        onRemove={() => {
          if (isEdit && item) {
            updateMutation.mutate({ id: item.id, request: { imageUrl: null } });
          }
        }}
      />

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
          <Select
            value={watch("currencyId")}
            onValueChange={(v) => setValue("currencyId", v)}
          >
            <SelectTrigger id="currencyId" aria-label="Currency">
              <SelectValue placeholder="Select currency" />
            </SelectTrigger>
            <SelectContent>
              {filteredCurrencyOptions.map((c) => (
                <SelectItem key={c.id} value={c.code}>
                  {c.name} ({c.type})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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
              {/* Batch processing is temporarily hidden — not offered when creating/editing catalog items. */}
              <SelectItem value="APPROVAL_REQUIRED">Approval Required</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Provider mapping is category-specific:
          CASH → an XTRM digital gift-card SKU, chosen from the searchable catalog so the value type
          (FIXED vs VARIABLE) and amount bounds are stamped from the vendor. Manual entry stays as the
          escape hatch for SKUs the catalog doesn't surface (or when XTRM is unavailable).
          NON_CASH → a Xoxoday provider item id, which the XTRM gift-card catalog knows nothing about,
          so there is nothing to pick from: plain text entry only. */}
      {category === "CASH" ? (
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <Label htmlFor="providerItemId">SKU (Provider Item ID) *</Label>
            <button
              type="button"
              className="text-xs text-muted-foreground underline-offset-2 hover:underline"
              onClick={() => {
                setManualSku((m) => !m);
                setSelectedSku(null);
              }}
            >
              {manualSku ? "Choose from catalog" : "Enter SKU manually"}
            </button>
          </div>

          {manualSku ? (
            <Input
              id="providerItemId"
              {...register("providerItemId")}
              placeholder="XTRM cash gift-card SKU (e.g. U561621)"
            />
          ) : (
            <GiftCardSkuCombobox
              id="providerItemId"
              value={watch("providerItemId") ?? ""}
              onSelect={handleSkuSelect}
              aria-invalid={!!errors.providerItemId}
            />
          )}

          {selectedSku && !manualSku && (
            <p className="text-xs text-muted-foreground">
              {selectedSku.valueType === "FIXED"
                ? `Fixed value — locked to ${skuValueLabel(selectedSku)}.`
                : `Variable value — redeemable ${skuValueLabel(selectedSku)}.`}
            </p>
          )}
          {errors.providerItemId && (
            <p className="text-sm text-destructive">{errors.providerItemId.message}</p>
          )}
        </div>
      ) : (
        <div className="space-y-1">
          <Label htmlFor="providerItemId">Provider Item ID (Xoxoday) *</Label>
          <Input
            id="providerItemId"
            {...register("providerItemId")}
            placeholder="Xoxoday product / provider item ID"
            aria-invalid={!!errors.providerItemId}
            data-testid="provider-item-id-input"
          />
          <p className="text-xs text-muted-foreground">
            Non-cash rewards are fulfilled by Xoxoday — enter the product ID from their catalog.
          </p>
          {errors.providerItemId && (
            <p className="text-sm text-destructive">{errors.providerItemId.message}</p>
          )}
        </div>
      )}

      {CATALOG_GEOGRAPHIC_SCOPE_ENABLED && (
      <div className="space-y-1">
        <Label>Geographic Scope</Label>
        <MultiSelect
          options={locationOptions.map((o) => ({
            value: o.value,
            label: o.isRegion ? `\u{1F4CD} ${o.label}` : o.label,
          }))}
          selected={derivedSelected}
          displayCount={currentScope.filter((c) => knownCodes.has(c)).length}
          onChange={(values) => {
            const prevSelected = new Set(derivedSelected);
            const nextSelected = new Set(values);
            const added = values.filter((v) => !prevSelected.has(v));
            const removed = [...prevSelected].filter((v) => !nextSelected.has(v));

            const newScope = new Set(currentScope.filter((c) => knownCodes.has(c)));
            for (const code of removed) {
              const children = regionToCountries.get(code);
              if (children && children.length > 0) children.forEach((c) => newScope.delete(c));
              else newScope.delete(code);
            }
            for (const code of added) {
              const children = regionToCountries.get(code);
              if (children && children.length > 0) children.forEach((c) => newScope.add(c));
              else newScope.add(code);
            }
            setValue("geographicScope", [...newScope, ...unmatchedCodes]);
          }}
          placeholder="Select regions or countries"
        />
        {unmatchedCodes.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {unmatchedCodes.map((code) => (
              <span
                key={code}
                className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full bg-muted text-muted-foreground"
              >
                {code}
                <span className="text-xs opacity-60">From sync</span>
                <button
                  type="button"
                  onClick={() =>
                    setValue(
                      "geographicScope",
                      currentScope.filter((c) => c !== code),
                    )
                  }
                  className="ml-1 hover:text-destructive"
                  aria-label={`Remove ${code}`}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}
      </div>
      )}

      {isEdit && (
        <div className="space-y-1">
          <Label htmlFor="minWalletBalance">Min Wallet Balance (optional)</Label>
          <Input
            id="minWalletBalance"
            {...register("minWalletBalance")}
            placeholder="e.g. 100.00"
          />
        </div>
      )}

      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <Switch
            id="isReturnable"
            checked={watch("isReturnable")}
            onCheckedChange={(v) => setValue("isReturnable", v)}
            disabled={category === "CASH"}
            data-testid="isReturnable-switch"
          />
          <Label htmlFor="isReturnable" className={category === "CASH" ? "opacity-50 cursor-not-allowed" : ""}>
            Returnable
          </Label>
          {errors.isReturnable && (
            <p className="text-sm text-destructive">{errors.isReturnable.message}</p>
          )}
        </div>

        {watch("isReturnable") && (
          <div className="space-y-1">
            <Label htmlFor="defaultReturnWindowDays">Return Window (days)</Label>
            <Input
              id="defaultReturnWindowDays"
              type="number"
              min={0}
              {...register("defaultReturnWindowDays")}
            />
          </div>
        )}
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
