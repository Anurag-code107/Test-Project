import { useAuth } from "@/hooks/useAuth";
import type { HomeDashboardTemplate } from "@/types/home-dashboard.types";

export function useHomeDashboardTemplate(): HomeDashboardTemplate | null {
  const { user } = useAuth();
  return user?.homeDashboardTemplate ?? null;
}
