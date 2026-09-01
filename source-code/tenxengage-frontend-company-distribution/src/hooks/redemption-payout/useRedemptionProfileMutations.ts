// Adapted from: src/hooks/useRedemptionSubmit.ts (TanStack Query mutation pattern)
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import { useAuth } from "@/hooks/useAuth";
import type { ErrorResponse } from "@/types/api.types";
import type { RedemptionProfileResponse } from "@/types/redemption-payout/redemption-payout.types";
import {
  saveRedemptionAddress,
  setPayoutMethod,
  linkBankAccount,
  removeBankAccount,
  setDefaultBank,
  addCard,
  removeCard,
  setDefaultCard,
  initiateWithdrawal,
  confirmWithdrawal,
} from "@/services/redemption-payout/redemption-payout.service";

/** Extract the machine error code from an XTRM 422 (errorCode, with a `code` fallback). */
export function xtrmErrorCode(error: unknown): string | undefined {
  const ax = error as AxiosError<ErrorResponse & { code?: string }>;
  const data = ax?.response?.data;
  return data?.errorCode ?? data?.code;
}

/** Map an XTRM error code to friendly, user-facing copy. */
export function friendlyXtrmError(code: string | undefined): string {
  switch (code) {
    case "BANK_NOT_LINKED":
      return "Link a bank account before choosing the bank payout method.";
    case "CARD_NOT_LINKED":
      return "Link a card before choosing the card payout method.";
    case "XTRM_SEND_LIMIT":
      return "This payout exceeds your current send limit. You can raise it from your digital wallet account.";
    case "XTRM_NOT_ENROLLED":
      return "Your payout profile isn't set up yet. Save your payout address to enroll.";
    case "XTRM_BANK_DUPLICATE":
      return "This bank account is already linked.";
    case "XTRM_BANK_LINK_FAILED":
      return "We couldn't link that bank account. Please check the details and try again.";
    case "XTRM_CARD_DUPLICATE":
      return "This card is already linked.";
    case "XTRM_CARD_LINK_FAILED":
      return "We couldn't link that card. Please check the details and try again.";
    case "XTRM_WITHDRAW_OTP_INVALID":
      return "That code wasn't accepted. Please request a new code and try again.";
    case "XTRM_WITHDRAW_FAILED":
      return "We couldn't process that withdrawal. Please try again.";
    case "XTRM_UNAVAILABLE":
      return "Payouts are temporarily unavailable. Please try again shortly.";
    case "XTRM_WALLETS_FAILED":
      return "We couldn't load your wallets. Please try again.";
    default:
      return "Something went wrong — please try again.";
  }
}

/**
 * Writes the mutation's returned profile straight into the cache (each mutation returns the fresh
 * RedemptionProfileResponse) — avoids an extra GET + a brief flash. Guarded on userId (null fallback).
 */
function useSyncProfile() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  return (data: RedemptionProfileResponse) => {
    if (userId) {
      queryClient.setQueryData(["redemption-profile", userId], data);
    }
  };
}

/**
 * Like {@link useSyncProfile}, but also invalidates the linked-banks list — the bank mutations
 * (add/remove/set-default) return only the profile, so the separate list query must be refetched.
 */
function useSyncProfileAndBanks() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  return (data: RedemptionProfileResponse) => {
    if (userId) {
      queryClient.setQueryData(["redemption-profile", userId], data);
      queryClient.invalidateQueries({ queryKey: ["linked-banks", userId] });
    }
  };
}

/** Like {@link useSyncProfileAndBanks} but for the card mutations — invalidates the linked-cards list. */
function useSyncProfileAndCards() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  return (data: RedemptionProfileResponse) => {
    if (userId) {
      queryClient.setQueryData(["redemption-profile", userId], data);
      queryClient.invalidateQueries({ queryKey: ["linked-cards", userId] });
    }
  };
}

export function useSaveAddress() {
  const sync = useSyncProfile();
  return useMutation({
    mutationFn: saveRedemptionAddress,
    onSuccess: (data) => sync(data),
  });
}

export function useSetPayoutMethod() {
  const sync = useSyncProfile();
  return useMutation({
    mutationFn: setPayoutMethod,
    onSuccess: (data) => sync(data),
  });
}

export function useLinkBankAccount() {
  const sync = useSyncProfileAndBanks();
  return useMutation({
    mutationFn: linkBankAccount,
    onSuccess: (data) => sync(data),
  });
}

