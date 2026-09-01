import type { HomeDashboardTemplate } from "@/types/home-dashboard.types";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  expiresIn: number;
  user: AuthUser;
  enabledFeatures: string[];
}

export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  permissions: string[];
  clientRoleId: string | null;
  clientRoleName: string | null;
  organizationId: string | null;
  clientId: string | null;
  clientName: string | null;
  partnerCompanyId: string | null;
  partnerCompanyName: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  homeDashboardTemplate?: HomeDashboardTemplate | null;
}
