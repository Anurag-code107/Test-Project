import { useNavigate } from "react-router-dom";
import { Gift } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useGiftCardPayoutReadiness } from "@/hooks/redemption-payout/useRedemptionProfile";

/**
 * Shown above the gift-card catalog when the user's XTRM payout profile isn't ready yet
 * (NOT_ENROLLED / FAILED). Gift-card payouts are delivered through the payout profile, so this nudges
 * the user to finish setup with a CTA to the Payout tab. Pairs with the dimmed, non-clickable catalog
 * cards the store page renders in the same state — this banner carries the explanation and the CTA,
 * the cards carry the visual "not available yet" signal.
 *
 * Renders nothing while loading, on error (e.g. a non-payout role → 403), or once ENROLLED —
 * see {@link useGiftCardPayoutReadiness}, which owns that rule for both surfaces.
 */
export function GiftCardEnrollmentNotice() {
  const navigate = useNavigate();
  const { isReady, enrollmentStatus } = useGiftCardPayoutReadiness();

  if (isReady) return null;

  const failed = enrollmentStatus === "FAILED";
  return (
    <Card data-testid="giftcard-enrollment-notice">
      <CardContent className="flex flex-col items-start gap-3 py-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-3">
          <Gift className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
          <div>
            <p className="text-sm font-medium">
              {failed
                ? "Your payout setup needs attention"
                : "Finish setting up payouts to redeem gift cards"}
            </p>
            <p className="text-sm text-muted-foreground">
              {failed
                ? "We couldn't complete your payout enrollment. Review your payout details and try again."
                : "Gift cards are delivered through your payout profile. Add your payout details to start redeeming."}
            </p>
          </div>
        </div>
        <Button
          className="shrink-0"
          onClick={() => navigate("/settings/profile?tab=payout&section=profile")}
        >
          Set up payouts
        </Button>
      </CardContent>
    </Card>
  );
}
