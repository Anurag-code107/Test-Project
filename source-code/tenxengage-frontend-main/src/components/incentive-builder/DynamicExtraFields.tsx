import { useBuilderConfig } from "@/hooks/useBuilderConfig";
import { DynamicFieldRenderer } from "./DynamicFieldRenderer";
import type { IncentiveType } from "@/types/incentive.types";

interface DynamicExtraFieldsProps {
  sectionKey: string;
  incentiveType: IncentiveType | null;
  values: Record<string, unknown>;
  onChange: (values: Record<string, unknown>) => void;
  context?: Record<string, string[]>;
}

export function DynamicExtraFields({
  sectionKey,
  incentiveType,
  values,
  onChange,
  context,
}: DynamicExtraFieldsProps) {
  const { data: config } = useBuilderConfig(incentiveType);

  if (!config) return null;

  // Find the section and get non-system fields
  const section = config.sections.find((s) => s.sectionKey === sectionKey);
  if (!section) return null;

  const dynamicFields = section.fields.filter((f) => !f.isSystem);
  if (dynamicFields.length === 0) return null;

  return (
    <div className="space-y-4 pt-4 mt-4 border-t border-border">
      <p className="text-xs text-muted-foreground font-medium uppercase tracking-wider">
        Additional Fields
      </p>
      {dynamicFields.map((field) => (
        <DynamicFieldRenderer
          key={field.id}
          field={field}
          value={values[field.fieldKey]}
          onChange={(val) => onChange({ ...values, [field.fieldKey]: val })}
          context={context}
        />
      ))}
    </div>
  );
}
