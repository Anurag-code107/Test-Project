// Adapted from: src/pages/balanceExpiration/BalanceExpirationSettingsPage.tsx (page shell pattern)
// Screen type: Report/table page (Screen Pattern Mirror)
import { FileBarChart2 } from "lucide-react";
import { BreakageReportTable } from "@/components/balanceExpiration/BreakageReportTable";

/**
 * BalanceBreakageReportPage
 *
 * Route: /redemption/breakage
 * Permission: action.redemption.expiration.view_breakage
 *
 * Shows the breakage (expired value) report — aggregated EXPIRY ledger entries
 * by currency type and period, with CSV export.
 *
 * Covers AC-1 (table renders rows), AC-2 (export download), AC-3 (429 toast),
 * AC-4 (range validation), AC-5 (ProtectedRoute guards the page).
 */
function BalanceBreakageReportPage() {
  return (
    <div className="animate-route-in space-y-6 p-6">
      {/* Page header */}
      <div>
        <div className="flex items-center gap-2 mb-1">
          <FileBarChart2 className="h-5 w-5 text-primary" aria-hidden="true" />
          <h1 className="text-xl font-semibold text-foreground">
            Breakage Report
          </h1>
        </div>
        <p className="text-sm text-muted-foreground">
          View expired balance totals by currency type and period. Export to CSV
          for further analysis.
        </p>
      </div>

      <BreakageReportTable />
    </div>
  );
}

export default BalanceBreakageReportPage;
