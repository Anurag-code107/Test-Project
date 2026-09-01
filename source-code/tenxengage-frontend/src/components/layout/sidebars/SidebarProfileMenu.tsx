import { useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { useNavigate } from "react-router-dom";
import {
  Bell,
  ChevronUp,
  LogOut,
  User,
  CheckCheck,
  ExternalLink,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  useUnreadCount,
  useNotifications,
  useMarkAsRead,
  useMarkAllAsRead,
} from "@/hooks/useNotificationApi";
import { SidebarTooltip } from "@/components/layout/sidebars/SidebarTooltip";
import { formatDistanceToNow } from "date-fns";

// ── Category icon color mapping ──────────────────────────────────────────

const categoryColors: Record<string, string> = {
  INCENTIVE: "text-blue-500",
  BUDGET: "text-amber-500",
  CLAIMS: "text-green-500",
  REWARDS: "text-purple-500",
  DATA: "text-cyan-500",
  INTEGRATION: "text-orange-500",
  USER_MANAGEMENT: "text-slate-500",
};

// ── Notification Bell ────────────────────────────────────────────────────

function NotificationBell({ collapsed }: { collapsed?: boolean }) {
  const navigate = useNavigate();
  const [bellOpen, setBellOpen] = useState(false);

  const { data: unreadData } = useUnreadCount();
  const { data: recentData, isLoading } = useNotifications(
    bellOpen ? { pageSize: 8 } : undefined,
  );
  const markRead = useMarkAsRead();
  const markAllRead = useMarkAllAsRead();

  const unreadCount = unreadData?.count ?? 0;
  const notifications = recentData?.data ?? [];

  function handleClickNotification(id: string) {
    markRead.mutate(id);
  }

  function handleMarkAllRead() {
    markAllRead.mutate();
  }

  function handleViewAll() {
    setBellOpen(false);
    navigate("/notifications");
  }

  const bellButton = (
    <button
      type="button"
      className={cn(
        "relative flex items-center justify-center rounded-lg transition-colors duration-150",
        "hover:bg-muted text-muted-foreground",
        collapsed ? "h-8 w-8" : "h-8 w-8",
      )}
    >
      <Bell className="h-[18px] w-[18px]" />
      {unreadCount > 0 && (
        <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white leading-none">
          {unreadCount > 99 ? "99+" : unreadCount}
        </span>
      )}
    </button>
  );

  return (
    <Popover open={bellOpen} onOpenChange={setBellOpen}>
      <PopoverTrigger asChild>
        {collapsed ? (
          <SidebarTooltip
            label={`Notifications${unreadCount > 0 ? ` (${unreadCount})` : ""}`}
            enabled
          >
            {bellButton}
          </SidebarTooltip>
        ) : (
          bellButton
        )}
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="start"
        sideOffset={8}
        className="w-80 p-0 rounded-xl shadow-lg border-border"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <h3 className="text-sm font-semibold text-foreground">
            Notifications
          </h3>
          {unreadCount > 0 && (
            <button
              type="button"
              onClick={handleMarkAllRead}
              disabled={markAllRead.isPending}
              className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 transition-colors"
            >
              <CheckCheck className="h-3 w-3" />
              Mark all read
            </button>
          )}
        </div>

        {/* Notification list */}
        <div className="max-h-80 overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : notifications.length === 0 ? (
            <div className="py-8 text-center">
              <Bell className="h-8 w-8 text-muted-foreground/30 mx-auto mb-2" />
              <p className="text-sm text-muted-foreground">No notifications</p>
            </div>
          ) : (
            notifications.map((n) => (
              <button
                key={n.id}
                type="button"
                onClick={() => handleClickNotification(n.id)}
                className={cn(
                  "flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/50 border-b border-border last:border-0",
                  !n.isRead && "bg-primary/[0.02]",
                )}
              >
                {/* Unread indicator */}
                <div className="mt-1.5 shrink-0">
                  {!n.isRead ? (
                    <span className="block h-2 w-2 rounded-full bg-primary" />
                  ) : (
                    <span className="block h-2 w-2 rounded-full bg-transparent" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p
                    className={cn(
                      "text-sm leading-tight truncate",
                      !n.isRead
                        ? "font-medium text-foreground"
                        : "text-muted-foreground",
                    )}
                  >
                    {n.title}
                  </p>
                  <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                    {n.message}
                  </p>
                  <div className="flex items-center gap-2 mt-1">
                    {n.category && (
                      <span
                        className={cn(
                          "text-[10px] font-medium uppercase tracking-wider",
                          categoryColors[n.category] ?? "text-muted-foreground",
                        )}
                      >
                        {n.category}
                      </span>
                    )}
                    <span className="text-[10px] text-muted-foreground">
                      {formatDistanceToNow(new Date(n.createdAt), {
                        addSuffix: true,
                      })}
                    </span>
                  </div>
                </div>
              </button>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-border px-4 py-2.5">
          <button
            type="button"
            onClick={handleViewAll}
            className="flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary/80 transition-colors"
          >
            <ExternalLink className="h-3 w-3" />
            View all notifications
          </button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

// ── Profile Menu ─────────────────────────────────────────────────────────

interface SidebarProfileMenuProps {
  collapsed?: boolean;
}

export function SidebarProfileMenu({ collapsed }: SidebarProfileMenuProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  if (!user) return null;

  const displayName = `${user.firstName} ${user.lastName}`;
  const initials = `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`;
  const roleLabel = user.clientRoleName ?? "User";

  async function handleLogout() {
    setOpen(false);
    await logout();
    navigate("/login", { replace: true });
  }

  function handleProfile() {
    setOpen(false);
    navigate("/settings/profile");
  }

  return (
    <div className="mt-auto border-t border-border p-2">
      {/* Profile button + popover with bell attached */}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            className={cn(
              "flex items-center gap-3 w-full rounded-lg px-2.5 py-2.5 transition-colors duration-150",
              "hover:bg-muted",
              collapsed && "justify-center px-2",
            )}
          >
            {collapsed && (
              <div className="relative" onClick={(e) => e.stopPropagation()}>
                <NotificationBell collapsed={collapsed} />
              </div>
            )}
            {!collapsed && (
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary tracking-wide">
                {initials}
              </span>
            )}
            {!collapsed && (
              <>
                <div className="flex-1 text-left min-w-0">
                  <div className="flex items-center gap-1.5">
                    <p className="text-sm font-medium text-foreground truncate">
                      {displayName}
                    </p>
                    <div
                      onClick={(e) => e.stopPropagation()}
                      className="shrink-0"
                    >
                      <NotificationBell collapsed={collapsed} />
                    </div>
                  </div>
                  <p className="text-xs text-muted-foreground truncate">
                    {roleLabel}
                  </p>
                </div>
                <ChevronUp
                  className={cn(
                    "h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform duration-200",
                    open && "rotate-180",
                  )}
                />
              </>
            )}
          </button>
        </PopoverTrigger>
        <PopoverContent
          side="top"
          align={collapsed ? "center" : "start"}
          sideOffset={8}
          className="w-52 p-1.5 rounded-xl shadow-lg border-border"
        >
          {collapsed && (
            <>
              <div className="px-2.5 py-2 mb-1">
                <p className="text-sm font-medium text-foreground">
                  {displayName}
                </p>
                <p className="text-xs text-muted-foreground">{roleLabel}</p>
              </div>
              <div className="h-px bg-border mx-1 mb-1" />
            </>
          )}
          <button
            type="button"
            onClick={handleProfile}
            className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-foreground hover:bg-muted hover:text-foreground transition-colors duration-150"
          >
            <User className="h-4 w-4" />
            View Profile
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-destructive hover:bg-destructive/5 transition-colors duration-150"
          >
            <LogOut className="h-4 w-4" />
            Sign Out
          </button>
        </PopoverContent>
      </Popover>
    </div>
  );
}
