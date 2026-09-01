import { useState } from "react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Info,
  Lock,
  Pencil,
  Plus,
  ShieldCheck,
  Trash2,
  Loader2,
} from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
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
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { removeField } from "@/services/builder-config.service";
import { BuilderFieldEditor } from "./BuilderFieldEditor";
import { toast } from "sonner";
import type {
  BuilderSectionConfigResponse,
  BuilderFieldConfigResponse,
} from "@/types/builder-config.types";

const FIELD_TYPE_LABELS: Record<string, string> = {
  TEXT_BOX: "Text",
  DROPDOWN: "Dropdown",
  MULTI_SELECT: "Multi-Select Dropdown",
  DATE_PICKER: "Date Picker",
  NUMBER_INPUT: "Number",
  TOGGLE: "Toggle",
  TEXT_AREA: "Text Area",
};

const VALUE_SOURCE_LABELS: Record<string, string> = {
  LOCATION_HIERARCHY: "Location Mapping",
  CLIENT_ROLES: "Client Roles",
  DATA_OBJECT_FIELD: "Data Object Field",
  ACTIVITY_CATEGORIES: "Activity Categories",
  STATIC: "Static Values",
};

interface BuilderConfigSectionProps {
  section: BuilderSectionConfigResponse;
  incentiveType: string;
  isFieldInvalid?: (field: BuilderFieldConfigResponse) => boolean;
}

