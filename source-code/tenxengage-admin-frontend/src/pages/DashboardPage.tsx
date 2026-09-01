import { Link } from "react-router-dom";
import { Building2, CheckCircle, Clock, Crown, Loader2 } from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useClientStats, useClients } from "@/hooks/useClientApi";
import { formatDate } from "@/utils/formatters";
import type { ClientStatus, SubscriptionTier } from "@/types/client.types";
import { cn } from "@/lib/utils";

/* -- Badge color mappings -- */

const statusColors: Record<ClientStatus, string> = {
  ACTIVE: "bg-green-100 text-green-700 border-green-200",
  INACTIVE: "bg-gray-100 text-gray-600 border-gray-200",
  SUSPENDED: "bg-red-100 text-red-700 border-red-200",
  TRIAL: "bg-amber-100 text-amber-700 border-amber-200",
};

const tierColors: Record<SubscriptionTier, string> = {
  STARTER: "bg-slate-100 text-slate-700 border-slate-200",
  PROFESSIONAL: "bg-blue-100 text-blue-700 border-blue-200",
  ENTERPRISE: "bg-purple-100 text-purple-700 border-purple-200",
};

/* -- Metric card config -- */

interface MetricDef {
  label: string;
  icon: React.ElementType;
  iconBg: string;
  iconColor: string;
  getValue: (stats: { totalClients: number; countByStatus: Record<string, number> }) => number;
}

const metrics: MetricDef[] = [
  {
    label: "Total Clients",
    icon: Building2,
    iconBg: "bg-blue-100",
    iconColor: "text-blue-600",
    getValue: (s) => s.totalClients,
  },
  {
    label: "Active Clients",
    icon: CheckCircle,
    iconBg: "bg-green-100",
    iconColor: "text-green-600",
    getValue: (s) => s.countByStatus["ACTIVE"] ?? 0,
  },
  {
    label: "Trial Clients",
    icon: Clock,
    iconBg: "bg-amber-100",
    iconColor: "text-amber-600",
    getValue: (s) => s.countByStatus["TRIAL"] ?? 0,
  },
  {
    label: "Enterprise Clients",
    icon: Crown,
    iconBg: "bg-purple-100",
    iconColor: "text-purple-600",
    getValue: (s) => s.countByStatus["ENTERPRISE"] ?? 0,
  },
];

/* -- Tier breakdown config -- */

interface TierDef {
  key: SubscriptionTier;
  label: string;
  barColor: string;
}

const tiers: TierDef[] = [
  { key: "STARTER", label: "Starter", barColor: "bg-slate-500" },
  { key: "PROFESSIONAL", label: "Professional", barColor: "bg-blue-500" },
  { key: "ENTERPRISE", label: "Enterprise", barColor: "bg-purple-500" },
];

/* -- Status breakdown config -- */

interface StatusDef {
  key: ClientStatus;
  label: string;
  badgeClass: string;
}

const statuses: StatusDef[] = [
  { key: "ACTIVE", label: "Active", badgeClass: statusColors.ACTIVE },
  { key: "TRIAL", label: "Trial", badgeClass: statusColors.TRIAL },
  { key: "INACTIVE", label: "Inactive", badgeClass: statusColors.INACTIVE },
  { key: "SUSPENDED", label: "Suspended", badgeClass: statusColors.SUSPENDED },
];

/* -- Page component -- */

