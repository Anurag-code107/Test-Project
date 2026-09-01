/**
 * Excel Template Generation & Parsing Utilities
 *
 * Generates a CSV-based template (browser-native, no external dependency).
 * For full .xlsx support, ExcelJS would be added in Phase 9.
 */

import type { IncentiveType } from "@/types/incentive.types";

interface TemplateRow {
  field: string;
  description: string;
  required: boolean;
  example: string;
}

const SHARED_FIELDS: TemplateRow[] = [
  {
    field: "name",
    description: "Incentive program name",
    required: true,
    example: "Q2 Server Push",
  },
  {
    field: "description",
    description: "Program description",
    required: false,
    example: "Drive enterprise server sales",
  },
  {
    field: "incentiveType",
    description: "SALES | TRAINING | ACTIVITY | JOURNEY",
    required: true,
    example: "SALES",
  },
  {
    field: "startDate",
    description: "Start date (YYYY-MM-DD)",
    required: true,
    example: "2026-04-01",
  },
  {
    field: "endDate",
    description: "End date (YYYY-MM-DD)",
    required: true,
    example: "2026-06-30",
  },
  {
    field: "timezone",
    description: "IANA timezone",
    required: false,
    example: "America/New_York",
  },
  {
    field: "totalBudget",
    description: "Total budget amount",
    required: true,
    example: "50000",
  },
  {
    field: "currency",
    description: "Currency code",
    required: true,
    example: "USD",
  },
  {
    field: "allocationMethod",
    description: "EQUAL | WEIGHTED | PERFORMANCE_BASED",
    required: true,
    example: "EQUAL",
  },
  {
    field: "budgetMode",
    description: "GLOBAL | PER_REGION",
    required: true,
    example: "GLOBAL",
  },
  {
    field: "regions",
    description: "Comma-separated regions",
    required: false,
    example: "USA,EMEAR,APJC",
  },
  {
    field: "partnerTypes",
    description: "Comma-separated partner types",
    required: false,
    example: "Reseller,Distributor",
  },
];

const SALES_FIELDS: TemplateRow[] = [
  {
    field: "requirementName",
    description: "Requirement name",
    required: true,
    example: "Enterprise Server Sales",
  },
  {
    field: "eligibleProducts",
    description: "Comma-separated product IDs",
    required: false,
    example: "srv-001,srv-002",
  },
  {
    field: "payoutType",
    description: "PERCENTAGE | FLAT",
    required: true,
    example: "PERCENTAGE",
  },
  {
    field: "payoutValue",
    description: "Payout amount or percentage",
    required: true,
    example: "5",
  },
  {
    field: "minAmount",
    description: "Minimum deal amount for band",
    required: false,
    example: "10000",
  },
  {
    field: "maxAmount",
    description: "Maximum deal amount for band",
    required: false,
    example: "50000",
  },
];

const TRAINING_FIELDS: TemplateRow[] = [
  {
    field: "courseId",
    description: "LMS course ID",
    required: true,
    example: "course-001",
  },
  {
    field: "courseName",
    description: "Course name",
    required: true,
    example: "Enterprise Networking",
  },
  {
    field: "required",
    description: "Is course required (true/false)",
    required: true,
    example: "true",
  },
];

const ACTIVITY_FIELDS: TemplateRow[] = [
  {
    field: "activityName",
    description: "Activity name",
    required: true,
    example: "Product Demo",
  },
  {
    field: "activityCategory",
    description: "Category ID",
    required: true,
    example: "CUSTOMER_ENGAGEMENT",
  },
  {
    field: "documentName",
    description: "Required document name",
    required: false,
    example: "Demo Recording",
  },
];

const TYPE_FIELDS: Record<IncentiveType, TemplateRow[]> = {
  SALES: SALES_FIELDS,
  TRAINING: TRAINING_FIELDS,
  ACTIVITY: ACTIVITY_FIELDS,
  JOURNEY: [], // Journey links existing incentives, not template-configurable
};

/**
 * Generate a CSV template string for download.
 */
export function generateTemplateCSV(type?: IncentiveType): string {
  const fields = [...SHARED_FIELDS, ...(type ? TYPE_FIELDS[type] : [])];

  const header = fields.map((f) => f.field).join(",");
  const descriptions = fields.map((f) => `"${f.description}"`).join(",");
  const required = fields
    .map((f) => (f.required ? "REQUIRED" : "optional"))
    .join(",");
  const example = fields.map((f) => `"${f.example}"`).join(",");

  return [
    "# TenXEngage Incentive Template",
    `# Type: ${type ?? "Generic"}`,
    "# Fill in the data row below and upload this file",
    "",
    header,
    descriptions,
    required,
    example,
  ].join("\n");
}

/**
 * Trigger a CSV file download in the browser.
 */
export function downloadTemplate(type?: IncentiveType) {
  const csv = generateTemplateCSV(type);
  const blob = new Blob([csv], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `incentive-template${type ? `-${type.toLowerCase()}` : ""}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Parse an uploaded CSV file into a key-value record.
 * Returns null if parsing fails.
 */
export function parseTemplateCSV(
  content: string,
): Record<string, string> | null {
  try {
    const lines = content
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l && !l.startsWith("#"));

    if (lines.length < 4) return null;

    const headerLine = lines[0];
    if (!headerLine) return null;
    const headers = headerLine.split(",").map((h) => h.trim());
    // Lines 1-2 are descriptions/required markers, line 3+ is data
    const dataLine = lines[3] ?? lines[lines.length - 1];
    if (!dataLine) return null;
    const values = dataLine
      .split(",")
      .map((v) => v.trim().replace(/^"|"$/g, ""));

    const result: Record<string, string> = {};
    headers.forEach((header, i) => {
      if (values[i]) {
        result[header] = values[i];
      }
    });

    return result;
  } catch {
    return null;
  }
}
