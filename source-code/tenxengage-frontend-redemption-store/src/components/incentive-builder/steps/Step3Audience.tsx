import { useEffect, useMemo, useState } from "react";
import * as XLSX from "xlsx";
import { Upload, Loader2 } from "lucide-react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MultiSelect } from "@/components/ui/multi-select";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import {
  getLocationValuesForLevel,
  useExternalRoles,
} from "@/hooks/useBuilderConfig";
import {
  useLocationBuilderOptions,
  useLocationHierarchy,
} from "@/hooks/useLocationApi";
import { DynamicExtraFields } from "@/components/incentive-builder/DynamicExtraFields";

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

export function Step3Audience() {
  const { state, dispatch } = useBuilder();
  const { audience } = state;
  const { toast } = useToast();

  // The level currently parsing an uploaded file, used to swap the button's
  // icon for a spinner while XLSX processes large sheets.
  const [parsingLevelId, setParsingLevelId] = useState<string | null>(null);

  // Fetch dynamic data
  const { data: builderLevels = [], isLoading: levelsLoading } =
    useLocationBuilderOptions();
  const { data: hierarchy } = useLocationHierarchy();
  const { data: roleOptions = [], isLoading: rolesLoading } =
    useExternalRoles();

  // Partner types — keep as static for now; will be sourced from data objects via builder config
  const partnerTypeOptions = useMemo(
    () => [
      { value: "Reseller", label: "Reseller" },
      { value: "Distributor", label: "Distributor" },
      { value: "OEM", label: "OEM" },
    ],
    [],
  );

  // Step completion check — `locationSelections` is the canonical source after
  // the BUG-030 retirement of `audience.regions`. The bidirectional sync
  // useEffects that used to live here (forward-sync regions ← locationSelections,
  // and the reverse-sync shim from commit 3ab0659) were both removed; AI
  // dispatches that still send legacy `{ regions: [...] }` get normalized into
  // `locationSelections` upstream by `translateAudiencePayload` in useAiChat.ts,
  // and edit-mode hydration of pre-migration incentives populates
  // `locationSelections` directly via `buildLocationSelectionsFromRules`.
  useEffect(() => {
    const requiredLevelsSatisfied = builderLevels
      .filter((l) => l.isRequired)
      .every((l) => (audience.locationSelections[l.id] ?? []).length > 0);
    const isComplete =
      requiredLevelsSatisfied && audience.userRoles.length > 0;
    if (isComplete && !state.completedSteps.includes("audience")) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "audience" });
    } else if (!isComplete && state.completedSteps.includes("audience")) {
      dispatch({ type: "MARK_STEP_INCOMPLETE", payload: "audience" });
    }
  }, [
    builderLevels,
    audience.locationSelections,
    audience.userRoles,
    state.completedSteps,
    dispatch,
  ]);

  function updateAudience(partial: Partial<typeof audience>) {
    dispatch({ type: "UPDATE_AUDIENCE", payload: partial });
  }

  function updateLocationSelection(levelId: string, values: string[]) {
    const newSelections = { ...audience.locationSelections, [levelId]: values };

    // Clear deeper level selections whose parents are no longer selected.
    // Thread the full ancestor chain (not just the immediate parent) so the
    // tree walker can filter correctly at every intermediate level — without
    // this, the walker fails the filter at shallower levels and never reaches
    // the deeper subtree. See BUG-031.
    const levelIdx = builderLevels.findIndex((l) => l.id === levelId);
    for (let i = levelIdx + 1; i < builderLevels.length; i++) {
      const deeperLevel = builderLevels[i];
      if (!deeperLevel) continue;
      const deeperLevelId = deeperLevel.id;
      if ((newSelections[deeperLevelId] ?? []).length === 0) continue;

      const ancestorSelections: Record<string, string[]> = {};
      for (let j = 0; j < i; j++) {
        const ancestor = builderLevels[j];
        if (ancestor) {
          ancestorSelections[ancestor.id] = newSelections[ancestor.id] ?? [];
        }
      }
      const validValues = getLocationValuesForLevel(
        deeperLevel.id,
        hierarchy,
        ancestorSelections,
      );
      const validNames = new Set(validValues.map((v) => v.value));
      newSelections[deeperLevelId] = (
        newSelections[deeperLevelId] ?? []
      ).filter((v) => validNames.has(v));
    }

    updateAudience({ locationSelections: newSelections });
  }

  function handleFileUploadClick(levelId: string) {
    const levelIdx = builderLevels.findIndex((l) => l.id === levelId);
    const level = builderLevels[levelIdx];
    if (!level) return;

    // Capture the available options at click time (ancestor-filtered) so the
    // lookup table matches exactly what the adjacent MultiSelect would show.
    const ancestorSelections: Record<string, string[]> = {};
    for (let i = 0; i < levelIdx; i++) {
      const ancestor = builderLevels[i];
      if (ancestor) {
        ancestorSelections[ancestor.id] =
          audience.locationSelections[ancestor.id] ?? [];
      }
    }
    const availableValues = getLocationValuesForLevel(
      level.id,
      hierarchy,
      levelIdx > 0 ? ancestorSelections : undefined,
    ).map((v) => v.value);
    const canonicalByNormalized = new Map(
      availableValues.map((v) => [normalize(v), v]),
    );

    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".xlsx,.xls,.csv";
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      setParsingLevelId(levelId);
      try {
        const buffer = await file.arrayBuffer();
        const workbook = XLSX.read(buffer, { type: "array" });
        const firstSheet = workbook.Sheets[workbook.SheetNames[0]!];
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

        // Drop the first row if it looks like a header (matches the level name
        // or a generic "Name"/"Value"/"Label"). Any other first-row content is
        // treated as data so we don't silently lose a real value.
        const headerTokens = new Set([
          normalize(level.name),
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

        const existing = audience.locationSelections[level.id] ?? [];
        const existingSet = new Set(existing);
        const added: string[] = [];
        for (const name of matched) {
          if (!existingSet.has(name)) {
            existingSet.add(name);
            added.push(name);
          }
        }
        updateLocationSelection(level.id, [...existingSet]);

        const lowerLevel = level.name.toLowerCase();
        const descriptionParts: string[] = [];
        if (added.length < matched.length) {
          descriptionParts.push(
            `${matched.length - added.length} already selected`,
          );
        }
        if (skipped.length > 0) {
          const preview = skipped.slice(0, 5).join(", ");
          descriptionParts.push(
            `${skipped.length} not recognized: ${preview}${skipped.length > 5 ? "…" : ""}`,
          );
        }
        toast({
          title: added.length
            ? `Added ${added.length} ${lowerLevel}${added.length === 1 ? "" : "s"}`
            : "No new values added",
          description:
            descriptionParts.length > 0 ? descriptionParts.join(" · ") : undefined,
        });
      } catch {
        toast({
          title: "Upload failed",
          description:
            "Couldn't read the file. Make sure it's a valid .xlsx, .xls, or .csv.",
          variant: "destructive",
        });
      } finally {
        setParsingLevelId(null);
      }
    };
    input.click();
  }

  return (
    <div className="space-y-4">
      {/* Dynamic Location Level Selectors */}
      {levelsLoading ? (
        <div className="space-y-2">
          <div className="h-4 w-24 bg-muted animate-pulse rounded" />
          <div className="h-10 bg-muted animate-pulse rounded" />
        </div>
      ) : (
        builderLevels.map((level, idx) => {
          const parentLevel = idx > 0 ? builderLevels[idx - 1] : null;
          const parentSelections = parentLevel
            ? (audience.locationSelections[parentLevel.id] ?? [])
            : [];
          const needsParent = idx > 0 && parentSelections.length === 0;

          // Thread the full ancestor chain so the tree walker filters at every
          // intermediate level, not just the immediate parent. A filter at
          // depth 2 only (e.g. Country) must not reject depth-0 nodes.
          const ancestorSelections: Record<string, string[]> = {};
          for (let i = 0; i < idx; i++) {
            const ancestor = builderLevels[i];
            if (ancestor) {
              ancestorSelections[ancestor.id] =
                audience.locationSelections[ancestor.id] ?? [];
            }
          }

          const options = getLocationValuesForLevel(
            level.id,
            hierarchy,
            idx > 0 ? ancestorSelections : undefined,
          );
          const selected = audience.locationSelections[level.id] ?? [];

          return (
            <div key={level.id} className="space-y-2">
              <Label>
                {level.name}
                {level.isRequired ? (
                  <span className="text-destructive"> *</span>
                ) : (
                  " (Optional)"
                )}
              </Label>
              {needsParent ? (
                <p className="text-sm text-muted-foreground italic">
                  Select {parentLevel?.name.toLowerCase()} first
                </p>
              ) : options.length === 0 ? (
                <p className="text-sm text-muted-foreground italic">
                  No {level.name.toLowerCase()}(s) available
                </p>
              ) : (
                <div className="flex items-center gap-2">
                  <div className="flex-1 min-w-0">
                    <MultiSelect
                      options={options}
                      selected={selected}
                      onChange={(v) => updateLocationSelection(level.id, v)}
                      placeholder={`Select ${level.name.toLowerCase()}(s)`}
                    />
                  </div>
                  {idx > 0 && (
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      className="h-[38px] w-[38px] shrink-0"
                      title={`Upload a list of ${level.name.toLowerCase()}s`}
                      onClick={() => handleFileUploadClick(level.id)}
                      disabled={parsingLevelId === level.id}
                    >
                      {parsingLevelId === level.id ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Upload className="h-4 w-4" />
                      )}
                    </Button>
                  )}
                </div>
              )}
            </div>
          );
        })
      )}

      {/* User Roles */}
      <div className="space-y-2">
        <Label>
          User Roles <span className="text-destructive">*</span>
        </Label>
        <MultiSelect
          options={roleOptions}
          selected={audience.userRoles}
          onChange={(v) => updateAudience({ userRoles: v })}
          placeholder={rolesLoading ? "Loading roles…" : "Select role(s)"}
        />
      </div>

      {/* Partner Types (Optional) */}
      <div className="space-y-2">
        <Label>Partner Types (Optional)</Label>
        <MultiSelect
          options={partnerTypeOptions}
          selected={audience.partnerTypes}
          onChange={(v) => updateAudience({ partnerTypes: v })}
          placeholder="Select partner type(s)"
        />
      </div>

      {/* Specific Partners (Optional) */}
      <div className="space-y-2">
        <Label>Specific Partners (Optional)</Label>
        <Textarea
          value={audience.specificPartners}
          onChange={(e) => updateAudience({ specificPartners: e.target.value })}
          placeholder="Enter partner names separated by commas…"
          rows={3}
        />
      </div>

      {/* Dynamic extra fields from builder configuration */}
      <DynamicExtraFields
        sectionKey="audience"
        incentiveType={state.basics.incentiveType}
        values={audience.dynamicFields}
        onChange={(fields) => updateAudience({ dynamicFields: fields })}
        context={{
          regions: Object.values(audience.locationSelections).flat(),
        }}
      />
    </div>
  );
}
