import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDistribution } from "@/hooks/useCompanyDistribution";
import { DistributionStatusBadge } from "@/components/distribution/DistributionStatusBadge";

interface Props {
  distributionId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Per-recipient outcomes for one distribution.
 *
 * <p>The underlying hook polls while the rollup is `PROCESSING`, so a sheet opened right after sending
 * fills in as each recipient settles instead of showing a frozen snapshot.</p>
 */
export function DistributionDetailSheet({ distributionId, open, onOpenChange }: Props) {
  const { data, isLoading } = useDistribution(open ? distributionId : null);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-2xl overflow-y-auto" data-testid="distribution-detail">
        <SheetHeader>
          <SheetTitle>Distribution detail</SheetTitle>
        </SheetHeader>

        {isLoading || !data ? (
          <Skeleton className="h-64 w-full mt-6" data-testid="detail-loading" />
        ) : (
          <div className="mt-6 space-y-6">
            <dl className="grid grid-cols-2 gap-4 text-sm">
              <Field label="Type" value={data.railDisplayName} />
              <Field label="Reward" value={data.catalogItemName ?? data.railDisplayName} />
              <Field label="Each recipient" value={`${data.amountPerRecipient} ${data.currencyId}`} />
              <Field label="Recipients" value={String(data.recipientCount)} />
              <Field label="Requested" value={`${data.requestedTotal} ${data.currencyId}`} />
              <Field label="Settled" value={`${data.settledTotal} ${data.currencyId}`} />
              <Field label="Initiated by" value={data.initiatedByName ?? "—"} />
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted-foreground">Status</dt>
                <dd className="mt-1">
                  <DistributionStatusBadge status={data.status} />
                </dd>
              </div>
              {data.note && (
                <div className="col-span-2">
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">Message</dt>
                  <dd className="mt-1">{data.note}</dd>
                </div>
              )}
            </dl>

            <div className="overflow-x-auto">
              <Table data-testid="detail-items">
                <TableHeader>
                  <TableRow>
                    <TableHead>Recipient</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Destination</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((item) => (
                    <TableRow key={item.itemId} data-testid={`item-${item.itemId}`}>
                      <TableCell>
                        <div className="font-medium">{item.recipientName ?? "—"}</div>
                        <div className="text-xs text-muted-foreground">{item.recipientEmail}</div>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{item.amount}</TableCell>
                      <TableCell>
                        <DistributionStatusBadge status={item.status} />
                        {item.failureReason && (
                          <p className="text-xs text-destructive mt-1">{item.failureReason}</p>
                        )}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        <div>{item.destination ?? "—"}</div>
                        {/* Only present once completed — a reference for an unfinished payout would be
                            misleading. */}
                        {item.paymentTransactionId && (
                          <div className="text-xs font-mono mt-1">{item.paymentTransactionId}</div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1">{value}</dd>
    </div>
  );
}
