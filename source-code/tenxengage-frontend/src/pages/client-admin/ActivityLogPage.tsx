import { useState, useCallback, useMemo } from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { FeatureGate } from "@/components/FeatureGate";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Download,
  Filter,
  Search,
  Calendar,
  User,
  Building2,
  ChevronLeft,
  ChevronRight,
  Loader2,
} from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { useAuditLogs } from "@/hooks/useAuditApi";
import type { UserType } from "@/types/audit.types";

// ─── Constants ──────────────────────────────────────────────────────────────

const actionTypes = [
  "Created",
  "Edited",
  "Deleted",
  "Activated",
  "Deactivated",
  "Submitted",
  "Approved",
  "Rejected",
  "Claimed",
  "Unclaimed",
  "Uploaded",
  "Synced",
  "Logged In",
  "Logged Out",
] as const;

// ─── Helpers ────────────────────────────────────────────────────────────────

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function getInternalActionBadgeClass(action: string) {
  switch (action) {
    case "Approved":
      return "bg-emerald-500/10 text-emerald-600 border-emerald-500/20";
    case "Rejected":
      return "bg-red-500/10 text-red-600 border-red-500/20";
    case "Created":
    case "Activated":
      return "bg-blue-500/10 text-blue-600 border-blue-500/20";
    case "Deactivated":
      return "bg-amber-500/10 text-amber-600 border-amber-500/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

function getPartnerActionBadgeClass(action: string) {
  switch (action) {
    case "Submitted":
      return "bg-purple-500/10 text-purple-600 border-purple-500/20";
    case "Created":
      return "bg-blue-500/10 text-blue-600 border-blue-500/20";
    case "Edited":
      return "bg-amber-500/10 text-amber-600 border-amber-500/20";
    default:
      return "bg-muted text-muted-foreground border-border";
  }
}

// ─── Component ──────────────────────────────────────────────────────────────

function ActivityLogPage() {
  const [userTypeFilter, setUserTypeFilter] = useState<UserType | "All">("All");
  const [actionTypeFilter, setActionTypeFilter] = useState<string>("All");
  const [dateFromFilter, setDateFromFilter] = useState("");
  const [dateToFilter, setDateToFilter] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading, isError } = useAuditLogs({
    userType: userTypeFilter !== "All" ? userTypeFilter : undefined,
    action: actionTypeFilter !== "All" ? actionTypeFilter : undefined,
    search: searchQuery || undefined,
    dateFrom: dateFromFilter || undefined,
    dateTo: dateToFilter || undefined,
    page,
    pageSize,
  });

  const entries = useMemo(() => data?.data ?? [], [data?.data]);
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  const internalEntries = entries.filter((e) => e.userType === "Internal");
  const partnerEntries = entries.filter((e) => e.userType === "Partner");

  const exportToExcel = useCallback(() => {
    const headers = [
      "Date",
      "User",
      "Email",
      "User Type",
      "Company",
      "Action",
      "Description",
      "Target",
    ];
    const rows = entries.map((entry) => [
      entry.date,
      entry.user,
      entry.email,
      entry.userType || "-",
      entry.company || "-",
      entry.action,
      entry.actionDescription,
      entry.target || "-",
    ]);

    const csvContent = [
      headers.join(","),
      ...rows.map((row) => row.map((cell) => `"${cell}"`).join(",")),
    ].join("\n");

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `activity-log-${new Date().toISOString().split("T")[0]}.csv`;
    link.click();
  }, [entries]);

  const clearFilters = () => {
    setUserTypeFilter("All");
    setActionTypeFilter("All");
    setDateFromFilter("");
    setDateToFilter("");
    setSearchQuery("");
    setPage(0);
  };

  return (
    <div className="space-y-6">
      <PageBanner
        theme="activity"
        title="Activity Log"
        subtitle="Track changes and actions across the system"
        actions={
          <PermissionGate permission="action.activity_log.export">
            <FeatureGate feature="export_reports">
              <Button
                onClick={exportToExcel}
                variant="outline"
                className="h-8 text-xs gap-1.5 hover:border-primary/30"
              >
                <Download className="h-3.5 w-3.5" />
                Export CSV
              </Button>
            </FeatureGate>
          </PermissionGate>
        }
      />

      <Card className="pt-6">
        <CardContent className="space-y-6">
          {/* Filters */}
          <div className="flex flex-wrap items-center gap-4 p-4 rounded-lg border border-border bg-muted/30">
            <div className="flex items-center gap-2">
              <Filter className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium text-foreground">
                Filters:
              </span>
            </div>

            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                type="text"
                placeholder="Search user/company"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setPage(0);
                }}
                className="h-8 w-[200px] pl-8 text-sm"
              />
            </div>

            <div className="flex items-center gap-2">
              <label className="text-sm text-muted-foreground">
                User Type:
              </label>
              <Select
                value={userTypeFilter}
                onValueChange={(value) => {
                  setUserTypeFilter(value as UserType | "All");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-[130px] h-8 text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="All">All Users</SelectItem>
                  <SelectItem value="Internal">Internal</SelectItem>
                  <SelectItem value="Partner">Partner</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <label className="text-sm text-muted-foreground">Action:</label>
              <Select
                value={actionTypeFilter}
                onValueChange={(value) => {
                  setActionTypeFilter(value);
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-[130px] h-8 text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="All">All Actions</SelectItem>
                  {actionTypes.map((action) => (
                    <SelectItem key={action} value={action}>
                      {action}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-muted-foreground" />
              <label className="text-sm text-muted-foreground">From:</label>
              <input
                type="date"
                value={dateFromFilter}
                onChange={(e) => {
                  setDateFromFilter(e.target.value);
                  setPage(0);
                }}
                className="h-8 px-2 text-sm rounded-md border border-input bg-background"
              />
            </div>

            <div className="flex items-center gap-2">
              <label className="text-sm text-muted-foreground">To:</label>
              <input
                type="date"
                value={dateToFilter}
                onChange={(e) => {
                  setDateToFilter(e.target.value);
                  setPage(0);
                }}
                className="h-8 px-2 text-sm rounded-md border border-input bg-background"
              />
            </div>

            <Button
              variant="ghost"
              size="sm"
              onClick={clearFilters}
              className="text-muted-foreground"
            >
              Clear Filters
            </Button>
          </div>

          {/* Results count + pagination info */}
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">
              {isLoading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading...
                </span>
              ) : (
                `Showing ${entries.length} of ${totalElements} activities`
              )}
            </div>
            {totalPages > 1 && (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <span className="text-sm text-muted-foreground">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setPage((p) => Math.min(totalPages - 1, p + 1))
                  }
                  disabled={page >= totalPages - 1}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>

          {isError && (
            <div className="text-sm text-red-600 p-4 text-center border border-red-200 rounded-lg bg-red-50">
              Failed to load activity logs. Please try again.
            </div>
          )}

          {/* Internal Users Activities */}
          {!isLoading &&
            (userTypeFilter === "All" || userTypeFilter === "Internal") && (
              <div className="rounded-lg border border-border overflow-hidden">
                <div className="flex items-center gap-2 p-4 bg-muted/50 border-b border-border">
                  <User className="h-4 w-4 text-primary" />
                  <h3 className="font-semibold text-foreground">
                    Internal User Activity
                  </h3>
                  <Badge
                    variant="outline"
                    className="ml-2 bg-primary/10 text-primary border-primary/20"
                  >
                    {internalEntries.length}
                  </Badge>
                </div>

                {/* Column Headers */}
                <div className="grid grid-cols-[180px,220px,1fr,180px,110px] gap-6 items-center px-3 py-2 bg-muted/30 border-b border-border text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  <div className="pl-11">User</div>
                  <div>Email</div>
                  <div>Action</div>
                  <div>Target</div>
                  <div className="text-right">Date</div>
                </div>

                {internalEntries.length === 0 ? (
                  <div className="text-sm text-muted-foreground p-4 text-center">
                    No internal user activities match the current filters
                  </div>
                ) : (
                  <div className="divide-y divide-border">
                    {internalEntries.map((entry) => (
                      <div
                        key={entry.id}
                        className="grid grid-cols-[180px,220px,1fr,180px,110px] gap-6 items-center p-3 hover:bg-muted/30 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                            <User className="h-4 w-4 text-primary" />
                          </div>
                          <span className="font-medium text-foreground">
                            {entry.user}
                          </span>
                        </div>
                        <div className="text-sm text-muted-foreground">
                          {entry.email}
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge
                            variant="outline"
                            className={getInternalActionBadgeClass(
                              entry.action,
                            )}
                          >
                            {entry.action}
                          </Badge>
                          <span className="text-sm text-muted-foreground truncate">
                            {entry.actionDescription}
                          </span>
                        </div>
                        <div className="text-sm font-medium text-foreground truncate">
                          {entry.target || "-"}
                        </div>
                        <div className="text-sm text-muted-foreground text-right whitespace-nowrap">
                          {formatDate(entry.date)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

          {/* Partner Users Activities */}
          {!isLoading &&
            (userTypeFilter === "All" || userTypeFilter === "Partner") && (
              <div className="rounded-lg border border-border overflow-hidden">
                <div className="flex items-center gap-2 p-4 bg-blue-500/5 border-b border-border">
                  <Building2 className="h-4 w-4 text-blue-600" />
                  <h3 className="font-semibold text-foreground">
                    Partner User Activity
                  </h3>
                  <Badge
                    variant="outline"
                    className="ml-2 bg-blue-500/10 text-blue-600 border-blue-500/20"
                  >
                    {partnerEntries.length}
                  </Badge>
                </div>

                {/* Column Headers */}
                <div className="grid grid-cols-[180px,220px,1fr,180px,110px] gap-6 items-center px-3 py-2 bg-muted/30 border-b border-border text-xs font-medium text-muted-foreground uppercase tracking-wider">
                  <div className="pl-11">User</div>
                  <div>Email</div>
                  <div>Action</div>
                  <div>Target</div>
                  <div className="text-right">Date</div>
                </div>

                {partnerEntries.length === 0 ? (
                  <div className="text-sm text-muted-foreground p-4 text-center">
                    No partner user activities match the current filters
                  </div>
                ) : (
                  <div className="divide-y divide-border">
                    {partnerEntries.map((entry) => (
                      <div
                        key={entry.id}
                        className="grid grid-cols-[180px,220px,1fr,180px,110px] gap-6 items-center p-3 hover:bg-muted/30 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <div className="h-8 w-8 rounded-full bg-blue-500/10 flex items-center justify-center shrink-0">
                            <Building2 className="h-4 w-4 text-blue-600" />
                          </div>
                          <div>
                            <div className="font-medium text-foreground">
                              {entry.user}
                            </div>
                            {entry.company && (
                              <div className="text-xs text-muted-foreground">
                                {entry.company}
                              </div>
                            )}
                          </div>
                        </div>
                        <div className="text-sm text-muted-foreground">
                          {entry.email}
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge
                            variant="outline"
                            className={getPartnerActionBadgeClass(entry.action)}
                          >
                            {entry.action}
                          </Badge>
                          <span className="text-sm text-muted-foreground truncate">
                            {entry.actionDescription}
                          </span>
                        </div>
                        <div className="text-sm font-medium text-foreground truncate">
                          {entry.target || "-"}
                        </div>
                        <div className="text-sm text-muted-foreground text-right whitespace-nowrap">
                          {formatDate(entry.date)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
        </CardContent>
      </Card>
    </div>
  );
}

export default ActivityLogPage;
