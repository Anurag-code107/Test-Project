/**
 * Excel Exporter for Incentive Detail
 *
 * Exports an IncentiveDetailResponse into an .xlsx file matching the
 * Summer_Sales_Blitz_Template structure so that the file can be re-imported
 * by the template parser (excelTemplateParser.ts).
 *
 * Uses ExcelJS for styled output (colors, fonts, borders, merged cells).
 */

import ExcelJS from "exceljs";
import type { IncentiveDetailResponse } from "@/types/incentive.types";

/* ─── Reverse enum mappings (backend → Excel) ─── */

import { getCurrency, monetaryCurrencyIds } from "@/config/currencies";

const RULE_TYPE_MAP: Record<string, string> = {
  PRODUCTS: "products",
  BOOKING_AMOUNT: "booking-amount",
  CUSTOMER_TYPE: "customer-type",
};

const OPERATOR_REVERSE: Record<string, string> = {
  GREATER_THAN: "greater-than",
  LESS_THAN: "less-than",
  EQUALS: "equal-to",
  BETWEEN: "between",
  GREATER_THAN_OR_EQUAL: "greater-than-or-equal",
};

const PAYOUT_AGAINST_REVERSE: Record<string, string> = {
  TOTAL_BOOKING: "total-booking",
  ELIGIBLE_PRODUCTS: "eligible-products",
};

/* ─── Styling constants (matches ai-incentive-pilot exactly) ─── */

const BORDER_THIN: Partial<ExcelJS.Borders> = {
  top: { style: "thin" },
  left: { style: "thin" },
  bottom: { style: "thin" },
  right: { style: "thin" },
};

const SECTION_HEADER_FONT: Partial<ExcelJS.Font> = {
  bold: true,
  size: 18,
  color: { argb: "FFFFFFFF" },
};

const SECTION_HEADER_FILL: ExcelJS.FillPattern = {
  type: "pattern",
  pattern: "solid",
  fgColor: { argb: "FF2D5F8A" },
};

const COLUMN_HEADER_FONT: Partial<ExcelJS.Font> = {
  bold: true,
  size: 15,
};

const COLUMN_HEADER_FILL: ExcelJS.FillPattern = {
  type: "pattern",
  pattern: "solid",
  fgColor: { argb: "FFD6E4F0" },
};

const BODY_FONT: Partial<ExcelJS.Font> = {
  size: 14,
};

const NOTES_FONT: Partial<ExcelJS.Font> = {
  size: 13,
  italic: true,
  color: { argb: "FF666666" },
};

function applySectionHeader(row: ExcelJS.Row, colCount: number) {
  row.font = SECTION_HEADER_FONT;
  row.fill = SECTION_HEADER_FILL;
  row.height = 34;
  for (let c = 1; c <= colCount; c++) {
    row.getCell(c).border = BORDER_THIN;
  }
}

function applyColumnHeader(row: ExcelJS.Row, colCount: number) {
  row.font = COLUMN_HEADER_FONT;
  row.fill = COLUMN_HEADER_FILL;
  row.height = 28;
  for (let c = 1; c <= colCount; c++) {
    row.getCell(c).border = BORDER_THIN;
  }
}

function applyBodyRow(row: ExcelJS.Row, colCount: number) {
  row.font = BODY_FONT;
  row.height = 24;
  for (let c = 1; c <= colCount; c++) {
    row.getCell(c).border = BORDER_THIN;
  }
  if (colCount >= 3) {
    row.getCell(3).font = NOTES_FONT;
  }
}

/* ─── Notes (matches the reference template) ─── */

function note(field: string): string {
  return NOTES[field] ?? "";
}

