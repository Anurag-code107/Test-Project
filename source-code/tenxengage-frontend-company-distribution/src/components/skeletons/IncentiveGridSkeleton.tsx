function SkeletonBlock({ className }: { className?: string }) {
  return <div className={`skeleton-shimmer ${className ?? ""}`} />;
}

function IncentiveCardSkeleton() {
  return (
    <div className="rounded-xl border bg-card p-5">
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center gap-2.5 min-w-0 flex-1">
          <SkeletonBlock className="h-8 w-8 rounded-lg shrink-0" />
          <div className="min-w-0 flex-1 space-y-1.5">
            <SkeletonBlock className="h-4 w-3/5" />
            <SkeletonBlock className="h-3 w-2/5" />
          </div>
        </div>
        <SkeletonBlock className="h-5 w-16 rounded-full shrink-0" />
      </div>

      <SkeletonBlock className="h-2 w-full rounded-full mb-4" />

      <div className="grid grid-cols-3 gap-3 mb-4">
        <div className="space-y-1.5">
          <SkeletonBlock className="h-3 w-12" />
          <SkeletonBlock className="h-4 w-16" />
        </div>
        <div className="space-y-1.5">
          <SkeletonBlock className="h-3 w-14" />
          <SkeletonBlock className="h-4 w-14" />
        </div>
        <div className="space-y-1.5">
          <SkeletonBlock className="h-3 w-10" />
          <SkeletonBlock className="h-4 w-12" />
        </div>
      </div>

      <div className="flex items-center justify-between pt-3 border-t">
        <SkeletonBlock className="h-3.5 w-24" />
        <div className="flex items-center gap-1.5">
          <SkeletonBlock className="h-7 w-7 rounded-md" />
          <SkeletonBlock className="h-7 w-7 rounded-md" />
        </div>
      </div>
    </div>
  );
}

interface IncentiveGridSkeletonProps {
  /** Number of card skeletons to render. Defaults to 4. */
  cardCount?: number;
  /** Whether to render the section banner at the top. Defaults to true. */
  showBanner?: boolean;
  /** Width of the trailing action placeholder in the banner (e.g. Create button). */
  bannerActionWidth?: string;
}

export function IncentiveGridSkeleton({
  cardCount = 4,
  showBanner = true,
  bannerActionWidth = "w-36",
}: IncentiveGridSkeletonProps = {}) {
  return (
    <section className="flex flex-col flex-1 min-h-0 animate-in fade-in duration-300">
      {showBanner && (
        <div className="flex items-center justify-between px-5 py-3.5 rounded-xl border bg-card mb-4 shrink-0">
          <div className="flex items-center gap-3">
            <SkeletonBlock className="h-8 w-8 rounded-lg" />
            <SkeletonBlock className="h-5 w-40" />
            <SkeletonBlock className="h-5 w-8 rounded-full" />
          </div>
          <SkeletonBlock className={`h-9 ${bannerActionWidth} rounded-md`} />
        </div>
      )}

      <div className="overflow-hidden flex-1">
        <div className="grid grid-cols-2 gap-4">
          {Array.from({ length: cardCount }).map((_, i) => (
            <IncentiveCardSkeleton key={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
