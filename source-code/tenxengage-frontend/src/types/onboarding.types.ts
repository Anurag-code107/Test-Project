export interface OnboardingStatusResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  currentStep: number; // 0-5
  completed: boolean;
  region: string | null;
  complianceConfig: RegionalComplianceConfigResponse | null;
}

export interface RegionalComplianceConfigResponse {
  regionCode: string;
  regionName: string;
  privacyNoticeRequired: boolean;
  termsOfServiceRequired: boolean;
  antiBriberyRequired: boolean;
  consentAiVisible: boolean;
  consentMarketingVisible: boolean;
  consentAnalyticsVisible: boolean;
  cookieNoticeVisible: boolean;
}

export interface LegalPolicyResponse {
  id: string;
  policyType: string;
  version: string;
  title: string;
  contentUrl: string | null;
  summary: string | null;
  effectiveDate: string;
  accepted: boolean;
}

export interface ConsentPreferenceResponse {
  consentType: string;
  granted: boolean;
  lastUpdated: string | null;
}

export interface SetPasswordRequest {
  token: string;
  password: string;
}

export interface CompleteProfileRequest {
  token: string;
  firstName: string;
  lastName: string;
  phone: string;
  countryCode: string;
}

export interface AcceptPoliciesRequest {
  token: string;
  policyIds: string[];
}

export interface SetConsentRequest {
  token: string;
  consents: Record<string, boolean>;
}
