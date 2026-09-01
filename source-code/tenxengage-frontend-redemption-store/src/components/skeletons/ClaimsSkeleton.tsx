function SkeletonBlock({ className }: { className?: string }) {
  return <div className={`skeleton-shimmer ${className ?? ""}`} />;
}

function ClaimRowSkeleton() {
  return (
    <div className="flex items-center gap-3 px-4 py-3 border-b border-border last:border-b-0">
      <SkeletonBlock className="h-4 w-4 rounded shrink-0" />
      <SkeletonBlock className="h-3.5 w-[10%]" />
      <SkeletonBlock className="h-3.5 w-[14%]" />
      <SkeletonBlock className="h-3.5 w-[18%]" />
      <SkeletonBlock className="h-3.5 w-[12%]" />
      <SkeletonBlock className="h-3.5 w-[12%]" />
      <SkeletonBlock className="h-5 w-16 rounded-full" />
      <div className="ml-auto">
        <SkeletonBlock className="h-7 w-7 rounded-md" />
      </div>
    </div>
  );
}

/** A single flat claims table skeleton (sticky header + body rows). */
export function ClaimsTableSkeleton({ rows = 6 }: { rows?: number } = {}) {
  return (
    <div className="animate-in fade-in duration-300">
      <div className="flex items-center gap-3 px-4 py-2.5 border-b border-border bg-background">
        <div className="w-4 shrink-0" />
        <SkeletonBlock className="h-3 w-16" />
        <SkeletonBlock className="h-3 w-20" />
        <SkeletonBlock className="h-3 w-12" />
        <SkeletonBlock className="h-3 w-16" />
        <SkeletonBlock className="h-3 w-20" />
        <SkeletonBlock className="h-3 w-12" />
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <ClaimRowSkeleton key={i} />
      ))}
    </div>
  );
}

function PartnerGroupSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="rounded-lg border border-border shadow-sm overflow-hidden">
      <div className="bg-muted/50 px-4 py-4 border-b border-border">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <SkeletonBlock className="h-5 w-5 rounded" />
            <SkeletonBlock className="h-5 w-48" />
            <SkeletonBlock className="h-5 w-16 rounded-full" />
          </div>
          <SkeletonBlock className="h-4 w-28" />
        </div>
      </div>
      <div className="flex items-center gap-3 px-4 py-2.5 border-b border-border bg-muted/20">
        <div className="w-4 shrink-0" />
        <SkeletonBlock className="h-3 w-10" />
        <SkeletonBlock className="h-3 w-16" />
        <SkeletonBlock className="h-3 w-16" />
        <SkeletonBlock className="h-3 w-12" />
        <SkeletonBlock className="h-3 w-12" />
        <SkeletonBlock className="h-3 w-12" />
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <ClaimRowSkeleton key={i} />
      ))}
    </div>
  );
}

/** Partner-grouped claims skeleton (multiple cards, each with its own header + table). */
export function PartnerGroupedClaimsSkeleton() {
  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <PartnerGroupSkeleton rows={3} />
      <PartnerGroupSkeleton rows={2} />
      <PartnerGroupSkeleton rows={4} />
    </div>
  );
}

/** Skeleton for the expanded claim row detail (eligible/ineligible incentives list). */
export function ClaimDetailSkeleton() {
  return (
    <div className="flex justify-center py-4 animate-in fade-in duration-200">
      <div className="w-full max-w-2xl space-y-3">
        <div className="flex items-center gap-1 w-fit mx-auto">
          <SkeletonBlock className="h-9 w-28 rounded-lg" />
          <SkeletonBlock className="h-9 w-28 rounded-lg" />
        </div>
        <div className="space-y-1.5">
          {Array.from({ length: 3 }).map((_, i) => (
            <div
              key={i}
              className="flex items-center justify-between rounded-lg border border-border bg-muted/20 px-3 py-2"
            >
              <div className="flex items-center gap-2">
                <SkeletonBlock className="h-5 w-16 rounded-full" />
                <SkeletonBlock className="h-4 w-44" />
              </div>
              <div className="flex flex-col items-end gap-1">
                <SkeletonBlock className="h-4 w-20" />
                <SkeletonBlock className="h-3 w-12" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
