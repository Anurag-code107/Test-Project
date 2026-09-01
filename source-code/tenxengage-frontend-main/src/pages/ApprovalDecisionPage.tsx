import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import axios from "axios";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  Calendar,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Loader2,
  FileText,
  Download,
  Eye,
  ShieldCheck,
  DollarSign,
  FileSpreadsheet,
  File,
  Megaphone,
  GraduationCap,
  FileCheck,
  Layers,
} from "lucide-react";
import { cn } from "@/lib/utils";
import webLogo from "@/assets/web_logo.png";
import {
  getIncentiveForApproval,
  submitApprovalDecision,
  downloadDocument,
  viewDocument,
} from "@/services/incentive.service";
import type { ApprovalReviewResponse } from "@/services/incentive.service";
import {
  formatDate,
  formatBudgetAmount,
} from "@/components/view-incentives/incentive-detail-shared";
import {
  getCurrency,
  monetaryCurrencyIds,
  nonMonetaryCurrencyIds,
} from "@/config/currencies";
import type {
  IncentiveDetailResponse,
  IncentiveType,
  DocumentSummary,
} from "@/types/incentive.types";

type PageState = "loading" | "expired" | "review" | "submitting" | "result";

interface DecisionResult {
  success: boolean;
  message: string;
  action: string;
}

// --- Icon/color maps ---

const engagementIcons: Record<IncentiveType, React.ReactNode> = {
  SALES: <Megaphone className="h-4 w-4" />,
  TRAINING: <GraduationCap className="h-4 w-4" />,
  ACTIVITY: <FileCheck className="h-4 w-4" />,
  JOURNEY: <Layers className="h-4 w-4" />,
};

const engagementColors: Record<IncentiveType, string> = {
  SALES: "text-primary",
  TRAINING: "text-warning",
  ACTIVITY: "text-blue-500",
  JOURNEY: "text-indigo-500",
};

const typeLabels: Record<IncentiveType, string> = {
  SALES: "Sales Incentive",
  TRAINING: "Training Incentive",
  ACTIVITY: "Activity Incentive",
  JOURNEY: "Journey Incentive",
};

const fileTypeIcons: Record<string, React.ReactNode> = {
  pdf: <FileText className="h-4 w-4 text-destructive" />,
  xlsx: <FileSpreadsheet className="h-4 w-4 text-success" />,
  docx: <File className="h-4 w-4 text-blue-500" />,
};

const documentTypeLabels: Record<string, string> = {
  "eligible-products": "Eligible Products",
  "terms-conditions": "Terms & Conditions",
  "program-rules": "Program Rules",
  faq: "FAQ",
};

// --- Sub-components ---