export default function DashboardPage() {
  const { data: stats, isLoading: statsLoading } = useClientStats();
  const { data: recentClientsPage, isLoading: clientsLoading } = useClients({
    page: 0,
    size: 5,
    sort: "createdAt,desc",
  });

  const isLoading = statsLoading || clientsLoading;

  if (isLoading) {
    return (
      <div className="flex flex-col">
        <PageBanner
          title="Platform Dashboard"
          subtitle="Overview of all clients and subscriptions"
          theme="default"
        />
        <div className="flex items-center justify-center py-32">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      </div>
    );
  }

  const totalClients = stats?.totalClients ?? 0;
  const countByStatus = stats?.countByStatus ?? {};
  const countByTier = stats?.countByTier ?? {};
  const recentClients = recentClientsPage?.data ?? [];

  // For the Enterprise metric card, pull from countByTier since it's a tier not a status
  const metricsWithOverride: MetricDef[] = metrics.map((m) =>
    m.label === "Enterprise Clients"
      ? { ...m, getValue: () => countByTier["ENTERPRISE"] ?? 0 }
      : m,
  );

  // Max value for progress bars in tier breakdown
  const tierMax = Math.max(
    ...tiers.map((t) => countByTier[t.key] ?? 0),
    1,
  );

  return (
    <div className="flex flex-col">
      <PageBanner
        title="Platform Dashboard"
        subtitle="Overview of all clients and subscriptions"
        theme="default"
      />

      <div className="mx-auto w-full max-w-7xl space-y-6 px-6 py-6">
        {/* Metric cards */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {metricsWithOverride.map((m) => {
            const Icon = m.icon;
            const value = m.getValue({ totalClients, countByStatus });
            return (
              <Card key={m.label} className="relative overflow-hidden">
                <CardContent className="flex items-center gap-4 p-5">
                  <div
                    className={cn(
                      "flex h-12 w-12 shrink-0 items-center justify-center rounded-full",
                      m.iconBg,
                    )}
                  >
                    <Icon className={cn("h-6 w-6", m.iconColor)} />
                  </div>
                  <div>
                    <p className="text-2xl font-bold tracking-tight">{value}</p>
                    <p className="text-sm text-muted-foreground">{m.label}</p>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>

        {/* Two-column breakdown */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {/* Clients by Tier */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Clients by Tier</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {tiers.map((tier) => {
                const count = countByTier[tier.key] ?? 0;
                const pct = tierMax > 0 ? (count / tierMax) * 100 : 0;
                return (
                  <div key={tier.key} className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{tier.label}</span>
                      <span className="tabular-nums text-muted-foreground">
                        {count}
                      </span>
                    </div>
                    <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                      <div
                        className={cn("h-full rounded-full transition-all", tier.barColor)}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </CardContent>
          </Card>

          {/* Clients by Status */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Clients by Status</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {statuses.map((s) => {
                const count = countByStatus[s.key] ?? 0;
                return (
                  <div
                    key={s.key}
                    className="flex items-center justify-between"
                  >
                    <Badge
                      variant="outline"
                      className={cn("font-medium", s.badgeClass)}
                    >
                      {s.label}
                    </Badge>
                    <span className="text-sm font-semibold tabular-nums">
                      {count}
                    </span>
                  </div>
                );
              })}
            </CardContent>
          </Card>
        </div>

        {/* Recent Clients table */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="text-base">Recent Clients</CardTitle>
            <Link
              to="/clients"
              className="text-sm font-medium text-primary hover:underline"
            >
              View All
            </Link>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="pb-3 pr-4 font-medium">Name</th>
                    <th className="pb-3 pr-4 font-medium">Subdomain</th>
                    <th className="pb-3 pr-4 font-medium">Tier</th>
                    <th className="pb-3 pr-4 font-medium">Status</th>
                    <th className="pb-3 font-medium">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {recentClients.map((client) => (
                    <tr
                      key={client.id}
                      className="border-b last:border-0 hover:bg-muted/50"
                    >
                      <td className="py-3 pr-4 font-medium">{client.name}</td>
                      <td className="py-3 pr-4 text-muted-foreground">
                        {client.subdomain}
                      </td>
                      <td className="py-3 pr-4">
                        <Badge
                          variant="outline"
                          className={cn(
                            "font-medium",
                            tierColors[client.subscriptionTier],
                          )}
                        >
                          {client.subscriptionTier}
                        </Badge>
                      </td>
                      <td className="py-3 pr-4">
                        <Badge
                          variant="outline"
                          className={cn(
                            "font-medium",
                            statusColors[client.status],
                          )}
                        >
                          {client.status}
                        </Badge>
                      </td>
                      <td className="py-3 text-muted-foreground">
                        {formatDate(client.createdAt)}
                      </td>
                    </tr>
                  ))}
                  {recentClients.length === 0 && (
                    <tr>
                      <td
                        colSpan={5}
                        className="py-8 text-center text-muted-foreground"
                      >
                        No clients found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
