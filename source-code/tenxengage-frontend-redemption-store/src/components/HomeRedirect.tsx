import { Navigate } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";

const HOME_ROUTES = [
  { permission: "module.home", path: "/home" },
  { permission: "module.activity_review", path: "/activity-review" },
  { permission: "module.incentives.sales", path: "/incentives" },
  { permission: "module.rewards.claims", path: "/rewards" },
  { permission: "module.redemption_store", path: "/redemption-store" },
  { permission: "module.settings.profile", path: "/settings/profile" },
] as const;

function HomeRedirect() {
  const { isAuthenticated } = useAuth();
  const { can } = usePermissions();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  for (const route of HOME_ROUTES) {
    if (can(route.permission)) {
      return <Navigate to={route.path} replace />;
    }
  }

  return <Navigate to="/settings/profile" replace />;
}

export default HomeRedirect;
