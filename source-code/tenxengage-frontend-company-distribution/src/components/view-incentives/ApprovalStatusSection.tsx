import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Mail,
  CheckCircle,
  XCircle,
  Clock,
  MessageSquare,
  ShieldCheck,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  useResendApprovalEmails,
  useResendApprovalToApprover,
} from "@/hooks/useIncentiveApi";
import { toast } from "sonner";
import type { ApprovalStatusResponse } from "@/types/incentive.types";

interface ApprovalStatusSectionProps {
  incentiveId: string;
  approvalStatus: ApprovalStatusResponse;
}

const categoryColors: Record<string, string> = {
  "Budget Approver": "bg-emerald-500/10 text-emerald-600 border-emerald-500/30",
  "Terms & Conditions Approver":
    "bg-blue-500/10 text-blue-600 border-blue-500/30",
  "Compliance Approver":
    "bg-purple-500/10 text-purple-600 border-purple-500/30",
  "Legal Approver": "bg-amber-500/10 text-amber-600 border-amber-500/30",
  "Overall Incentive Approver": "bg-primary/10 text-primary border-primary/30",
  "Finance Approver": "bg-rose-500/10 text-rose-600 border-rose-500/30",
  "Marketing Approver": "bg-cyan-500/10 text-cyan-600 border-cyan-500/30",
};

const statusIcons: Record<string, React.ReactNode> = {
  pending: <Clock className="h-4 w-4 text-warning" />,
  APPROVED: <CheckCircle className="h-4 w-4 text-success" />,
  REJECTED: <XCircle className="h-4 w-4 text-destructive" />,
};

const statusStyles: Record<string, string> = {
  pending: "bg-warning/10 text-warning border-warning/30",
  APPROVED: "bg-success/10 text-success border-success/30",
  REJECTED: "bg-destructive/10 text-destructive border-destructive/30",
};

const statusLabels: Record<string, string> = {
  pending: "Awaiting Response",
  APPROVED: "Approved",
  REJECTED: "Denied",
};

function formatDateTime(dateString: string) {
  return new Date(dateString).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function ApprovalStatusSection({
  incentiveId,
  approvalStatus,
}: ApprovalStatusSectionProps) {
  const resendAll = useResendApprovalEmails();
  const resendSingle = useResendApprovalToApprover();

  if (approvalStatus.approvers.length === 0) return null;

  const handleResendAll = () => {
    resendAll.mutate(incentiveId, {
      onSuccess: () => {
        toast.success("Emails Sent", {
          description: `Approval reminders sent to ${approvalStatus.pendingCount} pending approver(s)`,
        });
      },
      onError: (err) => {
        toast.error("Failed to Send", {
          description:
            err instanceof Error ? err.message : "Failed to resend emails",
        });
      },
    });
  };

  const handleResend = (email: string) => {
    resendSingle.mutate(
      { id: incentiveId, email },
      {
        onSuccess: () => {
          toast.success("Email Sent", {
            description: `Approval reminder sent to ${email}`,
          });
        },
        onError: (err) => {
          toast.error("Failed to Send", {
            description:
              err instanceof Error ? err.message : "Failed to resend email",
          });
        },
      },
    );
  };

  return (
    <div className="rounded-xl border border-warning/30 bg-warning/5 p-4 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldCheck className="h-4 w-4 text-warning" />
          <h3 className="text-sm font-semibold text-foreground">
            Approval Status
          </h3>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="text-xs">
            {approvalStatus.approvedCount}/{approvalStatus.requiredApprovals}{" "}
            required
          </Badge>
          {approvalStatus.pendingCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              className="h-7 text-xs"
              disabled={resendAll.isPending}
              onClick={handleResendAll}
            >
              {resendAll.isPending ? (
                <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" />
              ) : (
                <Mail className="h-3.5 w-3.5 mr-1" />
              )}
              Resend All
            </Button>
          )}
        </div>
      </div>

      <div className="space-y-2">
        {approvalStatus.approvers.map((approver) => {
          const statusKey = approver.decision ?? "pending";
          return (
            <div
              key={approver.id}
              className="flex items-start gap-3 rounded-lg border bg-card p-3"
            >
              <div className="mt-0.5 shrink-0">{statusIcons[statusKey]}</div>
              <div className="flex-1 min-w-0 space-y-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-medium">{approver.email}</span>
                  <Badge
                    variant="outline"
                    className={cn(
                      "text-xs",
                      categoryColors[approver.category] || "",
                    )}
                  >
                    {approver.category}
                  </Badge>
                </div>
                <div className="flex items-center gap-2">
                  <Badge
                    variant="outline"
                    className={cn("text-xs", statusStyles[statusKey])}
                  >
                    {statusLabels[statusKey]}
                  </Badge>
                  {approver.decidedAt && (
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(approver.decidedAt)}
                    </span>
                  )}
                </div>
                {approver.comment && (
                  <div className="flex items-start gap-1.5 text-xs text-muted-foreground mt-1">
                    <MessageSquare className="h-3 w-3 mt-0.5 shrink-0" />
                    <span>{approver.comment}</span>
                  </div>
                )}
              </div>
              {!approver.decision && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 text-xs shrink-0"
                  disabled={resendSingle.isPending}
                  onClick={() => handleResend(approver.email)}
                >
                  {resendSingle.isPending ? (
                    <Loader2 className="h-3 w-3 mr-1 animate-spin" />
                  ) : (
                    <Mail className="h-3 w-3 mr-1" />
                  )}
                  Resend
                </Button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
