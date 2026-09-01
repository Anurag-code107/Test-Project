import { useState, useMemo } from "react";
import { FlipTransition } from "@/components/FlipTransition";
import { FeatureGate } from "@/components/FeatureGate";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Wrench,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  Database,
  DollarSign,
  GraduationCap,
  FileText,
  ShoppingCart,
  Users,
  Save,
  FolderOpen,
  Play,
  Trash2,
  Clock,
  ArrowUpDown,
  Search,
  Plus,
  Filter,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { PageBanner } from "@/components/PageBanner";

// ─── Types ────────────────────────────────────────────────────────────────────

interface DatasetField {
  id: string;
  label: string;
  category: string;
}

interface Dataset {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  fields: DatasetField[];
  data: Record<string, string | number>[];
}

interface FieldFilter {
  id: string;
  fieldId: string;
  operator: string;
  value: string;
  valueTo?: string;
}

interface SavedTemplate {
  id: string;
  name: string;
  datasetId: string;
  fieldIds: string[];
  filters: FieldFilter[];
  createdAt: string;
  lastRunAt: string;
}

type ViewMode = "builder" | "templates";
type BuilderStep = "select-dataset" | "select-fields" | "preview";
type FieldType = "date" | "numeric" | "text";

// ─── Operators ────────────────────────────────────────────────────────────────

const textOperators = [{ id: "contains", label: "Contains" }];

const numericOperators = [
  { id: "equals", label: "=" },
  { id: "not_equals", label: "≠" },
  { id: "gt", label: ">" },
  { id: "gte", label: "≥" },
  { id: "lt", label: "<" },
  { id: "lte", label: "≤" },
];

const dateOperators = [
  { id: "between", label: "Between" },
  { id: "after", label: "After" },
  { id: "before", label: "Before" },
];

const numericFields = [
  "deal_value",
  "booking_amount",
  "commission_earned",
  "amount",
  "points_earned",
  "points_redeemed",
  "points_balance",
  "user_count",
  "active_incentives",
  "claims_submitted",
];

const dateFields = [
  "close_date",
  "submitted_date",
  "enrolled_date",
  "completed_date",
  "last_activity",
  "joined_date",
];

function getFieldType(fieldId: string): FieldType {
  if (dateFields.includes(fieldId)) return "date";
  if (numericFields.includes(fieldId)) return "numeric";
  return "text";
}

function getOperatorsForField(fieldId: string) {
  const type = getFieldType(fieldId);
  if (type === "date") return dateOperators;
  if (type === "numeric") return numericOperators;
  return textOperators;
}

function parseNumericValue(val: string | number): number {
  if (typeof val === "number") return val;
  return parseFloat(val.replace(/[^0-9.-]/g, "")) || 0;
}

function applyFilter(
  row: Record<string, string | number>,
  filter: FieldFilter,
): boolean {
  const raw = row[filter.fieldId];
  if (raw === undefined || raw === null) return false;
  const filterVal = filter.value.trim();
  if (!filterVal) return true;

  const fieldType = getFieldType(filter.fieldId);

  if (fieldType === "date") {
    const rowDate = String(raw);
    switch (filter.operator) {
      case "between": {
        const to = filter.valueTo?.trim();
        if (!to) return rowDate >= filterVal;
        return rowDate >= filterVal && rowDate <= to;
      }
      case "after":
        return rowDate > filterVal;
      case "before":
        return rowDate < filterVal;
      default:
        return true;
    }
  } else if (fieldType === "numeric") {
    const rowNum = parseNumericValue(raw);
    const filterNum = parseFloat(filterVal);
    if (isNaN(filterNum)) return true;
    switch (filter.operator) {
      case "equals":
        return rowNum === filterNum;
      case "not_equals":
        return rowNum !== filterNum;
      case "gt":
        return rowNum > filterNum;
      case "gte":
        return rowNum >= filterNum;
      case "lt":
        return rowNum < filterNum;
      case "lte":
        return rowNum <= filterNum;
      default:
        return true;
    }
  } else {
    const rowStr = String(raw).toLowerCase();
    const fStr = filterVal.toLowerCase();
    switch (filter.operator) {
      case "contains":
        return rowStr.includes(fStr);
      default:
        return true;
    }
  }
}

// ─── Mock Datasets ────────────────────────────────────────────────────────────

