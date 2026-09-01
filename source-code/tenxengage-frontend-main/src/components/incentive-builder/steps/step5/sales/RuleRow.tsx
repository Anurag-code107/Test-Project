import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { X, ChevronDown, Loader2, Plus, Upload } from "lucide-react";
import { useState, useRef } from "react";
import type {
  EligibilityRule,
  EligibilityRuleType,
  RuleOperator,
} from "@/types/incentive.types";
import {
  useProducts,
  useCreateProduct,
  useUploadProducts,
} from "@/hooks/useProductApi";
import { useRuleFields } from "@/hooks/useDataObjectApi";
import { useToast } from "@/hooks/use-toast";
import type { RuleFieldResponse } from "@/types/data-object.types";

const blockInvalidChars = (e: React.KeyboardEvent) => {
  if (
    e.key === "." ||
    e.key === "," ||
    e.key === "-" ||
    e.key === "e" ||
    e.key === "E"
  )
    e.preventDefault();
};

interface RuleRowProps {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
  onRemove: () => void;
  canRemove: boolean;
}

export function RuleRow({ rule, onUpdate, onRemove, canRemove }: RuleRowProps) {
  const { data: ruleFields = [], isLoading: fieldsLoading } = useRuleFields(
    undefined,
    "Sales Data",
  );

  // Find the active rule field
  const activeField = rule.ruleType
    ? ruleFields.find((f) => f.id === rule.ruleType)
    : null;

  return (
    <div className="flex items-start gap-2 bg-muted/30 rounded-md border border-border p-3">
      <div className="flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted-foreground whitespace-nowrap">
            The sale
          </span>
          <Select
            value={rule.ruleType || undefined}
            onValueChange={(v) =>
              onUpdate((r) => ({
                ...r,
                ruleType: v as EligibilityRuleType,
                selectedProducts: [],
                operator: undefined,
                value: undefined,
                valueMax: undefined,
                customerTypes: [],
                listValues: [],
              }))
            }
          >
            <SelectTrigger className="w-[200px] h-8 text-sm">
              <SelectValue placeholder="choose criteria..." />
            </SelectTrigger>
            <SelectContent>
              {fieldsLoading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                </div>
              ) : (
                <>
                  {ruleFields.map((f) => (
                    <SelectItem key={f.id} value={f.id}>
                      {f.ruleLabel || f.name}
                    </SelectItem>
                  ))}
                </>
              )}
            </SelectContent>
          </Select>

          {activeField && (
            <DynamicFieldTail
              rule={rule}
              field={activeField}
              onUpdate={onUpdate}
            />
          )}
        </div>
      </div>

      {canRemove && (
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
          onClick={onRemove}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      )}
    </div>
  );
}

