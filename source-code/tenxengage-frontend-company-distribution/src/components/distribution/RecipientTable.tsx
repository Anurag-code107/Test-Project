import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { DistributionRecipient } from "@/types/company-distribution.types";

interface Props {
  recipients: DistributionRecipient[];
  isLoading: boolean;
  selected: Set<string>;
  onChange: (next: Set<string>) => void;
}

/**
 * Who to pay.
 *
 * <p>Ineligible sellers are shown greyed with their reason rather than hidden. Hiding them would leave
 * the admin wondering who is missing; showing the reason ("No bank account linked — use Wallet Transfer
 * instead") tells them what to do about it, which is what makes the wallet rail discoverable.</p>
 */
export function RecipientTable({ recipients, isLoading, selected, onChange }: Props) {
  if (isLoading) {
    return <Skeleton className="h-48 w-full" data-testid="recipients-loading" />;
  }

  if (recipients.length === 0) {
    return (
      <Card data-testid="recipients-empty">
        <CardContent className="pt-6 text-sm text-muted-foreground">
          This company has no active sellers to distribute to.
        </CardContent>
      </Card>
    );
  }

  const eligible = recipients.filter((r) => r.eligible);
  const allEligibleSelected = eligible.length > 0 && eligible.every((r) => selected.has(r.userId));

  const toggle = (userId: string) => {
    const next = new Set(selected);
    if (next.has(userId)) next.delete(userId);
    else next.add(userId);
    onChange(next);
  };

  const toggleAll = () => {
    // Select-all only ever touches eligible rows — an ineligible seller cannot be paid on this rail.
    onChange(allEligibleSelected ? new Set() : new Set(eligible.map((r) => r.userId)));
  };

  return (
    <Card>
      <CardContent className="pt-6">
        <div className="overflow-x-auto">
          <Table data-testid="recipient-table">
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox
                    checked={allEligibleSelected}
                    onCheckedChange={toggleAll}
                    aria-label="Select all eligible sellers"
                    data-testid="select-all"
                    disabled={eligible.length === 0}
                  />
                </TableHead>
                <TableHead>Seller</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Destination</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recipients.map((r) => (
                <TableRow
                  key={r.userId}
                  data-testid={`recipient-${r.userId}`}
                  className={r.eligible ? "" : "opacity-60"}
                >
                  <TableCell>
                    <Checkbox
                      checked={selected.has(r.userId)}
                      onCheckedChange={() => toggle(r.userId)}
                      disabled={!r.eligible}
                      aria-label={`Select ${r.fullName}`}
                      data-testid={`select-${r.userId}`}
                    />
                  </TableCell>
                  <TableCell className="font-medium">{r.fullName}</TableCell>
                  <TableCell className="text-muted-foreground">{r.email}</TableCell>
                  <TableCell className="text-sm">
                    {r.eligible ? (
                      <span className="text-muted-foreground">{r.destination}</span>
                    ) : (
                      <span className="text-muted-foreground italic">{r.ineligibleReason}</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
}
