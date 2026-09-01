/**
 * Excel Template Parser
 *
 * Parses uploaded .xlsx files into builder state data.
 * Uses the SheetJS (xlsx) library for reading Excel files.
 */

import * as XLSX from "xlsx";
import type {
  SalesRequirement,
  EligibilityRuleType,
  RuleOperator,
  PayoutType,
  PayoutAgainst,
  AudienceRule,
} from "@/types/incentive.types";
import type {
  BasicsData,
  ScheduleData,
  AudienceData,
  BudgetData,
  CriteriaData,
  ApprovalData,
} from "@/types/builder-state.types";
import type {
  LocationHierarchyResponse,
  LocationValueResponse,
} from "@/types/location.types";

/* ─── Parsed result ─── */

/**
 * One location row from the upload, paired with its resolution status.
 * BUG-079: lets the upload modal surface unresolved names row-by-row before
 * save instead of relying on the mapper's save-time backstop.
 */
export interface ParsedLocationRow {
  /** As it appeared in the spreadsheet, untouched. */
  raw: string;
  /** Trimmed + lowercased for lookup. */
  normalized: string;
  /** LocationValue UUID when resolved against the loaded hierarchy; null on miss. */
  locationValueId: string | null;
  /** The level UUID this row was checked against. */
  locationLevelId: string;
}

/**
 * One role row from the upload, paired with its resolution status. Mirrors
 * `ParsedLocationRow` so the upload modal can surface unresolved role names
 * the same way it surfaces unresolved locations (BUG-082).
 */
export interface ParsedRoleRow {
  /** As it appeared in the spreadsheet, untouched. */
  raw: string;
  /** Trimmed + lowercased for lookup. */
  normalized: string;
  /** ClientRole UUID when resolved against the supplied role list; null on miss. */
  roleId: string | null;
}

export interface ParsedTemplateResult {
  basics: Partial<BasicsData>;
  schedule: Partial<ScheduleData>;
  audience: Partial<AudienceData>;
  budgetData: Partial<BudgetData>;
  criteria: Partial<CriteriaData>;
  approval: Partial<ApprovalData>;
  /** Which builder steps were successfully populated */
  filledSteps: string[];
  /**
   * BUG-079: per-row resolution status for every location name read from the
   * sheet. Empty when no hierarchy was supplied (caller falls back to today's
   * behavior — names land in state and are resolved at save). When hierarchy
   * is supplied, callers should inspect `locationRows.filter(r => !r.locationValueId)`
   * and surface a toast / row-level warning to the user before proceeding.
   */
  locationRows: ParsedLocationRow[];
  /**
   * BUG-082: per-row resolution status for every role name read from the
   * sheet. Empty when no role list was supplied (template ran in legacy mode
   * — role names land in state as-is and the user re-picks in Step 3). When a
   * role list is supplied, names are resolved to UUIDs at parse time and
   * unresolved rows surface here for the upload modal.
   */
  roleRows: ParsedRoleRow[];
}

/* ─── Currency mapping ─── */

const CURRENCY_LABEL_TO_ID: Record<string, string> = {
  cash: "cash",
  points: "points",
  tickets: "tickets",
  credits: "credits",
};

function matchCurrency(label: string): string | undefined {
  return CURRENCY_LABEL_TO_ID[label.toLowerCase()];
}

const MONETARY_CURRENCIES = ["cash", "points"];

/* ─── Operator mapping (excel → backend enum) ─── */

const OPERATOR_MAP: Record<string, RuleOperator> = {
  "greater-than": "GREATER_THAN",
  "less-than": "LESS_THAN",
  "equal-to": "EQUALS",
  between: "BETWEEN",
  "greater-than-or-equal": "GREATER_THAN_OR_EQUAL",
};

/* ─── Helpers ─── */

