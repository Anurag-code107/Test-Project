// Copied from contracts: endpoints/profile-phone.yaml
// Self-service mobile-number change, OTP-synced to the XTRM payout profile.

export interface InitiatePhoneUpdateRequest {
  /** National mobile number, digits only (no country code). */
  phone: string;
  /** 2-letter uppercase ISO country of the mobile. */
  phoneCountryIso2: string;
}

export interface ConfirmPhoneUpdateRequest extends InitiatePhoneUpdateRequest {
  otp: string;
}

/** On initiate for an enrolled payee → otpRequired=true (null values); otherwise the applied number. */
export interface PhoneUpdateResponse {
  otpRequired: boolean;
  phone: string | null;
  phoneCountryIso2: string | null;
}
