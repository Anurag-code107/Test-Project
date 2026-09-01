import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Plus, Trash2 } from "lucide-react";
import { RuleRow } from "./RuleRow";
import type {
  EligibilityRuleGroup,
  EligibilityRule,
  EligibilityRuleType,
} from "@/types/incentive.types";

let _uid = 0;
const uid = () => `elig-${Date.now()}-${++_uid}`;

function createEmptyRule(): EligibilityRule {
  return {
    id: uid(),
    ruleType: "" as EligibilityRuleType,
    selectedProducts: [],
    customerTypes: [],
    listValues: [],
  };
}

function createEmptyGroup(): EligibilityRuleGroup {
  return { id: uid(), rules: [createEmptyRule()] };
}

interface EligibilityRulesSectionProps {
  groups: EligibilityRuleGroup[];
  onChange: (groups: EligibilityRuleGroup[]) => void;
}

export function EligibilityRulesSection({
  groups,
  onChange,
}: EligibilityRulesSectionProps) {
  function addGroup() {
    onChange([...groups, createEmptyGroup()]);
  }

  function updateGroup(
    groupId: string,
    updater: (g: EligibilityRuleGroup) => EligibilityRuleGroup,
  ) {
    onChange(groups.map((g) => (g.id === groupId ? updater(g) : g)));
  }

  function removeGroup(groupId: string) {
    onChange(groups.filter((g) => g.id !== groupId));
  }

  return (
    <div className="space-y-3">
      {groups.map((group, gIdx) => (
        <div key={group.id ?? gIdx}>
          {gIdx > 0 && (
            <div className="flex items-center gap-3 py-3">
              <div className="flex-1 h-px bg-blue-200 dark:bg-blue-800" />
              <Badge className="text-xs font-semibold bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-300 border-amber-200 dark:border-amber-800 border shadow-sm px-3">
                OR
              </Badge>
              <div className="flex-1 h-px bg-blue-200 dark:bg-blue-800" />
            </div>
          )}
          <EligibilityGroupEditor
            group={group}
            onUpdate={(updater) =>
              updateGroup(group.id ?? String(gIdx), updater)
            }
            onRemove={() => removeGroup(group.id ?? String(gIdx))}
            canRemove={groups.length > 1}
          />
        </div>
      ))}

      <Button
        variant="outline"
        size="sm"
        className="text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900/30"
        onClick={addGroup}
      >
        <Plus className="h-3.5 w-3.5 mr-1" />
        Add OR Group
      </Button>
    </div>
  );
}

function EligibilityGroupEditor({
  group,
  onUpdate,
  onRemove,
  canRemove,
}: {
  group: EligibilityRuleGroup;
  onUpdate: (
    updater: (g: EligibilityRuleGroup) => EligibilityRuleGroup,
  ) => void;
  onRemove: () => void;
  canRemove: boolean;
}) {
  return (
    <div className="rounded-lg border border-blue-100 dark:border-blue-900/30 bg-background p-3 space-y-2 shadow-sm">
      {group.rules.map((rule, rIdx) => (
        <div key={rule.id ?? rIdx}>
          {rIdx > 0 && (
            <div className="flex items-center gap-2 py-1 pl-2">
              <Badge
                variant="secondary"
                className="text-xs px-1.5 py-0 font-semibold"
              >
                AND
              </Badge>
            </div>
          )}
          <RuleRow
            rule={rule}
            onUpdate={(updater) =>
              onUpdate((g) => ({
                ...g,
                rules: g.rules.map((r) => (r.id === rule.id ? updater(r) : r)),
              }))
            }
            onRemove={() =>
              onUpdate((g) => ({
                ...g,
                rules: g.rules.filter((r) => r.id !== rule.id),
              }))
            }
            canRemove={group.rules.length > 1}
          />
        </div>
      ))}

      <div className="flex items-center gap-2 pt-1">
        <Button
          variant="ghost"
          size="sm"
          className="text-xs h-7 text-blue-600 dark:text-blue-400"
          onClick={() =>
            onUpdate((g) => ({ ...g, rules: [...g.rules, createEmptyRule()] }))
          }
        >
          <Plus className="h-3 w-3 mr-1" />
          Add AND Rule
        </Button>
        {canRemove && (
          <Button
            variant="ghost"
            size="sm"
            className="text-xs h-7 text-destructive ml-auto"
            onClick={onRemove}
          >
            <Trash2 className="h-3 w-3 mr-1" />
            Remove Group
          </Button>
        )}
      </div>
    </div>
  );
}
