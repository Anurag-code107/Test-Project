import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getAllPermissions,
  getClientRoles,
  getClientRole,
  createClientRole,
  cloneClientRole,
  updateClientRole,
  updateRolePermissions,
  deleteClientRole,
  getCompanyOverrides,
  updateCompanyOverrides,
  getUserOverrides,
  updateUserOverrides,
} from "@/services/permission.service";
import type {
  CreateClientRoleRequest,
  CloneClientRoleRequest,
  UpdateClientRoleRequest,
  UpdatePermissionsRequest,
} from "@/types/permission.types";

// ── Permission catalog ────────────────────────────────────────────────────

export function usePermissionCatalog() {
  return useQuery({
    queryKey: ["permissions", "catalog"],
    queryFn: getAllPermissions,
    staleTime: 10 * 60 * 1000, // 10 min — catalog rarely changes
  });
}

// ── Client Roles ──────────────────────────────────────────────────────────

export function useClientRoles() {
  return useQuery({
    queryKey: ["client-roles"],
    queryFn: getClientRoles,
  });
}

export function useClientRole(id: string | undefined) {
  return useQuery({
    queryKey: ["client-roles", id],
    queryFn: () => getClientRole(id!),
    enabled: !!id,
  });
}

export function useCreateClientRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateClientRoleRequest) => createClientRole(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

export function useCloneClientRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: CloneClientRoleRequest }) =>
      cloneClientRole(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

export function useUpdateClientRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateClientRoleRequest }) =>
      updateClientRole(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

export function useUpdateRolePermissions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdatePermissionsRequest;
    }) => updateRolePermissions(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

export function useDeleteClientRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteClientRole(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["client-roles"] }),
  });
}

// ── Company Permission Overrides ──────────────────────────────────────────

export function useCompanyOverrides(companyId: string | undefined) {
  return useQuery({
    queryKey: ["company-permissions", companyId],
    queryFn: () => getCompanyOverrides(companyId!),
    enabled: !!companyId,
  });
}

export function useUpdateCompanyOverrides() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      companyId,
      data,
    }: {
      companyId: string;
      data: UpdatePermissionsRequest;
    }) => updateCompanyOverrides(companyId, data),
    onSuccess: (_, variables) =>
      qc.invalidateQueries({
        queryKey: ["company-permissions", variables.companyId],
      }),
  });
}

// ── User Permission Overrides ─────────────────────────────────────────────

export function useUserOverrides(
  companyId: string | undefined,
  userId: string | undefined,
) {
  return useQuery({
    queryKey: ["user-permissions", companyId, userId],
    queryFn: () => getUserOverrides(companyId!, userId!),
    enabled: !!companyId && !!userId,
  });
}

export function useUpdateUserOverrides() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      companyId,
      userId,
      data,
    }: {
      companyId: string;
      userId: string;
      data: UpdatePermissionsRequest;
    }) => updateUserOverrides(companyId, userId, data),
    onSuccess: (_, variables) =>
      qc.invalidateQueries({
        queryKey: ["user-permissions", variables.companyId, variables.userId],
      }),
  });
}
