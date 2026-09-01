import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  ArrowLeft,
  Plus,
  Pencil,
  Trash2,
  Loader2,
  Table2,
  FileSpreadsheet,
  Link2,
  ArrowRightLeft,
  AlertCircle,
  AlertTriangle,
  Lock,
} from "lucide-react";
import { toast } from "sonner";
import {
  useDataObject,
  useAddField,
  useUpdateField,
  useDeleteField,
} from "@/hooks/useDataObjectApi";
import {
  useBuilderFieldReferences,
  type BuilderFieldReference,
} from "@/hooks/useBuilderFieldReferences";
import {
  DataOperationsPanel,
  type ExpandedSection,
} from "./DataOperationsPanel";
import type {
  DataObjectFieldResponse,
  FieldDataType,
  CreateFieldRequest,
  UpdateFieldRequest,
} from "@/types/data-object.types";

interface DataObjectDetailProps {
  dataObjectId: string;
  onBack: () => void;
  expandedSection?: ExpandedSection;
  onExpandedChange?: (section: ExpandedSection) => void;
}

const FIELD_DATA_TYPES: { value: FieldDataType; label: string }[] = [
  { value: "TEXT", label: "Text" },
  { value: "NUMBER", label: "Number" },
  { value: "CURRENCY", label: "Currency" },
  { value: "DATE", label: "Date" },
  { value: "BOOLEAN", label: "Boolean" },
  { value: "LIST", label: "List" },
];

const dataTypeColors: Record<FieldDataType, string> = {
  TEXT: "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
  NUMBER:
    "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
  CURRENCY:
    "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400",
  DATE: "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400",
  BOOLEAN:
    "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400",
  LIST: "bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400",
};

