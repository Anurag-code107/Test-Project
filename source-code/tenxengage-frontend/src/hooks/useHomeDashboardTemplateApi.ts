import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getHomeDashboardTemplates,
  getHomeDashboardWidgets,
  getHomeDashboardLayouts,
  assignHomeDashboardTemplate,
  clearHomeDashboardTemplate,
} from "@/services/home-dashboard-template.service";

export function useHomeDashboardTemplates(roleType?: "INTERNAL" | "EXTERNAL") {
  return useQuery({
    queryKey: ["home-dashboard-templates", roleType ?? "all"],
    queryFn: () => getHomeDashboardTemplates(roleType),
    staleTime: 5 * 60 * 1000,
  });
}

export function useHomeDashboardWidgets() {
  return useQuery({
    queryKey: ["home-dashboard-widgets"],
    queryFn: getHomeDashboardWidgets,
    staleTime: 60 * 60 * 1000,
  });
}

export function useHomeDashboardLayouts() {
  return useQuery({
    queryKey: ["home-dashboard-layouts"],
    queryFn: getHomeDashboardLayouts,
    staleTime: 60 * 60 * 1000,
  });
}

export function useAssignHomeDashboardTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      roleId,
      templateId,
    }: {
      roleId: string;
      templateId: string;
    }) => assignHomeDashboardTemplate(roleId, templateId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

export function useClearHomeDashboardTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleId: string) => clearHomeDashboardTemplate(roleId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}
