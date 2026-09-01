import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  FileCheck,
  CheckCircle2,
  XCircle,
  Clock,
  Building2,
  User,
  Send,
  MessageSquare,
  File,
  Search,
  Globe,
  ChevronDown,
  Eye,
  Download,
} from "lucide-react";
import {
  mockActivitySubmissions,
  type ActivitySubmission,
  type ActivityProof,
  type ProofStatus,
  type ProofComment,
} from "@/data/mockActivitySubmissions";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { LocationFilter } from "@/components/LocationFilter";

type Region = string;

type ApproverStatus = "Pending Review" | "Approved" | "Denied";
const getApproverStatus = (status: ProofStatus): ApproverStatus => {
  if (status === "Approved") return "Approved";
  if (status === "Denied") return "Denied";
  return "Pending Review";
};

const approverStatusConfig: Record<
  ApproverStatus,
  { icon: React.ReactNode; color: string }
> = {
  "Pending Review": {
    icon: <Clock className="h-4 w-4" />,
    color: "text-warning bg-warning/10 border-warning/20",
  },
  Approved: {
    icon: <CheckCircle2 className="h-4 w-4" />,
    color: "text-success bg-success/10 border-success/20",
  },
  Denied: {
    icon: <XCircle className="h-4 w-4" />,
    color: "text-destructive bg-destructive/10 border-destructive/20",
  },
};

const companyRegionMap: Record<string, Region> = {
  "Acme Solutions Inc.": "AMERICAS",
  "TechBridge Partners": "EMEAR",
  "CloudFirst Consulting": "APJ",
  "Innovate LATAM": "LATAM",
};

