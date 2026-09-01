import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export interface DynamicField {
  id: string;
  name: string;
  description?: string;
  dataType: "TEXT" | "NUMBER" | "DATE" | "CURRENCY" | "LIST" | "BOOLEAN";
  mandatory: boolean;
  sampleValues?: string; // JSON array string, e.g. '["ACTIVE","INACTIVE"]'
  sortOrder: number;
}

interface DynamicFieldRendererProps {
  fields: DynamicField[];
  values: Record<string, unknown>;
  onChange: (key: string, value: unknown) => void;
  readOnly?: boolean;
}

/**
 * Renders form fields dynamically based on DataObjectField definitions.
 * Maps dataType to the appropriate UI control.
 */
export function DynamicFieldRenderer({
  fields,
  values,
  onChange,
  readOnly = false,
}: DynamicFieldRendererProps) {
  return (
    <div className="space-y-4">
      {fields.map((field) => {
        const key = field.name;
        const value = values[key];
        const required = field.mandatory;

        // Parse sample values for LIST type
        let listOptions: string[] = [];
        if (field.dataType === "LIST" && field.sampleValues) {
          try {
            listOptions = JSON.parse(field.sampleValues);
          } catch {
            listOptions = [];
          }
        }

        return (
          <div key={field.id} className="flex flex-col gap-2">
            {field.dataType !== "BOOLEAN" && (
              <Label htmlFor={`field-${field.id}`}>
                {field.name}
                {required && <span className="text-destructive ml-1">*</span>}
              </Label>
            )}

            {/* TEXT */}
            {field.dataType === "TEXT" && (
              <Input
                id={`field-${field.id}`}
                value={(value as string) ?? ""}
                onChange={(e) => onChange(key, e.target.value)}
                placeholder={field.description || field.name}
                disabled={readOnly}
                required={required}
              />
            )}

            {/* NUMBER / CURRENCY */}
            {(field.dataType === "NUMBER" || field.dataType === "CURRENCY") && (
              <Input
                id={`field-${field.id}`}
                type="number"
                value={(value as string) ?? ""}
                onChange={(e) => onChange(key, e.target.value)}
                placeholder={field.description || field.name}
                disabled={readOnly}
                required={required}
              />
            )}

            {/* DATE */}
            {field.dataType === "DATE" && (
              <Input
                id={`field-${field.id}`}
                type="date"
                value={(value as string) ?? ""}
                onChange={(e) => onChange(key, e.target.value)}
                disabled={readOnly}
                required={required}
              />
            )}

            {/* LIST */}
            {field.dataType === "LIST" && (
              <Select
                value={(value as string) ?? ""}
                onValueChange={(v) => onChange(key, v)}
                disabled={readOnly}
              >
                <SelectTrigger id={`field-${field.id}`}>
                  <SelectValue placeholder={`Select ${field.name}...`} />
                </SelectTrigger>
                <SelectContent>
                  {listOptions.map((opt) => (
                    <SelectItem key={opt} value={opt}>
                      {opt}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            {/* BOOLEAN */}
            {field.dataType === "BOOLEAN" && (
              <div className="flex items-center gap-3">
                <Switch
                  id={`field-${field.id}`}
                  checked={!!value}
                  onCheckedChange={(v) => onChange(key, v)}
                  disabled={readOnly}
                />
                <Label htmlFor={`field-${field.id}`} className="cursor-pointer">
                  {field.name}
                  {required && <span className="text-destructive ml-1">*</span>}
                </Label>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