const datasets: Dataset[] = [
  {
    id: "sales",
    name: "Sales & Deals",
    description: "Deal registrations, bookings, and revenue data by partner",
    icon: <DollarSign className="h-5 w-5 text-emerald-600" />,
    fields: [
      { id: "partner_company", label: "Partner Company", category: "Partner" },
      { id: "partner_user", label: "Partner User", category: "Partner" },
      { id: "region", label: "Region", category: "Partner" },
      { id: "deal_name", label: "Deal Name", category: "Deal" },
      { id: "deal_value", label: "Deal Value", category: "Deal" },
      { id: "deal_stage", label: "Deal Stage", category: "Deal" },
      { id: "product", label: "Product", category: "Deal" },
      { id: "close_date", label: "Close Date", category: "Deal" },
      {
        id: "booking_amount",
        label: "Booking Amount",
        category: "Financial",
      },
      {
        id: "commission_earned",
        label: "Commission Earned",
        category: "Financial",
      },
    ],
    data: [
      {
        partner_company: "TechVision Partners",
        partner_user: "John Smith",
        region: "AMERICAS",
        deal_name: "Acme Corp Expansion",
        deal_value: "$125,000",
        deal_stage: "Closed Won",
        product: "Enterprise Suite",
        close_date: "2025-01-15",
        booking_amount: "$125,000",
        commission_earned: "$6,250",
      },
      {
        partner_company: "TechVision Partners",
        partner_user: "Marcus Brown",
        region: "AMERICAS",
        deal_name: "Beta Inc Migration",
        deal_value: "$85,000",
        deal_stage: "Negotiation",
        product: "Cloud Platform",
        close_date: "2025-02-28",
        booking_amount: "$0",
        commission_earned: "$0",
      },
      {
        partner_company: "CloudSync Solutions",
        partner_user: "Pierre Dubois",
        region: "EMEAR",
        deal_name: "EuroTech Onboarding",
        deal_value: "$210,000",
        deal_stage: "Closed Won",
        product: "Security Suite",
        close_date: "2025-01-20",
        booking_amount: "$210,000",
        commission_earned: "$10,500",
      },
      {
        partner_company: "Pacific Digital",
        partner_user: "Wei Chen",
        region: "APJ",
        deal_name: "Tokyo Systems Upgrade",
        deal_value: "$340,000",
        deal_stage: "Closed Won",
        product: "Enterprise Suite",
        close_date: "2025-01-08",
        booking_amount: "$340,000",
        commission_earned: "$17,000",
      },
      {
        partner_company: "Pacific Digital",
        partner_user: "Kenji Tanaka",
        region: "APJ",
        deal_name: "Seoul Digital Transform",
        deal_value: "$175,000",
        deal_stage: "Proposal",
        product: "Cloud Platform",
        close_date: "2025-03-15",
        booking_amount: "$0",
        commission_earned: "$0",
      },
      {
        partner_company: "Innovate LATAM",
        partner_user: "Carlos Silva",
        region: "LATAM",
        deal_name: "BrazilTech Setup",
        deal_value: "$95,000",
        deal_stage: "Closed Won",
        product: "Security Suite",
        close_date: "2025-02-01",
        booking_amount: "$95,000",
        commission_earned: "$4,750",
      },
    ],
  },
  {
    id: "claims",
    name: "Claims",
    description: "Claim submissions, statuses, AI scores, and payout details",
    icon: <FileText className="h-5 w-5 text-blue-600" />,
    fields: [
      { id: "claim_id", label: "Claim ID", category: "Claim" },
      { id: "partner_company", label: "Partner Company", category: "Partner" },
      { id: "submitted_by", label: "Submitted By", category: "Partner" },
      { id: "incentive_name", label: "Incentive Name", category: "Claim" },
      { id: "claim_type", label: "Claim Type", category: "Claim" },
      { id: "amount", label: "Amount", category: "Financial" },
      { id: "status", label: "Status", category: "Claim" },
      { id: "ai_score", label: "AI Score", category: "AI" },
      { id: "submitted_date", label: "Submitted Date", category: "Date" },
      { id: "reviewer_notes", label: "Reviewer Notes", category: "Review" },
    ],
    data: [
      {
        claim_id: "CLM-001",
        partner_company: "TechVision Partners",
        submitted_by: "John Smith",
        incentive_name: "Q1 SMB Pipeline Accelerator",
        claim_type: "MDF",
        amount: "$4,500",
        status: "Approved",
        ai_score: "A",
        submitted_date: "2025-02-10",
        reviewer_notes: "All requirements met",
      },
      {
        claim_id: "CLM-002",
        partner_company: "TechVision Partners",
        submitted_by: "Lisa Wang",
        incentive_name: "New Product Launch SPIFF",
        claim_type: "SPIFF",
        amount: "$1,500",
        status: "Paid",
        ai_score: "A",
        submitted_date: "2025-02-15",
        reviewer_notes: "Payment processed",
      },
      {
        claim_id: "CLM-003",
        partner_company: "CloudSync Solutions",
        submitted_by: "Pierre Dubois",
        incentive_name: "Q1 SMB Pipeline Accelerator",
        claim_type: "MDF",
        amount: "$2,800",
        status: "Under Review",
        ai_score: "B",
        submitted_date: "2025-02-20",
        reviewer_notes: "-",
      },
      {
        claim_id: "CLM-004",
        partner_company: "Pacific Digital",
        submitted_by: "Kenji Tanaka",
        incentive_name: "Co-Marketing Event Fund",
        claim_type: "MDF",
        amount: "$3,000",
        status: "Pending",
        ai_score: "A",
        submitted_date: "2025-03-05",
        reviewer_notes: "-",
      },
      {
        claim_id: "CLM-005",
        partner_company: "Pacific Digital",
        submitted_by: "Wei Chen",
        incentive_name: "New Product Launch SPIFF",
        claim_type: "SPIFF",
        amount: "$1,500",
        status: "Rejected",
        ai_score: "D",
        submitted_date: "2025-03-12",
        reviewer_notes: "Missing signed contract",
      },
      {
        claim_id: "CLM-006",
        partner_company: "Innovate LATAM",
        submitted_by: "Ana Mendez",
        incentive_name: "Annual Revenue Rebate",
        claim_type: "Rebate",
        amount: "$8,200",
        status: "Pending",
        ai_score: "A",
        submitted_date: "2025-04-01",
        reviewer_notes: "-",
      },
    ],
  },
  {
    id: "training",
    name: "Training Completions",
    description: "Course enrollments, completions, certifications, and scores",
    icon: <GraduationCap className="h-5 w-5 text-purple-600" />,
    fields: [
      { id: "partner_company", label: "Partner Company", category: "Partner" },
      { id: "user_name", label: "User Name", category: "Partner" },
      { id: "region", label: "Region", category: "Partner" },
      { id: "course_name", label: "Course Name", category: "Training" },
      {
        id: "course_category",
        label: "Course Category",
        category: "Training",
      },
      { id: "completion_pct", label: "Completion %", category: "Training" },
      { id: "score", label: "Score", category: "Training" },
      { id: "certified", label: "Certified", category: "Training" },
      { id: "enrolled_date", label: "Enrolled Date", category: "Date" },
      { id: "completed_date", label: "Completed Date", category: "Date" },
    ],
    data: [
      {
        partner_company: "TechVision Partners",
        user_name: "Marcus Brown",
        region: "AMERICAS",
        course_name: "Product Fundamentals",
        course_category: "Product",
        completion_pct: "100%",
        score: "92/100",
        certified: "Yes",
        enrolled_date: "2025-01-05",
        completed_date: "2025-01-20",
      },
      {
        partner_company: "TechVision Partners",
        user_name: "Anna Lee",
        region: "AMERICAS",
        course_name: "Sales Enablement",
        course_category: "Sales",
        completion_pct: "75%",
        score: "-",
        certified: "No",
        enrolled_date: "2025-01-10",
        completed_date: "-",
      },
      {
        partner_company: "CloudSync Solutions",
        user_name: "Maria Garcia",
        region: "EMEAR",
        course_name: "Sales Enablement",
        course_category: "Sales",
        completion_pct: "85%",
        score: "-",
        certified: "No",
        enrolled_date: "2025-01-08",
        completed_date: "-",
      },
      {
        partner_company: "CloudSync Solutions",
        user_name: "Hans Mueller",
        region: "EMEAR",
        course_name: "Technical Deep Dive",
        course_category: "Technical",
        completion_pct: "100%",
        score: "88/100",
        certified: "Yes",
        enrolled_date: "2025-01-02",
        completed_date: "2025-01-18",
      },
      {
        partner_company: "Pacific Digital",
        user_name: "Wei Chen",
        region: "APJ",
        course_name: "Technical Deep Dive",
        course_category: "Technical",
        completion_pct: "100%",
        score: "95/100",
        certified: "Yes",
        enrolled_date: "2024-12-20",
        completed_date: "2025-01-18",
      },
      {
        partner_company: "Innovate LATAM",
        user_name: "Ana Mendez",
        region: "LATAM",
        course_name: "Product Fundamentals",
        course_category: "Product",
        completion_pct: "60%",
        score: "-",
        certified: "No",
        enrolled_date: "2025-01-15",
        completed_date: "-",
      },
      {
        partner_company: "Pacific Digital",
        user_name: "Priya Sharma",
        region: "APJ",
        course_name: "Advanced Security",
        course_category: "Technical",
        completion_pct: "100%",
        score: "91/100",
        certified: "Yes",
        enrolled_date: "2025-01-03",
        completed_date: "2025-01-25",
      },
    ],
  },
  {
    id: "rewards",
    name: "Rewards & Points",
    description: "Points earned, redeemed, balances, and reward transactions",
    icon: <ShoppingCart className="h-5 w-5 text-amber-600" />,
    fields: [
      { id: "partner_company", label: "Partner Company", category: "Partner" },
      { id: "user_name", label: "User Name", category: "Partner" },
      { id: "region", label: "Region", category: "Partner" },
      { id: "points_earned", label: "Points Earned", category: "Points" },
      {
        id: "points_redeemed",
        label: "Points Redeemed",
        category: "Points",
      },
      { id: "points_balance", label: "Points Balance", category: "Points" },
      {
        id: "redemption_type",
        label: "Last Redemption Type",
        category: "Redemption",
      },
      {
        id: "redemption_value",
        label: "Last Redemption Value",
        category: "Redemption",
      },
      {
        id: "last_activity",
        label: "Last Activity Date",
        category: "Date",
      },
    ],
    data: [
      {
        partner_company: "TechVision Partners",
        user_name: "John Smith",
        region: "AMERICAS",
        points_earned: 12500,
        points_redeemed: 5000,
        points_balance: 7500,
        redemption_type: "Gift Card",
        redemption_value: "$50",
        last_activity: "2025-01-15",
      },
      {
        partner_company: "TechVision Partners",
        user_name: "Lisa Wang",
        region: "AMERICAS",
        points_earned: 8200,
        points_redeemed: 3000,
        points_balance: 5200,
        redemption_type: "Gift Card",
        redemption_value: "$30",
        last_activity: "2025-01-08",
      },
      {
        partner_company: "CloudSync Solutions",
        user_name: "Pierre Dubois",
        region: "EMEAR",
        points_earned: 18000,
        points_redeemed: 15000,
        points_balance: 3000,
        redemption_type: "Travel Credit",
        redemption_value: "$150",
        last_activity: "2025-01-12",
      },
      {
        partner_company: "Pacific Digital",
        user_name: "Kenji Tanaka",
        region: "APJ",
        points_earned: 32000,
        points_redeemed: 25000,
        points_balance: 7000,
        redemption_type: "Electronics",
        redemption_value: "$250",
        last_activity: "2025-01-10",
      },
      {
        partner_company: "Pacific Digital",
        user_name: "Wei Chen",
        region: "APJ",
        points_earned: 22000,
        points_redeemed: 10000,
        points_balance: 12000,
        redemption_type: "Gift Card",
        redemption_value: "$100",
        last_activity: "2025-01-18",
      },
      {
        partner_company: "Innovate LATAM",
        user_name: "Carlos Silva",
        region: "LATAM",
        points_earned: 9500,
        points_redeemed: 4000,
        points_balance: 5500,
        redemption_type: "Merchandise",
        redemption_value: "$40",
        last_activity: "2025-01-20",
      },
    ],
  },
  {
    id: "partners",
    name: "Partner Companies",
    description: "Partner company details, tiers, engagement, and user counts",
    icon: <Users className="h-5 w-5 text-indigo-600" />,
    fields: [
      { id: "company_name", label: "Company Name", category: "Company" },
      { id: "region", label: "Region", category: "Company" },
      { id: "tier", label: "Partner Tier", category: "Company" },
      { id: "user_count", label: "User Count", category: "Company" },
      {
        id: "active_incentives",
        label: "Active Incentives",
        category: "Program",
      },
      { id: "total_revenue", label: "Total Revenue", category: "Financial" },
      {
        id: "claims_submitted",
        label: "Claims Submitted",
        category: "Activity",
      },
      { id: "approval_rate", label: "Approval Rate", category: "Activity" },
      {
        id: "engagement_score",
        label: "Engagement Score",
        category: "Performance",
      },
      { id: "joined_date", label: "Joined Date", category: "Date" },
    ],
    data: [
      {
        company_name: "TechVision Partners",
        region: "AMERICAS",
        tier: "Platinum",
        user_count: 12,
        active_incentives: 4,
        total_revenue: "$1.2M",
        claims_submitted: 24,
        approval_rate: "92%",
        engagement_score: "A",
        joined_date: "2023-06-15",
      },
      {
        company_name: "CloudSync Solutions",
        region: "EMEAR",
        tier: "Gold",
        user_count: 8,
        active_incentives: 3,
        total_revenue: "$890K",
        claims_submitted: 18,
        approval_rate: "83%",
        engagement_score: "B+",
        joined_date: "2023-09-01",
      },
      {
        company_name: "Pacific Digital",
        region: "APJ",
        tier: "Platinum",
        user_count: 15,
        active_incentives: 5,
        total_revenue: "$1.5M",
        claims_submitted: 32,
        approval_rate: "94%",
        engagement_score: "A+",
        joined_date: "2023-03-20",
      },
      {
        company_name: "Innovate LATAM",
        region: "LATAM",
        tier: "Silver",
        user_count: 6,
        active_incentives: 2,
        total_revenue: "$450K",
        claims_submitted: 12,
        approval_rate: "83%",
        engagement_score: "B",
        joined_date: "2024-01-10",
      },
    ],
  },
];