function parseCommaSeparated(val: string): string[] {
  return val
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

/**
 * Read a key-value sheet (Field / Your Value columns).
 * Skips section headers and column header rows.
 */
function readKVSheet(
  wb: XLSX.WorkBook,
  sheetName: string,
): Record<string, string> {
  const ws = wb.Sheets[sheetName];
  if (!ws) return {};
  const rows: string[][] = XLSX.utils.sheet_to_json(ws, { header: 1 });
  const map: Record<string, string> = {};
  for (const row of rows) {
    const field = (row?.[0] || "").toString().trim();
    const value = (row?.[1] || "").toString().trim();
    if (!field || field.startsWith("SECTION") || field === "Field") continue;
    if (value) map[field] = value;
  }
  return map;
}

/* ─── Main parse function ─── */

/**
 * Build a name index keyed by `(levelId, lowercased trimmed name) → UUID`.
 * Mirrors the helper in `builderRequestMapper` so upload-time and save-time
 * resolution stay byte-for-byte identical (a name that resolves at upload
 * also resolves at save, eliminating the "looked fine in the modal but failed
 * to save" inconsistency).
 */
function buildNameIndex(
  hierarchy: LocationHierarchyResponse | undefined,
): Map<string, Map<string, string>> {
  const byLevel = new Map<string, Map<string, string>>();
  if (!hierarchy?.tree) return byLevel;
  const visit = (node: LocationValueResponse): void => {
    let levelMap = byLevel.get(node.levelId);
    if (!levelMap) {
      levelMap = new Map<string, string>();
      byLevel.set(node.levelId, levelMap);
    }
    levelMap.set(node.name.trim().toLowerCase(), node.id);
    for (const child of node.children ?? []) visit(child);
  };
  for (const root of hierarchy.tree) visit(root);
  return byLevel;
}

/**
 * Parse an Excel template file into a partial builder state.
 *
 * @param file The uploaded .xlsx file.
 * @param topLocationLevelId The top-level location level id for this tenant.
 *   Used to populate `audience.locationSelections[topLevelId]` from the flat
 *   "Eligible Regions" column.
 * @param hierarchy Optional. When supplied (BUG-079), each region name from
 *   the sheet is resolved against the hierarchy at parse time and the result
 *   is reported via `locationRows` so callers can surface unresolved rows
 *   before the user clicks Save. When omitted, the parser falls back to
 *   today's behavior (names land in state, mapper resolves at save). Names
 *   are still written into `audience.locationSelections` either way — the
 *   mapper is the single resolution-of-truth at save time.
 */
export function parseExcelTemplate(
  file: File,
  topLocationLevelId?: string,
  hierarchy?: LocationHierarchyResponse,
  availableRoles?: Array<{ id: string; name: string }>,
): Promise<ParsedTemplateResult> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target!.result as ArrayBuffer);
        const wb = XLSX.read(data, { type: "array" });

        // Read setup sheet (key-value pairs)
        const setup = readKVSheet(wb, "Incentive Setup");

        // --- Basics ---
        const basics: Partial<BasicsData> = {};
        if (setup["Incentive Name"]) basics.name = setup["Incentive Name"];
        if (setup["Description"]) basics.description = setup["Description"];

        // --- Schedule ---
        const schedule: Partial<ScheduleData> = {};
        if (setup["Start Date"]) schedule.startDate = setup["Start Date"];
        if (setup["End Date"]) schedule.endDate = setup["End Date"];
        if (setup["Fiscal Years"])
          schedule.fiscalYears = parseCommaSeparated(setup["Fiscal Years"]);
        if (setup["Fiscal Quarters"])
          schedule.fiscalQuarters = parseCommaSeparated(
            setup["Fiscal Quarters"],
          );

        // --- Audience ---
        const audience: Partial<AudienceData> = {};
        const regions = setup["Eligible Regions"]
          ? parseCommaSeparated(setup["Eligible Regions"])
          : [];
        const roles = setup["Eligible Roles"]
          ? parseCommaSeparated(setup["Eligible Roles"])
          : [];
        const partnerTypes = setup["Partner Types"]
          ? parseCommaSeparated(setup["Partner Types"])
          : [];

        // BUG-079: when a hierarchy is supplied, resolve each region name to
        // its LocationValue UUID at parse time so callers can surface
        // unresolved rows before save. Resolution semantics match the mapper
        // exactly (see buildNameIndex) so the modal and the save path can
        // never disagree. When hierarchy is omitted, locationRows is empty
        // and the parser behaves as it did pre-BUG-079.
        const nameIndex = buildNameIndex(hierarchy);
        const locationRows: ParsedLocationRow[] = [];
        if (topLocationLevelId && hierarchy) {
          const levelMap = nameIndex.get(topLocationLevelId);
          for (const raw of regions) {
            const normalized = raw.trim().toLowerCase();
            locationRows.push({
              raw,
              normalized,
              locationValueId: levelMap?.get(normalized) ?? null,
              locationLevelId: topLocationLevelId,
            });
          }
        }

        if (regions.length > 0 && topLocationLevelId) {
          audience.locationSelections = {
            [topLocationLevelId]: regions,
          };
        }
        // BUG-082: when a role list is supplied, resolve role-name cells to
        // role-id UUIDs at parse time and emit unresolved rows for the upload
        // modal. When omitted, fall back to legacy behavior (names flow into
        // state; the user re-picks in Step 3 because there's nowhere to
        // resolve them safely on save).
        const roleRows: ParsedRoleRow[] = [];
        let resolvedRoleIds: string[] | null = null;
        if (roles.length > 0 && availableRoles && availableRoles.length > 0) {
          const roleIdByName = new Map<string, string>();
          for (const r of availableRoles) {
            roleIdByName.set(r.name.trim().toLowerCase(), r.id);
          }
          const ids: string[] = [];
          const seen = new Set<string>();
          for (const raw of roles) {
            const normalized = raw.trim().toLowerCase();
            const resolvedId = roleIdByName.get(normalized) ?? null;
            roleRows.push({ raw, normalized, roleId: resolvedId });
            if (resolvedId && !seen.has(resolvedId)) {
              ids.push(resolvedId);
              seen.add(resolvedId);
            }
          }
          resolvedRoleIds = ids;
        }
        if (resolvedRoleIds !== null) {
          audience.userRoles = resolvedRoleIds;
        } else if (roles.length > 0) {
          audience.userRoles = roles;
        }
        if (partnerTypes.length > 0) audience.partnerTypes = partnerTypes;

        // Build audience rules array for in-state preview. BUG-079: ruleValue
        // carries the resolved UUID when a hierarchy is supplied; otherwise it
        // falls back to the name (which the mapper will still resolve at save).
        // Either way, the wire format the backend ultimately receives is
        // UUID-only — the mapper is the source of truth for what crosses the
        // wire, not this in-state preview list.
        const rules: AudienceRule[] = [];
        if (topLocationLevelId) {
          for (const r of regions) {
            const resolved = hierarchy
              ? nameIndex.get(topLocationLevelId)?.get(r.trim().toLowerCase())
              : undefined;
            rules.push({
              ruleType: "LOCATION",
              ruleValue: resolved ?? r,
              locationLevelId: topLocationLevelId,
              locationValueName: r,
            });
          }
        }
        // BUG-082: in-state preview ROLE rules carry the resolved UUID when a
        // role list was supplied; otherwise fall back to the raw name (which
        // the user will re-pick in Step 3 — the wire never carries the name
        // because the mapper reads the `userRoles` array, not these preview
        // rules).
        if (resolvedRoleIds !== null) {
          for (const id of resolvedRoleIds) {
            rules.push({ ruleType: "ROLE", ruleValue: id });
          }
        } else {
          for (const r of roles) {
            rules.push({ ruleType: "ROLE", ruleValue: r });
          }
        }
        for (const p of partnerTypes) {
          rules.push({ ruleType: "PARTNER_TYPE", ruleValue: p });
        }
        if (rules.length > 0) audience.rules = rules;

        // --- Budget ---
        const budgetData: Partial<BudgetData> = {};
        const currencyLabels = setup["Reward Currencies"]
          ? parseCommaSeparated(setup["Reward Currencies"])
          : [];
        const selectedCurrencies = currencyLabels
          .map(matchCurrency)
          .filter((id): id is string => !!id);

        if (selectedCurrencies.length > 0)
          budgetData.selectedCurrencies = selectedCurrencies;

        const budgetMode = (setup["Budget Mode"] || "global") as
          | "global"
          | "per-region";
        budgetData.budgetMode = budgetMode;

        const globalBudgets: Record<string, string> = {};
        for (const c of MONETARY_CURRENCIES) {
          const label = c.charAt(0).toUpperCase() + c.slice(1);
          const val = setup[`${label} Budget`];
          if (val) globalBudgets[c] = val;
        }
        if (Object.keys(globalBudgets).length > 0)
          budgetData.globalBudgets = globalBudgets;

        if (setup["Max Per Partner"])
          budgetData.maxPerPartner = setup["Max Per Partner"];
        if (setup["Max Per User"])
          budgetData.maxPerUser = setup["Max Per User"];
        if (setup["Claimers Per PO"])
          budgetData.maxClaimersPerDeal = setup["Claimers Per PO"];

        // Reward Message (belongs in basics, not budgetData)
        if (setup["Reward Message"])
          basics.rewardMessage = setup["Reward Message"];

        // Per-completion reward amounts
        const ALL_CURRENCIES = ["cash", "points", "tickets", "credits"];
        const CURRENCY_LABELS: Record<string, string> = {
          cash: "Cash",
          points: "Points",
          tickets: "Tickets",
          credits: "Credits",
        };
        const rewardAmounts: Record<string, string> = {};
        for (const cid of ALL_CURRENCIES) {
          const label = CURRENCY_LABELS[cid];
          const val = setup[`${label} Per Completion`];
          if (val) rewardAmounts[cid] = val;
        }
        if (Object.keys(rewardAmounts).length > 0)
          budgetData.rewardAmounts = rewardAmounts;

        // --- Approval (Section 5) ---
        const approval: Partial<ApprovalData> = {};
        const requiresApprovalStr = (
          setup["Requires Approval"] || ""
        ).toLowerCase();
        if (requiresApprovalStr === "yes" || requiresApprovalStr === "true") {
          approval.requiresApproval = true;
        } else if (
          requiresApprovalStr === "no" ||
          requiresApprovalStr === "false"
        ) {
          approval.requiresApproval = false;
        }
        const approverEmails = setup["Approver Emails"]
          ? parseCommaSeparated(setup["Approver Emails"])
          : [];
        const approverCategories = setup["Approver Categories"]
          ? parseCommaSeparated(setup["Approver Categories"])
          : [];
        if (approverEmails.length > 0) {
          approval.approvers = approverEmails.map((email, i) => ({
            id: `approver-${i}`,
            email,
            category: approverCategories[i] ?? "",
          }));
        }
        if (setup["Required Approvals"]) {
          approval.requiredApprovals =
            parseInt(setup["Required Approvals"], 10) || 0;
        }

        // --- Criteria (Promotion Rules sheet → SalesRequirements) ---
        const criteria: Partial<CriteriaData> = {};
        const salesRequirements = parseRequirementsSheet(wb);
        if (salesRequirements.length > 0) {
          criteria.salesRequirements = salesRequirements;
        }

        // --- Determine filled steps ---
        const filledSteps: string[] = [];
        if (basics.name) filledSteps.push("basics");
        if (schedule.startDate && schedule.endDate)
          filledSteps.push("schedule");
        if (regions.length > 0 || roles.length > 0)
          filledSteps.push("audience");
        if (selectedCurrencies.length > 0) filledSteps.push("budget");
        if (salesRequirements.length > 0) filledSteps.push("criteria");
        if (approval.requiresApproval != null) filledSteps.push("approval");

        resolve({
          basics,
          schedule,
          audience,
          budgetData,
          criteria,
          approval,
          filledSteps,
          locationRows,
          roleRows,
        });
      } catch (err) {
        reject(err);
      }
    };
    reader.onerror = () => reject(new Error("Failed to read file"));
    reader.readAsArrayBuffer(file);
  });
}

