import { useMemo, useState } from "react";
import { useBuilderConfig } from "@/hooks/useBuilderConfig";
import { useDataFieldValidity } from "@/hooks/useDataFieldValidity";
import { BuilderConfigSection } from "@/components/settings/BuilderConfigSection";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AlertTriangle, Loader2 } from "lucide-react";
import type { IncentiveType } from "@/types/incentive.types";

const TYPES: { value: IncentiveType; label: string }[] = [
  { value: "SALES", label: "Sales" },
  { value: "TRAINING", label: "Training" },
  { value: "ACTIVITY", label: "Activity" },
  { value: "JOURNEY", label: "Journey" },
];

export function BuilderConfigTab() {
  const [selectedType, setSelectedType] = useState<IncentiveType>("SALES");
  const { data: config, isLoading } = useBuilderConfig(selectedType);
  const { isBuilderFieldInvalid, isLoading: validityLoading } =
    useDataFieldValidity();

  const invalidCount = useMemo(() => {
    if (!config || validityLoading) return 0;
    let count = 0;
    for (const section of config.sections) {
      for (const field of section.fields) {
        if (isBuilderFieldInvalid(field)) count++;
      }
    }
    return count;
  }, [config, isBuilderFieldInvalid, validityLoading]);

  return (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-muted-foreground">
          Configure which sections and fields appear in the incentive builder
          for each type. Locked sections contain system fields that cannot be
          removed. Editable sections allow adding custom fields linked to your
          data objects.
        </p>
      </div>

      <div className="flex gap-2">
        {TYPES.map((t) => (
          <Button
            key={t.value}
            variant={selectedType === t.value ? "default" : "outline"}
            size="sm"
            onClick={() => setSelectedType(t.value)}
          >
            {t.label}
          </Button>
        ))}
      </div>

      {invalidCount > 0 && (
        <Alert className="border-amber-500/50 bg-amber-50 text-amber-900 dark:bg-amber-950/30 dark:text-amber-200 [&>svg]:text-amber-600">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>
            {invalidCount}{" "}
            {invalidCount === 1 ? "field references" : "fields reference"} a
            deleted data field
          </AlertTitle>
          <AlertDescription className="text-amber-800 dark:text-amber-300">
            These fields will be hidden from end users until you reassign them
            to an existing data field or remove them.
          </AlertDescription>
        </Alert>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        config?.sections.map((section) => (
          <BuilderConfigSection
            key={section.id}
            section={section}
            incentiveType={selectedType}
            isFieldInvalid={isBuilderFieldInvalid}
          />
        ))
      )}
    </div>
  );
}
