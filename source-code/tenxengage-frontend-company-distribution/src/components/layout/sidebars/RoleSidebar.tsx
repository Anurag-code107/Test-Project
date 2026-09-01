import { useState, useEffect, useCallback } from "react";
import { useLocation } from "react-router-dom";
import { ChevronDown, PanelLeftClose, PanelLeft } from "lucide-react";
import { cn } from "@/lib/utils";
import { SidebarNavItem } from "@/components/layout/sidebars/SidebarNavItem";
import { GuardedNavLink } from "@/components/layout/sidebars/GuardedNavLink";
import { SidebarProfileMenu } from "@/components/layout/sidebars/SidebarProfileMenu";
import { SidebarTooltip } from "@/components/layout/sidebars/SidebarTooltip";
import { SidebarFlyout } from "@/components/layout/sidebars/SidebarFlyout";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";
import { useBrandingContext } from "@/contexts/BrandingContext";

/**
 * Derive the portal label shown under the logo from the user's effective
 * permissions. Keeps the labels meaningful for custom roles that a Client
 * Admin creates — anyone with incentive-management permissions gets
 * "Client Admin Portal" regardless of the role's name.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function derivePortalLabel(
  can: (key: string) => boolean,
  fallback: string,
): string {
  if (can("module.manage_incentives")) return "Client Admin Portal";
  if (can("action.activity.review") || can("action.activity.approve"))
    return "Approver Portal";
  if (can("module.rewards.balances") || can("module.rewards.claims"))
    return "Partner Portal";
  return fallback;
}

/* ------------------------------------------------------------------ */
/*  Config types                                                        */
/* ------------------------------------------------------------------ */

export interface NavItem {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  /** If set, item is hidden when the user lacks this permission */
  permissionKey?: string;
  /**
   * If set, item is shown only when the user holds AT LEAST ONE of these
   * permissions (OR). Combined with `permissionKey`/`featureKey` via AND.
   * Use for capability-gated items that more than one permission can unlock
   * (e.g. the storefront, which either redeem permission unlocks).
   */
  anyPermission?: string[];
  /**
   * If set, item is hidden when the tenant's subscription tier does not
   * include this feature. Evaluated against `useFeatures().has()` —
   * fail-closed for unknown keys.
   */
  featureKey?: string;
}

export interface NavSection {
  /** Section heading shown when expanded (e.g. "Insights", "Configuration") */
  heading?: string;
  items?: NavItem[];
  /** Collapsible group — renders with expand/collapse + flyout when collapsed */
  groups?: NavGroupConfig[];
}

export interface NavGroupConfig {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  /** Prefix(es) used to determine if the group is "active" */
  activePrefixes: string[];
  /**
   * If set, the ENTIRE group is hidden when the user lacks this permission —
   * even if some sub-items would otherwise be permitted. Used to make a module
   * permission (e.g. `module.redemption_store`) the master switch for the whole
   * group, so a user/company override that removes it hides the module outright.
   */
  permissionKey?: string;
  items: NavItem[];
}

export interface RoleSidebarConfig {
  /** Top-level section label shown under the logo (e.g. "Management", "Partner Portal") */
  topLabel: string;
  /** Flat nav items rendered at the top */
  primaryItems: NavItem[];
  /** Additional sections with headings and/or collapsible groups */
  sections?: NavSection[];
}

/* ------------------------------------------------------------------ */
/*  Collapsible nav group with animated height                         */
/* ------------------------------------------------------------------ */
function NavGroup({
  icon: Icon,
  label,
  isActive,
  children,
  defaultOpen,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  isActive: boolean;
  children: React.ReactNode;
  defaultOpen: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);

  // Sync open/closed state when the route changes (e.g. after a guided tour
  // opens this group then navigates away). Only fires when isActive flips.
  useEffect(() => {
    setOpen(isActive);
  }, [isActive]);

  return (
    <div>
      <button
        type="button"
        data-tour={`nav-group-${label.toLowerCase().replace(/\s+/g, "-")}`}
        onClick={() => setOpen(!open)}
        className={cn(
          "group relative flex items-center gap-3 pl-3 pr-3 py-2 rounded-lg text-sm transition-colors duration-150 w-full",
          isActive
            ? "text-primary font-medium bg-primary/5"
            : "text-muted-foreground hover:text-foreground hover:bg-muted",
        )}
      >
        {/* Active indicator bar */}
        <span
          className={cn(
            "absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full transition-[height,background-color] duration-200",
            isActive ? "h-4 bg-primary" : "h-0 bg-transparent",
          )}
        />
        <Icon className="h-[18px] w-[18px] shrink-0 transition-colors duration-150" />
        <span className="flex-1 text-left truncate">{label}</span>
        <ChevronDown
          className={cn(
            "h-3.5 w-3.5 text-muted-foreground transition-transform duration-200",
            open && "rotate-180",
          )}
        />
      </button>
      {/* Animated expand/collapse via grid-template-rows */}
      <div
        className="grid transition-[grid-template-rows] duration-200 ease-out"
        style={{ gridTemplateRows: open ? "1fr" : "0fr" }}
      >
        <div className="overflow-hidden">
          <div className="pl-5 mt-0.5 space-y-0.5">{children}</div>
        </div>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Sub-item used inside NavGroup                                      */
/* ------------------------------------------------------------------ */
function NavSubItem({ to, icon: Icon, label }: NavItem) {
  return (
    <GuardedNavLink
      to={to}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-2.5 pl-3 pr-3 py-1.5 rounded-lg text-sm transition-colors duration-150 w-full",
          isActive
            ? "text-primary font-medium bg-primary/5"
            : "text-muted-foreground hover:text-foreground hover:bg-muted",
        )
      }
    >
      <Icon className="h-4 w-4 shrink-0" />
      <span className="truncate">{label}</span>
    </GuardedNavLink>
  );
}

