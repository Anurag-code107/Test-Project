import type { ReactNode } from "react";
import { useFeatures } from "@/hooks/useFeatures";

interface FeatureGateProps {
  /** Single feature key to check */
  feature?: string;
  /** Check if tenant has ANY of these features */
  any?: string[];
  /** Check if tenant has ALL of these features */
  all?: string[];
  /** Content to render when the feature is enabled */
  children: ReactNode;
  /** Content to render when the feature is disabled (defaults to nothing) */
  fallback?: ReactNode;
}

/**
 * Conditionally renders children based on the tenant's effective enabled
 * features. Mirrors `PermissionGate` and is intentionally orthogonal to it —
 * permissions answer "is this user allowed?", features answer "does this
 * tenant's subscription tier include this?".
 *
 * Usage:
 *   <FeatureGate feature="audit_log">
 *     <AuditLogPanel />
 *   </FeatureGate>
 */
export function FeatureGate({
  feature,
  any,
  all,
  children,
  fallback = null,
}: FeatureGateProps) {
  const { has, hasAny, hasAll } = useFeatures();

  let allowed = true;
  if (feature) {
    allowed = has(feature);
  } else if (any) {
    allowed = hasAny(...any);
  } else if (all) {
    allowed = hasAll(...all);
  }

  return allowed ? <>{children}</> : <>{fallback}</>;
}