export function ApprovalsWidget() {
  const { user } = useAuth();
  const firstName = user?.firstName ?? "Approver";

  const [submissions, setSubmissions] = useState<ActivitySubmission[]>(
    mockActivitySubmissions,
  );
  const [selectedSubmission, setSelectedSubmission] =
    useState<ActivitySubmission | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [commentTexts, setCommentTexts] = useState<Record<string, string>>({});
  const [denyingActivityId, setDenyingActivityId] = useState<string | null>(
    null,
  );
  const [denyReason, setDenyReason] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("pending-review");
  const [selectedRegion, setSelectedRegion] = useState<Region>("GLOBAL");

  const [seenCommentCounts, setSeenCommentCounts] = useState<
    Record<string, number>
  >(() => {
    const counts: Record<string, number> = {};
    mockActivitySubmissions.forEach((sub) => {
      sub.activities.forEach((a) => {
        counts[a.id] = a.comments.filter((c) => c.role === "approver").length;
      });
    });
    return counts;
  });

  const getNewCommentCount = (activity: ActivityProof): number => {
    const seen = seenCommentCounts[activity.id] ?? 0;
    const unseen = Math.max(0, activity.comments.length - seen);
    if (unseen === 0) return 0;
    const lastComment = activity.comments[activity.comments.length - 1];
    if (lastComment && lastComment.role === "approver") return 0;
    return unseen;
  };

  const markCommentsAsSeen = (sub: ActivitySubmission) => {
    setSeenCommentCounts((prev) => {
      const updated = { ...prev };
      sub.activities.forEach((a) => {
        updated[a.id] = a.comments.length;
      });
      return updated;
    });
  };

  const allActivities = submissions.flatMap((s) => s.activities);
  const pendingCount = allActivities.filter(
    (a) => getApproverStatus(a.proofStatus) === "Pending Review",
  ).length;
  const approvedCount = allActivities.filter(
    (a) => getApproverStatus(a.proofStatus) === "Approved",
  ).length;
  const deniedCount = allActivities.filter(
    (a) => getApproverStatus(a.proofStatus) === "Denied",
  ).length;

  const stats = [
    {
      label: "Pending Review",
      value: pendingCount,
      icon: Clock,
      color: "text-warning",
    },
    {
      label: "Approved",
      value: approvedCount,
      icon: CheckCircle2,
      color: "text-success",
    },
    {
      label: "Denied",
      value: deniedCount,
      icon: XCircle,
      color: "text-destructive",
    },
  ];

  const [drawerNewCounts, setDrawerNewCounts] = useState<
    Record<string, number>
  >({});

  const handleOpenSubmission = (sub: ActivitySubmission) => {
    const counts: Record<string, number> = {};
    sub.activities.forEach((a) => {
      counts[a.id] = getNewCommentCount(a);
    });
    setDrawerNewCounts(counts);
    setSelectedSubmission(sub);
    setDrawerOpen(true);
    setCommentTexts({});
    setDenyingActivityId(null);
    setDenyReason("");
    markCommentsAsSeen(sub);
  };

  const updateActivityStatus = (
    submissionId: string,
    activityId: string,
    newStatus: ProofStatus,
  ) => {
    setSubmissions((prev) =>
      prev.map((s) =>
        s.id === submissionId
          ? {
              ...s,
              activities: s.activities.map((a) =>
                a.id === activityId
                  ? {
                      ...a,
                      proofStatus: newStatus,
                      reviewedAt: new Date().toISOString(),
                      reviewedBy: "You (Approver)",
                    }
                  : a,
              ),
            }
          : s,
      ),
    );
    if (selectedSubmission?.id === submissionId) {
      setSelectedSubmission((prev) =>
        prev
          ? {
              ...prev,
              activities: prev.activities.map((a) =>
                a.id === activityId
                  ? {
                      ...a,
                      proofStatus: newStatus,
                      reviewedAt: new Date().toISOString(),
                      reviewedBy: "You (Approver)",
                    }
                  : a,
              ),
            }
          : null,
      );
    }
    if (newStatus === "Approved") {
      toast.success("Activity Approved", {
        description: `Status updated to ${newStatus}.`,
      });
    } else {
      toast.error("Activity Denied", {
        description: `Status updated to ${newStatus}.`,
      });
    }
    setDenyingActivityId(null);
    setDenyReason("");
  };

  const handleDenyWithReason = (submissionId: string, activityId: string) => {
    if (!denyReason.trim()) {
      toast.error("Reason Required", {
        description: "Please provide a reason for denial.",
      });
      return;
    }
    const denialComment: ProofComment = {
      id: `c-${Date.now()}`,
      author: "You (Approver)",
      role: "approver",
      text: `Denied: ${denyReason.trim()}`,
      timestamp: new Date().toISOString(),
    };
    const addComment = (s: ActivitySubmission) =>
      s.id === submissionId
        ? {
            ...s,
            activities: s.activities.map((a) =>
              a.id === activityId
                ? { ...a, comments: [...a.comments, denialComment] }
                : a,
            ),
          }
        : s;
    setSubmissions((prev) => prev.map(addComment));
    if (selectedSubmission?.id === submissionId) {
      setSelectedSubmission((prev) => (prev ? addComment(prev) : null));
    }
    updateActivityStatus(submissionId, activityId, "Denied");
  };

  const handleAddComment = (submissionId: string, activityId: string) => {
    const text = commentTexts[activityId]?.trim();
    if (!text) return;
    const newComment: ProofComment = {
      id: `c-${Date.now()}`,
      author: "You (Approver)",
      role: "approver",
      text,
      timestamp: new Date().toISOString(),
    };
    const updater = (s: ActivitySubmission) =>
      s.id === submissionId
        ? {
            ...s,
            activities: s.activities.map((a) =>
              a.id === activityId
                ? { ...a, comments: [...a.comments, newComment] }
                : a,
            ),
          }
        : s;
    setSubmissions((prev) => prev.map(updater));
    if (selectedSubmission?.id === submissionId) {
      setSelectedSubmission((prev) => (prev ? updater(prev) : null));
    }
    setCommentTexts((prev) => ({ ...prev, [activityId]: "" }));
  };

  const getFilteredActivities = (sub: ActivitySubmission) => {
    if (statusFilter === "all") return sub.activities;
    return sub.activities.filter((a) => {
      const s = getApproverStatus(a.proofStatus);
      if (statusFilter === "pending-review") return s === "Pending Review";
      if (statusFilter === "approved") return s === "Approved";
      if (statusFilter === "denied") return s === "Denied";
      return true;
    });
  };

  const filteredSubmissions = submissions.filter((sub) => {
    const matchesSearch =
      sub.incentiveName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      sub.partnerCompany.toLowerCase().includes(searchQuery.toLowerCase()) ||
      sub.submittedBy.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesRegion =
      selectedRegion === "GLOBAL" ||
      companyRegionMap[sub.partnerCompany] === selectedRegion;
    if (!matchesSearch || !matchesRegion) return false;
    return getFilteredActivities(sub).length > 0;
  });

  const groupedByEngagement = filteredSubmissions.reduce<
    Record<string, ActivitySubmission[]>
  >((acc, sub) => {
    const name = sub.incentiveName ?? "Unknown";
    if (!acc[name]) acc[name] = [];
    acc[name].push(sub);
    return acc;
  }, {});

  return (
    <div className="flex flex-col w-full gap-8">
      {/* Header with filters */}
      <div className="flex items-start justify-between shrink-0">
        <div>
          <h1 className="text-3xl font-bold text-foreground">
            Good Morning, {firstName}
          </h1>
          <p className="text-muted-foreground mt-1">
            Review and approve proof-of-execution submissions
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 w-[200px] h-9"
            />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-[180px] h-9">
              <SelectValue placeholder="Filter Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Submissions</SelectItem>
              <SelectItem value="pending-review">Pending Review</SelectItem>
              <SelectItem value="approved">Approved</SelectItem>
              <SelectItem value="denied">Denied</SelectItem>
            </SelectContent>
          </Select>
          <div className="flex items-center gap-2">
            <Globe className="h-5 w-5 text-muted-foreground" />
            <LocationFilter
              value={selectedRegion}
              onChange={setSelectedRegion}
              className="h-9 w-[160px] text-sm"
            />
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 shrink-0">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardContent className="p-6 flex items-center gap-4">
              <div className={`p-3 rounded-lg bg-muted ${stat.color}`}>
                <stat.icon className="h-6 w-6" />
              </div>
              <div>
                <p className="text-2xl font-bold text-foreground">
                  {stat.value}
                </p>
                <p className="text-sm text-muted-foreground">{stat.label}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* User Submissions */}
      <Card className="flex flex-col">
        <CardHeader className="pb-4 shrink-0">
          <CardTitle className="text-lg">User Submissions</CardTitle>
          <p className="text-sm text-muted-foreground">
            Review proof-of-execution submissions from partner users
          </p>
        </CardHeader>
        <CardContent className="flex flex-col">
          <div>
            {Object.keys(groupedByEngagement).length === 0 ? (
              <div className="p-8 text-center text-muted-foreground">
                No submissions found.
              </div>
            ) : (
              <div className="space-y-8">
                {Object.entries(groupedByEngagement).map(
                  ([engagementName, subs]) => (
                    <div
                      key={engagementName}
                      className="rounded-lg border border-border shadow-sm p-4 space-y-3"
                    >
                      <div className="flex items-center gap-2">
                        <FileCheck className="h-4 w-4 text-primary" />
                        <h3 className="font-semibold text-foreground">
                          {engagementName}
                        </h3>
                        {(() => {
                          const count = subs.reduce(
                            (sum, s) => sum + getFilteredActivities(s).length,
                            0,
                          );
                          const filterLabel =
                            statusFilter === "all"
                              ? "Submissions"
                              : statusFilter === "pending-review"
                                ? "Pending Review"
                                : statusFilter === "approved"
                                  ? "Approved"
                                  : "Denied";
                          const badgeColor =
                            statusFilter === "pending-review"
                              ? "text-warning bg-warning/10 border-warning/20"
                              : statusFilter === "approved"
                                ? "text-success bg-success/10 border-success/20"
                                : statusFilter === "denied"
                                  ? "text-destructive bg-destructive/10 border-destructive/20"
                                  : "";
                          return (
                            <Badge
                              variant="outline"
                              className={cn("text-xs", badgeColor)}
                            >
                              {count} {filterLabel}
                            </Badge>
                          );
                        })()}
                      </div>
                      <div className="space-y-2">
                        {subs.map((sub) => {
                          const visibleActivities = getFilteredActivities(sub);
                          return (
                            <div key={sub.id} className="space-y-1">
                              {visibleActivities.map((activity) => {
                                const displayStatus = getApproverStatus(
                                  activity.proofStatus,
                                );
                                const status =
                                  approverStatusConfig[displayStatus];
                                const isPending =
                                  displayStatus === "Pending Review";
                                const newCount = getNewCommentCount(activity);
                                return (
                                  <Card
                                    key={activity.id}
                                    className={cn(
                                      "cursor-pointer hover:shadow-md transition-all",
                                      isPending
                                        ? "border-warning/50 bg-warning/5 hover:border-warning/70"
                                        : "hover:border-primary/50",
                                    )}
                                    onClick={() => handleOpenSubmission(sub)}
                                  >
                                    <CardContent className="p-4 flex items-center justify-between">
                                      <div className="flex items-center gap-3">
                                        {isPending && (
                                          <div className="w-1 h-10 rounded-full bg-warning shrink-0" />
                                        )}
                                        <div className="space-y-0.5">
                                          <div className="flex items-center gap-2">
                                            <span className="text-xs font-semibold text-muted-foreground">
                                              Activity {activity.order}
                                            </span>
                                            <span className="font-medium text-sm text-foreground">
                                              {activity.name}
                                            </span>
                                          </div>
                                          <div className="flex items-center gap-4 text-xs text-muted-foreground">
                                            <span className="flex items-center gap-1">
                                              <Building2 className="h-3 w-3" />
                                              {sub.partnerCompany}
                                            </span>
                                            <span className="flex items-center gap-1">
                                              <User className="h-3 w-3" />
                                              {sub.submittedBy}
                                            </span>
                                          </div>
                                        </div>
                                      </div>
                                      <div className="flex items-center gap-2">
                                        {newCount > 0 && (
                                          <Badge className="bg-violet-500 text-white text-xs px-2 py-0 h-5 flex items-center gap-1 animate-pulse">
                                            <MessageSquare className="h-3 w-3" />
                                            {newCount} new
                                          </Badge>
                                        )}
                                        <Badge
                                          variant="outline"
                                          className={cn(
                                            "text-xs",
                                            status.color,
                                          )}
                                        >
                                          {status.icon}
                                          <span className="ml-1">
                                            {displayStatus}
                                          </span>
                                        </Badge>
                                        {isPending && (
                                          <span className="text-xs font-medium text-warning">
                                            Action Required
                                          </span>
                                        )}
                                      </div>
                                    </CardContent>
                                  </Card>
                                );
                              })}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ),
                )}
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Approval Detail Drawer */}
      <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
        <SheetContent className="sm:max-w-2xl p-0 flex flex-col">
          {selectedSubmission && (
            <>
              <SheetHeader className="p-6 pb-4 border-b shrink-0">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-blue-500/10">
                    <FileCheck className="h-5 w-5 text-blue-500" />
                  </div>
                  <div>
                    <SheetTitle className="text-lg">
                      Review Submission
                    </SheetTitle>
                    <p className="text-sm text-muted-foreground mt-0.5">
                      {selectedSubmission.incentiveName}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-4 mt-3 text-sm text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <Building2 className="h-3.5 w-3.5" />
                    {selectedSubmission.partnerCompany}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <User className="h-3.5 w-3.5" />
                    {selectedSubmission.submittedBy}
                  </span>
                </div>
              </SheetHeader>
              <ScrollArea className="flex-1">
                <div className="p-6 space-y-4">
                  {selectedSubmission.activities.map((activity) => {
                    const displayStatus = getApproverStatus(
                      activity.proofStatus,
                    );
                    const status = approverStatusConfig[displayStatus];
                    const canAction = displayStatus === "Pending Review";
                    return (
                      <div
                        key={activity.id}
                        className={cn(
                          "rounded-lg border bg-card p-4 space-y-3",
                          canAction && "border-warning/40",
                        )}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex-1">
                            <div className="flex items-center gap-2 mb-1">
                              <span className="text-xs font-semibold text-muted-foreground">
                                Activity {activity.order}
                              </span>
                              <Badge
                                variant="outline"
                                className={cn("text-xs", status.color)}
                              >
                                {status.icon}
                                <span className="ml-1">{displayStatus}</span>
                              </Badge>
                            </div>
                            <h4 className="font-semibold text-sm text-foreground">
                              {activity.name}
                            </h4>
                            <p className="text-sm text-muted-foreground mt-1">
                              {activity.description}
                            </p>
                          </div>
                        </div>

                        {activity.uploadedFileName && (
                          <div className="flex items-center gap-2 text-xs text-muted-foreground bg-muted/50 rounded-md px-3 py-2">
                            <File className="h-3.5 w-3.5 shrink-0" />
                            <span className="truncate">
                              {activity.uploadedFileName}
                            </span>
                            {activity.uploadedAt && (
                              <span className="shrink-0 ml-1">
                                {new Date(
                                  activity.uploadedAt,
                                ).toLocaleDateString("en-US", {
                                  month: "short",
                                  day: "numeric",
                                  year: "numeric",
                                })}
                              </span>
                            )}
                            <div className="ml-auto flex items-center gap-1 shrink-0">
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-6 px-2 text-xs"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  toast.info("Opening file in browser...");
                                }}
                              >
                                <Eye className="h-3 w-3 mr-1" />
                                View
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-6 px-2 text-xs"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  toast.info(
                                    `Downloading ${activity.uploadedFileName}...`,
                                  );
                                }}
                              >
                                <Download className="h-3 w-3 mr-1" />
                                Download
                              </Button>
                            </div>
                          </div>
                        )}

                        {canAction && (
                          <div className="space-y-2">
                            <div className="flex items-center gap-2 justify-end">
                              <Button
                                size="sm"
                                variant="default"
                                onClick={() =>
                                  updateActivityStatus(
                                    selectedSubmission.id,
                                    activity.id,
                                    "Approved",
                                  )
                                }
                              >
                                <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />
                                Approve
                              </Button>
                              {denyingActivityId === activity.id ? (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="text-muted-foreground"
                                  onClick={() => {
                                    setDenyingActivityId(null);
                                    setDenyReason("");
                                  }}
                                >
                                  Cancel
                                </Button>
                              ) : (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="text-destructive border-destructive/30 hover:bg-destructive/10"
                                  onClick={() => {
                                    setDenyingActivityId(activity.id);
                                    setDenyReason("");
                                  }}
                                >
                                  <XCircle className="h-3.5 w-3.5 mr-1.5" />
                                  Deny
                                </Button>
                              )}
                            </div>
                            {denyingActivityId === activity.id && (
                              <div className="flex gap-2 rounded-md border border-destructive/30 bg-destructive/5 p-3">
                                <Textarea
                                  placeholder="Provide a reason for denial..."
                                  value={denyReason}
                                  onChange={(e) =>
                                    setDenyReason(e.target.value)
                                  }
                                  className="min-h-[60px] text-sm"
                                  autoFocus
                                />
                                <Button
                                  size="sm"
                                  variant="destructive"
                                  className="shrink-0 mt-auto"
                                  onClick={() =>
                                    handleDenyWithReason(
                                      selectedSubmission.id,
                                      activity.id,
                                    )
                                  }
                                >
                                  <Send className="h-3.5 w-3.5 mr-1.5" />
                                  Submit Denial
                                </Button>
                              </div>
                            )}
                          </div>
                        )}

                        {/* Collapsible Comments */}
                        <Separator />
                        <Collapsible
                          defaultOpen={(drawerNewCounts[activity.id] ?? 0) > 0}
                        >
                          <CollapsibleTrigger className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground transition-colors w-full">
                            <ChevronDown className="h-3.5 w-3.5 transition-transform data-[state=open]:rotate-180" />
                            <MessageSquare className="h-3.5 w-3.5" />
                            <span>Comments ({activity.comments.length})</span>
                            {(drawerNewCounts[activity.id] ?? 0) > 0 && (
                              <Badge className="bg-violet-500 text-white text-xs px-2 py-0 h-5 flex items-center gap-1 ml-1">
                                <MessageSquare className="h-3 w-3" />
                                {drawerNewCounts[activity.id]} new
                              </Badge>
                            )}
                          </CollapsibleTrigger>
                          <CollapsibleContent className="space-y-2 mt-2">
                            {activity.comments.map((comment) => (
                              <div
                                key={comment.id}
                                className={cn(
                                  "rounded-md px-3 py-2 text-sm",
                                  comment.role === "approver"
                                    ? "bg-primary/5 border border-primary/10"
                                    : "bg-muted/50 border border-border",
                                )}
                              >
                                <div className="flex items-center justify-between mb-1">
                                  <span className="text-xs font-semibold text-foreground">
                                    {comment.role === "approver"
                                      ? "You"
                                      : selectedSubmission.submittedBy}
                                  </span>
                                  <span className="text-xs text-muted-foreground">
                                    {new Date(
                                      comment.timestamp,
                                    ).toLocaleDateString("en-US", {
                                      month: "short",
                                      day: "numeric",
                                    })}
                                  </span>
                                </div>
                                <p className="text-sm text-muted-foreground">
                                  {comment.text}
                                </p>
                              </div>
                            ))}
                            <div className="flex gap-2 mt-2">
                              <Textarea
                                placeholder="Add a comment..."
                                value={commentTexts[activity.id] || ""}
                                onChange={(e) =>
                                  setCommentTexts((prev) => ({
                                    ...prev,
                                    [activity.id]: e.target.value,
                                  }))
                                }
                                className="min-h-[60px] text-sm"
                              />
                              <Button
                                size="icon"
                                variant="ghost"
                                className="shrink-0 mt-auto"
                                onClick={() =>
                                  handleAddComment(
                                    selectedSubmission.id,
                                    activity.id,
                                  )
                                }
                              >
                                <Send className="h-4 w-4" />
                              </Button>
                            </div>
                          </CollapsibleContent>
                        </Collapsible>
                      </div>
                    );
                  })}
                </div>
              </ScrollArea>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}