export function DataObjectDetail({
  dataObjectId,
  onBack,
  expandedSection,
  onExpandedChange,
}: DataObjectDetailProps) {
  const { data: dataObject, isLoading } = useDataObject(dataObjectId);
  const addFieldMutation = useAddField();
  const updateFieldMutation = useUpdateField();
  const deleteFieldMutation = useDeleteField();
  const { findReferences: findBuilderReferences } = useBuilderFieldReferences();

  const [fieldDialogOpen, setFieldDialogOpen] = useState(false);
  const [editingField, setEditingField] =
    useState<DataObjectFieldResponse | null>(null);
  const [deleteFieldId, setDeleteFieldId] = useState<string | null>(null);

  // Field form state
  const [fieldName, setFieldName] = useState("");
  const [fieldDescription, setFieldDescription] = useState("");
  const [fieldDataType, setFieldDataType] = useState<FieldDataType>("TEXT");
  const [fieldRuleLabel, setFieldRuleLabel] = useState("");
  const [fieldExcludeFromRules, setFieldExcludeFromRules] = useState(false);

  function openAddField() {
    setEditingField(null);
    setFieldName("");
    setFieldDescription("");
    setFieldDataType("TEXT");
    setFieldRuleLabel("");
    setFieldExcludeFromRules(false);
    setFieldDialogOpen(true);
  }

  function openEditField(field: DataObjectFieldResponse) {
    setEditingField(field);
    setFieldName(field.name);
    setFieldDescription(field.description ?? "");
    setFieldDataType(field.dataType);
    setFieldRuleLabel(field.ruleLabel ?? "");
    setFieldExcludeFromRules(field.excludeFromRules);
    setFieldDialogOpen(true);
  }

  function handleFieldSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!fieldName.trim()) {
      toast.error("Field name is required");
      return;
    }

    if (editingField) {
      const data: UpdateFieldRequest = {
        name: fieldName.trim(),
        description: fieldDescription.trim() || undefined,
        dataType: fieldDataType,
        ruleLabel: fieldRuleLabel.trim() || undefined,
        excludeFromRules: fieldExcludeFromRules,
      };
      updateFieldMutation.mutate(
        { dataObjectId, fieldId: editingField.id, data },
        {
          onSuccess: () => {
            toast.success("Field updated");
            setFieldDialogOpen(false);
          },
          onError: () => toast.error("Failed to update field"),
        },
      );
    } else {
      const data: CreateFieldRequest = {
        name: fieldName.trim(),
        description: fieldDescription.trim() || undefined,
        dataType: fieldDataType,
        ruleLabel: fieldRuleLabel.trim() || undefined,
        excludeFromRules: fieldExcludeFromRules,
      };
      addFieldMutation.mutate(
        { dataObjectId, data },
        {
          onSuccess: () => {
            toast.success("Field added");
            setFieldDialogOpen(false);
          },
          onError: () => toast.error("Failed to add field"),
        },
      );
    }
  }

  function handleDeleteField() {
    if (!deleteFieldId) return;
    deleteFieldMutation.mutate(
      { dataObjectId, fieldId: deleteFieldId },
      {
        onSuccess: () => {
          toast.success("Field deleted");
          setDeleteFieldId(null);
        },
        onError: () => toast.error("Failed to delete field"),
      },
    );
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!dataObject) return null;

  const isMutating =
    addFieldMutation.isPending || updateFieldMutation.isPending;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={onBack}
            className="h-8 w-8"
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-semibold text-foreground">
                {dataObject.name}
              </h3>
              {dataObject.isDefault && (
                <Badge variant="secondary" className="text-xs">
                  Default
                </Badge>
              )}
            </div>
            {dataObject.description && (
              <p className="text-sm text-muted-foreground">
                {dataObject.description}
              </p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {dataObject.connectorMapping && (
            <Badge variant="outline" className="gap-1.5">
              <Link2 className="h-3 w-3" />
              {dataObject.connectorMapping.connectorName}
            </Badge>
          )}
        </div>
      </div>

      {/* Data Operations & Tagging - Collapsible, above fields */}
      <DataOperationsPanel
        dataObject={dataObject}
        expandedSection={expandedSection}
        onExpandedChange={onExpandedChange}
      />

      {/* Fields Table */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Table2 className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-base text-foreground">
                Fields
              </CardTitle>
              <Badge variant="secondary" className="text-xs">
                {dataObject.fields.length}
              </Badge>
            </div>
            <Button size="sm" onClick={openAddField} className="gap-1.5">
              <Plus className="h-3.5 w-3.5" />
              Add Field
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {dataObject.fields.length > 0 ? (
            <div className="rounded-lg border overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Field Name</TableHead>
                    <TableHead>Data Type</TableHead>
                    <TableHead className="hidden md:table-cell">
                      Description
                    </TableHead>
                    {dataObject.name === "Sales Data" && (
                      <TableHead className="hidden lg:table-cell">
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <span className="inline-flex items-center gap-1 cursor-help border-b border-dashed border-muted-foreground/40">
                                Rule Label
                                <AlertCircle className="h-3 w-3 text-muted-foreground" />
                              </span>
                            </TooltipTrigger>
                            <TooltipContent
                              side="top"
                              className="max-w-[280px] text-xs"
                            >
                              The natural-language phrase used in the Incentive
                              Rules Engine sentence builder (e.g. "has
                              products", "has a booking amount"). Fields without
                              a rule label or marked as excluded won't appear in
                              the rules dropdown.
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      </TableHead>
                    )}
                    {dataObject.name === "Partner Data" && (
                      <TableHead className="hidden lg:table-cell">
                        Source
                      </TableHead>
                    )}
                    {dataObject.name === "Partner User Data" && (
                      <>
                        <TableHead className="text-center">
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <span className="inline-flex items-center gap-1 cursor-help border-b border-dashed border-muted-foreground/40">
                                  Profile
                                  <AlertCircle className="h-3 w-3 text-muted-foreground" />
                                </span>
                              </TooltipTrigger>
                              <TooltipContent
                                side="top"
                                className="max-w-[240px] text-xs"
                              >
                                When enabled, this field is displayed on the
                                partner user's My Profile page.
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        </TableHead>
                        <TableHead className="text-center">
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <span className="inline-flex items-center gap-1 cursor-help border-b border-dashed border-muted-foreground/40">
                                  Editable
                                  <AlertCircle className="h-3 w-3 text-muted-foreground" />
                                </span>
                              </TooltipTrigger>
                              <TooltipContent
                                side="top"
                                className="max-w-[240px] text-xs"
                              >
                                When enabled, the partner user can edit this
                                field on their My Profile page. Requires Profile
                                to be enabled first.
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        </TableHead>
                      </>
                    )}
                    <TableHead className="w-[100px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {dataObject.fields.map((field) => (
                    <TableRow key={field.id}>
                      <TableCell className="font-medium text-foreground">
                        <div className="flex items-center gap-1.5">
                          {field.name}
                          {field.mandatory && (
                            <TooltipProvider>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Lock className="h-3 w-3 text-muted-foreground" />
                                </TooltipTrigger>
                                <TooltipContent side="top" className="text-xs">
                                  Required field — cannot be removed
                                </TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge
                          className={`text-xs ${dataTypeColors[field.dataType]}`}
                        >
                          {FIELD_DATA_TYPES.find(
                            (dt) => dt.value === field.dataType,
                          )?.label ?? field.dataType}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm hidden md:table-cell max-w-[250px] truncate">
                        {field.description}
                      </TableCell>
                      {dataObject.name === "Sales Data" && (
                        <TableCell className="hidden lg:table-cell">
                          {field.ruleLabel && !field.excludeFromRules ? (
                            <Badge variant="outline" className="text-xs">
                              {field.ruleLabel}
                            </Badge>
                          ) : field.excludeFromRules ? (
                            <span className="text-xs text-muted-foreground italic">
                              excluded
                            </span>
                          ) : (
                            <span className="text-xs text-muted-foreground">
                              —
                            </span>
                          )}
                        </TableCell>
                      )}
                      {dataObject.name === "Partner Data" && (
                        <TableCell className="hidden lg:table-cell">
                          {field.isLocationHierarchyField ? (
                            <Badge className="text-xs bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400">
                              Location Hierarchy
                            </Badge>
                          ) : (
                            <span className="text-xs text-muted-foreground">
                              Data Field
                            </span>
                          )}
                        </TableCell>
                      )}
                      {dataObject.name === "Partner User Data" && (
                        <>
                          <TableCell className="text-center">
                            <Switch
                              checked={field.visibleOnProfile}
                              onCheckedChange={(checked) =>
                                updateFieldMutation.mutate(
                                  {
                                    dataObjectId,
                                    fieldId: field.id,
                                    data: {
                                      visibleOnProfile: checked,
                                      ...(checked
                                        ? {}
                                        : { editableByUser: false }),
                                    },
                                  },
                                  {
                                    onSuccess: () =>
                                      toast.success(
                                        `Profile visibility updated for ${field.name}`,
                                      ),
                                    onError: () =>
                                      toast.error("Failed to update field"),
                                  },
                                )
                              }
                            />
                          </TableCell>
                          <TableCell className="text-center">
                            {field.name === "Email" ? (
                              <TooltipProvider>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span className="inline-flex">
                                      <Switch checked={false} disabled />
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent
                                    side="top"
                                    className="max-w-[240px] text-xs"
                                  >
                                    Email is the login credential and cannot be
                                    made user-editable. Email changes must go
                                    through an admin workflow.
                                  </TooltipContent>
                                </Tooltip>
                              </TooltipProvider>
                            ) : (
                              <Switch
                                checked={field.editableByUser}
                                disabled={!field.visibleOnProfile}
                                onCheckedChange={(checked) =>
                                  updateFieldMutation.mutate(
                                    {
                                      dataObjectId,
                                      fieldId: field.id,
                                      data: { editableByUser: checked },
                                    },
                                    {
                                      onSuccess: () =>
                                        toast.success(
                                          `Editability updated for ${field.name}`,
                                        ),
                                      onError: () =>
                                        toast.error("Failed to update field"),
                                    },
                                  )
                                }
                              />
                            )}
                          </TableCell>
                        </>
                      )}
                      <TableCell>
                        {field.isLocationHierarchyField ? (
                          <span className="text-xs text-muted-foreground italic pl-1">
                            auto
                          </span>
                        ) : (
                          <div className="flex items-center gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => openEditField(field)}
                            >
                              <Pencil className="h-3.5 w-3.5" />
                            </Button>
                            {field.mandatory ? (
                              <TooltipProvider>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <span className="inline-flex items-center justify-center h-7 w-7">
                                      <Trash2 className="h-3.5 w-3.5 text-muted-foreground/30" />
                                    </span>
                                  </TooltipTrigger>
                                  <TooltipContent
                                    side="top"
                                    className="text-xs"
                                  >
                                    Required field — cannot be removed
                                  </TooltipContent>
                                </Tooltip>
                              </TooltipProvider>
                            ) : (
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-7 w-7 text-destructive hover:text-destructive"
                                onClick={() => setDeleteFieldId(field.id)}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </Button>
                            )}
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              <FileSpreadsheet className="h-8 w-8 mx-auto mb-2 opacity-40" />
              <p className="text-sm">
                No fields defined yet. Add fields to define the data structure.
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Connector Mapping Summary */}
      {dataObject.connectorMapping && (
        <Card className="border-dashed">
          <CardHeader className="pb-3">
            <div className="flex items-center gap-2">
              <Link2 className="h-5 w-5 text-primary" />
              <CardTitle className="text-base text-foreground">
                Mapped to {dataObject.connectorMapping.connectorName}
              </CardTitle>
              <Badge variant="outline" className="text-xs">
                {dataObject.connectorMapping.mappings.length} of{" "}
                {dataObject.fields.length} fields mapped
              </Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
              {dataObject.connectorMapping.mappings.map((m) => {
                const field = dataObject.fields.find((f) => f.id === m.fieldId);
                return (
                  <div
                    key={m.fieldId}
                    className="flex items-center gap-2 text-xs bg-muted/50 rounded-md px-3 py-2"
                  >
                    <span className="font-medium text-foreground">
                      {field?.name || m.fieldId}
                    </span>
                    <ArrowRightLeft className="h-3 w-3 text-muted-foreground shrink-0" />
                    <span className="text-muted-foreground truncate">
                      {m.sourceTable}.{m.sourceField}
                    </span>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Add/Edit Field Dialog */}
      <Dialog open={fieldDialogOpen} onOpenChange={setFieldDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editingField ? "Edit Field" : "Add Field"}
            </DialogTitle>
            <DialogDescription>
              {editingField
                ? "Update the field configuration"
                : "Add a new field to this data object"}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleFieldSubmit} className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="field-name">Field Name</Label>
              <Input
                id="field-name"
                value={fieldName}
                onChange={(e) => setFieldName(e.target.value)}
                placeholder="e.g. Net Bookings"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="field-desc">Description</Label>
              <Textarea
                id="field-desc"
                value={fieldDescription}
                onChange={(e) => setFieldDescription(e.target.value)}
                placeholder="What does this field represent?"
                rows={2}
              />
            </div>
            <div className="space-y-2">
              <Label>Data Type</Label>
              <Select
                value={fieldDataType}
                onValueChange={(v) => setFieldDataType(v as FieldDataType)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {FIELD_DATA_TYPES.map((dt) => (
                    <SelectItem key={dt.value} value={dt.value}>
                      {dt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {dataObject.name === "Sales Data" && (
              <>
                <div className="space-y-2">
                  <div className="flex items-center gap-1.5">
                    <Label htmlFor="field-rule-label">
                      Rule Label{" "}
                      <span className="text-muted-foreground text-xs">
                        (optional)
                      </span>
                    </Label>
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <AlertCircle className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                        </TooltipTrigger>
                        <TooltipContent
                          side="top"
                          className="max-w-[280px] text-xs"
                        >
                          This label is what users will see in the drop-down
                          menu of the Incentive Rules Engine. Instead of using
                          technical field names like "Product SKU", you can
                          provide natural-sounding English grammar like "has
                          products" so the sentence reads smoothly.
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </div>
                  <Input
                    id="field-rule-label"
                    value={fieldRuleLabel}
                    onChange={(e) => setFieldRuleLabel(e.target.value)}
                    placeholder='e.g. "has products", "has a booking amount"'
                  />
                  <p className="text-xs text-muted-foreground">
                    Natural language label used in the Incentive Rules Engine
                    sentence builder
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Switch
                    id="field-exclude"
                    checked={fieldExcludeFromRules}
                    onCheckedChange={setFieldExcludeFromRules}
                  />
                  <Label
                    htmlFor="field-exclude"
                    className="text-sm font-normal"
                  >
                    Exclude from Incentive Rules Engine
                  </Label>
                </div>
              </>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setFieldDialogOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isMutating || !fieldName.trim()}>
                {isMutating && (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                )}
                {editingField ? "Save Changes" : "Add Field"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Field Confirm */}
      <DeleteFieldDialog
        deleteFieldId={deleteFieldId}
        deletingFieldName={
          dataObject.fields.find((f) => f.id === deleteFieldId)?.name ?? null
        }
        builderReferences={
          deleteFieldId ? findBuilderReferences(deleteFieldId) : []
        }
        isPending={deleteFieldMutation.isPending}
        onCancel={() => setDeleteFieldId(null)}
        onConfirm={handleDeleteField}
      />
    </div>
  );
}

function DeleteFieldDialog({
  deleteFieldId,
  deletingFieldName,
  builderReferences,
  isPending,
  onCancel,
  onConfirm,
}: {
  deleteFieldId: string | null;
  deletingFieldName: string | null;
  builderReferences: BuilderFieldReference[];
  isPending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const refCount = builderReferences.length;
  const hasReferences = refCount > 0;

  return (
    <AlertDialog
      open={!!deleteFieldId}
      onOpenChange={(open) => !open && onCancel()}
    >
      <AlertDialogContent className="max-w-md">
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Field</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="space-y-3 text-sm text-muted-foreground">
              <p>
                Are you sure you want to delete
                {deletingFieldName ? (
                  <>
                    {" "}
                    <span className="font-medium text-foreground">
                      &ldquo;{deletingFieldName}&rdquo;
                    </span>
                  </>
                ) : (
                  " this field"
                )}
                ? This action cannot be undone.
              </p>

              {hasReferences && (
                <div className="rounded-md border border-amber-500/50 bg-amber-50 dark:bg-amber-950/30 p-3 space-y-2">
                  <div className="flex items-start gap-2">
                    <AlertTriangle className="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
                    <p className="text-amber-900 dark:text-amber-200 font-medium">
                      Used by {refCount} Builder Config{" "}
                      {refCount === 1 ? "field" : "fields"}
                    </p>
                  </div>
                  <p className="text-xs text-amber-800 dark:text-amber-300 pl-6">
                    Deleting will mark the following as invalid until you
                    reassign or remove them in Builder Config:
                  </p>
                  <ul className="pl-6 space-y-1 max-h-40 overflow-auto">
                    {builderReferences.map((ref) => (
                      <li
                        key={`${ref.incentiveType}-${ref.field.id}`}
                        className="text-xs text-amber-900 dark:text-amber-200"
                      >
                        <span className="font-medium">
                          {ref.field.displayName}
                        </span>{" "}
                        <span className="text-amber-700 dark:text-amber-400">
                          — {ref.incentiveType.toLowerCase()} ·{" "}
                          {ref.section.displayName}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            onClick={onConfirm}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
            {hasReferences ? "Delete and flag dependents" : "Delete"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