/** Dynamic tail that renders based on ruleWidget (if set) or FieldDataType */
function DynamicFieldTail({
  rule,
  field,
  onUpdate,
}: {
  rule: EligibilityRule;
  field: RuleFieldResponse;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  // Specialized widget override
  if (field.ruleWidget === "PRODUCT_PICKER") {
    return <ProductsTail rule={rule} onUpdate={onUpdate} />;
  }

  switch (field.dataType) {
    case "NUMBER":
    case "CURRENCY":
      return (
        <NumericTail
          rule={rule}
          onUpdate={onUpdate}
          isCurrency={field.dataType === "CURRENCY"}
        />
      );
    case "LIST":
      return (
        <ListTail
          rule={rule}
          options={field.sampleValues ?? []}
          onUpdate={onUpdate}
        />
      );
    case "TEXT":
      return <TextTail rule={rule} onUpdate={onUpdate} />;
    case "BOOLEAN":
      return <BooleanTail rule={rule} onUpdate={onUpdate} />;
    case "DATE":
      return <DateTail rule={rule} onUpdate={onUpdate} />;
    default:
      return <TextTail rule={rule} onUpdate={onUpdate} />;
  }
}

function NumericTail({
  rule,
  onUpdate,
  isCurrency,
}: {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
  isCurrency: boolean;
}) {
  return (
    <>
      <Select
        value={rule.operator ?? ""}
        onValueChange={(v) =>
          onUpdate((r) => ({ ...r, operator: v as RuleOperator }))
        }
      >
        <SelectTrigger className="w-[180px] h-8 text-sm">
          <SelectValue placeholder="condition..." />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="GREATER_THAN">greater than</SelectItem>
          <SelectItem value="GREATER_THAN_OR_EQUAL">
            greater than or equal to
          </SelectItem>
          <SelectItem value="LESS_THAN">less than</SelectItem>
          <SelectItem value="EQUALS">equal to</SelectItem>
          <SelectItem value="BETWEEN">between</SelectItem>
        </SelectContent>
      </Select>

      <div className="relative">
        {isCurrency && (
          <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
            $
          </span>
        )}
        <Input
          type="number"
          min="0"
          step="1"
          className={`h-8 w-[120px] text-sm ${isCurrency ? "pl-6" : ""}`}
          placeholder="amount"
          value={rule.value ?? ""}
          onKeyDown={blockInvalidChars}
          onChange={(e) => onUpdate((r) => ({ ...r, value: e.target.value }))}
        />
      </div>

      {rule.operator === "BETWEEN" && (
        <>
          <span className="text-muted-foreground">and</span>
          <div className="relative">
            {isCurrency && (
              <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground text-xs">
                $
              </span>
            )}
            <Input
              type="number"
              min="0"
              step="1"
              className={`h-8 w-[120px] text-sm ${isCurrency ? "pl-6" : ""}`}
              placeholder="max"
              value={rule.valueMax ?? ""}
              onKeyDown={blockInvalidChars}
              onChange={(e) =>
                onUpdate((r) => ({ ...r, valueMax: e.target.value }))
              }
            />
          </div>
        </>
      )}
    </>
  );
}

function ListTail({
  rule,
  options,
  onUpdate,
}: {
  rule: EligibilityRule;
  options: string[];
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  const selected = rule.listValues ?? [];

  return (
    <>
      <span className="text-muted-foreground">of</span>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="outline"
            size="sm"
            className="h-8 text-sm min-w-[150px] justify-between"
          >
            {selected.length > 0 ? selected.join(", ") : "Select values..."}
            <ChevronDown className="h-3 w-3 opacity-50 ml-1" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          className="min-w-[180px] max-h-[280px] overflow-y-auto"
          align="start"
        >
          <div className="flex items-center justify-between px-2 py-1.5">
            <button
              type="button"
              className="text-xs text-primary hover:underline"
              onClick={() =>
                onUpdate((r) => ({ ...r, listValues: [...options] }))
              }
            >
              Select All
            </button>
            <button
              type="button"
              className="text-xs text-muted-foreground hover:underline"
              onClick={() => onUpdate((r) => ({ ...r, listValues: [] }))}
            >
              Clear
            </button>
          </div>
          <DropdownMenuSeparator />
          {options.map((opt) => (
            <DropdownMenuCheckboxItem
              key={opt}
              checked={selected.includes(opt)}
              onCheckedChange={(checked) =>
                onUpdate((r) => ({
                  ...r,
                  listValues: checked
                    ? [...(r.listValues ?? []), opt]
                    : (r.listValues ?? []).filter((v) => v !== opt),
                }))
              }
            >
              {opt}
            </DropdownMenuCheckboxItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

function TextTail({
  rule,
  onUpdate,
}: {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  return (
    <>
      <Select
        value={rule.operator ?? ""}
        onValueChange={(v) =>
          onUpdate((r) => ({ ...r, operator: v as RuleOperator }))
        }
      >
        <SelectTrigger className="w-[140px] h-8 text-sm">
          <SelectValue placeholder="condition..." />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="EQUALS">equals</SelectItem>
          <SelectItem value="IN">contains</SelectItem>
          <SelectItem value="NOT_IN">does not contain</SelectItem>
        </SelectContent>
      </Select>
      <Input
        className="h-8 w-[160px] text-sm"
        placeholder="value"
        value={rule.value ?? ""}
        onChange={(e) => onUpdate((r) => ({ ...r, value: e.target.value }))}
      />
    </>
  );
}

function BooleanTail({
  rule,
  onUpdate,
}: {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  return (
    <>
      <span className="text-muted-foreground">is</span>
      <Select
        value={rule.value ?? ""}
        onValueChange={(v) =>
          onUpdate((r) => ({
            ...r,
            value: v,
            operator: "EQUALS" as RuleOperator,
          }))
        }
      >
        <SelectTrigger className="w-[100px] h-8 text-sm">
          <SelectValue placeholder="..." />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="true">True</SelectItem>
          <SelectItem value="false">False</SelectItem>
        </SelectContent>
      </Select>
    </>
  );
}

function DateTail({
  rule,
  onUpdate,
}: {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  return (
    <>
      <Select
        value={rule.operator ?? ""}
        onValueChange={(v) =>
          onUpdate((r) => ({ ...r, operator: v as RuleOperator }))
        }
      >
        <SelectTrigger className="w-[140px] h-8 text-sm">
          <SelectValue placeholder="condition..." />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="EQUALS">on</SelectItem>
          <SelectItem value="GREATER_THAN">after</SelectItem>
          <SelectItem value="LESS_THAN">before</SelectItem>
          <SelectItem value="BETWEEN">between</SelectItem>
        </SelectContent>
      </Select>
      <Input
        type="date"
        className="h-8 w-[150px] text-sm"
        value={rule.value ?? ""}
        onChange={(e) => onUpdate((r) => ({ ...r, value: e.target.value }))}
      />
      {rule.operator === "BETWEEN" && (
        <>
          <span className="text-muted-foreground">and</span>
          <Input
            type="date"
            className="h-8 w-[150px] text-sm"
            value={rule.valueMax ?? ""}
            onChange={(e) =>
              onUpdate((r) => ({ ...r, valueMax: e.target.value }))
            }
          />
        </>
      )}
    </>
  );
}

function ProductsTail({
  rule,
  onUpdate,
}: {
  rule: EligibilityRule;
  onUpdate: (updater: (r: EligibilityRule) => EligibilityRule) => void;
}) {
  const { data: allProducts = [], isLoading } = useProducts();
  const selected = rule.selectedProducts ?? [];
  const createProduct = useCreateProduct();
  const uploadProducts = useUploadProducts();
  const { toast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [showAddForm, setShowAddForm] = useState(false);
  const [newName, setNewName] = useState("");
  const [newCategory, setNewCategory] = useState("");

  const grouped = allProducts.reduce<Record<string, typeof allProducts>>(
    (acc, p) => {
      (acc[p.category] ??= []).push(p);
      return acc;
    },
    {},
  );

  const handleAddProduct = () => {
    if (!newName.trim()) return;
    createProduct.mutate(
      { name: newName.trim(), category: newCategory.trim() || undefined },
      {
        onSuccess: (product) => {
          toast({
            title: "Product added",
            description: `${product.name} (${product.sku})`,
          });
          setNewName("");
          setNewCategory("");
          setShowAddForm(false);
        },
        onError: (err) => {
          toast({
            title: "Error",
            description:
              err instanceof Error ? err.message : "Failed to add product",
            variant: "destructive",
          });
        },
      },
    );
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    uploadProducts.mutate(file, {
      onSuccess: (res) => {
        toast({
          title: "Upload complete",
          description: `${res.added} added, ${res.skipped} skipped`,
        });
      },
      onError: (err) => {
        toast({
          title: "Upload failed",
          description: err instanceof Error ? err.message : "Failed to upload",
          variant: "destructive",
        });
      },
    });
    e.target.value = "";
  };

  return (
    <>
      <span className="text-muted-foreground">that include</span>
      <div className="flex items-center gap-1">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              className="h-8 text-sm min-w-[160px] justify-between"
            >
              {selected.length > 0
                ? `${selected.length} product${selected.length > 1 ? "s" : ""}`
                : "Select products..."}
              <ChevronDown className="h-3 w-3 opacity-50 ml-1" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="max-h-[340px] overflow-y-auto min-w-[280px]"
            align="start"
          >
            {isLoading ? (
              <div className="flex items-center justify-center py-4">
                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
              </div>
            ) : (
              <>
                <div className="sticky top-0 bg-popover z-10">
                  <div className="flex items-center justify-between px-2 py-1.5">
                    <button
                      type="button"
                      className="text-xs text-primary hover:underline"
                      onClick={() =>
                        onUpdate((r) => ({
                          ...r,
                          selectedProducts: allProducts.map((p) => p.sku),
                        }))
                      }
                    >
                      Select All
                    </button>
                    <button
                      type="button"
                      className="text-xs text-primary hover:underline"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        setShowAddForm((v) => !v);
                      }}
                    >
                      <span className="flex items-center gap-1">
                        <Plus className="h-3 w-3" />
                        Add
                      </span>
                    </button>
                    <button
                      type="button"
                      className="text-xs text-muted-foreground hover:underline"
                      onClick={() =>
                        onUpdate((r) => ({ ...r, selectedProducts: [] }))
                      }
                    >
                      Clear
                    </button>
                  </div>
                  {showAddForm && (
                    <div
                      className="px-2 pb-2 space-y-2"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <Input
                        className="h-7 text-xs"
                        placeholder="Product name"
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        onKeyDown={(e) => {
                          e.stopPropagation();
                          if (e.key === "Enter") handleAddProduct();
                        }}
                        autoFocus
                      />
                      <Input
                        className="h-7 text-xs"
                        placeholder="Category (optional)"
                        value={newCategory}
                        onChange={(e) => setNewCategory(e.target.value)}
                        onKeyDown={(e) => {
                          e.stopPropagation();
                          if (e.key === "Enter") handleAddProduct();
                        }}
                      />
                      <div className="flex gap-1">
                        <Button
                          size="sm"
                          className="h-7 text-xs flex-1"
                          onClick={handleAddProduct}
                          disabled={!newName.trim() || createProduct.isPending}
                        >
                          {createProduct.isPending ? (
                            <Loader2 className="h-3 w-3 animate-spin" />
                          ) : (
                            "Add"
                          )}
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 text-xs"
                          onClick={() => {
                            setShowAddForm(false);
                            setNewName("");
                            setNewCategory("");
                          }}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
                <DropdownMenuSeparator />
                {Object.entries(grouped).map(([category, products]) => (
                  <div key={category}>
                    <div className="px-2 py-1 text-xs font-semibold text-muted-foreground">
                      {category}
                    </div>
                    {products.map((p) => (
                      <DropdownMenuCheckboxItem
                        key={p.sku}
                        checked={selected.includes(p.sku)}
                        onCheckedChange={(checked) =>
                          onUpdate((r) => ({
                            ...r,
                            selectedProducts: checked
                              ? [...(r.selectedProducts ?? []), p.sku]
                              : (r.selectedProducts ?? []).filter(
                                  (id) => id !== p.sku,
                                ),
                          }))
                        }
                      >
                        {p.name}
                      </DropdownMenuCheckboxItem>
                    ))}
                  </div>
                ))}
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv"
          className="hidden"
          onChange={handleFileUpload}
        />
        <Button
          variant="outline"
          size="icon"
          className="h-8 w-8 shrink-0"
          title="Upload products CSV"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadProducts.isPending}
        >
          {uploadProducts.isPending ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <Upload className="h-3.5 w-3.5" />
          )}
        </Button>
      </div>
      {selected.length > 0 && (
        <div className="flex flex-wrap gap-1 w-full pt-1">
          {selected.slice(0, 5).map((sku) => {
            const p = allProducts.find((x) => x.sku === sku);
            return p ? (
              <Badge key={sku} variant="secondary" className="text-xs gap-1">
                {p.name}
                <X
                  className="h-2.5 w-2.5 cursor-pointer"
                  onClick={() =>
                    onUpdate((r) => ({
                      ...r,
                      selectedProducts: (r.selectedProducts ?? []).filter(
                        (id) => id !== sku,
                      ),
                    }))
                  }
                />
              </Badge>
            ) : null;
          })}
          {selected.length > 5 && (
            <Badge variant="outline" className="text-xs">
              +{selected.length - 5} more
            </Badge>
          )}
        </div>
      )}
    </>
  );
}
