import { Package } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";

export function RedemptionCatalogTab() {
  return (
    <Card className="border-dashed">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Package className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-foreground">Redemption Catalog</CardTitle>
        </div>
        <CardDescription>
          Manage global catalog items and configure tenant-level availability.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <GlobalCatalogAdminPage />
      </CardContent>
    </Card>
  );
}
