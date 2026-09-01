import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Loader2 } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  addFieldToSection,
  updateField,
} from "@/services/builder-config.service";
import { toast } from "sonner";
import type {
  BuilderFieldConfigResponse,
  CreateBuilderFieldRequest,
  UpdateBuilderFieldRequest,
} from "@/types/builder-config.types";
import {
  useAvailableCustomFields,
  type AvailableFieldOption,
} from "@/hooks/useAvailableCustomFields";

const DATA_TYPE_TO_FIELD_TYPE: Record<string, string> = {
  TEXT: "TEXT_BOX",
  LIST: "MULTI_SELECT",
  NUMBER: "NUMBER_INPUT",
  CURRENCY: "NUMBER_INPUT",
  DATE: "DATE_PICKER",
  BOOLEAN: "TOGGLE",
};

function toKebabCase(str: string): string {
  return str
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

interface BuilderFieldEditorProps {
  sectionId: string;
  sectionKey: string;
  incentiveType: string;
  existingFieldKeys: string[];
  field?: BuilderFieldConfigResponse;
  onClose: () => void;
}

export function BuilderFieldEditor({
  sectionId,
  sectionKey,
  incentiveType,
  existingFieldKeys,
  field,
  onClose,
}: BuilderFieldEditorProps) {
  const isEdit = !!field;
  const queryClient = useQueryClient();

  const { data: availableFields, isLoading: fieldsLoading } =
    useAvailableCustomFields(sectionKey, incentiveType, existingFieldKeys);

  // --- Form state ---
  const [selectedFieldId, setSelectedFieldId] = useState("");
  const [selectedOption, setSelectedOption] =
    useState<AvailableFieldOption | null>(null);
  const [displayName, setDisplayName] = useState(field?.displayName ?? "");
  const [helperText, setHelperText] = useState(field?.helperText ?? "");
  const [isMandatory, setIsMandatory] = useState(field?.isMandatory ?? false);
  const [supportsExcelUpload, setSupportsExcelUpload] = useState(
    field?.supportsExcelUpload ?? false,
  );

  function handleFieldSelection(compositeValue: string) {
    setSelectedFieldId(compositeValue);
    const match = availableFields.find((f) => f.fieldId === compositeValue);
    if (match) {
      setSelectedOption(match);
      setDisplayName(match.fieldName);
    }
  }

  // --- Derived values for create ---
  const derivedFieldKey = selectedOption
    ? toKebabCase(selectedOption.fieldName)
    : "";
  const derivedFieldType = selectedOption
    ? (DATA_TYPE_TO_FIELD_TYPE[selectedOption.dataType] ?? "TEXT_BOX")
    : "TEXT_BOX";
  const derivedValueSource =
    selectedOption?.dataType === "LIST" ? "DATA_OBJECT_FIELD" : undefined;
  const derivedDataObjectFieldId = selectedOption?.fieldId ?? undefined;

  // Excel upload only renders for option-list field types in the runtime
  // renderer (DynamicFieldRenderer). Match that gate here so the toggle is
  // disabled — with a tooltip explanation — for incompatible types. In create
  // mode the type is derived from the picked Data Object Field; in edit mode
  // we read the existing field's type, which is defensive against any drift
  // between the editor's mapping and historical persisted values.
  const effectiveFieldType = isEdit ? field?.fieldType : derivedFieldType;
  const excelUploadAvailable =
    effectiveFieldType === "MULTI_SELECT" || effectiveFieldType === "DROPDOWN";
  // Display + submit value: when the field type is incompatible, the toggle
  // reads as off regardless of any earlier intent stored in state. This keeps
  // the visible state and the persisted state in sync, and lets an admin who
  // briefly switches field types not lose their setting if they switch back.
  const effectiveSupportsExcelUpload =
    excelUploadAvailable && supportsExcelUpload;

  // --- Mutations ---
  const createMutation = useMutation({
    mutationFn: (data: CreateBuilderFieldRequest) =>
      addFieldToSection(sectionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["builder-config"] });
      toast.success("Field added");
      onClose();
    },
    onError: (err: unknown) => {
      const message =
        err instanceof Error ? err.message : "Failed to add field";
      toast.error(message);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: UpdateBuilderFieldRequest) =>
      updateField(field!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["builder-config"] });
      toast.success("Field updated");
      onClose();
    },
    onError: (err: unknown) => {
      const message =
        err instanceof Error ? err.message : "Failed to update field";
      toast.error(message);
    },
  });

  const isSaving = createMutation.isPending || updateMutation.isPending;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (isEdit) {
      const payload: UpdateBuilderFieldRequest = {
        displayName: displayName.trim(),
        helperText: helperText.trim() || undefined,
        isMandatory,
        isEligibility: true,
        supportsExcelUpload: effectiveSupportsExcelUpload,
      };
      updateMutation.mutate(payload);
    } else {
      if (!selectedOption || !displayName.trim()) return;

      const payload: CreateBuilderFieldRequest = {
        fieldKey: derivedFieldKey,
        displayName: displayName.trim(),
        fieldType: derivedFieldType,
        helperText: helperText.trim() || undefined,
        isMandatory,
        isEligibility: true,
        dataObjectFieldId: derivedDataObjectFieldId,
        valueSource: derivedValueSource,
        supportsExcelUpload: effectiveSupportsExcelUpload,
      };
      createMutation.mutate(payload);
    }
  }

  // --- Group available fields by data object name ---
  const grouped = availableFields.reduce<
    Record<string, AvailableFieldOption[]>
  >((acc, opt) => {
    const group = opt.dataObjectName;
    if (!acc[group]) acc[group] = [];
    acc[group].push(opt);
    return acc;
  }, {});

  const canSubmit = isEdit ? displayName.trim().length > 0 : !!selectedOption;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit Field" : "Add Field"}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          {!isEdit && (
            <div className="space-y-2">
              <Label htmlFor="dataObjectField">Data Object Field</Label>
              {fieldsLoading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground py-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading available fields...
                </div>
              ) : availableFields.length === 0 ? (
                <p className="text-sm text-muted-foreground py-2">
                  No available fields for this section.
                </p>
              ) : (
                <Select
                  value={selectedFieldId}
                  onValueChange={handleFieldSelection}
                >
                  <SelectTrigger id="dataObjectField">
                    <SelectValue placeholder="Select a data object field" />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(grouped).map(([groupName, fields]) => (
                      <SelectGroup key={groupName}>
                        <SelectLabel>{groupName}</SelectLabel>
                        {fields.map((f) => (
                          <SelectItem key={f.fieldId} value={f.fieldId}>
                            {f.fieldName}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          )}

          {isEdit && field?.dataObjectFieldName && (
            <div className="space-y-1">
              <Label className="text-muted-foreground text-xs">
                Linked Data Object Field
              </Label>
              <p className="text-sm">
                {field.dataObjectName} &gt; {field.dataObjectFieldName}
              </p>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="displayName">Display Name</Label>
            <Input
              id="displayName"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="e.g. Partner Region"
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="helperText">Helper Text</Label>
            <Textarea
              id="helperText"
              value={helperText}
              onChange={(e) => setHelperText(e.target.value)}
              placeholder="Optional description shown below the field"
              rows={2}
            />
          </div>

          <div className="flex items-center justify-between">
            <Label htmlFor="mandatory">Mandatory</Label>
            <Switch
              id="mandatory"
              checked={isMandatory}
              onCheckedChange={setIsMandatory}
            />
          </div>

          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <Label
                htmlFor="excelUpload"
                className={
                  excelUploadAvailable ? undefined : "text-muted-foreground"
                }
              >
                Supports Excel Upload
              </Label>
              {excelUploadAvailable ? (
                <Switch
                  id="excelUpload"
                  checked={effectiveSupportsExcelUpload}
                  onCheckedChange={setSupportsExcelUpload}
                />
              ) : (
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      {/* Span wrapper so the tooltip still receives hover
                          events when the inner Switch is disabled. */}
                      <span className="inline-flex">
                        <Switch
                          id="excelUpload"
                          checked={false}
                          disabled
                          aria-describedby="excelUpload-helper"
                        />
                      </span>
                    </TooltipTrigger>
                    <TooltipContent side="top" className="max-w-[240px] text-xs">
                      Only available for List-type fields. Pick a Data Object
                      Field whose data type is List to enable Excel upload.
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              )}
            </div>
            {!excelUploadAvailable && (
              <p
                id="excelUpload-helper"
                className="text-xs text-muted-foreground"
              >
                Only available for List-type fields.
              </p>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSaving || !canSubmit}>
              {isSaving && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
              {isEdit ? "Save Changes" : "Add Field"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
