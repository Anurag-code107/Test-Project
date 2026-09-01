import {
  Home,
  Target,
  FileText,
  Search,
  Settings,
  MessageSquare,
  LayoutGrid,
  BarChart3,
  FileBarChart,
  History,
  UserCircle,
  Users,
  Cog,
  CheckCircle,
  Gift,
  UsersRound,
  ShoppingBag,
  ClipboardList,
  Building2,
  LineChart,
  Timer,
  Send,
  HandCoins,
  Award,
} from "lucide-react";
import type { RoleSidebarConfig } from "./RoleSidebar";

export const sidebarConfig: RoleSidebarConfig = {
  topLabel: "tenXengage",
  primaryItems: [
    { to: "/home", icon: Home, label: "Home", permissionKey: "module.home" },
    {
      to: "/builder",
      icon: MessageSquare,
      label: "Incentive Builder",
      permissionKey: "module.incentive_builder",
    },
    {
      to: "/manage-incentives",
      icon: LayoutGrid,
      label: "Manage Incentives",
      permissionKey: "module.manage_incentives",
    },
    {
      to: "/claims",
      icon: FileText,
      label: "Manage Claims",
      permissionKey: "module.manage_claims",
    },
    {
      to: "/incentives",
      icon: Target,
      label: "Incentives",
      permissionKey: "module.incentives.sales",
    },
    {
      to: "/rewards",
      icon: Gift,
      label: "Manage Rewards",
      permissionKey: "module.rewards.claims",
    },
    {
      to: "/deal-qualifier",
      icon: Search,
      label: "Deal Qualifier",
      permissionKey: "module.deal_qualifier",
      featureKey: "deal_qualifier",
    },
    {
      to: "/activity-review",
      icon: CheckCircle,
      label: "Activity Review",
      permissionKey: "module.activity_review",
    },
  ],
  sections: [
    {
      // CR-03: consolidated Redemption nav — one collapsible parent. No `heading`,
      // so it renders as a top-level group with sub-items (the section renderer only
      // draws groups). Role-awareness is automatic via each sub-item's permission gate;
      // the existing empty-group guard hides the parent when no sub-items are permitted.
      groups: [
        {
          icon: ShoppingBag,
          label: "Redemption",
          activePrefixes: ["/redemption", "/settings/redemption"],
          // Whole-module gate: removing `module.redemption_store` (user- or company-level
          // override) hides the entire Redemption tab, not just the storefront sub-item.
          permissionKey: "module.redemption_store",
          items: [
            {
              to: "/redemption-store",
              icon: ShoppingBag,
              label: "Redemption Store",
              // Requires BOTH gates:
              // - `module.redemption_store` so disabling the module (company- or user-level override)
              //   actually hides the storefront — matches the BE, which gates the store endpoints
              //   (WalletController / RedemptionCatalogController) on this same key.
              // - action.redemption.redeem so a Client Admin who holds the module umbrella but
              //   cannot redeem still does not see the storefront (CR-04). A Partner Admin holds
              //   this key too (V52); the company variant was retired with company redemption.
              permissionKey: "module.redemption_store",
              anyPermission: ["action.redemption.redeem"],
            },
            {
              // Sits directly under the storefront it belongs to, mirroring the
              // Distribution Store / Distribution History pairing below.
              to: "/redemption/history",
              icon: History,
              label: "Redemption History",
              permissionKey: "action.redemption.view_history",
            },
            {
              // Partner admin only. The company's own store — a different wallet from the personal
              // one above, which is why it is a separate page rather than a tab inside it.
              to: "/redemption/distribution",
              icon: Send,
              label: "Distribution Store",
              permissionKey: "action.redemption.distribute",
              featureKey: "company_distribution",
            },
            {
              to: "/redemption/distribution/history",
              icon: HandCoins,
              label: "Distribution History",
              permissionKey: "action.redemption.view_distribution_history",
              featureKey: "company_distribution",
            },
            {
              // Partner seller only — rewards received FROM company admins. Distributions are excluded
              // from Redemption History above, so this is the only place a seller sees them.
              to: "/redemption/awards",
              icon: Award,
              label: "Company Awards",
              permissionKey: "action.redemption.view_company_awards",
              featureKey: "company_distribution",
            },
            {
              to: "/redemption/admin/history",
              icon: Building2,
              label: "All Redemptions",
              permissionKey: "action.redemption.view_all_history",
            },
            {
              to: "/redemption/approval-queue",
              icon: ClipboardList,
              label: "Approval Queue",
              permissionKey: "action.redemption.approve",
            },
            {
              to: "/redemption/admin/analytics",
              icon: LineChart,
              label: "Analytics",
              permissionKey: "action.redemption.view_analytics",
            },
            {
              to: "/redemption/breakage",
              icon: FileBarChart,
              label: "Breakage",
              permissionKey: "action.redemption.expiration.view_breakage",
              featureKey: "reward_balance_expiration",
            },
            {
              to: "/settings/redemption/balance-expiration",
              icon: Timer,
              label: "Balance Expiration",
              permissionKey: "action.redemption.expiration.configure",
              featureKey: "reward_balance_expiration",
            },
          ],
        },
      ],
    },
    {
      heading: "Insights",
      groups: [
        {
          icon: BarChart3,
          label: "Reporting",
          activePrefixes: ["/reporting", "/activity-log"],
          items: [
            {
              to: "/reporting",
              icon: FileBarChart,
              label: "Report Builder",
              permissionKey: "module.reporting",
            },
            {
              to: "/activity-log",
              icon: History,
              label: "Activity Log",
              permissionKey: "module.activity_log",
              featureKey: "audit_log",
            },
          ],
        },
      ],
    },
    {
      heading: "Configuration",
      groups: [
        {
          icon: Settings,
          label: "Settings",
          activePrefixes: ["/settings"],
          items: [
            {
              to: "/settings/profile",
              icon: UserCircle,
              label: "My Profile",
              permissionKey: "module.settings.profile",
            },
            {
              to: "/settings/team",
              icon: UsersRound,
              label: "Team Management",
              permissionKey: "module.settings.team",
            },
            {
              to: "/settings/users",
              icon: Users,
              label: "User Settings",
              permissionKey: "module.settings.users",
            },
            {
              to: "/settings/platform",
              icon: Cog,
              label: "Platform Settings",
              permissionKey: "module.settings.tenx",
            },
          ],
        },
      ],
    },
  ],
};