// ─── Initial Templates ────────────────────────────────────────────────────────

const initialTemplates: SavedTemplate[] = [
  {
    id: "tpl-001",
    name: "Monthly Sales Summary",
    datasetId: "sales",
    fieldIds: [
      "partner_company",
      "region",
      "deal_name",
      "deal_value",
      "deal_stage",
      "close_date",
      "booking_amount",
    ],
    filters: [],
    createdAt: "2025-01-15",
    lastRunAt: "2025-02-15",
  },
  {
    id: "tpl-002",
    name: "Quarterly Claims Audit",
    datasetId: "claims",
    fieldIds: [
      "claim_id",
      "partner_company",
      "submitted_by",
      "claim_type",
      "amount",
      "status",
      "ai_score",
      "submitted_date",
    ],
    filters: [
      {
        id: "f-preset-1",
        fieldId: "status",
        operator: "contains",
        value: "Approved",
      },
    ],
    createdAt: "2025-01-20",
    lastRunAt: "2025-02-01",
  },
  {
    id: "tpl-003",
    name: "Training Completion Tracker",
    datasetId: "training",
    fieldIds: [
      "partner_company",
      "user_name",
      "course_name",
      "completion_pct",
      "certified",
      "completed_date",
    ],
    filters: [],
    createdAt: "2025-02-01",
    lastRunAt: "2025-02-20",
  },
];

