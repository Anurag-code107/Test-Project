import { useState } from "react";
import { Plus, Pencil, Trash2, Loader2, Shield } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { toast } from "sonner";
import {
  useRewardCurrencies,
  useCreateRewardCurrency,
  useUpdateRewardCurrency,
  useDeleteRewardCurrency,
} from "@/hooks/useRewardCurrencyApi";
import { getCurrency } from "@/config/currencies";
import type {
  RewardCurrencyResponse,
  RewardCurrencyType,
} from "@/types/reward-currency.types";

interface FormState {
  code: string;
  name: string;
  type: RewardCurrencyType;
  conversionRate: string;
  unit: string;
  isCurrencyFormatted: boolean;
}

const emptyForm = (type: RewardCurrencyType): FormState => ({
  code: "",
  name: "",
  type,
  conversionRate: "",
  unit: "",
  isCurrencyFormatted: false,
});

const blockInvalidChars = (e: React.KeyboardEvent) => {
  if (e.key === "-" || e.key === "e" || e.key === "E") e.preventDefault();
};

function nameToCode(name: string): string {
  const code = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "");
  if (!code) return "";
  const safe = /^[a-z]/.test(code) ? code : `c_${code}`;
  return safe.slice(0, 50);
}

function generateUniqueCode(
  baseName: string,
  existingCodes: string[],
  excludeCode?: string,
): string {
  const base = nameToCode(baseName);
  if (!base) return "";
  const taken = new Set(existingCodes.filter((c) => c !== excludeCode));
  if (!taken.has(base)) return base;
  let n = 2;
  while (taken.has(`${base}_${n}`)) n++;
  const candidate = `${base}_${n}`;
  if (candidate.length <= 50) return candidate;
  return `${base.slice(0, 50 - String(n).length - 1)}_${n}`;
}

