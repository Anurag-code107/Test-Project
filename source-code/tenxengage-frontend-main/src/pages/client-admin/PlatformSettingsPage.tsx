import { useState } from "react";
import {
  Database,
  FileSpreadsheet,
  Scale,
  Palette,
  MapPin,
  CalendarDays,
  Coins,
  Sparkles,
  Settings2,
} from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { FeatureGate } from "@/components/FeatureGate";
import { useFeatures } from "@/hooks/useFeatures";
import { IntegrationsTab } from "@/components/settings/IntegrationsTab";
import { ManageDataTab } from "@/components/settings/ManageDataTab";
import { LocationMappingSection } from "@/components/settings/LocationMappingSection";
import { FiscalYearMappingSection } from "@/components/settings/FiscalYearMappingSection";
import { ManageRewardTypesSection } from "@/components/settings/ManageRewardTypesSection";
import { RecommendationSettingsSection } from "@/components/settings/RecommendationSettingsSection";
import { BrandingSection } from "@/components/settings/BrandingSection";
import { BuilderConfigTab } from "@/components/settings/BuilderConfigTab";
import { PageBanner } from "@/components/PageBanner";

function PlatformSettingsPage() {
  const { has } = useFeatures();
  // Fall back to manage-data when integrations is gated off so the default
  // active tab isn't an empty pane.
  const [activeTab, setActiveTab] = useState(
    has("api_access") ? "integrations" : "manage-data",
  );

  return (
    <div className="space-y-6">
      <PageBanner
        theme="settings"
        title="Platform Settings"
        subtitle="Configure platform integrations, data management, and business rules"
      />

      <Tabs
        value={activeTab}
        onValueChange={setActiveTab}
        className="space-y-4"
      >
        <TabsList>
          <FeatureGate feature="api_access">
            <TabsTrigger value="integrations" className="gap-2">
              <Database className="h-4 w-4" />
              Integrations
            </TabsTrigger>
          </FeatureGate>
          <TabsTrigger value="manage-data" className="gap-2">
            <FileSpreadsheet className="h-4 w-4" />
            Manage Data
          </TabsTrigger>
          <TabsTrigger value="business-rules" className="gap-2">
            <Scale className="h-4 w-4" />
            Manage Business Rules
          </TabsTrigger>
          <TabsTrigger value="builder-config" className="gap-2">
            <Settings2 className="h-4 w-4" />
            Builder Config
          </TabsTrigger>
          <FeatureGate feature="custom_branding">
            <TabsTrigger value="branding" className="gap-2">
              <Palette className="h-4 w-4" />
              Branding
            </TabsTrigger>
          </FeatureGate>
        </TabsList>

        <FeatureGate feature="api_access">
          <TabsContent value="integrations" className="mt-4">
            <IntegrationsTab />
          </TabsContent>
        </FeatureGate>

        <TabsContent value="manage-data" className="mt-4">
          <ManageDataTab />
        </TabsContent>

        <TabsContent value="business-rules" className="space-y-4">
          <Card className="border-dashed">
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <Scale className="h-5 w-5 text-muted-foreground" />
                <CardTitle className="text-foreground">
                  Manage Business Rules
                </CardTitle>
              </div>
              <CardDescription>
                Configure Location Hierarchies, Fiscal Year Definitions, And
                Reward Types For Your Organization.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Tabs defaultValue="location-mapping" className="space-y-4">
                <TabsList>
                  <TabsTrigger
                    value="location-mapping"
                    className="gap-2 text-xs"
                  >
                    <MapPin className="h-3.5 w-3.5" />
                    Location Mapping
                  </TabsTrigger>
                  <TabsTrigger value="fiscal-year" className="gap-2 text-xs">
                    <CalendarDays className="h-3.5 w-3.5" />
                    Fiscal Year Mapping
                  </TabsTrigger>
                  <FeatureGate feature="multi_currency">
                    <TabsTrigger value="reward-types" className="gap-2 text-xs">
                      <Coins className="h-3.5 w-3.5" />
                      Manage Reward Types
                    </TabsTrigger>
                  </FeatureGate>
                  <TabsTrigger
                    value="recommendations"
                    className="gap-2 text-xs"
                  >
                    <Sparkles className="h-3.5 w-3.5" />
                    Recommendations
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="location-mapping">
                  <LocationMappingSection />
                </TabsContent>

                <TabsContent value="fiscal-year">
                  <FiscalYearMappingSection />
                </TabsContent>

                <FeatureGate feature="multi_currency">
                  <TabsContent value="reward-types">
                    <ManageRewardTypesSection />
                  </TabsContent>
                </FeatureGate>

                <TabsContent value="recommendations">
                  <RecommendationSettingsSection />
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="builder-config" className="space-y-4">
          <BuilderConfigTab />
        </TabsContent>

        <FeatureGate feature="custom_branding">
          <TabsContent value="branding" className="space-y-4">
            <BrandingSection />
          </TabsContent>
        </FeatureGate>
      </Tabs>
    </div>
  );
}

export default PlatformSettingsPage;
