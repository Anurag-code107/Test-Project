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
  Package,
  Tag,
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
    {
      to: "/redemption-store",
      icon: ShoppingBag,
      label: "Redemption Store",
      permissionKey: "module.redemption_store",
    },
    {
      to: "/admin/redemption-catalog",
      icon: Package,
      label: "Global Catalog",
      permissionKey: "action.redemption.catalog.manage",
    },
  ],
  sections: [
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
          activePrefixes: ["/settings", "/settings/redemption"],
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
            {
              to: "/settings/redemption/catalog",
              icon: Tag,
              label: "Redemption Catalog",
              permissionKey: "action.redemption.configure",
            },
          ],
        },
      ],
    },
  ],
};