function FieldCard({
  field,
  onEdit,
  onDelete,
  isInvalid,
}: {
  field: BuilderFieldConfigResponse;
  onEdit: (field: BuilderFieldConfigResponse) => void;
  onDelete: (field: BuilderFieldConfigResponse) => void;
  isInvalid: boolean;
}) {
  const typeLabel = FIELD_TYPE_LABELS[field.fieldType] ?? field.fieldType;
  // Determine source display: prefer linked data object name over generic label
  const hasLinkedObject = field.dataObjectName && field.dataObjectFieldName;
  let sourceLabel: string | null = null;
  if (field.valueSource) {
    if (field.valueSource === "DATA_OBJECT_FIELD") {
      if (field.dataObjectName) {
        sourceLabel = field.dataObjectName;
      } else if (field.valueSourceConfig) {
        try {
          const config = JSON.parse(field.valueSourceConfig);
          sourceLabel = config.dataObjectName ?? "Data Object Field";
        } catch {
          sourceLabel = "Data Object Field";
        }
      } else {
        sourceLabel = "Data Object Field";
      }
    } else if (
      field.valueSource === "STATIC" &&
      (field.fieldKey === "fiscal_year" || field.fieldKey === "fiscal_quarters")
    ) {
      sourceLabel = "Fiscal Year Settings";
    } else {
      sourceLabel = VALUE_SOURCE_LABELS[field.valueSource] ?? field.valueSource;
    }
  }

  return (
    <div
      className={
        isInvalid
          ? "rounded-lg border bg-card p-4 space-y-1.5 border-l-4 border-l-amber-500 bg-amber-50/30 dark:bg-amber-950/10"
          : "rounded-lg border bg-card p-4 space-y-1.5"
      }
    >
      {/* Row 1: Display name + field type */}
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-semibold text-foreground inline-flex items-center gap-1.5">
          {isInvalid && (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  <AlertTriangle className="h-3.5 w-3.5 text-amber-600 shrink-0" />
                </TooltipTrigger>
                <TooltipContent side="top" className="text-xs max-w-[260px]">
                  This field references a deleted data field. Reassign or remove
                  it.
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
          {field.displayName}
        </span>
        <div className="flex items-center gap-2 shrink-0">
          <Badge variant="outline" className="text-xs">
            {typeLabel}
          </Badge>
          {!field.isSystem && (
            <div className="flex items-center gap-0.5">
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={() => onEdit(field)}
              >
                <Pencil className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 text-destructive hover:text-destructive"
                onClick={() => onDelete(field)}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Row 2: Field key + required/optional */}
      <div className="flex items-center gap-2">
        <span className="text-xs text-muted-foreground font-mono">
          field key: {field.fieldKey}
        </span>
        {field.isMandatory ? (
          <Badge
            variant="default"
            className="text-[10px] px-1.5 py-0 bg-amber-100 text-amber-800 hover:bg-amber-100"
          >
            Required
          </Badge>
        ) : (
          <Badge
            variant="outline"
            className="text-[10px] px-1.5 py-0 text-muted-foreground"
          >
            Optional
          </Badge>
        )}
      </div>

      {/* Row 3: Helper text */}
      {field.helperText && (
        <p className="text-xs italic text-muted-foreground">
          &ldquo;{field.helperText}&rdquo;
        </p>
      )}

      {/* Row 4: Source / linked info + system badge */}
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs text-muted-foreground">
          {isInvalid ? (
            <span>
              Linked to:{" "}
              <span className="font-medium text-foreground line-through decoration-amber-600/70">
                {field.dataObjectName ?? "Data Object"}
                {field.dataObjectFieldName
                  ? ` > ${field.dataObjectFieldName}`
                  : ""}
              </span>{" "}
              <span className="text-amber-700 dark:text-amber-500 font-medium">
                (deleted)
              </span>
            </span>
          ) : hasLinkedObject ? (
            <span>
              Linked to:{" "}
              <span className="font-medium text-foreground">
                {field.dataObjectName}
              </span>{" "}
              &gt;{" "}
              <span className="font-medium text-foreground">
                {field.dataObjectFieldName}
              </span>
            </span>
          ) : sourceLabel ? (
            <span>
              Source:{" "}
              <span className="font-medium text-foreground">{sourceLabel}</span>
            </span>
          ) : null}
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          {field.supportsExcelUpload && (
            <Badge
              variant="default"
              className="text-[10px] px-1.5 py-0 bg-emerald-100 text-emerald-800 hover:bg-emerald-100"
            >
              Excel upload
            </Badge>
          )}
          {field.isEligibility && (
            <Badge
              variant="default"
              className="text-[10px] px-1.5 py-0 bg-blue-100 text-blue-800 hover:bg-blue-100"
            >
              Eligibility
            </Badge>
          )}
          {field.isSystem && (
            <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
              System Default
            </Badge>
          )}
        </div>
      </div>

      {/* Row 5: Invalid action hint */}
      {isInvalid && (
        <p className="text-xs text-amber-700 dark:text-amber-500">
          Reassign this field to a different data field, or remove it.
        </p>
      )}
    </div>
  );
}

function CriteriaLockedMessage({ incentiveType }: { incentiveType: string }) {
  if (incentiveType.includes("SALES")) {
    return (
      <div className="flex items-start gap-2 rounded-md bg-muted/50 border border-muted px-4 py-3">
        <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
        <p className="text-sm text-muted-foreground">
          The Incentive Criteria section uses the Rules Engine which is
          dynamically configured when creating an incentive. Field configuration
          is not available here.
        </p>
      </div>
    );
  }

  if (incentiveType.includes("JOURNEY")) {
    return (
      <div className="flex items-start gap-2 rounded-md bg-muted/50 border border-muted px-4 py-3">
        <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
        <p className="text-sm text-muted-foreground">
          Journey Stages are configured when creating a journey incentive by
          selecting existing incentives. Field configuration is not available
          here.
        </p>
      </div>
    );
  }

  return (
    <div className="flex items-start gap-2 rounded-md bg-muted/50 border border-muted px-4 py-3">
      <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
      <p className="text-sm text-muted-foreground">
        This section is managed by the system.
      </p>
    </div>
  );
}

export function BuilderConfigSection({
  section,
  incentiveType,
  isFieldInvalid,
}: BuilderConfigSectionProps) {
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(!section.isLocked);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingField, setEditingField] =
    useState<BuilderFieldConfigResponse | null>(null);
  const [deleteTarget, setDeleteTarget] =
    useState<BuilderFieldConfigResponse | null>(null);

  const deleteMutation = useMutation({
    mutationFn: (fieldId: string) => removeField(fieldId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["builder-config"] });
      toast.success("Field removed");
      setDeleteTarget(null);
    },
    onError: (err: unknown) => {
      const message =
        err instanceof Error ? err.message : "Failed to remove field";
      toast.error(message);
      setDeleteTarget(null);
    },
  });

  function handleAddField() {
    setEditingField(null);
    setEditorOpen(true);
  }

  function handleEditField(field: BuilderFieldConfigResponse) {
    setEditingField(field);
    setEditorOpen(true);
  }

  function handleEditorClose() {
    setEditorOpen(false);
    setEditingField(null);
  }

  const isLocked = section.isLocked;
  const isCriteriaLocked = section.sectionKey === "criteria" && isLocked;

  return (
    <>
      <Card
        className={
          isLocked ? "border-muted bg-muted/30" : "border-l-4 border-l-primary"
        }
      >
        <CardHeader
          className="cursor-pointer select-none"
          onClick={() => setExpanded((prev) => !prev)}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {isLocked && <Lock className="h-4 w-4 text-muted-foreground" />}
              <div>
                <h3 className="text-base font-semibold">
                  {section.displayName}
                </h3>
                {section.subtitle && (
                  <p className="text-sm text-muted-foreground">
                    {section.subtitle}
                  </p>
                )}
              </div>
            </div>
            <div className="flex items-center gap-2">
              {isLocked ? (
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Badge
                        variant="secondary"
                        className="text-xs gap-1 cursor-default"
                      >
                        <ShieldCheck className="h-3 w-3" />
                        System Managed
                      </Badge>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>
                        Fields in this section are managed by the system and
                        cannot be modified.
                      </p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              ) : (
                <Badge className="text-xs bg-emerald-100 text-emerald-800 hover:bg-emerald-100">
                  Customizable
                </Badge>
              )}
              <Badge variant="outline" className="text-xs">
                {section.fields.length}{" "}
                {section.fields.length === 1 ? "field" : "fields"}
              </Badge>
              {expanded ? (
                <ChevronUp className="h-4 w-4 text-muted-foreground" />
              ) : (
                <ChevronDown className="h-4 w-4 text-muted-foreground" />
              )}
            </div>
          </div>
        </CardHeader>

        {expanded && (
          <CardContent className="space-y-3">
            {/* Criteria locked section: show contextual message */}
            {isCriteriaLocked && (
              <CriteriaLockedMessage incentiveType={incentiveType} />
            )}

            {/* Non-criteria locked section with no fields */}
            {isLocked && !isCriteriaLocked && section.fields.length === 0 && (
              <div className="flex items-start gap-2 rounded-md bg-muted/50 border border-muted px-4 py-3">
                <Info className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                <p className="text-sm text-muted-foreground">
                  This section is managed by the system.
                </p>
              </div>
            )}

            {/* Editable section with no fields */}
            {!isLocked && section.fields.length === 0 && (
              <p className="text-sm text-muted-foreground py-2">
                No custom fields yet. Click &ldquo;Add Field&rdquo; to get
                started.
              </p>
            )}

            {/* Field cards */}
            {section.fields.map((field) => (
              <FieldCard
                key={field.id}
                field={field}
                onEdit={handleEditField}
                onDelete={setDeleteTarget}
                isInvalid={isFieldInvalid?.(field) ?? false}
              />
            ))}

            {/* Add Field button for editable sections */}
            {!isLocked && (
              <Button
                variant="outline"
                size="sm"
                className="mt-2"
                onClick={handleAddField}
              >
                <Plus className="h-4 w-4 mr-1" />
                Add Field
              </Button>
            )}
          </CardContent>
        )}
      </Card>

      {editorOpen && (
        <BuilderFieldEditor
          sectionId={section.id}
          sectionKey={section.sectionKey}
          incentiveType={incentiveType}
          existingFieldKeys={section.fields
            .filter((f) => !f.isSystem)
            .map((f) => f.fieldKey)}
          field={editingField ?? undefined}
          onClose={handleEditorClose}
        />
      )}

      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove Field</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to remove &quot;{deleteTarget?.displayName}
              &quot;? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (deleteTarget) deleteMutation.mutate(deleteTarget.id);
              }}
            >
              {deleteMutation.isPending && (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              )}
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
