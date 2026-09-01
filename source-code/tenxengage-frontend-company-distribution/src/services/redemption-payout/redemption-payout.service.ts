// Adapted from: src/services/redemption-flow.service.ts (TanStack Query pattern)
import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  RedemptionProfileResponse,
  SaveRedemptionAddressRequest,
  SetPayoutMethodRequest,
  LinkBankAccountRequest,
  LinkedBank,
  DigitalWallet,
  LinkedCard,
  AddCardRequest,
  InitiateWithdrawalRequest,
  ConfirmWithdrawalRequest,
  WithdrawalResult,
  WithdrawalHistoryPage,
} from "@/types/redemption-payout/redemption-payout.types";

const BASE = "/redemption/profile";

export async function getRedemptionProfile(): Promise<RedemptionProfileResponse> {
  const response = await api.get<ApiResponse<RedemptionProfileResponse>>(BASE);
  return response.data.data;
}

export async function saveRedemptionAddress(
  req: SaveRedemptionAddressRequest,
): Promise<RedemptionProfileResponse> {
  const response = await api.put<ApiResponse<RedemptionProfileResponse>>(`${BASE}/address`, req);
  return response.data.data;
}

export async function setPayoutMethod(
  req: SetPayoutMethodRequest,
): Promise<RedemptionProfileResponse> {
  const response = await api.put<ApiResponse<RedemptionProfileResponse>>(`${BASE}/payout-method`, req);
  return response.data.data;
}

export async function linkBankAccount(
  req: LinkBankAccountRequest,
): Promise<RedemptionProfileResponse> {
  const response = await api.post<ApiResponse<RedemptionProfileResponse>>(`${BASE}/bank-account`, req);
  return response.data.data;
}

/** List the user's linked banks (fast local read on the server — no XTRM round-trip). */
export async function listBanks(): Promise<LinkedBank[]> {
  const response = await api.get<ApiResponse<LinkedBank[]>>(`${BASE}/banks`);
  return response.data.data;
}

/** Remove a specific linked bank by our bank id (not the XTRM reference). */
export async function removeBankAccount(bankId: string): Promise<RedemptionProfileResponse> {
  const response = await api.delete<ApiResponse<RedemptionProfileResponse>>(`${BASE}/banks/${bankId}`);
  return response.data.data;
}

/** Set which linked bank is the default (destination for the BANK payout rail). */
export async function setDefaultBank(bankId: string): Promise<RedemptionProfileResponse> {
  const response = await api.put<ApiResponse<RedemptionProfileResponse>>(`${BASE}/banks/default`, { bankId });
  return response.data.data;
}

/** List the user's XTRM digital wallets (view-only, every currency). Requires enrollment server-side. */
export async function listWallets(): Promise<DigitalWallet[]> {
  const response = await api.get<ApiResponse<DigitalWallet[]>>(`${BASE}/wallets`);
  return response.data.data;
}

/** List the user's linked cards (fast local read on the server — no XTRM round-trip). */
export async function listCards(): Promise<LinkedCard[]> {
  const response = await api.get<ApiResponse<LinkedCard[]>>(`${BASE}/cards`);
  return response.data.data;
}

/**
 * Link a card. ⚠️ PCI: the raw card fields are sent once to the server (which forwards to XTRM LinkCard)
 * and are never persisted anywhere — only the returned token + masked last-4 are stored server-side.
 */
export async function addCard(req: AddCardRequest): Promise<RedemptionProfileResponse> {
  const response = await api.post<ApiResponse<RedemptionProfileResponse>>(`${BASE}/card`, req);
  return response.data.data;
}

/** Remove a specific linked card by our card id (not the XTRM token). */
export async function removeCard(cardId: string): Promise<RedemptionProfileResponse> {
  const response = await api.delete<ApiResponse<RedemptionProfileResponse>>(`${BASE}/cards/${cardId}`);
  return response.data.data;
}

/** Set which linked card is the default (destination for the CARD payout rail). */
export async function setDefaultCard(cardId: string): Promise<RedemptionProfileResponse> {
  const response = await api.put<ApiResponse<RedemptionProfileResponse>>(`${BASE}/cards/default`, { cardId });
  return response.data.data;
}

/** Step 1 of a wallet withdrawal — sends the OTP (no transaction yet). */
export async function initiateWithdrawal(req: InitiateWithdrawalRequest): Promise<WithdrawalResult> {
  const response = await api.post<ApiResponse<WithdrawalResult>>(`${BASE}/withdrawals/initiate`, req);
  return response.data.data;
}

/** Step 2 — confirms with the OTP; on success the transfer executes. */
export async function confirmWithdrawal(req: ConfirmWithdrawalRequest): Promise<WithdrawalResult> {
  const response = await api.post<ApiResponse<WithdrawalResult>>(`${BASE}/withdrawals/confirm`, req);
  return response.data.data;
}

/** A page of the user's withdrawal history (newest first). `size` defaults to 5 to match the UI. */
export async function listWithdrawals(page = 0, pageSize = 5): Promise<WithdrawalHistoryPage> {
  const response = await api.get<ApiResponse<WithdrawalHistoryPage>>(`${BASE}/withdrawals`, {
    params: { page, pageSize },
  });
  return response.data.data;
}
