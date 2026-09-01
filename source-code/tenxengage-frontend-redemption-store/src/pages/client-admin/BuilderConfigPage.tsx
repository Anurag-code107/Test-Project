import { BuilderConfigTab } from "@/components/settings/BuilderConfigTab";

export default function BuilderConfigPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">
          Builder Configuration
        </h1>
      </div>
      <BuilderConfigTab />
    </div>
  );
}
