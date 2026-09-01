import { useState } from "react";
import { ChevronDown, ChevronRight, AlertTriangle } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  computeEffectiveAllocations,
  findOvershoots,
  parseAmount,
  summarizeAllocation,
  type AllocationSummary,
  type BudgetTreeNode,
} from "./budgetTreeHelpers";

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

interface BudgetAllocationTreeProps {
  /** Eligibility-scoped tree (already filtered to the user's picks). */
  nodes: BudgetTreeNode[];
  /** User-typed amounts keyed by `locationValueId`. Sparse. */
  values: Record<string, string>;
  /** The per-currency top-level budget total — drives root residuals + overshoot detection. */
  globalTotal: number | null;
  /** Called when the user types into any node's input. */
  onChange: (locationValueId: string, value: string) => void;
  /**
   * Optional currency adornment (e.g. `<DollarSign />`) shown on the left of
   * every input. Pre-rendered so the parent controls its size/color.
   */
  currencyPrefix?: React.ReactNode;
  /**
   * Currency display label (e.g. "Cash") used in top-level overshoot copy
   * to name which currency's total was exceeded. Falls back to a generic
   * "this currency's total" when not supplied.
   */
  currencyLabel?: string;
}

/**
 * Recursive expand/collapse budget allocation tree.
 *
 * - Each row is a LocationValue with a numeric input. Input is sparse: blank
 *   means "auto-fill from parent residual," which is shown as a placeholder.
 * - Children sum to parent at every depth — overshoots surface as an inline
 *   warning row directly below the offending parent.
 * - Top-level (root) rows are validated against `globalTotal` (the
 *   per-currency budget the user entered above the tree).
 */
export function BudgetAllocationTree({
  nodes,
  values,
  globalTotal,
  onChange,
  currencyPrefix,
  currencyLabel,
}: BudgetAllocationTreeProps) {
  const effective = computeEffectiveAllocations(nodes, values, globalTotal);
  const overshoots = findOvershoots(nodes, values, globalTotal);
  const overshootByParentId = new Map(
    overshoots.map((o) => [o.parentLocationValueId, o] as const),
  );
  const globalOvershoot = overshootByParentId.get("__GLOBAL__");

  if (nodes.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        No locations selected in Participant Eligibility. Go back to select
        locations first.
      </p>
    );
  }

  return (
    <div className="space-y-1">
      {globalOvershoot && (
        <OvershootBanner
          scopeLabel="Top-level rows"
          totalLabel={
            currencyLabel ? `the ${currencyLabel} total` : "the currency total"
          }
          filledNames={globalOvershoot.filledChildNames}
          parentAmount={globalOvershoot.parentAmount}
          childrenSum={globalOvershoot.childrenSum}
        />
      )}
      {nodes.map((node) => (
        <BudgetTreeRow
          key={node.locationValueId}
          node={node}
          depth={0}
          values={values}
          effective={effective}
          overshootByParentId={overshootByParentId}
          onChange={onChange}
          currencyPrefix={currencyPrefix}
        />
      ))}
    </div>
  );
}

interface BudgetTreeRowProps {
  node: BudgetTreeNode;
  depth: number;
  values: Record<string, string>;
  effective: Record<string, number>;
  overshootByParentId: Map<string, ReturnType<typeof findOvershoots>[number]>;
  onChange: (locationValueId: string, value: string) => void;
  currencyPrefix?: React.ReactNode;
}

