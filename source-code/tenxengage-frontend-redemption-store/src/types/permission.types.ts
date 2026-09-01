export type PermissionType = "MODULE" | "ACTION";

export interface PermissionDef {
  id: string;
  permissionKey: string;
  displayName: string;
  description: string;
  category: string;
  permissionType: PermissionType;
  scope: string;
  sortOrder: number;
}

export interface ClientRole {
  id: string;
  clientId: string;
  name: string;
  description: string;
  baseRoleName: string | null;
  isSystem: boolean;
  isDefault: boolean;
  roleType: string;
  permissions: Record<string, boolean>;
  homeDashboardTemplateId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PermissionGrant {
  permissionKey: string;
  granted: boolean;
}

export interface CreateClientRoleRequest {
  name: string;
  description?: string;
  roleType: "INTERNAL" | "EXTERNAL";
  baseRoleName?: string;
  permissions?: Record<string, boolean>;
}

export interface CloneClientRoleRequest {
  name: string;
  description?: string;
}

export interface UpdateClientRoleRequest {
  name?: string;
  description?: string;
}

export interface UpdatePermissionsRequest {
  permissions: Record<string, boolean>;
}
