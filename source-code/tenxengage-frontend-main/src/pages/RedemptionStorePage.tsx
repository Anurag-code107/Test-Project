import { useState } from "react";
import { CatalogBrowseGrid } from "@/components/redemption-catalog/CatalogBrowseGrid";
import { CatalogItemDetailSheet } from "@/components/redemption-catalog/CatalogItemDetailSheet";

export default function RedemptionStorePage() {
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);

  return (
    <div className="animate-route-in p-6 space-y-6" data-testid="redemption-store-page">
      <div>
        <h1 className="text-2xl font-semibold">Redemption Store</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Browse available rewards and redeem your balance.
        </p>
      </div>

      <CatalogBrowseGrid onItemClick={(id) => setSelectedItemId(id)} />

      <CatalogItemDetailSheet
        itemId={selectedItemId ?? ""}
        open={!!selectedItemId}
        onOpenChange={(open) => {
          if (!open) setSelectedItemId(null);
        }}
      />
    </div>
  );
}
