import { useState, useMemo } from "react";
import { Clock, RotateCcw } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { getCurrency } from "@/config/currencies";
import { usePartnerCatalogItem } from "@/hooks/useRedemptionCatalog";
import { useMyWallets, useCompanyWallet } from "@/hooks/useWallet";
import { useAuth } from "@/hooks/useAuth";
import { useNavigate } from "react-router-dom";
import { RedemptionSubmitModal } from "@/components/redemption-flow/RedemptionSubmitModal";
import { ShortfallBadge } from "./ShortfallBadge";

interface Props {
  itemId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CatalogItemDetailSheet({ itemId, open, onOpenChange }: Props) {
  const { data: item, isLoading, isError, error } = usePartnerCatalogItem(itemId);
  const { data: wallets } = useMyWallets();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [redeemModalOpen, setRedeemModalOpen] = useState(false);
  const [redeemCompanyModalOpen, setRedeemCompanyModalOpen] = useState(false);

  const canRedeem = user?.permissions.includes("action.redemption.redeem") ?? false;
  const canRedeemCompany = user?.permissions.includes("action.redemption.redeem_company") ?? false;
  const wallet = useMemo(
    () => (item ? wallets?.find((w) => w.currencyId === item.currencyId) : undefined),
    [item, wallets],
  );

  const { data: companyWallets } = useCompanyWallet(canRedeemCompany ? user?.partnerCompanyId : null);
  const companyWallet = useMemo(
    () => (item ? companyWallets?.find((w) => w.currencyId === item.currencyId) : undefined),
    [item, companyWallets],
  );

  const is404 =
    isError &&
    (error as { response?: { status?: number } })?.response?.status === 404;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-md overflow-y-auto">
        {isLoading && (
          <div className="space-y-4 pt-6">
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        )}

        {is404 && (
          <div className="pt-8 flex flex-col items-center gap-3 text-center">
            <p className="text-sm text-muted-foreground">This item is no longer available.</p>
            <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        )}

        {isError && !is404 && (
          <div className="pt-8 flex flex-col items-center gap-3 text-center">
            <p className="text-sm text-destructive">Could not load item details. Please try again.</p>
            <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        )}

        {item && (
          <>
            <SheetHeader className="mb-4">
              <SheetTitle>{item.name}</SheetTitle>
              {item.description && (
                <SheetDescription>{item.description}</SheetDescription>
              )}
            </SheetHeader>

            <div className="space-y-4">
              <div>
                <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                  Minimum amount
                </p>
                <p className="text-sm font-medium">
                  {getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount)}
                </p>
              </div>

              <div>
                <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                  Estimated payout
                </p>
                <div className="flex items-center gap-1 text-sm">
                  <Clock className="w-3.5 h-3.5 text-muted-foreground" />
                  {item.estimatedPayoutTimeline}
                </div>
              </div>

              {item.isReturnable && (
                <div>
                  <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                    Return window
                  </p>
                  <div className="flex items-center gap-1 text-sm">
                    <RotateCcw className="w-3.5 h-3.5 text-muted-foreground" />
                    {item.effectiveReturnWindowDays} days
                  </div>
                </div>
              )}

              {!item.canAfford && (
                <ShortfallBadge
                  shortfallAmount={item.shortfallAmount}
                  currencyId={item.currencyId}
                />
              )}

              {canRedeem && (
                <div className="pt-2">
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span className="inline-block w-full">
                          <Button
                            className="w-full"
                            disabled={!item.canAfford}
                            onClick={() => item.canAfford && setRedeemModalOpen(true)}
                          >
                            Redeem
                          </Button>
                        </span>
                      </TooltipTrigger>
                      {!item.canAfford && (
                        <TooltipContent>Insufficient balance</TooltipContent>
                      )}
                    </Tooltip>
                  </TooltipProvider>
                </div>
              )}

              {canRedeemCompany && (
                <div className="pt-2">
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span className="inline-block w-full">
                          <Button
                            className="w-full"
                            variant="outline"
                            disabled={!companyWallet}
                            onClick={() => companyWallet && setRedeemCompanyModalOpen(true)}
                          >
                            Redeem (Company)
                          </Button>
                        </span>
                      </TooltipTrigger>
                      {!companyWallet && (
                        <TooltipContent>No company wallet available</TooltipContent>
                      )}
                    </Tooltip>
                  </TooltipProvider>
                </div>
              )}
            </div>

            {canRedeem && wallet && redeemModalOpen && (
              <RedemptionSubmitModal
                open={redeemModalOpen}
                onOpenChange={setRedeemModalOpen}
                item={item}
                wallet={wallet}
                onSuccess={(id) => {
                  setRedeemModalOpen(false);
                  onOpenChange(false);
                  navigate(`/redemption/confirmation/${id}`);
                }}
              />
            )}

            {canRedeemCompany && companyWallet && redeemCompanyModalOpen && (
              <RedemptionSubmitModal
                open={redeemCompanyModalOpen}
                onOpenChange={setRedeemCompanyModalOpen}
                item={item}
                wallet={companyWallet}
                type="company"
                companyId={user?.partnerCompanyId ?? undefined}
                onSuccess={(id) => {
                  setRedeemCompanyModalOpen(false);
                  onOpenChange(false);
                  navigate(`/redemption/confirmation/${id}`);
                }}
              />
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