/** Remove a specific linked bank by our bank id. */
export function useRemoveBankAccount() {
  const sync = useSyncProfileAndBanks();
  return useMutation({
    mutationFn: (bankId: string) => removeBankAccount(bankId),
    onSuccess: (data) => sync(data),
  });
}

/** Set the default bank (destination for the BANK payout rail) by our bank id. */
export function useSetDefaultBank() {
  const sync = useSyncProfileAndBanks();
  return useMutation({
    mutationFn: (bankId: string) => setDefaultBank(bankId),
    onSuccess: (data) => sync(data),
  });
}

/** Link a card. ⚠️ PCI: raw card fields are pass-through — never stored client-side beyond the request. */
export function useAddCard() {
  const sync = useSyncProfileAndCards();
  return useMutation({
    mutationFn: addCard,
    onSuccess: (data) => sync(data),
  });
}

/** Remove a specific linked card by our card id. */
export function useRemoveCard() {
  const sync = useSyncProfileAndCards();
  return useMutation({
    mutationFn: (cardId: string) => removeCard(cardId),
    onSuccess: (data) => sync(data),
  });
}

/** Set the default card (destination for the CARD payout rail) by our card id. */
export function useSetDefaultCard() {
  const sync = useSyncProfileAndCards();
  return useMutation({
    mutationFn: (cardId: string) => setDefaultCard(cardId),
    onSuccess: (data) => sync(data),
  });
}

/** Step 1 of a withdrawal — sends the OTP. Returns { otpRequired: true }; no cache write. */
export function useInitiateWithdrawal() {
  return useMutation({
    mutationFn: initiateWithdrawal,
  });
}

/**
 * Step 2 — confirms with the OTP. On success invalidates the withdrawal history AND the wallet balance
 * (the withdraw modal now lives alongside the balance on the Payout tab, so the balance must refresh).
 */
export function useConfirmWithdrawal() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  return useMutation({
    mutationFn: confirmWithdrawal,
    onSuccess: () => {
      if (userId) {
        queryClient.invalidateQueries({ queryKey: ["withdrawals", userId] });
        queryClient.invalidateQueries({ queryKey: ["digital-wallets", userId] });
      }
    },
  });
}

type DestinationCallbacks = { onSuccess?: () => void; onError?: (error: unknown) => void };

/**
 * Unified "Set as default" for the Payout tabs. Setting a bank/card as default makes it the single payout
 * destination by composing the per-rail default with the payout method: e.g. Bank → setDefaultBank then
 * setPayoutMethod("BANK"). If the method step fails AFTER the per-rail default succeeded, the cache would hold
 * the new default but the old method — so we invalidate the profile query to resync (the mutations use
 * setQueryData, not invalidate, and won't self-correct).
 */
export function useSetDefaultDestination() {
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const setBank = useSetDefaultBank();
  const setCard = useSetDefaultCard();
  const setMethod = useSetPayoutMethod();

  const resync = () => {
    if (userId) queryClient.invalidateQueries({ queryKey: ["redemption-profile", userId] });
  };

  return {
    isPending: setBank.isPending || setCard.isPending || setMethod.isPending,
    /** Make a bank the payout destination: default bank + method=BANK. */
    setBank: (bankId: string, cbs?: DestinationCallbacks) =>
      setBank.mutate(bankId, {
        onSuccess: () =>
          setMethod.mutate(
            { payoutMethod: "BANK" },
            { onSuccess: () => cbs?.onSuccess?.(), onError: (e) => { resync(); cbs?.onError?.(e); } },
          ),
        onError: (e) => cbs?.onError?.(e),
      }),
    /** Make a card the payout destination: default card + method=CARD. */
    setCard: (cardId: string, cbs?: DestinationCallbacks) =>
      setCard.mutate(cardId, {
        onSuccess: () =>
          setMethod.mutate(
            { payoutMethod: "CARD" },
            { onSuccess: () => cbs?.onSuccess?.(), onError: (e) => { resync(); cbs?.onError?.(e); } },
          ),
        onError: (e) => cbs?.onError?.(e),
      }),
    /** Make the digital wallet the payout destination: method=ANYPAY. */
    setWallet: (cbs?: DestinationCallbacks) =>
      setMethod.mutate(
        { payoutMethod: "ANYPAY" },
        { onSuccess: () => cbs?.onSuccess?.(), onError: (e) => cbs?.onError?.(e) },
      ),
  };
}
