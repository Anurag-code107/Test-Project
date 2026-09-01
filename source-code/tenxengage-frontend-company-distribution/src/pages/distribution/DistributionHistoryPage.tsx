import { useState } from "react";
import { useSearchParams } from "react-router-dom";
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
import { useDistributions } from "@/hooks/useCompanyDistribution";
import { DistributionStatusBadge } from "@/components/distribution/DistributionStatusBadge";
import { DistributionDetailSheet } from "@/components/distribution/DistributionDetailSheet";

/**
 * Every distribution drawn from this company's wallet — by any of its admins, not just the viewer's.
 *
 * <p>Several admins share one wallet, so hiding a peer's distribution would leave the balance falling
 * with no explanation. "Initiated by" attributes each one instead.</p>
 */
export default function DistributionHistoryPage() {
  const [searchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  // Arriving from a just-sent distribution opens its detail straight away, so the admin sees the
  // per-recipient outcomes rather than having to hunt for the row they just created.
  const [openId, setOpenId] = useState<string | null>(searchParams.get("highlight"));

  const { data, isLoading } = useDistributions({ page, size: 20 });

  return (
    <div className="animate-route-in p-6 space-y-6" data-testid="distribution-history-page">
      <div>
        <h1 className="text-2xl font-semibold">Distribution History</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Everything your company has distributed to its sellers.
        </p>
      </div>

      {isLoading ? (
        <Skeleton className="h-64 w-full" data-testid="history-loading" />
      ) : !data || data.data.length === 0 ? (
        <Card data-testid="history-empty">
          <CardContent className="pt-6 text-sm text-muted-foreground">
            No distributions yet.
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="pt-6">
            <div className="overflow-x-auto">
              <Table data-testid="distribution-table">
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Reward</TableHead>
                    <TableHead className="text-right">Recipients</TableHead>
                    <TableHead className="text-right">Requested</TableHead>
                    <TableHead className="text-right">Settled</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Initiated by</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.data.map((d) => (
                    <TableRow key={d.id} data-testid={`distribution-${d.id}`}>
                      <TableCell className="whitespace-nowrap">
                        {new Date(d.createdAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell>{d.railDisplayName}</TableCell>
                      <TableCell>{d.catalogItemName ?? d.railDisplayName}</TableCell>
                      <TableCell className="text-right tabular-nums">{d.recipientCount}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {d.requestedTotal} {d.currencyId}
                      </TableCell>
                      {/* Settled differs from requested after a partial failure — showing only the
                          requested figure would read as though it all paid out. */}
                      <TableCell
                        className={`text-right tabular-nums ${
                          d.settledTotal !== d.requestedTotal ? "text-amber-600 dark:text-amber-400" : ""
                        }`}
                      >
                        {d.settledTotal} {d.currencyId}
                      </TableCell>
                      <TableCell>
                        <DistributionStatusBadge status={d.status} />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {d.initiatedByName ?? "—"}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setOpenId(d.id)}
                          data-testid={`view-${d.id}`}
                        >
                          View
                        </Button>
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

      <DistributionDetailSheet
        distributionId={openId}
        open={!!openId}
        onOpenChange={(open) => {
          if (!open) setOpenId(null);
        }}
      />
    </div>
  );
}
