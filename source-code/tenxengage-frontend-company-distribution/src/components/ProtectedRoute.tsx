import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";
import AccessDenied from "@/components/AccessDenied";

interface ProtectedRouteProps {
  permission?: string;
  anyPermission?: string[];
  feature?: string;
  anyFeature?: string[];
}

function ProtectedRoute({
  permission,
  anyPermission,
  feature,
  anyFeature,
}: ProtectedRouteProps) {
  const { isAuthenticated } = useAuth();
  const { can, canAny } = usePermissions();
  const { has, hasAny } = useFeatures();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Authenticated but lacking access → show an explanation instead of a silent redirect home.
  if (permission && !can(permission)) {
    return <AccessDenied />;
  }

  if (anyPermission && !canAny(...anyPermission)) {
    return <AccessDenied />;
  }

  if (feature && !has(feature)) {
    return <AccessDenied />;
  }

  if (anyFeature && !hasAny(...anyFeature)) {
    return <AccessDenied />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
