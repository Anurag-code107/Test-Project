import { useMemo, useCallback } from "react";
import { useAuth } from "@/hooks/useAuth";

/**
 * Hook that provides permission checking utilities based on the current user's
 * effective permissions (resolved by the backend across all 4 layers:
 * subscription tier → role → company → user).
 */
export function usePermissions() {
  const { user } = useAuth();

  const permissionSet = useMemo(
    () => new Set(user?.permissions ?? []),
    [user?.permissions],
  );

  /** Check if the user has a specific permission */
  const can = useCallback(
    (key: string) => permissionSet.has(key),
    [permissionSet],
  );

  /** Check if the user has ANY of the specified permissions */
  const canAny = useCallback(
    (...keys: string[]) => keys.some((k) => permissionSet.has(k)),
    [permissionSet],
  );

  /** Check if the user has ALL of the specified permissions */
  const canAll = useCallback(
    (...keys: string[]) => keys.every((k) => permissionSet.has(k)),
    [permissionSet],
  );

  return { can, canAny, canAll, permissions: permissionSet };
}