// ─── Template Row Component ───────────────────────────────────────────────────

function TemplateRow({
  template,
  dataset,
  onRun,
  onDelete,
}: {
  template: SavedTemplate;
  dataset: Dataset | undefined;
  onRun: (t: SavedTemplate) => void;
  onDelete: (id: string) => void;
}) {
  const filterCount = (template.filters || []).length;

  return (
    <div className="rounded-lg border border-border overflow-hidden">
      <div className="flex items-center justify-between p-4 bg-muted/30">
        <div className="flex items-center gap-4">
          <div className="p-2 rounded-lg bg-background border border-border">
            {dataset?.icon || (
              <Database className="h-5 w-5 text-muted-foreground" />
            )}
          </div>
          <div>
            <div className="font-semibold text-foreground">{template.name}</div>
            <div className="flex items-center gap-3 text-xs text-muted-foreground mt-1">
              <span>{dataset?.name || "Unknown"}</span>
              <span>&bull;</span>
              <span>{template.fieldIds.length} fields</span>
              {filterCount > 0 && (
                <>
                  <span>&bull;</span>
                  <span className="flex items-center gap-1">
                    <Filter className="h-3 w-3" />
                    {filterCount} filter{filterCount !== 1 ? "s" : ""}
                  </span>
                </>
              )}
              <span>&bull;</span>
              <span className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                Last run: {template.lastRunAt}
              </span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" className="gap-2" onClick={() => onRun(template)}>
            <Play className="h-4 w-4" />
            Run
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 text-muted-foreground hover:text-destructive"
            onClick={() => onDelete(template.id)}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Main Component ───────────────────────────────────────────────────────────

function ReportBuilderPage() {
  const [viewMode, setViewMode] = useState<ViewMode>("builder");
  const [step, setStepRaw] = useState<BuilderStep>("select-dataset");
  const [stepReverse, setStepReverse] = useState(false);

  const stepOrder: BuilderStep[] = [
    "select-dataset",
    "select-fields",
    "preview",
  ];
  const setStep = (next: BuilderStep) => {
    setStepReverse(stepOrder.indexOf(next) < stepOrder.indexOf(step));
    setStepRaw(next);
  };
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(
    null,
  );
  const [selectedFieldIds, setSelectedFieldIds] = useState<Set<string>>(
    new Set(),
  );
  const [filters, setFilters] = useState<FieldFilter[]>([]);
  const [savedTemplates, setSavedTemplates] =
    useState<SavedTemplate[]>(initialTemplates);
  const [templateName, setTemplateName] = useState("");
  const [showSaveForm, setShowSaveForm] = useState(false);
  const [templateSortBy, setTemplateSortBy] = useState<
    "name" | "lastRunAt" | "createdAt"
  >("lastRunAt");
  const [templateSortDir, setTemplateSortDir] = useState<"asc" | "desc">(
    "desc",
  );
  const [templateSearch, setTemplateSearch] = useState("");

  const selectedDataset =
    datasets.find((d) => d.id === selectedDatasetId) || null;

  const fieldsByCategory = useMemo(() => {
    if (!selectedDataset) return {};
    const grouped: Record<string, DatasetField[]> = {};
    selectedDataset.fields.forEach((f) => {
      if (!grouped[f.category]) grouped[f.category] = [];
      grouped[f.category]!.push(f);
    });
    return grouped;
  }, [selectedDataset]);

  const previewColumns = useMemo(() => {
    if (!selectedDataset) return [];
    return selectedDataset.fields.filter((f) => selectedFieldIds.has(f.id));
  }, [selectedDataset, selectedFieldIds]);

  const previewData = useMemo(() => {
    if (!selectedDataset) return [];
    const activeFilters = filters.filter((f) => f.value.trim() !== "");
    if (activeFilters.length === 0) return selectedDataset.data;
    return selectedDataset.data.filter((row) =>
      activeFilters.every((filter) => applyFilter(row, filter)),
    );
  }, [selectedDataset, filters]);

  const sortedTemplates = useMemo(() => {
    const filtered = savedTemplates.filter(
      (t) =>
        !templateSearch ||
        t.name.toLowerCase().includes(templateSearch.toLowerCase()) ||
        datasets
          .find((d) => d.id === t.datasetId)
          ?.name.toLowerCase()
          .includes(templateSearch.toLowerCase()),
    );
    filtered.sort((a, b) => {
      const valA = a[templateSortBy];
      const valB = b[templateSortBy];
      const cmp = valA < valB ? -1 : valA > valB ? 1 : 0;
      return templateSortDir === "asc" ? cmp : -cmp;
    });
    return filtered;
  }, [savedTemplates, templateSearch, templateSortBy, templateSortDir]);

  const filterableFields = useMemo(() => {
    if (!selectedDataset) return [];
    return selectedDataset.fields.filter((f) => selectedFieldIds.has(f.id));
  }, [selectedDataset, selectedFieldIds]);

  const activeFilterCount = filters.filter((f) => f.value.trim() !== "").length;

  // ─── Handlers ─────────────────────────────────────────────────────────

  const handleSelectDataset = (id: string) => {
    setSelectedDatasetId(id);
    const ds = datasets.find((d) => d.id === id);
    if (ds) setSelectedFieldIds(new Set(ds.fields.map((f) => f.id)));
    setFilters([]);
    setStep("select-fields");
  };

  const toggleField = (fieldId: string) => {
    setSelectedFieldIds((prev) => {
      const next = new Set(prev);
      if (next.has(fieldId)) {
        next.delete(fieldId);
        setFilters((f) => f.filter((fl) => fl.fieldId !== fieldId));
      } else {
        next.add(fieldId);
      }
      return next;
    });
  };

  const selectAllFields = () => {
    if (selectedDataset)
      setSelectedFieldIds(new Set(selectedDataset.fields.map((f) => f.id)));
  };

  const deselectAllFields = () => {
    setSelectedFieldIds(new Set());
    setFilters([]);
  };

  const handleReset = () => {
    setStep("select-dataset");
    setSelectedDatasetId(null);
    setSelectedFieldIds(new Set());
    setFilters([]);
    setShowSaveForm(false);
    setTemplateName("");
  };

  const addFilter = () => {
    if (filterableFields.length === 0) return;
    const firstField = filterableFields[0]!;
    const ops = getOperatorsForField(firstField.id);
    setFilters((prev) => [
      ...prev,
      {
        id: `f-${Date.now()}`,
        fieldId: firstField.id,
        operator: ops[0]!.id,
        value: "",
      },
    ]);
  };

  const updateFilter = (id: string, updates: Partial<FieldFilter>) => {
    setFilters((prev) =>
      prev.map((f) => {
        if (f.id !== id) return f;
        const updated = { ...f, ...updates };
        if (updates.fieldId) {
          const ops = getOperatorsForField(updates.fieldId);
          if (!ops.find((o) => o.id === updated.operator)) {
            updated.operator = ops[0]!.id;
          }
        }
        return updated;
      }),
    );
  };

  const removeFilter = (id: string) => {
    setFilters((prev) => prev.filter((f) => f.id !== id));
  };

  const handleSaveTemplate = () => {
    if (!templateName.trim() || !selectedDatasetId) return;
    const newTemplate: SavedTemplate = {
      id: `tpl-${Date.now()}`,
      name: templateName.trim(),
      datasetId: selectedDatasetId,
      fieldIds: Array.from(selectedFieldIds),
      filters: filters.filter((f) => f.value.trim() !== ""),
      createdAt: new Date().toISOString().split("T")[0]!,
      lastRunAt: new Date().toISOString().split("T")[0]!,
    };
    setSavedTemplates((prev) => [newTemplate, ...prev]);
    setShowSaveForm(false);
    setTemplateName("");
    toast.success("Report template saved!", {
      description: `"${newTemplate.name}" is now available in your saved templates.`,
    });
  };

  const handleRunTemplate = (template: SavedTemplate) => {
    setSelectedDatasetId(template.datasetId);
    setSelectedFieldIds(new Set(template.fieldIds));
    setFilters(template.filters || []);
    setSavedTemplates((prev) =>
      prev.map((t) =>
        t.id === template.id
          ? { ...t, lastRunAt: new Date().toISOString().split("T")[0]! }
          : t,
      ),
    );
    setStep("preview");
    setViewMode("builder");
  };

  const handleDeleteTemplate = (id: string) => {
    setSavedTemplates((prev) => prev.filter((t) => t.id !== id));
    toast.success("Template deleted");
  };

  const exportToExcel = () => {
    if (!selectedDataset || previewColumns.length === 0) return;
    const headers = previewColumns.map((f) => f.label);
    const rows = previewData.map((row) =>
      previewColumns.map((f) => String(row[f.id] ?? "")),
    );
    const csvContent = [
      headers.join(","),
      ...rows.map((row) => row.map((cell) => `"${cell}"`).join(",")),
    ].join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `custom-report-${selectedDataset.name.toLowerCase().replace(/\s+/g, "-")}.csv`;
    link.click();
  };

  // ─── Render ───────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      <PageBanner
        theme="reports"
        title="Report Builder"
        subtitle="Build custom reports from your program data"
      />

      <Tabs
        defaultValue="builder"
        value={viewMode}
        className="space-y-4"
        onValueChange={(v) => setViewMode(v as ViewMode)}
      >
        <TabsList>
          <TabsTrigger value="builder" className="gap-2">
            <Wrench className="h-4 w-4" />
            Builder
          </TabsTrigger>
          <TabsTrigger
            value="templates"
            className="gap-2"
            data-tour="tab-templates"
          >
            <FolderOpen className="h-4 w-4" />
            Templates
          </TabsTrigger>
        </TabsList>

        <TabsContent value="builder" data-tour="report-builder-content">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <CardTitle className="text-foreground flex items-center gap-2">
                    <Wrench className="h-5 w-5 text-muted-foreground" />
                    Custom Report Builder
                  </CardTitle>
                  <CardDescription>
                    Choose a Dataset, Select Fields, Set Filters, Then Preview
                    and Export
                  </CardDescription>
                </div>
                {step !== "select-dataset" && (
                  <Button
                    variant="ghost"
                    onClick={handleReset}
                    className="gap-2"
                  >
                    <ChevronLeft className="h-4 w-4" />
                    Start Over
                  </Button>
                )}
              </div>
            </CardHeader>
            <CardContent>
              {/* Step indicator */}
              <div className="flex items-center gap-2 mb-6">
                {[
                  { key: "select-dataset", label: "Choose Dataset" },
                  { key: "select-fields", label: "Fields & Filters" },
                  { key: "preview", label: "Preview & Export" },
                ].map((s, i) => (
                  <div key={s.key} className="flex items-center gap-2">
                    <div
                      className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                        step === s.key
                          ? "bg-primary text-primary-foreground"
                          : (step === "preview" && i < 2) ||
                              (step === "select-fields" && i === 0)
                            ? "bg-primary/10 text-primary"
                            : "bg-muted text-muted-foreground"
                      }`}
                    >
                      <span className="w-4 h-4 rounded-full bg-current/20 flex items-center justify-center text-[10px]">
                        {i + 1}
                      </span>
                      {s.label}
                    </div>
                    {i < 2 && (
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    )}
                  </div>
                ))}
              </div>

              <FlipTransition transitionKey={step} reverse={stepReverse}>
                {/* Step 1: Select Dataset */}
                {step === "select-dataset" && (
                  <div
                    className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
                    data-tour="report-dataset-picker"
                  >
                    {datasets.map((ds) => (
                      <button
                        key={ds.id}
                        onClick={() => handleSelectDataset(ds.id)}
                        className="flex flex-col items-start gap-3 p-5 rounded-lg border border-border bg-muted/30 hover:bg-muted/50 hover:border-primary/30 transition-colors text-left group"
                      >
                        <div className="p-2.5 rounded-lg bg-background border border-border group-hover:border-primary/20 transition-colors">
                          {ds.icon}
                        </div>
                        <div>
                          <div className="font-semibold text-foreground">
                            {ds.name}
                          </div>
                          <div className="text-sm text-muted-foreground mt-1">
                            {ds.description}
                          </div>
                        </div>
                        <Badge variant="secondary" className="text-xs">
                          {ds.fields.length} fields available
                        </Badge>
                      </button>
                    ))}
                  </div>
                )}

                {/* Step 2: Select Fields + Filters */}
                {step === "select-fields" && selectedDataset && (
                  <div className="space-y-6">
                    {/* Field Selection */}
                    <div className="space-y-4">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                          <div className="p-2 rounded-lg bg-muted">
                            {selectedDataset.icon}
                          </div>
                          <div>
                            <div className="font-semibold text-foreground">
                              {selectedDataset.name}
                            </div>
                            <div className="text-sm text-muted-foreground">
                              {selectedFieldIds.size} of{" "}
                              {selectedDataset.fields.length} fields selected
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={selectAllFields}
                          >
                            Select All
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={deselectAllFields}
                          >
                            Unselect All
                          </Button>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {Object.entries(fieldsByCategory).map(
                          ([category, fields]) => (
                            <div
                              key={category}
                              className="rounded-lg border border-border p-4 space-y-3"
                            >
                              <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                                {category}
                              </div>
                              {fields.map((field) => (
                                <label
                                  key={field.id}
                                  className="flex items-center gap-3 cursor-pointer group"
                                >
                                  <Checkbox
                                    checked={selectedFieldIds.has(field.id)}
                                    onCheckedChange={() =>
                                      toggleField(field.id)
                                    }
                                  />
                                  <span className="text-sm text-foreground group-hover:text-primary transition-colors">
                                    {field.label}
                                  </span>
                                </label>
                              ))}
                            </div>
                          ),
                        )}
                      </div>
                    </div>

                    {/* Filters Section */}
                    <div className="rounded-lg border border-border p-4 space-y-3">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                          <Filter className="h-4 w-4 text-muted-foreground" />
                          Data Filters
                          {activeFilterCount > 0 && (
                            <Badge variant="secondary" className="text-xs ml-1">
                              {activeFilterCount} active
                            </Badge>
                          )}
                        </div>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={addFilter}
                          disabled={filterableFields.length === 0}
                          className="gap-1.5 text-xs h-7"
                        >
                          <Plus className="h-3.5 w-3.5" />
                          Add Filter
                        </Button>
                      </div>

                      {filters.length === 0 ? (
                        <div className="text-xs text-muted-foreground py-1">
                          No filters applied. Click &quot;Add Filter&quot; to
                          narrow your report results.
                        </div>
                      ) : (
                        <div className="space-y-2">
                          {filters.map((filter, idx) => {
                            const ops = getOperatorsForField(filter.fieldId);
                            const fieldType = getFieldType(filter.fieldId);
                            return (
                              <div
                                key={filter.id}
                                className="flex items-center gap-2 bg-muted/30 rounded-md px-3 py-2"
                              >
                                <span className="text-xs font-medium text-muted-foreground shrink-0 w-8">
                                  {idx === 0 ? "Where" : "AND"}
                                </span>
                                <Select
                                  value={filter.fieldId}
                                  onValueChange={(val) =>
                                    updateFilter(filter.id, { fieldId: val })
                                  }
                                >
                                  <SelectTrigger className="h-7 text-xs w-[160px] shrink-0">
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {filterableFields.map((f) => (
                                      <SelectItem
                                        key={f.id}
                                        value={f.id}
                                        className="text-xs"
                                      >
                                        {f.label}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                                <Select
                                  value={filter.operator}
                                  onValueChange={(val) =>
                                    updateFilter(filter.id, { operator: val })
                                  }
                                >
                                  <SelectTrigger className="h-7 text-xs w-[130px] shrink-0">
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {ops.map((op) => (
                                      <SelectItem
                                        key={op.id}
                                        value={op.id}
                                        className="text-xs"
                                      >
                                        {op.label}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                                {fieldType === "date" ? (
                                  <div className="flex items-center gap-1.5 flex-1 min-w-[100px]">
                                    <Input
                                      type="date"
                                      value={filter.value}
                                      onChange={(e) =>
                                        updateFilter(filter.id, {
                                          value: e.target.value,
                                        })
                                      }
                                      className="h-7 text-xs flex-1"
                                    />
                                    {filter.operator === "between" && (
                                      <>
                                        <span className="text-xs text-muted-foreground shrink-0">
                                          and
                                        </span>
                                        <Input
                                          type="date"
                                          value={filter.valueTo || ""}
                                          onChange={(e) =>
                                            updateFilter(filter.id, {
                                              valueTo: e.target.value,
                                            })
                                          }
                                          className="h-7 text-xs flex-1"
                                        />
                                      </>
                                    )}
                                  </div>
                                ) : (
                                  <Input
                                    placeholder={
                                      fieldType === "numeric"
                                        ? "Amount..."
                                        : "Value..."
                                    }
                                    type={
                                      fieldType === "numeric"
                                        ? "number"
                                        : "text"
                                    }
                                    value={filter.value}
                                    onChange={(e) =>
                                      updateFilter(filter.id, {
                                        value: e.target.value,
                                      })
                                    }
                                    className="h-7 text-xs flex-1 min-w-[100px]"
                                  />
                                )}
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
                                  onClick={() => removeFilter(filter.id)}
                                >
                                  <X className="h-3.5 w-3.5" />
                                </Button>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    <div className="flex justify-end pt-2">
                      <Button
                        onClick={() => setStep("preview")}
                        disabled={selectedFieldIds.size === 0}
                        className="gap-2"
                      >
                        <Eye className="h-4 w-4" />
                        Preview Report
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                )}

                {/* Step 3: Preview & Export */}
                {step === "preview" && selectedDataset && (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between p-4 rounded-lg border border-border bg-primary/5">
                      <div className="flex items-center gap-4">
                        <div className="p-3 rounded-lg bg-background border border-border">
                          {selectedDataset.icon}
                        </div>
                        <div>
                          <div className="font-semibold text-lg text-foreground">
                            Custom {selectedDataset.name} Report
                          </div>
                          <div className="text-sm text-muted-foreground flex items-center gap-2 flex-wrap">
                            <span>
                              {previewColumns.length} fields &bull;{" "}
                              {previewData.length} rows
                            </span>
                            {activeFilterCount > 0 && (
                              <>
                                <span className="text-muted-foreground/50">
                                  |
                                </span>
                                <span className="flex items-center gap-1">
                                  <Filter className="h-3.5 w-3.5" />
                                  {activeFilterCount} filter
                                  {activeFilterCount !== 1 ? "s" : ""}
                                </span>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        {!showSaveForm && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setShowSaveForm(true)}
                            className="gap-2"
                          >
                            <Save className="h-4 w-4" />
                            Save as Template
                          </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setStep("select-fields")}
                          className="gap-2"
                        >
                          <ChevronLeft className="h-4 w-4" />
                          Edit
                        </Button>
                        <FeatureGate feature="export_reports">
                          <Button
                            size="sm"
                            onClick={exportToExcel}
                            className="gap-2"
                          >
                            <Download className="h-4 w-4" />
                            Export to Excel
                          </Button>
                        </FeatureGate>
                      </div>
                    </div>

                    {/* Active Filters Summary */}
                    {activeFilterCount > 0 && (
                      <div className="flex items-center gap-2 flex-wrap px-1">
                        <span className="text-xs font-medium text-muted-foreground">
                          Filters:
                        </span>
                        {filters
                          .filter((f) => f.value.trim() !== "")
                          .map((filter) => {
                            const field = selectedDataset.fields.find(
                              (f) => f.id === filter.fieldId,
                            );
                            const ops = getOperatorsForField(filter.fieldId);
                            const opLabel =
                              ops.find((o) => o.id === filter.operator)
                                ?.label || filter.operator;
                            return (
                              <Badge
                                key={filter.id}
                                variant="outline"
                                className="text-xs gap-1 font-normal"
                              >
                                {field?.label} {opLabel} &quot;{filter.value}
                                &quot;
                                {filter.operator === "between" && filter.valueTo
                                  ? ` and "${filter.valueTo}"`
                                  : ""}
                              </Badge>
                            );
                          })}
                      </div>
                    )}

                    {/* Save Template Form */}
                    {showSaveForm && (
                      <div className="flex items-center gap-3 p-3 rounded-lg border border-primary/20 bg-primary/5">
                        <Save className="h-4 w-4 text-primary shrink-0" />
                        <Input
                          placeholder="Enter template name..."
                          value={templateName}
                          onChange={(e) => setTemplateName(e.target.value)}
                          className="h-8 text-sm max-w-xs"
                          onKeyDown={(e) =>
                            e.key === "Enter" && handleSaveTemplate()
                          }
                        />
                        <Button
                          size="sm"
                          onClick={handleSaveTemplate}
                          disabled={!templateName.trim()}
                          className="h-8"
                        >
                          Save
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setShowSaveForm(false);
                            setTemplateName("");
                          }}
                          className="h-8"
                        >
                          Cancel
                        </Button>
                      </div>
                    )}

                    <div className="rounded-lg border border-border overflow-hidden">
                      <Table>
                        <TableHeader>
                          <TableRow className="bg-muted/50">
                            {previewColumns.map((col) => (
                              <TableHead key={col.id} className="font-medium">
                                {col.label}
                              </TableHead>
                            ))}
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {previewData.length === 0 ? (
                            <TableRow>
                              <TableCell
                                colSpan={previewColumns.length}
                                className="text-center py-8 text-muted-foreground"
                              >
                                No rows match the current filters.
                              </TableCell>
                            </TableRow>
                          ) : (
                            previewData.map((row, idx) => (
                              <TableRow key={idx}>
                                {previewColumns.map((col) => (
                                  <TableCell
                                    key={col.id}
                                    className="text-foreground"
                                  >
                                    {row[col.id]}
                                  </TableCell>
                                ))}
                              </TableRow>
                            ))
                          )}
                        </TableBody>
                      </Table>
                    </div>

                    <div className="text-sm text-muted-foreground text-center py-2">
                      Showing preview of {previewData.length} rows &bull; Export
                      for full report
                    </div>
                  </div>
                )}
              </FlipTransition>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="templates" data-tour="report-templates">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <CardTitle className="text-foreground flex items-center gap-2">
                    <FolderOpen className="h-5 w-5 text-muted-foreground" />
                    Saved Report Templates
                  </CardTitle>
                  <CardDescription>
                    Re-Run Saved Reports for Different Time Periods or Export
                    Them Directly
                  </CardDescription>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setViewMode("builder");
                    handleReset();
                  }}
                  className="gap-2"
                >
                  <Plus className="h-4 w-4" />
                  New Report
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {savedTemplates.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <FolderOpen className="h-12 w-12 text-muted-foreground/30 mb-4" />
                  <div className="font-medium text-foreground">
                    No Saved Templates Yet
                  </div>
                  <div className="text-sm text-muted-foreground mt-1">
                    Build a custom report and save it as a template to see it
                    here.
                  </div>
                  <Button
                    variant="outline"
                    className="mt-4 gap-2"
                    onClick={() => {
                      setViewMode("builder");
                      handleReset();
                    }}
                  >
                    <Plus className="h-4 w-4" />
                    Create Your First Report
                  </Button>
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Search and sort */}
                  <div className="flex items-center gap-3">
                    <div className="relative flex-1 max-w-xs">
                      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input
                        placeholder="Search templates..."
                        value={templateSearch}
                        onChange={(e) => setTemplateSearch(e.target.value)}
                        className="pl-9 h-9"
                      />
                    </div>
                    <Select
                      value={`${templateSortBy}-${templateSortDir}`}
                      onValueChange={(val) => {
                        const [field, dir] = val.split("-") as [
                          typeof templateSortBy,
                          typeof templateSortDir,
                        ];
                        setTemplateSortBy(field);
                        setTemplateSortDir(dir);
                      }}
                    >
                      <SelectTrigger className="w-[200px] h-9">
                        <ArrowUpDown className="h-3.5 w-3.5 mr-2 text-muted-foreground" />
                        <SelectValue placeholder="Sort by..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="lastRunAt-desc">
                          Last Run (Newest)
                        </SelectItem>
                        <SelectItem value="lastRunAt-asc">
                          Last Run (Oldest)
                        </SelectItem>
                        <SelectItem value="createdAt-desc">
                          Created (Newest)
                        </SelectItem>
                        <SelectItem value="createdAt-asc">
                          Created (Oldest)
                        </SelectItem>
                        <SelectItem value="name-asc">Name (A-Z)</SelectItem>
                        <SelectItem value="name-desc">Name (Z-A)</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {/* Template list */}
                  <div className="space-y-3">
                    {sortedTemplates.map((template) => {
                      const ds = datasets.find(
                        (d) => d.id === template.datasetId,
                      );
                      return (
                        <TemplateRow
                          key={template.id}
                          template={template}
                          dataset={ds}
                          onRun={handleRunTemplate}
                          onDelete={handleDeleteTemplate}
                        />
                      );
                    })}
                  </div>

                  {sortedTemplates.length === 0 && templateSearch && (
                    <div className="text-center py-8 text-sm text-muted-foreground">
                      No templates match &quot;{templateSearch}&quot;
                    </div>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

export default ReportBuilderPage;
