import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useMyAwards } from "@/hooks/useCompanyDistribution";
import { DistributionStatusBadge } from "@/components/distribution/DistributionStatusBadge";

/**
 * Company Awards — what a seller received from their company admins.
 *
 * <p>Distributions are excluded from Redemption History, which means "redemptions I made from my own
 * wallet", so this is the only place a seller sees them. A Wallet Transfer additionally shows up as a
 * credit in their balance, which is correct: that money genuinely arrived.</p>
 */
export default function CompanyAwardsPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useMyAwards({ page, size: 20 });

  return (
    <div className="animate-route-in p-6 space-y-6" data-testid="company-awards-page">
      <div>
        <h1 className="text-2xl font-semibold">Company Awards</h1>
        <p className="text-sm text-muted-foreground mt-1">Rewards your company has sent you.</p>
      </div>

      {isLoading ? (
        <Skeleton className="h-64 w-full" data-testid="awards-loading" />
      ) : !data || data.data.length === 0 ? (
        <Card data-testid="awards-empty">
          <CardContent className="pt-6 text-sm text-muted-foreground">
            You have not received any company rewards yet.
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="pt-6">
            <div className="overflow-x-auto">
              <Table data-testid="awards-table">
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Awarded by</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Reward</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Destination</TableHead>
                    <TableHead>Message</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.data.map((a) => (
                    <TableRow key={a.awardId} data-testid={`award-${a.awardId}`}>
                      <TableCell className="whitespace-nowrap">
                        {new Date(a.receivedAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell>{a.awardedByName ?? "—"}</TableCell>
                      <TableCell>{a.railDisplayName}</TableCell>
                      <TableCell>{a.rewardName ?? "—"}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {a.amount} {a.currencyId}
                      </TableCell>
                      <TableCell>
                        <DistributionStatusBadge status={a.status} />
                        {a.failureReason && (
                          <p className="text-xs text-destructive mt-1">{a.failureReason}</p>
                        )}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        <div>{a.destination ?? "—"}</div>
                        {a.paymentTransactionId && (
                          <div className="text-xs font-mono mt-1">{a.paymentTransactionId}</div>
                        )}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground max-w-[16rem] truncate">
                        {a.note ?? ""}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {(data.hasPrevious || data.hasNext) && (
              <div className="flex items-center justify-end gap-2 pt-4">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!data.hasPrevious}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!data.hasNext}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