function BudgetPanel({ incentive }: { incentive: IncentiveDetailResponse }) {
  const budgetTotal = incentive.budget?.totalBudget ?? incentive.budgetTotal;
  if (!budgetTotal) return null;

  const totalNum = parseFloat(budgetTotal);
  const utilPercent = incentive.budgetUtilizationPercent ?? 0;
  const utilizedNum = totalNum * (utilPercent / 100);
  const budgetCurrency = incentive.budget?.currency;
  const amounts = incentive.rewardAmounts ?? {};

  const monetaryIds = [...monetaryCurrencyIds];
  const nonMonetaryIds = [...nonMonetaryCurrencyIds];

  const getCurrencyAmount = (currencyId: string): string => {
    if (currencyId === budgetCurrency && incentive.budget) {
      return formatBudgetAmount(totalNum);
    }
    const raw = amounts[currencyId];
    const baseNum = raw ? parseFloat(raw) : 0;
    return getCurrency(currencyId).format(isNaN(baseNum) ? 0 : baseNum);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          <DollarSign className="h-3.5 w-3.5" />
          Budget
        </div>
        <span className="text-sm font-semibold text-foreground">
          {formatBudgetAmount(totalNum)}
        </span>
      </div>

      <div className="w-full bg-muted rounded-full h-2">
        <div
          className="bg-primary rounded-full h-2 transition-[width]"
          style={{ width: `${utilPercent}%` }}
        />
      </div>
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{formatBudgetAmount(utilizedNum)} utilized</span>
        <span>{utilPercent}%</span>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {monetaryIds.length > 0 && (
          <div className="bg-muted/40 rounded-lg p-2.5 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <DollarSign className="h-3 w-3 text-emerald-500" />
              Monetary
            </div>
            <div className="space-y-1 pl-4">
              {monetaryIds.map((id) => {
                const cfg = getCurrency(id);
                const Icon = cfg.icon;
                return (
                  <div
                    key={id}
                    className="flex items-center justify-between text-xs"
                  >
                    <span className="flex items-center gap-1 text-muted-foreground">
                      <Icon className={`h-3 w-3 ${cfg.iconClass}`} />
                      {cfg.label}
                    </span>
                    <span className="font-medium">{getCurrencyAmount(id)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
        {nonMonetaryIds.length > 0 && (
          <div className="bg-muted/40 rounded-lg p-2.5 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              {(() => {
                const Icon = getCurrency("credits").icon;
                return <Icon className="h-3 w-3 text-violet-500" />;
              })()}
              Non-Monetary
            </div>
            <div className="space-y-1 pl-4">
              {nonMonetaryIds.map((id) => {
                const cfg = getCurrency(id);
                const Icon = cfg.icon;
                return (
                  <div
                    key={id}
                    className="flex items-center justify-between text-xs"
                  >
                    <span className="flex items-center gap-1 text-muted-foreground">
                      <Icon className={`h-3 w-3 ${cfg.iconClass}`} />
                      {cfg.label}
                    </span>
                    <span className="font-medium">{getCurrencyAmount(id)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function DocumentsPanel({
  documents,
  incentiveId,
}: {
  documents: DocumentSummary[];
  incentiveId: string;
}) {
  const [loadingDocId, setLoadingDocId] = useState<string | null>(null);

  const handleView = async (doc: DocumentSummary) => {
    if (!doc.downloadUrl || loadingDocId) return;
    setLoadingDocId(doc.id);
    try {
      await viewDocument(incentiveId, doc.id);
    } finally {
      setLoadingDocId(null);
    }
  };

  const handleDownload = async (doc: DocumentSummary) => {
    if (!doc.downloadUrl || loadingDocId) return;
    setLoadingDocId(doc.id);
    try {
      await downloadDocument(incentiveId, doc.id, doc.name);
    } finally {
      setLoadingDocId(null);
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground uppercase tracking-wide">
        <FileText className="h-3.5 w-3.5" />
        Documents ({documents.length})
      </div>
      <div className="space-y-1.5">
        {documents.map((doc) => (
          <div
            key={doc.id}
            className="flex items-center gap-2.5 p-2 rounded-lg border bg-card hover:bg-muted/50 transition-colors"
          >
            <div className="shrink-0">
              {fileTypeIcons[doc.fileType] ?? (
                <FileText className="h-4 w-4 text-muted-foreground" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium truncate">{doc.name}</p>
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span>
                  {documentTypeLabels[doc.documentType] ?? doc.documentType}
                </span>
                <span>&middot;</span>
                <span>{doc.size}</span>
              </div>
            </div>
            <div className="flex items-center gap-0.5 shrink-0">
              {loadingDocId === doc.id ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground mx-1" />
              ) : (
                <>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => handleView(doc)}
                    disabled={!doc.downloadUrl || !!loadingDocId}
                    title="View"
                  >
                    <Eye className="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    onClick={() => handleDownload(doc)}
                    disabled={!doc.downloadUrl || !!loadingDocId}
                    title="Download"
                  >
                    <Download className="h-3.5 w-3.5" />
                  </Button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// --- Page States ---

function ExpiredState() {
  return (
    <div className="min-h-screen w-full flex-1 bg-background flex items-center justify-center px-4">
      <div className="flex flex-col items-center space-y-6 w-full max-w-md">
        <img src={webLogo} alt="TenXEngage" className="h-16 object-contain" />
        <Card className="w-full">
          <CardContent className="p-8 text-center space-y-4">
            <div className="h-14 w-14 rounded-full bg-warning/10 flex items-center justify-center mx-auto">
              <AlertTriangle className="h-7 w-7 text-warning" />
            </div>
            <h1 className="text-lg font-semibold text-foreground">
              Link Expired
            </h1>
            <p className="text-muted-foreground text-sm leading-relaxed">
              This approval link has already been used or is no longer valid.
              Please contact a program administrator for more information.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function SubmittedState({ decision }: { decision: "approved" | "denied" }) {
  return (
    <div className="min-h-screen w-full flex-1 bg-background flex items-center justify-center px-4">
      <div className="flex flex-col items-center space-y-6 w-full max-w-md">
        <img src={webLogo} alt="TenXEngage" className="h-16 object-contain" />
        <Card className="w-full">
          <CardContent className="p-8 text-center space-y-4">
            <div
              className={cn(
                "h-14 w-14 rounded-full flex items-center justify-center mx-auto",
                decision === "approved" ? "bg-success/10" : "bg-destructive/10",
              )}
            >
              {decision === "approved" ? (
                <CheckCircle className="h-7 w-7 text-success" />
              ) : (
                <XCircle className="h-7 w-7 text-destructive" />
              )}
            </div>
            <h1 className="text-lg font-semibold text-foreground">
              {decision === "approved"
                ? "Approval Submitted"
                : "Denial Submitted"}
            </h1>
            <p className="text-muted-foreground text-sm leading-relaxed">
              You have successfully{" "}
              {decision === "approved" ? "approved" : "denied"} this incentive.
              You may now close this browser tab.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="min-h-screen w-full flex-1 bg-background flex items-center justify-center px-4">
      <div className="flex flex-col items-center space-y-6 w-full max-w-md">
        <img src={webLogo} alt="TenXEngage" className="h-16 object-contain" />
        <Card className="w-full">
          <CardContent className="p-8 text-center space-y-4">
            <div className="h-14 w-14 rounded-full bg-warning/10 flex items-center justify-center mx-auto">
              <AlertTriangle className="h-7 w-7 text-warning" />
            </div>
            <h1 className="text-lg font-semibold text-foreground">Error</h1>
            <p className="text-muted-foreground text-sm leading-relaxed">
              {message}
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

// --- Main Component ---

function ApprovalDecisionPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [state, setState] = useState<PageState>("loading");
  const [reviewData, setReviewData] = useState<ApprovalReviewResponse | null>(
    null,
  );
  const [comment, setComment] = useState("");
  const [commentError, setCommentError] = useState("");
  const [result, setResult] = useState<DecisionResult | null>(null);

  useEffect(() => {
    if (!token) {
      setState("expired");
      return;
    }

    getIncentiveForApproval(token)
      .then((data) => {
        setReviewData(data);
        setState("review");
      })
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 409) {
          setState("expired");
        } else if (axios.isAxiosError(err) && err.response?.status === 400) {
          setState("expired");
        } else {
          setState("expired");
        }
      });
  }, [token]);

  const handleDecision = async (action: "APPROVED" | "REJECTED") => {
    if (action === "REJECTED" && !comment.trim()) {
      setCommentError("A comment is required when denying an incentive.");
      return;
    }
    setCommentError("");
    setState("submitting");
    try {
      const res = await submitApprovalDecision(
        token!,
        action,
        comment.trim() || undefined,
      );
      setResult(res);
    } catch (err: unknown) {
      const message =
        axios.isAxiosError(err) && err.response?.data?.message
          ? err.response.data.message
          : err instanceof Error
            ? err.message
            : "Something went wrong. Please try again.";
      setResult({ success: false, message, action: "" });
    }
    setState("result");
  };

  // --- Loading ---
  if (state === "loading") {
    return (
      <div className="min-h-screen w-full flex-1 bg-background flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            Loading incentive details...
          </p>
        </div>
      </div>
    );
  }

  // --- Expired / Used / Invalid ---
  if (state === "expired") {
    return <ExpiredState />;
  }

  // --- Result (after decision) ---
  if (state === "result" && result) {
    if (!result.success) {
      return <ErrorState message={result.message} />;
    }
    return (
      <SubmittedState
        decision={result.action === "approved" ? "approved" : "denied"}
      />
    );
  }

  // --- Review state ---
  if (!reviewData) return null;

  const incentive = reviewData.incentive;
  const documents = incentive.documents ?? [];

  return (
    <div className="min-h-screen w-full flex-1 bg-background flex items-center justify-center px-4 py-6">
      <div className="flex flex-col items-center w-full max-w-4xl space-y-4 mx-auto">
        {/* Logo */}
        <img src={webLogo} alt="TenXEngage" className="h-16 object-contain" />

        {/* Approver Info Banner */}
        <div className="flex items-center gap-2 bg-primary/5 rounded-lg px-4 py-2.5 border border-primary/20 w-full">
          <ShieldCheck className="h-4 w-4 text-primary shrink-0" />
          <p className="text-sm text-foreground">
            Reviewing as:{" "}
            <span className="font-medium text-primary">
              {reviewData.approverCategory}
            </span>
            <span className="text-muted-foreground ml-1.5 text-xs">
              ({reviewData.approverEmail})
            </span>
          </p>
        </div>

        {/* Incentive Detail Card */}
        <Card className="w-full">
          <CardContent className="p-5 space-y-4">
            {/* Header */}
            <div className="flex items-center gap-2">
              <span
                className={cn(
                  "shrink-0",
                  engagementColors[incentive.incentiveType],
                )}
              >
                {engagementIcons[incentive.incentiveType]}
              </span>
              <h2 className="text-base font-semibold text-foreground flex-1">
                {incentive.name}
              </h2>
              <Badge variant="outline" className="text-xs shrink-0">
                {typeLabels[incentive.incentiveType]}
              </Badge>
            </div>

            {/* Description */}
            {incentive.description && (
              <p className="text-sm text-muted-foreground leading-relaxed">
                {incentive.description}
              </p>
            )}

            {/* Duration */}
            {incentive.startDate && incentive.endDate && (
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Calendar className="h-3.5 w-3.5" />
                <span className="font-medium">
                  {formatDate(incentive.startDate)} &ndash;{" "}
                  {formatDate(incentive.endDate)}
                </span>
              </div>
            )}

            {/* Two columns: Budget | Documents */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="rounded-lg border p-4">
                <BudgetPanel incentive={incentive} />
              </div>
              {documents.length > 0 && (
                <div className="rounded-lg border p-4">
                  <DocumentsPanel
                    documents={documents}
                    incentiveId={incentive.id}
                  />
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Decision Section */}
        <Card className="w-full">
          <CardContent className="p-5 space-y-3">
            <h3 className="text-sm font-semibold text-foreground">
              Your Decision
            </h3>

            <div className="space-y-1.5">
              <Label htmlFor="approval-comment" className="text-xs">
                Comment{" "}
                <span className="text-muted-foreground">
                  (required if denying)
                </span>
              </Label>
              <Textarea
                id="approval-comment"
                placeholder="Add a comment about your decision..."
                value={comment}
                onChange={(e) => {
                  setComment(e.target.value);
                  if (commentError && e.target.value.trim())
                    setCommentError("");
                }}
                rows={2}
                className="resize-none"
              />
              {commentError && (
                <p className="text-xs text-destructive">{commentError}</p>
              )}
            </div>

            <div className="flex gap-3">
              <Button
                className="flex-1 bg-success hover:bg-success/90 text-success-foreground"
                onClick={() => handleDecision("APPROVED")}
                disabled={state === "submitting"}
              >
                {state === "submitting" ? (
                  <Loader2 className="h-4 w-4 animate-spin mr-2" />
                ) : (
                  <CheckCircle className="h-4 w-4 mr-2" />
                )}
                Approve
              </Button>
              <Button
                variant="destructive"
                className="flex-1"
                onClick={() => handleDecision("REJECTED")}
                disabled={state === "submitting"}
              >
                {state === "submitting" ? (
                  <Loader2 className="h-4 w-4 animate-spin mr-2" />
                ) : (
                  <XCircle className="h-4 w-4 mr-2" />
                )}
                Deny
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default ApprovalDecisionPage;
