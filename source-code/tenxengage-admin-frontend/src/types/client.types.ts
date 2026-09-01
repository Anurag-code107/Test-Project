export type ClientStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED" | "TRIAL";
export type SubscriptionTier = "STARTER" | "PROFESSIONAL" | "ENTERPRISE";

export interface ClientResponse {
  id: string;
  name: string;
  subdomain: string;
  logoUrl: string | null;
  status: ClientStatus;
  subscriptionTier: SubscriptionTier;
  createdAt: string;
  updatedAt: string;
}

export interface CreateClientRequest {
  name: string;
  subdomain: string;
  subscriptionTier: SubscriptionTier;
  logoUrl?: string;
  status?: ClientStatus;
}

export interface UpdateClientRequest {
  name?: string;
  logoUrl?: string;
  status?: ClientStatus;
  subscriptionTier?: SubscriptionTier;
}

export interface ClientStatsResponse {
  totalClients: number;
  countByStatus: Record<string, number>;
  countByTier: Record<string, number>;
}

export interface FeatureFlagResponse {
  id: string;
  featureKey: string;
  description: string;
  category: string;
  starterEnabled: boolean;
  professionalEnabled: boolean;
  enterpriseEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFeatureFlagRequest {
  featureKey: string;
  description?: string;
  starterEnabled?: boolean;
  professionalEnabled?: boolean;
  enterpriseEnabled?: boolean;
}

export interface UpdateFeatureFlagRequest {
  description?: string;
  starterEnabled?: boolean;
  professionalEnabled?: boolean;
  enterpriseEnabled?: boolean;
}

export interface ClientFeatureOverrideResponse {
  featureFlagId: string;
  featureKey: string;
  enabled: boolean;
}

export interface SetFeatureOverrideRequest {
  featureFlagId: string;
  enabled: boolean;
}
