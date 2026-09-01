import { TenantCatalogConfigTable } from "@/components/redemption-catalog/TenantCatalogConfigTable";
import { TenantRedemptionSettingsForm } from "@/components/redemption-catalog/TenantRedemptionSettingsForm";

export default function CatalogConfigPage() {
  return (
    <div className="animate-route-in p-6 space-y-8" data-testid="catalog-config-page">
      <div>
        <h1 className="text-2xl font-semibold mb-1">Redemption Settings</h1>
        <p className="text-sm text-muted-foreground">
          Enable catalog items for your organization and configure overrides.
        </p>
      </div>

      <section className="space-y-4">
        <h2 className="text-lg font-medium">Catalog Items</h2>
        <TenantCatalogConfigTable />
      </section>

      <section className="space-y-4">
        <h2 className="text-lg font-medium">Batch Settings</h2>
        <TenantRedemptionSettingsForm />
      </section>
    </div>
  );
}