const NOTES: Record<string, string> = {
  "Incentive Name": "Required. E.g. 'Q1 2025 Growth Accelerator'",
  Description: "Required. Brief description of the incentive program.",
  "Fiscal Years": "Comma-separated: FY2024, FY2025, FY2026",
  "Fiscal Quarters": "Comma-separated: Q1, Q2, Q3, Q4",
  "Start Date": "Format: YYYY-MM-DD (e.g. 2025-01-01)",
  "End Date": "Format: YYYY-MM-DD (e.g. 2025-03-31)",
  "Eligible Regions": "Comma-separated from: USA, CA, LATAM, EMEAR, APJC",
  "Eligible Roles": "Comma-separated from: Company Admin, Partner Seller",
  "Partner Types": "Optional. Comma-separated from: Reseller, Distributor, OEM",
  "Reward Currencies": "Comma-separated from: Cash, Points, Tickets, Credits",
  "Budget Mode": "Either 'global' or 'per-region'",
  "Cash Budget": "Global budget amount for Cash (number only)",
  "Points Budget": "Global budget amount for Points (number only)",
  "Reward Message":
    'How the reward is displayed to partners, e.g. "Earn up to $5,000"',
  "Cash Per Completion": "Amount of Cash earned per completion (number only)",
  "Points Per Completion":
    "Amount of Points earned per completion (number only)",
  "Tickets Per Completion":
    "Amount of Tickets earned per completion (number only)",
  "Credits Per Completion":
    "Amount of Credits earned per completion (number only)",
  "Max Per Partner": "Optional. Maximum budget per partner company",
  "Max Per User": "Optional. Maximum budget per individual user",
  "Requires Approval":
    "Yes or No — whether this incentive needs approval before activation",
  "Approver Emails": "Comma-separated email addresses of approvers",
  "Approver Categories":
    "Comma-separated categories matching each approver (e.g. Finance, Legal)",
  "Required Approvals": "Number of approvals required (number only)",
};

/* ─── Build "Incentive Setup" sheet ─── */

function buildIncentiveSetupSheet(
  ws: ExcelJS.Worksheet,
  detail: IncentiveDetailResponse,
) {
  ws.columns = [{ width: 40 }, { width: 48 }, { width: 58 }];

  const COL_COUNT = 3;

  function addSection(title: string, fields: [string, string, string][]) {
    const headerRow = ws.addRow([title, "", ""]);
    ws.mergeCells(headerRow.number, 1, headerRow.number, COL_COUNT);
    applySectionHeader(headerRow, COL_COUNT);

    const colRow = ws.addRow(["Field", "Your Value", "Notes / Instructions"]);
    applyColumnHeader(colRow, COL_COUNT);

    for (const [field, value, notes] of fields) {
      const row = ws.addRow([field, value, notes]);
      applyBodyRow(row, COL_COUNT);
    }

    ws.addRow([]);
  }

  // --- Extract audience data ---
  // LOCATION rules carry the LocationValue UUID in ruleValue; locationValueName
  // is the resolved display name. Fall back to ruleValue if the server didn't
  // resolve (shouldn't happen for fresh saves but keeps export forgiving).
  const regions =
    detail.audienceRules
      ?.filter((r) => r.ruleType === "LOCATION")
      .map((r) => r.locationValueName ?? r.ruleValue) ?? [];
  const roles =
    detail.audienceRules
      ?.filter((r) => r.ruleType === "ROLE")
      .map((r) => r.ruleValue) ?? [];
  const partnerTypes =
    detail.audienceRules
      ?.filter((r) => r.ruleType === "PARTNER_TYPE")
      .map((r) => r.ruleValue) ?? [];

  // --- Extract budget data ---
  const currencyLabels = (detail.rewardCurrencies ?? [])
    .map((id) => getCurrency(id).label)
    .filter(Boolean);

  const budgetMode =
    detail.budget?.budgetMode === "PER_LOCATION" ? "per-location" : "global";

  // Section 1: Basic Information
  addSection("SECTION 1: BASIC INFORMATION", [
    ["Incentive Name", detail.name, note("Incentive Name")],
    ["Description", detail.description ?? "", note("Description")],
  ]);

  // Section 2: Timeline
  addSection("SECTION 2: TIMELINE", [
    [
      "Fiscal Years",
      (detail.fiscalYears ?? []).join(", "),
      note("Fiscal Years"),
    ],
    [
      "Fiscal Quarters",
      (detail.fiscalQuarters ?? []).join(", "),
      note("Fiscal Quarters"),
    ],
    [
      "Start Date",
      detail.startDate ? detail.startDate.slice(0, 10) : "",
      note("Start Date"),
    ],
    [
      "End Date",
      detail.endDate ? detail.endDate.slice(0, 10) : "",
      note("End Date"),
    ],
  ]);

  // Section 3: Participant Eligibility
  addSection("SECTION 3: PARTICIPANT ELIGIBILITY", [
    ["Eligible Regions", regions.join(", "), note("Eligible Regions")],
    ["Eligible Roles", roles.join(", "), note("Eligible Roles")],
    ["Partner Types", partnerTypes.join(", "), note("Partner Types")],
  ]);

  // Section 4: Budget & Rewards
  const budgetFields: [string, string, string][] = [
    ["Reward Currencies", currencyLabels.join(", "), note("Reward Currencies")],
    ["Budget Mode", budgetMode, note("Budget Mode")],
  ];

  // Add per-currency budget rows for monetary currencies
  const monetaryCurrencies = [...monetaryCurrencyIds];
  for (const cid of monetaryCurrencies) {
    const label = getCurrency(cid).label;
    if (!label) continue;
    let amount = "";
    const detailCurrency =
      detail.budget?.currencyId ?? detail.budget?.currency ?? "";
    if (detail.budget && detailCurrency.toLowerCase() === cid) {
      amount = detail.budget.totalBudget;
    } else if (detail.budget?.locationAllocations) {
      // Sum allocations for this currency. Note: the singular `budget` is a
      // single-currency entity, so all of its locationAllocations belong to
      // its own currency — only sum if the currency matches.
      if (detailCurrency.toLowerCase() === cid) {
        let total = 0;
        for (const alloc of detail.budget.locationAllocations) {
          if (alloc.amount) total += parseFloat(alloc.amount) || 0;
        }
        if (total > 0) amount = String(total);
      }
    }
    if (amount || (detail.rewardCurrencies ?? []).includes(cid)) {
      budgetFields.push([`${label} Budget`, amount, note(`${label} Budget`)]);
    }
  }

  // Always include Reward Message
  budgetFields.push([
    "Reward Message",
    detail.rewardMessage ?? "",
    note("Reward Message"),
  ]);

  // Per-completion reward amounts
  if (detail.rewardAmounts) {
    for (const [cid, amount] of Object.entries(detail.rewardAmounts)) {
      const label = getCurrency(cid).label ?? cid;
      budgetFields.push([
        `${label} Per Completion`,
        amount,
        note(`${label} Per Completion`),
      ]);
    }
  }

  budgetFields.push([
    "Max Per Partner",
    detail.budget?.maxPerPartner ?? "",
    note("Max Per Partner"),
  ]);
  budgetFields.push([
    "Max Per User",
    detail.budget?.maxPerUser ?? "",
    note("Max Per User"),
  ]);
  if (detail.incentiveType === "SALES") {
    budgetFields.push([
      "Claimers Per PO",
      detail.maxClaimersPerDeal != null
        ? String(detail.maxClaimersPerDeal)
        : "1",
      note("Claimers Per PO"),
    ]);
  }

  addSection("SECTION 4: BUDGET & REWARDS", budgetFields);

  // Section 5: Approval Configuration
  const approvalFields: [string, string, string][] = [
    [
      "Requires Approval",
      detail.requiresApproval ? "Yes" : "No",
      note("Requires Approval"),
    ],
    [
      "Approver Emails",
      (detail.approvers ?? []).map((a) => a.email).join(", "),
      note("Approver Emails"),
    ],
    [
      "Approver Categories",
      (detail.approvers ?? []).map((a) => a.category).join(", "),
      note("Approver Categories"),
    ],
    [
      "Required Approvals",
      detail.requiredApprovals != null ? String(detail.requiredApprovals) : "",
      note("Required Approvals"),
    ],
  ];

  addSection("SECTION 5: APPROVAL CONFIGURATION", approvalFields);
}

