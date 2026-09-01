import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";

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

  if (permission && !can(permission)) {
    return <Navigate to="/" replace />;
  }

  if (anyPermission && !canAny(...anyPermission)) {
    return <Navigate to="/" replace />;
  }

  if (feature && !has(feature)) {
    return <Navigate to="/" replace />;
  }

  if (anyFeature && !hasAny(...anyFeature)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