export function ManageRewardTypesSection() {
  const { data: currencies, isLoading, isError } = useRewardCurrencies();
  const createMutation = useCreateRewardCurrency();
  const updateMutation = useUpdateRewardCurrency();
  const deleteMutation = useDeleteRewardCurrency();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<FormState>(emptyForm("MONETARY"));
  const [deleteTarget, setDeleteTarget] =
    useState<RewardCurrencyResponse | null>(null);

  const monetary = currencies?.filter((c) => c.type === "MONETARY") ?? [];
  const nonMonetary =
    currencies?.filter((c) => c.type === "NON_MONETARY") ?? [];

  function startCreate(type: RewardCurrencyType) {
    setEditingId(null);
    setIsNew(true);
    setForm(emptyForm(type));
  }

  function startEdit(currency: RewardCurrencyResponse) {
    setEditingId(currency.id);
    setIsNew(false);
    setForm({
      code: currency.code,
      name: currency.name,
      type: currency.type,
      conversionRate: currency.conversionRate?.toString() ?? "",
      unit: currency.unit,
      isCurrencyFormatted: currency.isCurrencyFormatted,
    });
  }

  function cancelEdit() {
    setEditingId(null);
    setIsNew(false);
  }

  async function handleSave() {
    const payload = {
      code: form.code.trim().toLowerCase(),
      name: form.name.trim(),
      type: form.type,
      conversionRate:
        form.type === "MONETARY" && form.conversionRate
          ? parseFloat(form.conversionRate)
          : undefined,
      unit: form.unit.trim(),
      isCurrencyFormatted: form.isCurrencyFormatted,
    };

    try {
      if (isNew) {
        await createMutation.mutateAsync(payload);
        toast.success(`Created "${payload.name}" currency`);
      } else if (editingId) {
        await updateMutation.mutateAsync({ id: editingId, data: payload });
        toast.success(`Updated "${payload.name}" currency`);
      }
      cancelEdit();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Operation failed";
      toast.error(message);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      toast.success(`Deleted "${deleteTarget.name}" currency`);
      setDeleteTarget(null);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Delete failed";
      toast.error(message);
      setDeleteTarget(null);
    }
  }

  const isSaving = createMutation.isPending || updateMutation.isPending;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">
          Loading currencies...
        </span>
      </div>
    );
  }

  if (isError) {
    return (
      <p className="text-sm text-destructive py-4">
        Failed to load currencies. Please try again later.
      </p>
    );
  }

  function renderCurrencyCard(currency: RewardCurrencyResponse) {
    const cfg = getCurrency(currency.code);
    const Icon = cfg.icon;
    const isEditing = editingId === currency.id;

    if (isEditing) {
      return (
        <div
          key={currency.id}
          className="rounded-lg border border-border bg-muted/20 p-4 space-y-3"
        >
          {renderForm(currency.isDefault)}
        </div>
      );
    }

    return (
      <div
        key={currency.id}
        className={`flex items-center gap-3 rounded-lg border p-3 ${cfg.borderClass} ${cfg.bgClass}`}
      >
        <div
          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${cfg.iconBgClass}`}
        >
          <Icon className={`h-4 w-4 ${cfg.iconClass}`} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground">
              {currency.name}
            </span>
            {currency.isDefault && (
              <span className="inline-flex items-center gap-1 rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                <Shield className="h-2.5 w-2.5" />
                Default
              </span>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            {currency.type === "MONETARY"
              ? `${currency.conversionRate}:1 (${currency.conversionRate} ${currency.code} = $1)`
              : `Unit: ${currency.unit || currency.code}`}
          </p>
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => startEdit(currency)}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          {!currency.isDefault && (
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-destructive hover:text-destructive"
              onClick={() => setDeleteTarget(currency)}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          )}
        </div>
      </div>
    );
  }

  function renderForm(isDefault = false) {
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <Label className="text-xs">Display Name</Label>
            <Input
              value={form.name}
              onChange={(e) => {
                const newName = e.target.value;
                if (isDefault) {
                  setForm({ ...form, name: newName });
                  return;
                }
                const existingCodes = currencies?.map((c) => c.code) ?? [];
                const excludeCode = editingId
                  ? currencies?.find((c) => c.id === editingId)?.code
                  : undefined;
                setForm({
                  ...form,
                  name: newName,
                  code: generateUniqueCode(newName, existingCodes, excludeCode),
                });
              }}
              placeholder="e.g. Bonus Bucks"
              className="h-8 text-xs"
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">Code</Label>
            <Input
              value={form.code}
              readOnly
              tabIndex={-1}
              placeholder="Auto-generated from name"
              className="h-8 text-xs bg-muted/50 cursor-default"
            />
            <p className="text-[10px] text-muted-foreground">
              Auto-generated from display name
            </p>
          </div>
        </div>

        {!isNew && (
          <div className="space-y-1">
            <Label className="text-xs">Type</Label>
            <Select
              value={form.type}
              onValueChange={(v) =>
                setForm({ ...form, type: v as RewardCurrencyType })
              }
            >
              <SelectTrigger className="h-8 text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="MONETARY">Monetary</SelectItem>
                <SelectItem value="NON_MONETARY">Non-Monetary</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}

        {form.type === "MONETARY" && (
          <div className="space-y-1">
            <Label className="text-xs">
              Conversion Rate (units per $1 USD)
            </Label>
            <Input
              type="number"
              value={form.conversionRate}
              onChange={(e) =>
                setForm({ ...form, conversionRate: e.target.value })
              }
              onKeyDown={blockInvalidChars}
              placeholder="e.g. 100"
              className="h-8 text-xs"
              min={0}
              step="any"
            />
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <Label className="text-xs">Unit Label</Label>
            <Input
              value={form.unit}
              onChange={(e) => setForm({ ...form, unit: e.target.value })}
              placeholder="e.g. pts, credits"
              className="h-8 text-xs"
            />
          </div>
          <div className="flex items-center gap-2 pt-5">
            <Switch
              checked={form.isCurrencyFormatted}
              onCheckedChange={(v) =>
                setForm({ ...form, isCurrencyFormatted: v })
              }
              className="scale-75"
            />
            <Label className="text-xs">Currency formatted ($)</Label>
          </div>
        </div>

        <div className="flex items-center gap-2 pt-1">
          <Button
            size="sm"
            className="h-7 text-xs"
            onClick={handleSave}
            disabled={isSaving || !form.code || !form.name}
          >
            {isSaving && <Loader2 className="h-3 w-3 mr-1 animate-spin" />}
            {isNew ? "Create" : "Save"}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-7 text-xs"
            onClick={cancelEdit}
          >
            Cancel
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Monetary Currencies */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-semibold text-foreground">
            Monetary Currencies
          </h4>
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs gap-1"
            onClick={() => startCreate("MONETARY")}
            disabled={isNew || !!editingId}
          >
            <Plus className="h-3 w-3" />
            Add Monetary
          </Button>
        </div>
        <div className="space-y-2">
          {monetary.map(renderCurrencyCard)}
          {isNew && form.type === "MONETARY" && (
            <div className="rounded-lg border border-dashed border-border bg-muted/20 p-4 space-y-3">
              {renderForm()}
            </div>
          )}
        </div>
      </div>

      {/* Non-Monetary Currencies */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-semibold text-foreground">
            Non-Monetary Currencies
          </h4>
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs gap-1"
            onClick={() => startCreate("NON_MONETARY")}
            disabled={isNew || !!editingId}
          >
            <Plus className="h-3 w-3" />
            Add Non-Monetary
          </Button>
        </div>
        <div className="space-y-2">
          {nonMonetary.map(renderCurrencyCard)}
          {isNew && form.type === "NON_MONETARY" && (
            <div className="rounded-lg border border-dashed border-border bg-muted/20 p-4 space-y-3">
              {renderForm()}
            </div>
          )}
        </div>
      </div>

      {/* Delete confirmation dialog */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Currency</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete{" "}
              <strong>{deleteTarget?.name}</strong>? This action cannot be
              undone. Currencies that are in use by existing incentives or
              transactions cannot be deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteMutation.isPending && (
                <Loader2 className="h-3 w-3 mr-1 animate-spin" />
              )}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
