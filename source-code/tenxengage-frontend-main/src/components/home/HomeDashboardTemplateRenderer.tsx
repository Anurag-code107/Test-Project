import { getWidgetComponent } from "@/components/home/widgetRegistry";
import { rowLayoutClass } from "@/components/home/rowLayoutClasses";
import type { HomeDashboardTemplate } from "@/types/home-dashboard.types";

interface HomeDashboardTemplateRendererProps {
  template: HomeDashboardTemplate | null;
  /**
   * What to render when `template` is null or has no rows. Defaults to a small
   * "ask your admin to assign a template" helper message.
   */
  emptyState?: React.ReactNode;
}

const warnedUnknownWidgets = new Set<string>();
const warnedUnknownLayouts = new Set<string>();

function warnOnce(kind: "widget" | "layout", key: string) {
  const cache = kind === "widget" ? warnedUnknownWidgets : warnedUnknownLayouts;
  if (cache.has(key)) return;
  cache.add(key);
   
  console.warn(
    `[HomeDashboardTemplateRenderer] Unknown ${kind} "${key}" — rendering fallback.`,
  );
}

function UnknownWidgetPlaceholder({ widgetKey }: { widgetKey: string }) {
  return (
    <div
      data-testid="home-dashboard-unknown-widget"
      data-widget-key={widgetKey}
      className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground"
    >
      Widget &quot;{widgetKey}&quot; is not available. An administrator may need
      to update this dashboard template.
    </div>
  );
}

function DefaultEmptyState() {
  return (
    <div
      data-testid="home-dashboard-empty"
      className="rounded-2xl border border-dashed border-border bg-muted/30 p-10 text-center"
    >
      <p className="text-base font-semibold text-foreground">
        No widgets configured
      </p>
      <p className="mt-1 text-sm text-muted-foreground">
        Ask your administrator to assign a home dashboard template to your role.
      </p>
    </div>
  );
}

export function HomeDashboardTemplateRenderer({
  template,
  emptyState,
}: HomeDashboardTemplateRendererProps) {
  const rows = template?.layout?.rows ?? [];

  if (rows.length === 0) {
    return <>{emptyState ?? <DefaultEmptyState />}</>;
  }

  return (
    <div className="space-y-6">
      {rows.map((row, rowIdx) => {
        const gridClass = rowLayoutClass(row.layout);
        if (!gridClass) {
          warnOnce("layout", row.layout);
          return null;
        }

        return (
          <div
            key={`row-${rowIdx}`}
            className={gridClass}
            data-layout={row.layout}
          >
            {row.slots.map((slot, slotIdx) => {
              const Component = getWidgetComponent(slot.widgetKey);
              if (!Component) {
                warnOnce("widget", slot.widgetKey);
                return (
                  <UnknownWidgetPlaceholder
                    key={`slot-${rowIdx}-${slotIdx}`}
                    widgetKey={slot.widgetKey}
                  />
                );
              }
              return <Component key={`slot-${rowIdx}-${slotIdx}`} />;
            })}
          </div>
        );
      })}
    </div>
  );
}