/* ─── Build "Promotion Rules" sheet ─── */

function buildPromotionRulesSheet(
  ws: ExcelJS.Worksheet,
  detail: IncentiveDetailResponse,
) {
  const REQ_COLS = 14;
  ws.columns = [
    { width: 26 },
    { width: 18 },
    { width: 26 },
    { width: 25 },
    { width: 14 },
    { width: 14 },
    { width: 32 },
    { width: 18 },
    { width: 16 },
    { width: 20 },
    { width: 14 },
    { width: 14 },
    { width: 14 },
    { width: 16 },
  ];

  const secRow = ws.addRow(["SECTION 6: PROMOTION RULES"]);
  ws.mergeCells(secRow.number, 1, secRow.number, REQ_COLS);
  applySectionHeader(secRow, REQ_COLS);

  const headers = [
    "Requirement Name",
    "Rule Type",
    "Products (IDs)",
    "Operator",
    "Amount",
    "Amount Max",
    "Customer Types",
    "Payout Currency",
    "Payout Type",
    "Against",
    "Band Min",
    "Band Max",
    "Band Value",
    "Max Per Deal",
  ];
  const hRow = ws.addRow(headers);
  applyColumnHeader(hRow, REQ_COLS);

  if (!detail.salesRequirements?.length) return;

  for (const req of detail.salesRequirements) {
    // Eligibility rule rows
    if (req.eligibilityGroups?.length) {
      for (const group of req.eligibilityGroups) {
        for (const rule of group.rules) {
          const ruleType =
            RULE_TYPE_MAP[rule.ruleType] ?? rule.ruleType.toLowerCase();
          const rowData: string[] = new Array(14).fill("");
          rowData[0] = req.name;
          rowData[1] = ruleType;

          if (rule.ruleType === "PRODUCTS") {
            rowData[2] = (rule.selectedProducts ?? []).join(", ");
          } else if (rule.ruleType === "BOOKING_AMOUNT") {
            rowData[3] = OPERATOR_REVERSE[rule.operator ?? ""] ?? "";
            rowData[4] = rule.value ?? "";
            rowData[5] = rule.valueMax ?? "";
          } else if (rule.ruleType === "CUSTOMER_TYPE") {
            rowData[6] = (rule.customerTypes ?? []).join(", ");
          }

          const row = ws.addRow(rowData);
          applyBodyRow(row, REQ_COLS);
        }
      }
    }

    // Payout band rows
    if (req.payouts?.length) {
      for (const payout of req.payouts) {
        const currLabel = getCurrency(payout.currencyId).label;
        const payoutType = payout.payoutType.toLowerCase();
        const against = payout.against
          ? (PAYOUT_AGAINST_REVERSE[payout.against] ??
            payout.against.toLowerCase().replace(/_/g, "-"))
          : "";

        if (payout.bands?.length) {
          for (const band of payout.bands) {
            const rowData: string[] = new Array(14).fill("");
            rowData[0] = req.name;
            rowData[7] = currLabel;
            rowData[8] = payoutType;
            rowData[9] = against;
            rowData[10] = band.minAmount;
            rowData[11] = band.maxAmount;
            rowData[12] = band.payoutValue;
            rowData[13] = payout.maxPerDeal ?? "";
            const row = ws.addRow(rowData);
            applyBodyRow(row, REQ_COLS);
          }
        } else {
          const rowData: string[] = new Array(14).fill("");
          rowData[0] = req.name;
          rowData[7] = currLabel;
          rowData[8] = payoutType;
          rowData[9] = against;
          rowData[13] = payout.maxPerDeal ?? "";
          const row = ws.addRow(rowData);
          applyBodyRow(row, REQ_COLS);
        }
      }
    }
  }
}

