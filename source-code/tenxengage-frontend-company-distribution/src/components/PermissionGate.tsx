import type { ReactNode } from "react";
import { usePermissions } from "@/hooks/usePermissions";

interface PermissionGateProps {
  /** Single permission key to check */
  permission?: string;
  /** Check if user has ANY of these permissions */
  any?: string[];
  /** Check if user has ALL of these permissions */
  all?: string[];
  /** Content to render when permission is granted */
  children: ReactNode;
  /** Content to render when permission is denied (defaults to nothing) */
  fallback?: ReactNode;
}

/**
 * Conditionally renders children based on the current user's effective permissions.
 *
 * Usage:
 *   <PermissionGate permission="action.incentive.create">
 *     <Button>Create Incentive</Button>
 *   </PermissionGate>
 *
 *   <PermissionGate any={["action.claim.edit", "action.claim.approve"]}>
 *     <EditPanel />
 *   </PermissionGate>
 */
export function PermissionGate({
  permission,
  any,
  all,
  children,
  fallback = null,
}: PermissionGateProps) {
  const { can, canAny, canAll } = usePermissions();

  let allowed = true;
  if (permission) {
    allowed = can(permission);
  } else if (any) {
    allowed = canAny(...any);
  } else if (all) {
    allowed = canAll(...all);
  }

  return allowed ? <>{children}</> : <>{fallback}</>;
}
