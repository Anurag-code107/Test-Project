import { useState } from "react";
import * as XLSX from "xlsx";
import { Loader2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { MultiSelect } from "@/components/ui/multi-select";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useFieldValues } from "@/hooks/useBuilderConfig";
import { useToast } from "@/hooks/use-toast";
import type { BuilderFieldConfigResponse } from "@/types/builder-config.types";

interface DynamicFieldRendererProps {
  field: BuilderFieldConfigResponse;
  value: unknown;
  onChange: (value: unknown) => void;
  context?: Record<string, string[]>;
}

function normalize(v: string): string {
  return v.trim().toLowerCase();
}

export function DynamicFieldRenderer({
  field,
  value,
  onChange,
  context,
}: DynamicFieldRendererProps) {
  const { toast } = useToast();
  const [isParsing, setIsParsing] = useState(false);

  // Fetch dropdown values if the field has a valueSource
  const { data: options = [] } = useFieldValues(
    field.valueSource ? field.id : null,
    context,
  );

  const selectOptions = options.map((o) => ({
    value: o.value,
    label: o.label,
  }));

  // Excel upload is only meaningful for fields backed by an option list — the
  // sheet's first column maps onto known option values. For TEXT_BOX, NUMBER,
  // DATE, TOGGLE, etc. there is no canonical lookup, so the toggle is treated
  // as a no-op there even if it was enabled in Builder Config.
  const supportsUpload =
    field.supportsExcelUpload &&
    (field.fieldType === "MULTI_SELECT" || field.fieldType === "DROPDOWN");

  function handleUploadClick() {
    const canonicalByNormalized = new Map(
      selectOptions.map((o) => [normalize(o.value), o.value] as const),
    );

    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".xlsx,.xls,.csv";
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      setIsParsing(true);
      try {
        const buffer = await file.arrayBuffer();
        const workbook = XLSX.read(buffer, { type: "array" });
        const firstSheetName = workbook.SheetNames[0];
        const firstSheet = firstSheetName
          ? workbook.Sheets[firstSheetName]
          : undefined;
        if (!firstSheet) {
          toast({
            title: "Empty file",
            description: "That file doesn't have any sheets.",
            variant: "destructive",
          });
          return;
        }
        const rows = XLSX.utils.sheet_to_json<unknown[]>(firstSheet, {
          header: 1,
          blankrows: false,
        });
        const rawValues = rows
          .map((row) => (row[0] == null ? "" : String(row[0])).trim())
          .filter((v) => v.length > 0);

        // Drop the first row if it looks like a header (matches the field's
        // display name or a generic "Name"/"Value"/"Label"). Anything else is
        // treated as data so a real value isn't silently lost.
        const headerTokens = new Set([
          normalize(field.displayName),
          "name",
          "value",
          "label",
        ]);
        if (
          rawValues.length > 0 &&
          headerTokens.has(normalize(rawValues[0]!))
        ) {
          rawValues.shift();
        }

        const matched: string[] = [];
        const skipped: string[] = [];
        const seen = new Set<string>();
        for (const raw of rawValues) {
          const canonical = canonicalByNormalized.get(normalize(raw));
          if (canonical) {
            if (!seen.has(canonical)) {
              matched.push(canonical);
              seen.add(canonical);
            }
          } else {
            skipped.push(raw);
          }
        }

        if (field.fieldType === "MULTI_SELECT") {
          const existing = Array.isArray(value) ? (value as string[]) : [];
          const existingSet = new Set(existing);
          const merged = [...existing];
          let added = 0;
          for (const v of matched) {
            if (!existingSet.has(v)) {
              merged.push(v);
              existingSet.add(v);
              added++;
            }
          }
          onChange(merged);

          const descriptionParts: string[] = [];
          if (added < matched.length) {
            descriptionParts.push(
              `${matched.length - added} already selected`,
            );
          }
          if (skipped.length > 0) {
            const preview = skipped.slice(0, 5).join(", ");
            descriptionParts.push(
              `${skipped.length} not recognized: ${preview}${skipped.length > 5 ? "…" : ""}`,
            );
          }
          toast({
            title: added
              ? `Added ${added} value${added === 1 ? "" : "s"}`
              : "No new values added",
            description:
              descriptionParts.length > 0
                ? descriptionParts.join(" · ")
                : undefined,
          });
        } else {
          // DROPDOWN holds a single value — take the first match and warn if
          // the sheet contained more.
          if (matched.length === 0) {
            toast({
              title: "No matching value found",
              description:
                "Couldn't match any cell to a known option for this field.",
              variant: "destructive",
            });
          } else {
            onChange(matched[0]);
            const extra = matched.length - 1 + skipped.length;
            toast({
              title: `Selected "${matched[0]}"`,
              description:
                extra > 0
                  ? `${extra} other value${extra === 1 ? "" : "s"} ignored — this field holds one value.`
                  : undefined,
            });
          }
        }
      } catch {
        toast({
          title: "Upload failed",
          description:
            "Couldn't read the file. Make sure it's a valid .xlsx, .xls, or .csv.",
          variant: "destructive",
        });
      } finally {
        setIsParsing(false);
      }
    };
    input.click();
  }

  function renderField() {
    switch (field.fieldType) {
      case "TEXT_BOX":
        return (
          <Input
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            placeholder={field.displayName}
          />
        );

      case "TEXT_AREA":
        return (
          <Textarea
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            rows={3}
          />
        );

      case "NUMBER_INPUT":
        return (
          <Input
            type="number"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            placeholder={field.displayName}
          />
        );

      case "DATE_PICKER":
        return (
          <Input
            type="date"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
          />
        );

      case "TOGGLE":
        return <Switch checked={!!value} onCheckedChange={onChange} />;

      case "DROPDOWN":
        return (
          <Select value={(value as string) ?? ""} onValueChange={onChange}>
            <SelectTrigger>
              <SelectValue
                placeholder={`Select ${field.displayName.toLowerCase()}...`}
              />
            </SelectTrigger>
            <SelectContent>
              {selectOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );

      case "MULTI_SELECT":
        return (
          <MultiSelect
            options={selectOptions}
            selected={(value as string[]) ?? []}
            onChange={(selected) => onChange(selected)}
            placeholder={`Select ${field.displayName.toLowerCase()}...`}
          />
        );

      default:
        return (
          <Input
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            placeholder={field.displayName}
          />
        );
    }
  }

  return (
    <div className="space-y-1.5">
      <Label className="text-sm">
        {field.displayName}
        {field.isMandatory && (
          <span className="text-destructive ml-0.5">*</span>
        )}
      </Label>
      {field.helperText && (
        <p className="text-xs text-muted-foreground">{field.helperText}</p>
      )}
      {supportsUpload ? (
        <div className="flex items-center gap-2">
          <div className="flex-1 min-w-0">{renderField()}</div>
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="h-10 w-10 shrink-0"
            title={`Upload an Excel file to populate ${field.displayName}`}
            aria-label={`Upload Excel file for ${field.displayName}`}
            onClick={handleUploadClick}
            disabled={isParsing}
          >
            {isParsing ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Upload className="h-4 w-4" />
            )}
          </Button>
        </div>
      ) : (
        renderField()
      )}
    </div>
  );
}
