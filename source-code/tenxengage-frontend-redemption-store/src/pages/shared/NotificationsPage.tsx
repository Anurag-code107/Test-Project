import { useState } from "react";
import { PageBanner } from "@/components/PageBanner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Bell, CheckCheck, Loader2 } from "lucide-react";
import { formatDistanceToNow } from "date-fns";
import { cn } from "@/lib/utils";
import {
  useNotifications,
  useMarkAsRead,
  useMarkAllAsRead,
} from "@/hooks/useNotificationApi";
import type {
  NotificationResponse,
  NotificationCategory,
} from "@/types/notification.types";

const CATEGORIES: NotificationCategory[] = [
  "INCENTIVE",
  "BUDGET",
  "CLAIMS",
  "REWARDS",
  "DATA",
  "INTEGRATION",
  "USER_MANAGEMENT",
];

const categoryColors: Record<NotificationCategory, string> = {
  INCENTIVE: "bg-blue-100 text-blue-700 border-blue-200",
  BUDGET: "bg-emerald-100 text-emerald-700 border-emerald-200",
  CLAIMS: "bg-amber-100 text-amber-700 border-amber-200",
  REWARDS: "bg-purple-100 text-purple-700 border-purple-200",
  DATA: "bg-slate-100 text-slate-700 border-slate-200",
  INTEGRATION: "bg-cyan-100 text-cyan-700 border-cyan-200",
  USER_MANAGEMENT: "bg-rose-100 text-rose-700 border-rose-200",
};

const categoryLabels: Record<NotificationCategory, string> = {
  INCENTIVE: "Incentive",
  BUDGET: "Budget",
  CLAIMS: "Claims",
  REWARDS: "Rewards",
  DATA: "Data",
  INTEGRATION: "Integration",
  USER_MANAGEMENT: "User Management",
};

type FilterTab = "all" | "unread";

export default function NotificationsPage() {
  const [filterTab, setFilterTab] = useState<FilterTab>("all");
  const [categoryFilter, setCategoryFilter] = useState<string>("all");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data, isLoading } = useNotifications({
    page,
    pageSize,
    unreadOnly: filterTab === "unread" ? true : undefined,
  });

  const markAsRead = useMarkAsRead();
  const markAllAsRead = useMarkAllAsRead();

  const handleMarkAsRead = (notification: NotificationResponse) => {
    if (!notification.isRead) {
      markAsRead.mutate(notification.id);
    }
  };

  const handleMarkAllAsRead = () => {
    markAllAsRead.mutate();
  };

  const notifications = data?.data ?? [];
  const filteredNotifications =
    categoryFilter === "all"
      ? notifications
      : notifications.filter((n) => n.category === categoryFilter);

  const totalPages = data?.totalPages ?? 1;
  const hasPrevious = data?.hasPrevious ?? false;
  const hasNext = data?.hasNext ?? false;

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <PageBanner
        title="Notifications"
        subtitle="Stay up to date with your latest alerts and updates"
        actions={
          <Button
            variant="outline"
            size="sm"
            className="gap-2"
            onClick={handleMarkAllAsRead}
            disabled={markAllAsRead.isPending}
          >
            <CheckCheck className="h-4 w-4" />
            Mark All as Read
          </Button>
        }
      />

      {/* Filters row */}
      <div className="flex items-center justify-between">
        {/* Tab pills */}
        <div className="flex items-center gap-1">
          <button
            onClick={() => {
              setFilterTab("all");
              setPage(0);
            }}
            className={cn(
              "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
              filterTab === "all"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted",
            )}
          >
            All
          </button>
          <button
            onClick={() => {
              setFilterTab("unread");
              setPage(0);
            }}
            className={cn(
              "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
              filterTab === "unread"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted",
            )}
          >
            Unread
          </button>
        </div>

        {/* Category filter */}
        <Select
          value={categoryFilter}
          onValueChange={(v) => {
            setCategoryFilter(v);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-[200px] h-9 rounded-lg text-sm text-muted-foreground">
            <SelectValue placeholder="All Categories" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Categories</SelectItem>
            {CATEGORIES.map((cat) => (
              <SelectItem key={cat} value={cat}>
                {categoryLabels[cat]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      ) : filteredNotifications.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
          <Bell className="h-10 w-10 text-muted-foreground mb-3" />
          <p className="text-sm text-muted-foreground">
            {filterTab === "unread"
              ? "No unread notifications"
              : "No notifications yet"}
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {filteredNotifications.map((notification) => (
            <button
              key={notification.id}
              onClick={() => handleMarkAsRead(notification)}
              className={cn(
                "flex items-start gap-4 w-full text-left rounded-xl border p-4 transition-colors",
                notification.isRead
                  ? "bg-card border-border hover:bg-muted/50"
                  : "bg-primary/5 border-primary/15 hover:bg-primary/10",
              )}
            >
              {/* Unread indicator */}
              <div className="flex items-center pt-1.5 shrink-0">
                <div
                  className={cn(
                    "h-2.5 w-2.5 rounded-full",
                    notification.isRead
                      ? "bg-transparent"
                      : "bg-primary",
                  )}
                />
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span
                    className={cn(
                      "text-sm truncate",
                      notification.isRead
                        ? "font-medium text-foreground"
                        : "font-semibold text-foreground",
                    )}
                  >
                    {notification.title}
                  </span>
                  {notification.category && (
                    <Badge
                      variant="outline"
                      className={cn(
                        "text-[10px] font-medium shrink-0",
                        categoryColors[
                          notification.category as NotificationCategory
                        ] ?? "bg-muted text-muted-foreground border-border",
                      )}
                    >
                      {categoryLabels[
                        notification.category as NotificationCategory
                      ] ?? notification.category}
                    </Badge>
                  )}
                </div>
                <p className="text-sm text-muted-foreground line-clamp-2">
                  {notification.message}
                </p>
              </div>

              {/* Timestamp */}
              <span className="text-xs text-muted-foreground whitespace-nowrap shrink-0 pt-0.5">
                {formatDistanceToNow(new Date(notification.createdAt), {
                  addSuffix: true,
                })}
              </span>
            </button>
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <p className="text-sm text-muted-foreground">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!hasPrevious}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!hasNext}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
