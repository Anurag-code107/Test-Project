/**
 * Skeleton placeholders for the dashboard home page.
 * Shows shimmer cards that match the real layout while data loads.
 */

function SkeletonBlock({ className }: { className?: string }) {
  return <div className={`skeleton-shimmer ${className ?? ""}`} />;
}

/** Skeleton for the hero "Total Rewards Earned" card */
function HeroCardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-6">
      <div className="flex items-start justify-between mb-3">
        <SkeletonBlock className="h-4 w-36" />
        <SkeletonBlock className="h-4 w-20" />
      </div>
      <SkeletonBlock className="h-10 w-48 mt-1" />
      <SkeletonBlock className="h-16 w-full mt-5 rounded-lg" />
      <div className="flex items-center justify-between mt-4 pt-3 border-t">
        <SkeletonBlock className="h-3.5 w-28" />
        <SkeletonBlock className="h-3.5 w-20" />
      </div>
    </div>
  );
}

/** Skeleton for smaller stacked metric cards */
function MetricCardSkeleton() {
  return (
    <div className="flex-1 rounded-xl border bg-card p-5">
      <div className="flex items-center gap-2 mb-3">
        <SkeletonBlock className="h-3.5 w-3.5 rounded-full" />
        <SkeletonBlock className="h-3.5 w-28" />
      </div>
      <SkeletonBlock className="h-7 w-20" />
      <SkeletonBlock className="h-3 w-36 mt-2" />
      <SkeletonBlock className="h-4 w-24 mt-4" />
    </div>
  );
}

/** Skeleton for the participation section's 3-column metric cards */
function ParticipationCardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-5">
      <div className="flex items-center justify-between mb-2">
        <SkeletonBlock className="h-4 w-36" />
        <SkeletonBlock className="h-4 w-4 rounded" />
      </div>
      <SkeletonBlock className="h-8 w-24 mt-1" />
      <SkeletonBlock className="h-3 w-28 mt-2" />
    </div>
  );
}

/** Full dashboard skeleton matching the HomePage layout */
export function DashboardSkeleton() {
  return (
    <div className="space-y-10 animate-in fade-in duration-300">
      {/* Header skeleton */}
      <header>
        <div className="flex items-end justify-between gap-4 mb-5">
          <div>
            <SkeletonBlock className="h-7 w-64" />
            <SkeletonBlock className="h-4 w-48 mt-2" />
          </div>
          <div className="flex items-center gap-2">
            <SkeletonBlock className="h-8 w-[180px] rounded-md" />
            <SkeletonBlock className="h-8 w-28 rounded-md" />
            <SkeletonBlock className="h-8 w-56 rounded-md" />
          </div>
        </div>
      </header>

      {/* AI assistant skeleton */}
      <SkeletonBlock className="h-12 w-full rounded-xl" />

      {/* Incentive Performance section */}
      <section>
        <div className="flex items-center gap-2.5 mb-5">
          <SkeletonBlock className="h-5 w-40" />
          <SkeletonBlock className="h-7 w-48 rounded-md" />
        </div>

        <div
          className="grid grid-cols-1 lg:grid-cols-[2fr_1fr] gap-4"
          style={{ alignItems: "stretch" }}
        >
          <HeroCardSkeleton />
          <div className="flex flex-col gap-4">
            <MetricCardSkeleton />
            <MetricCardSkeleton />
          </div>
        </div>
      </section>

      {/* Participation section */}
      <section>
        <SkeletonBlock className="h-5 w-28 mb-5" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <ParticipationCardSkeleton />
          <ParticipationCardSkeleton />
          <ParticipationCardSkeleton />
        </div>
      </section>
    </div>
  );
}