function BudgetTreeRow({
  node,
  depth,
  values,
  effective,
  overshootByParentId,
  onChange,
  currencyPrefix,
}: BudgetTreeRowProps) {
  // Collapsed by default — the top level is what's required, and deeper
  // allocations are progressive disclosure. Nodes that already have a typed
  // value somewhere in their subtree start expanded so edit-mode shows what's
  // already filled.
  const hasChildren = node.children.length > 0;
  const [expanded, setExpanded] = useState(() =>
    hasChildren ? subtreeHasTypedValue(node, values) : false,
  );
  const userValue = values[node.locationValueId] ?? "";
  const isUserTyped = parseAmount(userValue) !== null;
  const effectiveAmount = effective[node.locationValueId];
  const placeholder =
    !isUserTyped && effectiveAmount !== undefined && effectiveAmount > 0
      ? formatResidual(effectiveAmount)
      : "0";
  const overshoot = overshootByParentId.get(node.locationValueId);
  // Parent-level allocation summary — drives the muted caption shown below
  // when this row is expanded and has children. Uses the row's effective
  // amount as the parent total so a blank-but-residual row still gets a
  // sensible breakdown caption.
  const parentTotalForChildren =
    hasChildren && effectiveAmount !== undefined && effectiveAmount > 0
      ? effectiveAmount
      : null;
  const childSummary =
    hasChildren && parentTotalForChildren !== null
      ? summarizeAllocation(node.children, values, parentTotalForChildren)
      : null;

  return (
    <div>
      <div
        className="flex items-center gap-2 py-1"
        style={{ paddingLeft: depth * 20 }}
      >
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className={`shrink-0 rounded p-0.5 hover:bg-muted ${
            hasChildren ? "" : "invisible"
          }`}
          aria-label={expanded ? "Collapse" : "Expand"}
        >
          {expanded ? (
            <ChevronDown className="h-3.5 w-3.5" />
          ) : (
            <ChevronRight className="h-3.5 w-3.5" />
          )}
        </button>
        <Label className="flex-1 text-xs">
          <span className="font-medium text-foreground">
            {node.locationValueName}
          </span>
          {depth === 0 && (
            <span className="text-destructive ml-0.5">*</span>
          )}
        </Label>
        <div className="relative w-40">
          {currencyPrefix && (
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
              {currencyPrefix}
            </span>
          )}
          <Input
            type="number"
            min="0"
            step="1"
            className={currencyPrefix ? "pl-7 text-sm" : "text-sm"}
            placeholder={placeholder}
            value={userValue}
            onKeyDown={blockInvalidChars}
            onChange={(e) => onChange(node.locationValueId, e.target.value)}
          />
        </div>
      </div>
      {overshoot && (
        <div style={{ paddingLeft: (depth + 1) * 20 }}>
          <OvershootBanner
            scopeLabel={`${node.locationValueName} children`}
            totalLabel={`${node.locationValueName}'s total`}
            filledNames={overshoot.filledChildNames}
            parentAmount={overshoot.parentAmount}
            childrenSum={overshoot.childrenSum}
          />
        </div>
      )}
      {hasChildren && expanded && (
        <div>
          {childSummary && (
            <div style={{ paddingLeft: (depth + 1) * 20 }}>
              <AllocationIndicator
                summary={childSummary}
                hasOvershoot={!!overshoot}
              />
            </div>
          )}
          {node.children.map((child) => (
            <BudgetTreeRow
              key={child.locationValueId}
              node={child}
              depth={depth + 1}
              values={values}
              effective={effective}
              overshootByParentId={overshootByParentId}
              onChange={onChange}
              currencyPrefix={currencyPrefix}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function OvershootBanner({
  scopeLabel,
  totalLabel,
  filledNames,
  parentAmount,
  childrenSum,
}: {
  /** "Top-level rows" or "Americas children" — names the offending group. */
  scopeLabel: string;
  /** "the Cash total" or "Americas's total" — names what was exceeded. */
  totalLabel: string;
  /** Filled children driving the overshoot, listed in tree order. */
  filledNames: string[];
  parentAmount: number;
  childrenSum: number;
}) {
  const overage = Math.max(0, childrenSum - parentAmount);
  const namesDisplay = formatChildList(filledNames);
  return (
    <div className="flex items-start gap-1.5 my-1 px-2 py-1 text-xs text-destructive bg-destructive/5 border border-destructive/20 rounded">
      <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
      <span>
        {scopeLabel} ({namesDisplay}) sum to $
        {childrenSum.toLocaleString()} — that's $
        {overage.toLocaleString()} over {totalLabel} of $
        {parentAmount.toLocaleString()}.
      </span>
    </div>
  );
}

/** Cap names list at 3 with "+N more" — keeps the banner readable. */
function formatChildList(names: string[]): string {
  if (names.length <= 3) return names.join(", ");
  return `${names.slice(0, 3).join(", ")}, +${names.length - 3} more`;
}

/**
 * Format the residual as a placeholder. Upstream math now returns integers,
 * so a defensive `Math.round` would just hide bugs — keep the value verbatim
 * and let any non-integer surface as a regression.
 */
function formatResidual(amount: number): string {
  if (amount <= 0) return "0";
  return String(amount);
}

/**
 * Subtle muted caption rendered under a parent input (or under the global
 * total) describing how the children break down. Hidden when the parent has
 * an overshoot — the OvershootBanner already covers that case and showing
 * both would be noisy.
 *
 * When there are blank rows the caption names the row directly:
 *   - 1 blank → "Brazil"
 *   - >1 blank → "Brazil +5" with a hover tooltip listing every blank name.
 */
function AllocationIndicator({
  summary,
  hasOvershoot,
}: {
  summary: AllocationSummary;
  hasOvershoot: boolean;
}) {
  if (hasOvershoot) return null;
  if (summary.parentTotal <= 0) return null;
  const total = `$${summary.parentTotal.toLocaleString()}`;
  const allocated = `$${summary.allocatedSum.toLocaleString()}`;
  const residual = `$${summary.residual.toLocaleString()}`;

  if (summary.isFullyAllocated) {
    return (
      <p className="text-[11px] text-muted-foreground py-0.5">
        {`${total} of ${total} allocated`}
      </p>
    );
  }

  if (summary.blankCount > 0 && summary.residual > 0) {
    const prefix =
      summary.allocatedSum > 0
        ? `${allocated} allocated · ${residual} auto-distributed across `
        : `${residual} auto-distributed across `;
    return (
      <p className="text-[11px] text-muted-foreground py-0.5">
        {prefix}
        <BlankRowsLabel names={summary.blankNames} />
      </p>
    );
  }

  return (
    <p className="text-[11px] text-muted-foreground py-0.5">
      {`${allocated} of ${total} allocated`}
    </p>
  );
}

/**
 * Names the blank rows feeding the auto-distribute residual. Single blank
 * renders inline; 2+ blanks render `First +N` with a tooltip listing every
 * blank name on hover so the user can see the full set without cluttering
 * the caption.
 */
function BlankRowsLabel({ names }: { names: string[] }) {
  if (names.length === 0) return null;
  if (names.length === 1) {
    return <span className="font-medium">{names[0]}</span>;
  }
  const first = names[0];
  const extra = names.length - 1;
  return (
    <TooltipProvider delayDuration={150}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="font-medium underline decoration-dotted underline-offset-2 cursor-help">
            {first} +{extra}
          </span>
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-xs">
          <ul className="text-xs leading-relaxed">
            {names.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export { AllocationIndicator };

/** True iff any descendant (not the node itself) has a non-blank user-typed amount. */
function subtreeHasTypedValue(
  node: BudgetTreeNode,
  values: Record<string, string>,
): boolean {
  for (const child of node.children) {
    if (parseAmount(values[child.locationValueId]) !== null) return true;
    if (subtreeHasTypedValue(child, values)) return true;
  }
  return false;
}
