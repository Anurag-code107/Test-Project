import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { GuardedNavLink } from "@/components/layout/sidebars/GuardedNavLink";
import { SidebarTooltip } from "@/components/layout/sidebars/SidebarTooltip";

interface SidebarNavItemProps {
  to: string;
  label: string;
  icon: LucideIcon;
  collapsed?: boolean;
}

export function SidebarNavItem({
  to,
  label,
  icon: Icon,
  collapsed,
}: SidebarNavItemProps) {
  return (
    <SidebarTooltip label={label} enabled={!!collapsed}>
      <GuardedNavLink
        to={to}
        className={({ isActive }) =>
          cn(
            "group relative flex items-center gap-3 rounded-lg text-sm transition-colors duration-150 w-full",
            collapsed ? "justify-center px-2 py-2.5" : "pl-3 pr-3 py-2",
            isActive
              ? "text-primary font-medium bg-primary/5"
              : "text-muted-foreground hover:text-foreground hover:bg-muted",
          )
        }
      >
        {({ isActive }) => (
          <>
            {/* Active indicator bar */}
            <span
              className={cn(
                "absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full transition-[height,background-color] duration-200",
                isActive ? "h-4 bg-primary" : "h-0 bg-transparent",
              )}
            />
            <Icon
              className={cn(
                "shrink-0 transition-colors duration-150",
                collapsed ? "h-5 w-5" : "h-[18px] w-[18px]",
              )}
            />
            {!collapsed && <span className="truncate">{label}</span>}
          </>
        )}
      </GuardedNavLink>
    </SidebarTooltip>
  );
}
