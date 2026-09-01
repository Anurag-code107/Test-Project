// Copied from contracts: endpoints/redemption-payout.yaml + enums.md
// (XTRM payout & enrollment enhancement to F-03)

export type XtrmEnrollmentStatus = "NOT_ENROLLED" | "ENROLLED" | "FAILED";

export type RedemptionPayoutMethod = "ANYPAY" | "BANK" | "CARD";

/** Withdrawal destination kind — a linked bank or a linked card. */
export type WithdrawalDestinationType = "BANK" | "CARD";

/** Safe view of the user's payout profile — never includes the PAT, bank id, card token, or numbers.
 *  Includes the user's own saved payout address (self-only endpoint) so the form can pre-fill. */
export interface RedemptionProfileResponse {
  enrollmentStatus: XtrmEnrollmentStatus;
  payoutMethod: RedemptionPayoutMethod;
  bankLinked: boolean;
  linkedBankLabel: string | null;
  cardLinked: boolean;
  linkedCardLabel: string | null;
  identityLevel: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  postalCode: string | null;
  countryIso2: string | null;
}

export interface SaveRedemptionAddressRequest {
  addressLine1: string;
  addressLine2?: string;
  city?: string;
  region?: string;
  postalCode?: string;
  countryIso2: string;
}

export interface SetPayoutMethodRequest {
  payoutMethod: RedemptionPayoutMethod;
}

/** One linked bank in the payout profile. `id` is OUR bank id (used for remove/set-default) — never the XTRM reference. */
export interface LinkedBank {
  id: string;
  label: string;
  currency: string;
  isDefault: boolean;
}

/** Select which linked bank is the default payout destination. `bankId` = our bank id from the list. */
export interface SetDefaultBankRequest {
  bankId: string;
}

/** A user's XTRM digital wallet (view-only). `balance` is formatted client-side with the currency symbol. */
export interface DigitalWallet {
  id: string;
  name: string;
  currency: string;
  balance: number;
}

/** Pass-through to XTRM — never persisted client- or server-side beyond the returned reference. */
export interface LinkBankAccountRequest {
  contactName: string;
  contactPhone: string;
  accountNumber: string;
  routingNumber: string;
  swiftBic?: string;
  institutionName: string;
  addressLine1: string;
  addressLine2?: string;
  city?: string;
  region?: string;
  postalCode?: string;
  countryIso2: string;
  withdrawType: string;
}

/** One linked card in the payout profile. `id` is OUR card id (remove/set-default) — never the XTRM token. */
export interface LinkedCard {
  id: string;
  label: string;
  cardType: string;
  status: string;
  isDefault: boolean;
}

/**
 * ⚠️ PCI — pass-through to XTRM LinkCard, NEVER persisted client- or server-side beyond the returned token.
 * The raw cardNumber/cvv/expiry are sent once and cleared from state after submit; never store or log them.
 */
export interface AddCardRequest {
  cardNumber: string;
  expMonth: string;
  expYear: string;
  cvv: string;
  cardType: string;
  nameOnCard: string;
  firstName: string;
  lastName: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  region: string;
  postalCode: string;
  countryIso2: string;
}

/** Select which linked card is the default payout destination. `cardId` = our card id from the list. */
export interface SetDefaultCardRequest {
  cardId: string;
}

/** Step 1 of a wallet withdrawal (no OTP). `destinationId` = our bank/card id, never the XTRM reference. */
export interface InitiateWithdrawalRequest {
  amount: number;
  destinationType: WithdrawalDestinationType;
  destinationId: string;
}

/** Step 2 — resends the initiate values plus the one-time password. */
export interface ConfirmWithdrawalRequest extends InitiateWithdrawalRequest {
  otp: string;
}

/** Result of a withdrawal step. On initiate, otpRequired=true + null amounts; on confirm, the executed amounts. */
export interface WithdrawalResult {
  otpRequired: boolean;
  transactionId: string | null;
  status: string | null;
  amountGross: number | null;
  fee: number | null;
  amountNet: number | null;
  currency: string | null;
  destinationLabel: string | null;
}

/** One row in the user's withdrawal history (newest first). */
export interface WithdrawalHistoryItem {
  id: string;
  amountGross: number;
  fee: number;
  amountNet: number;
  currency: string;
  destinationType: WithdrawalDestinationType;
  destinationLabel: string | null;
  status: string;
  createdAt: string;
}

/** A page of withdrawal history — mirrors the backend PaginatedResponse (server-paginated, newest first). */
export interface WithdrawalHistoryPage {
  data: WithdrawalHistoryItem[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