/* ─── Build "Training Courses" sheet ─── */

function buildTrainingSheet(
  ws: ExcelJS.Worksheet,
  detail: IncentiveDetailResponse,
) {
  ws.columns = [
    { width: 15 },
    { width: 30 },
    { width: 20 },
    { width: 20 },
    { width: 12 },
    { width: 15 },
    { width: 10 },
  ];
  const COL_COUNT = 4;

  const headerRow = ws.addRow(["Training Courses"]);
  ws.mergeCells(headerRow.number, 1, headerRow.number, COL_COUNT);
  applySectionHeader(headerRow, COL_COUNT);

  const colRow = ws.addRow([
    "Course ID",
    "Course Name",
    "Category",
    "Required",
  ]);
  applyColumnHeader(colRow, COL_COUNT);

  for (const course of detail.trainingCourses!) {
    const row = ws.addRow([
      course.courseId,
      course.courseName,
      course.courseCategory ?? "",
      course.required ? "Yes" : "No",
    ]);
    row.font = BODY_FONT;
    row.height = 24;
    for (let c = 1; c <= COL_COUNT; c++) {
      row.getCell(c).border = BORDER_THIN;
    }
  }

  if (detail.trainingRequiredCount != null) {
    ws.addRow([]);
    const infoRow = ws.addRow([
      "Required Courses to Complete",
      detail.trainingRequiredCount,
    ]);
    infoRow.font = { ...BODY_FONT, bold: true };
  }
}

/* ─── Build "Activity Definitions" sheet ─── */

function buildActivitySheet(
  ws: ExcelJS.Worksheet,
  detail: IncentiveDetailResponse,
) {
  ws.columns = [
    { width: 25 },
    { width: 40 },
    { width: 25 },
    { width: 12 },
    { width: 40 },
  ];
  const COL_COUNT = 5;

  const headerRow = ws.addRow(["Activity Definitions"]);
  ws.mergeCells(headerRow.number, 1, headerRow.number, COL_COUNT);
  applySectionHeader(headerRow, COL_COUNT);

  const colRow = ws.addRow([
    "Name",
    "Description",
    "Category",
    "Sort Order",
    "Required Documents",
  ]);
  applyColumnHeader(colRow, COL_COUNT);

  for (const activity of detail.activityDefinitions!) {
    const docNames =
      activity.requiredDocuments
        ?.map((d) => `${d.name}${d.required ? " (required)" : ""}`)
        .join("; ") ?? "";
    const row = ws.addRow([
      activity.name,
      activity.description ?? "",
      activity.categoryId,
      activity.sortOrder,
      docNames,
    ]);
    row.font = BODY_FONT;
    row.height = 24;
    for (let c = 1; c <= COL_COUNT; c++) {
      row.getCell(c).border = BORDER_THIN;
    }
  }
}

