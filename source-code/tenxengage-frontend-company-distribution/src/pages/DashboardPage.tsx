import { Users, ArrowLeftRight, DollarSign, Clock } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface StatCardProps {
  title: string;
  value: string;
  description: string;
  icon: React.ReactNode;
}

function StatCard({ title, value, description, icon }: StatCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        <div className="text-muted-foreground">{icon}</div>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-semibold">{value}</div>
        <p className="text-xs text-muted-foreground">{description}</p>
      </CardContent>
    </Card>
  );
}

function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Dashboard</h2>
        <p className="text-muted-foreground">
          Overview of your channel incentive platform
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Total Users"
          value="1,248"
          description="+12% from last month"
          icon={<Users className="h-4 w-4" />}
        />
        <StatCard
          title="Active Transactions"
          value="342"
          description="+8% from last month"
          icon={<ArrowLeftRight className="h-4 w-4" />}
        />
        <StatCard
          title="Revenue"
          value="$45,231"
          description="+20% from last month"
          icon={<DollarSign className="h-4 w-4" />}
        />
        <StatCard
          title="Pending Approvals"
          value="18"
          description="3 require immediate attention"
          icon={<Clock className="h-4 w-4" />}
        />
      </div>
    </div>
  );
}

export default DashboardPage;
