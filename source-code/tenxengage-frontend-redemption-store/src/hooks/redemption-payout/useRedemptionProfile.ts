// Adapted from: src/hooks/redemption-history/usePersonalRedemptions.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/useAuth";
import {
  getRedemptionProfile,
  listBanks,
  listWallets,
  listCards,
  listWithdrawals,
} from "@/services/redemption-payout/redemption-payout.service";
import type { XtrmEnrollmentStatus } from "@/types/redemption-payout/redemption-payout.types";

/**
 * Loads the current user's XTRM payout profile.
 * retry:false — a non-payee (403) or unenrolled user should not be retried.
 */
export function useRedemptionProfile() {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  return useQuery({
    queryKey: ["redemption-profile", userId],
    queryFn: getRedemptionProfile,
    staleTime: 5 * 60 * 1000,
    retry: false,
    enabled: !!userId,
  });
}

/**
 * Whether the user's payout profile can actually receive a gift-card payout.
 *
 * Gift cards are delivered through the XTRM payout profile, so a NOT_ENROLLED / FAILED profile means
 * nothing in the catalog is redeemable yet (and a bank account can't be linked either — that needs
 * the profile first). Single source of truth for the rule, shared by GiftCardEnrollmentNotice (the
 * banner) and the store page (dimming the cards) so the two can never disagree. Reuses the same
 * query, so it costs no extra request.
 *
 * Treated as ready whenever the state is unknown — loading, error (e.g. a non-payout role → 403), or
 * no profile returned — matching the conditions under which the notice hides itself. Never dim the
 * catalog on a guess; the server is authoritative at submit time.
 */
export function useGiftCardPayoutReadiness(): {
  isReady: boolean;
  isKnown: boolean;
  enrollmentStatus: XtrmEnrollmentStatus | null;
} {
  const { data: profile, isLoading, isError } = useRedemptionProfile();

  if (isLoading || isError || !profile) {
    return { isReady: true, isKnown: false, enrollmentStatus: null };
  }
  return {
    isReady: profile.enrollmentStatus === "ENROLLED",
    isKnown: true,
    enrollmentStatus: profile.enrollmentStatus,
  };
}

/**
 * Loads the current user's linked bank accounts (multi-bank). Separate query from the profile so the
 * Payout tab can list banks with a fast local read; mutations invalidate this key to refresh the list.
 */
export function useLinkedBanks() {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  return useQuery({
    queryKey: ["linked-banks", userId],
    queryFn: listBanks,
    staleTime: 5 * 60 * 1000,
    retry: false,
    enabled: !!userId,
  });
}

/**
 * Loads the current user's XTRM digital wallets (view-only). Gated on `enabled` (the caller passes whether
 * the user is enrolled) so we don't fire a guaranteed 422 for an unenrolled user. Short staleTime — it's a
 * live XTRM call.
 */
export function useDigitalWallets(enabled: boolean) {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  return useQuery({
    queryKey: ["digital-wallets", userId],
    queryFn: listWallets,
    staleTime: 60 * 1000,
    retry: false,
    enabled: !!userId && enabled,
  });
}

/**
 * Loads the current user's linked cards (multi-card). Mirrors {@link useLinkedBanks}: a separate query so the
 * Payout tab lists cards with a fast local read; card mutations invalidate this key to refresh the list.
 */
export function useLinkedCards() {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  return useQuery({
    queryKey: ["linked-cards", userId],
    queryFn: listCards,
    staleTime: 5 * 60 * 1000,
    retry: false,
    enabled: !!userId,
  });
}

/**
 * Loads a page of the current user's withdrawal history (newest first, 5 per page). Gated on `enabled`
 * (enrollment); confirming a withdrawal invalidates the `["withdrawals", userId]` prefix (all pages).
 * `placeholderData` keeps the previous page visible while the next loads, for smooth paging.
 */
export function useWithdrawals(enabled: boolean, page = 0) {
  const { user } = useAuth();
  const userId = user?.id ?? null;

  return useQuery({
    queryKey: ["withdrawals", userId, page],
    queryFn: () => listWithdrawals(page, 5),
    staleTime: 60 * 1000,
    retry: false,
    enabled: !!userId && enabled,
    placeholderData: (previous) => previous,
  });
}