/* ------------------------------------------------------------------ */
/*  RoleSidebar — shared across all roles                              */
/* ------------------------------------------------------------------ */
export function RoleSidebar({ config }: { config: RoleSidebarConfig }) {
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const { can } = usePermissions();
  const { has } = useFeatures();
  const { logoSrc } = useBrandingContext();
  const topLabel = derivePortalLabel(can, config.topLabel);

  /** Check if any prefix in the list matches the current pathname */
  const isGroupActive = (prefixes: string[]) =>
    prefixes.some((p) => location.pathname.startsWith(p));

  /**
   * Filter out items the user doesn't have permission to see, OR whose
   * tenant tier doesn't include the gated feature. Both gates apply when
   * both keys are set.
   */
  const filterByPermission = useCallback(
    (items: NavItem[]) =>
      items.filter(
        (item) =>
          (!item.permissionKey || can(item.permissionKey)) &&
          (!item.anyPermission || item.anyPermission.some((k) => can(k))) &&
          (!item.featureKey || has(item.featureKey)),
      ),
    [can, has],
  );

  return (
    <aside
      className={cn(
        "flex h-full shrink-0 flex-col border-r border-border bg-background transition-[width] duration-200 ease-out overflow-hidden",
        collapsed ? "w-[3.5rem]" : "w-[240px]",
      )}
    >
      {/* Header */}
      <div className="shrink-0 p-3">
        {collapsed ? (
          <div className="flex flex-col items-center gap-1">
            <SidebarTooltip label="Expand sidebar" enabled>
              <button
                type="button"
                onClick={() => setCollapsed(false)}
                className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors duration-150"
              >
                <PanelLeft className="h-4.5 w-4.5" />
              </button>
            </SidebarTooltip>
          </div>
        ) : (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5 min-w-0">
              <img
                key={logoSrc}
                src={logoSrc}
                alt="Logo"
                className="block w-[155px] h-auto min-h-7 max-h-16 object-contain object-left shrink-0 brand-logo-pop"
              />
            </div>
            <button
              type="button"
              onClick={() => setCollapsed(true)}
              className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors duration-150"
              title="Collapse sidebar"
            >
              <PanelLeftClose className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {/* Top section label */}
      {!collapsed && (
        <div className="px-4 pt-2 pb-1">
          <p className="text-xs font-semibold tracking-[0.08em] uppercase text-muted-foreground">
            {topLabel}
          </p>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2 pb-2 space-y-0.5">
        {/* Primary flat items */}
        {filterByPermission(config.primaryItems).map((item) => (
          <SidebarNavItem
            key={item.to}
            to={item.to}
            label={item.label}
            icon={item.icon as import("lucide-react").LucideIcon}
            collapsed={collapsed}
          />
        ))}

        {/* Additional sections */}
        {config.sections?.map((section) =>
          section.groups?.map((group) => {
            // Group-level gate: a missing module permission hides the whole group.
            if (group.permissionKey && !can(group.permissionKey)) return null;
            const filteredGroupItems = filterByPermission(group.items);
            if (filteredGroupItems.length === 0) return null;
            const active = isGroupActive(group.activePrefixes);

            return collapsed ? (
              <SidebarFlyout
                key={group.label}
                icon={group.icon}
                label={group.label}
                items={filteredGroupItems}
                isGroupActive={active}
              />
            ) : (
              <NavGroup
                key={group.label}
                icon={group.icon}
                label={group.label}
                isActive={active}
                defaultOpen={active}
              >
                {filteredGroupItems.map((sub) => (
                  <NavSubItem key={sub.to} {...sub} />
                ))}
              </NavGroup>
            );
          }),
        )}
      </nav>

      <SidebarProfileMenu collapsed={collapsed} />
    </aside>
  );
}
