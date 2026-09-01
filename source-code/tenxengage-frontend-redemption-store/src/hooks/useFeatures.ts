import { useMemo, useCallback } from "react";
import { useAuth } from "@/hooks/useAuth";

/**
 * Hook that provides feature-flag checking utilities based on the tenant's
 * effective enabled features (resolved by the backend from the subscription
 * tier and any per-client overrides, returned on /auth/login and /auth/me).
 *
 * Fail-closed: unknown / mistyped keys return `false` so a typo cannot
 * silently expose a tier-gated feature.
 */
export function useFeatures() {
  const { enabledFeatures } = useAuth();

  const featureSet = useMemo(
    () => new Set(enabledFeatures),
    [enabledFeatures],
  );

  /** Check if the tenant has a specific feature enabled */
  const has = useCallback(
    (key: string) => featureSet.has(key),
    [featureSet],
  );

  /** Check if the tenant has ANY of the specified features enabled */
  const hasAny = useCallback(
    (...keys: string[]) => keys.some((k) => featureSet.has(k)),
    [featureSet],
  );

  /** Check if the tenant has ALL of the specified features enabled */
  const hasAll = useCallback(
    (...keys: string[]) => keys.every((k) => featureSet.has(k)),
    [featureSet],
  );

  return { has, hasAny, hasAll, features: featureSet };
}