/* ─── Build "Instructions" sheet ─── */

function buildInstructionsSheet(
  ws: ExcelJS.Worksheet,
  detail: IncentiveDetailResponse,
) {
  ws.columns = [{ width: 95 }];

  const titleRow = ws.addRow([
    "INCENTIVE BUILDER \u2014 TEMPLATE INSTRUCTIONS",
  ]);
  titleRow.font = { bold: true, size: 16 };
  titleRow.height = 30;

  ws.addRow([]);
  const typeRow = ws.addRow([`Incentive Type: ${detail.incentiveType}`]);
  typeRow.font = { bold: true, size: 12 };
  ws.addRow([]);

  const instLines: { text: string; bold?: boolean }[] = [
    { text: "HOW TO USE:", bold: true },
    {
      text: "1. Fill out the 'Incentive Setup' sheet with your incentive details",
    },
    { text: "2. Leave fields blank if not applicable" },
    {
      text: "3. Use comma-separated values where noted in the instructions column",
    },
    { text: "4. Save the file and upload it in the Incentive Builder" },
    { text: "" },
    { text: "SHEETS IN THIS WORKBOOK:", bold: true },
    {
      text: "\u2022 Incentive Setup \u2014 All core fields (name, timeline, eligibility, budget, approval)",
    },
    ...(detail.incentiveType === "SALES"
      ? [
          {
            text: "\u2022 Promotion Rules \u2014 Eligibility rules and payout criteria for promotions",
          },
        ]
      : []),
    ...(detail.trainingCourses?.length
      ? [{ text: "\u2022 Training Courses \u2014 Course assignments" }]
      : []),
    ...(detail.activityDefinitions?.length
      ? [{ text: "\u2022 Activity Definitions \u2014 Activity configurations" }]
      : []),
    { text: "\u2022 Instructions \u2014 This sheet" },
    { text: "" },
    { text: "VALID VALUES REFERENCE:", bold: true },
    { text: "  Regions: USA, CA, LATAM, EMEAR, APJC" },
    { text: "  Roles: Company Admin, Partner Seller" },
    { text: "  Partner Types: Reseller, Distributor, OEM" },
    { text: "  Currencies: Cash, Points, Tickets, Credits" },
    {
      text: "  Operators: greater-than, less-than, equal-to, between, greater-than-or-equal",
    },
    { text: "  Payout Types: percentage, flat" },
    { text: "  Against: total-booking, eligible-products" },
  ];

  for (const line of instLines) {
    const r = ws.addRow([line.text]);
    r.font = { size: 14, bold: line.bold || false };
  }
}

/* ─── Main export function ─── */

export async function exportIncentiveToExcel(
  detail: IncentiveDetailResponse,
): Promise<void> {
  const wb = new ExcelJS.Workbook();

  // Main setup sheet (always present)
  const setupSheet = wb.addWorksheet("Incentive Setup");
  buildIncentiveSetupSheet(setupSheet, detail);

  // Promotion Rules sheet (Sales only)
  if (detail.incentiveType === "SALES") {
    const rulesSheet = wb.addWorksheet("Promotion Rules");
    buildPromotionRulesSheet(rulesSheet, detail);
  }

  // Training Courses sheet
  if (detail.trainingCourses?.length) {
    const trainingSheet = wb.addWorksheet("Training Courses");
    buildTrainingSheet(trainingSheet, detail);
  }

  // Activity Definitions sheet
  if (detail.activityDefinitions?.length) {
    const activitySheet = wb.addWorksheet("Activity Definitions");
    buildActivitySheet(activitySheet, detail);
  }

  // Instructions sheet
  const instructionsSheet = wb.addWorksheet("Instructions");
  buildInstructionsSheet(instructionsSheet, detail);

  // Download
  const buffer = await wb.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  const safeName = detail.name
    .replace(/[^a-zA-Z0-9_\- ]/g, "")
    .replace(/\s+/g, "_");
  a.download = `${safeName}_Template.xlsx`;
  a.click();
  URL.revokeObjectURL(url);
}