/* ─── Parse "Promotion Rules" sheet into SalesRequirements ─── */

function parseRequirementsSheet(wb: XLSX.WorkBook): SalesRequirement[] {
  const ws = wb.Sheets["Promotion Rules"] || wb.Sheets["Requirements"];
  if (!ws) return [];

  const rows: unknown[][] = XLSX.utils.sheet_to_json(ws, { header: 1 });

  // Find header row
  let headerIdx = 0;
  for (let i = 0; i < Math.min(rows.length, 5); i++) {
    const firstCell = (rows[i]?.[0] || "").toString().trim();
    if (firstCell === "Requirement Name") {
      headerIdx = i;
      break;
    }
  }
  if (rows.length < headerIdx + 2) return [];

  const header = (rows[headerIdx] as unknown[]).map((h) =>
    (h || "").toString().trim(),
  );
  const col = (name: string) => header.indexOf(name);

  const reqNameIdx = col("Requirement Name");
  const ruleTypeIdx = col("Rule Type");
  const productsIdx = col("Products (IDs)");
  const operatorIdx = col("Operator");
  const amountIdx = col("Amount");
  const amountMaxIdx = col("Amount Max");
  const customerTypesIdx = col("Customer Types");
  const payoutCurrIdx = col("Payout Currency");
  const payoutTypeIdx = col("Payout Type");
  const againstIdx = col("Against");
  const bandMinIdx = col("Band Min");
  const bandMaxIdx = col("Band Max");
  const bandValueIdx = col("Band Value");
  const maxPerDealIdx = col("Max Per Deal");

  const cell = (row: unknown[], idx: number): string =>
    idx >= 0 && row[idx] != null ? row[idx]!.toString().trim() : "";

  // Group rows by requirement name
  const reqMap = new Map<string, unknown[][]>();
  for (let i = headerIdx + 1; i < rows.length; i++) {
    const row = rows[i];
    if (!row || row.length === 0) continue;
    const name = cell(row, reqNameIdx);
    if (!name || name.startsWith("HOW TO") || name.startsWith("•")) continue;
    if (!reqMap.has(name)) reqMap.set(name, []);
    reqMap.get(name)!.push(row);
  }

  const requirements: SalesRequirement[] = [];

  reqMap.forEach((reqRows, name) => {
    const eligRules: SalesRequirement["eligibilityGroups"][0]["rules"] = [];
    // Track payout configs by currency+payoutType key
    const payoutConfigs = new Map<string, SalesRequirement["payouts"][0]>();

    for (const row of reqRows) {
      const ruleType = cell(row, ruleTypeIdx);
      const payoutCurr = cell(row, payoutCurrIdx);

      // Eligibility rules
      if (ruleType) {
        if (ruleType === "products") {
          const productIds = parseCommaSeparated(cell(row, productsIdx));
          if (productIds.length > 0) {
            eligRules.push({
              ruleType: "PRODUCTS" as EligibilityRuleType,
              selectedProducts: productIds,
            });
          }
        } else if (ruleType === "booking-amount") {
          const op = cell(row, operatorIdx);
          eligRules.push({
            ruleType: "BOOKING_AMOUNT" as EligibilityRuleType,
            operator: (OPERATOR_MAP[op] || "GREATER_THAN") as RuleOperator,
            value: cell(row, amountIdx) || undefined,
            valueMax: cell(row, amountMaxIdx) || undefined,
          });
        } else if (ruleType === "customer-type") {
          const types = parseCommaSeparated(cell(row, customerTypesIdx));
          if (types.length > 0) {
            eligRules.push({
              ruleType: "CUSTOMER_TYPE" as EligibilityRuleType,
              customerTypes: types,
            });
          }
        }
      }

      // Payout bands
      if (payoutCurr) {
        const currId = matchCurrency(payoutCurr);
        if (currId) {
          const pType = (cell(row, payoutTypeIdx).toUpperCase() ||
            "PERCENTAGE") as PayoutType;
          const key = `${currId}-${pType}`;
          if (!payoutConfigs.has(key)) {
            payoutConfigs.set(key, {
              currencyId: currId,
              payoutType: pType,
              against: cell(row, againstIdx)
                ? (cell(row, againstIdx)
                    .toUpperCase()
                    .replace(/-/g, "_") as PayoutAgainst)
                : undefined,
              maxPerDeal: cell(row, maxPerDealIdx) || undefined,
              bands: [],
            });
          }
          const bandMin = cell(row, bandMinIdx);
          const bandValue = cell(row, bandValueIdx);
          if (bandValue) {
            payoutConfigs.get(key)!.bands.push({
              minAmount: bandMin,
              maxAmount: cell(row, bandMaxIdx),
              payoutValue: bandValue,
            });
          }
        }
      }
    }

    if (eligRules.length > 0 || payoutConfigs.size > 0) {
      requirements.push({
        name,
        eligibilityGroups: eligRules.length > 0 ? [{ rules: eligRules }] : [],
        payouts: Array.from(payoutConfigs.values()),
      });
    }
  });

  return requirements;
}
