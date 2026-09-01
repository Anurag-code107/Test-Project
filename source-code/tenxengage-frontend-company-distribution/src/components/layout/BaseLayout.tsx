import { Outlet, useLocation } from "react-router-dom";
import { useEffect, useRef } from "react";
import type { ReactNode } from "react";

const prefersReducedMotion = () =>
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;
import { useRewardCurrencies } from "@/hooks/useRewardCurrencyApi";
import { useAuth } from "@/hooks/useAuth";

interface BaseLayoutProps {
  sidebar: ReactNode;
}

function BaseLayout({ sidebar }: BaseLayoutProps) {
  // Skip currency hydration for TenX Admin — they have no clientId/tenant context
  const { user } = useAuth();
  const isTenxAdmin = user?.clientId === null;
  useRewardCurrencies({ enabled: !isTenxAdmin });
  const { pathname } = useLocation();
  const containerRef = useRef<HTMLDivElement>(null);
  const mainRef = useRef<HTMLDivElement>(null);
  const prevPathRef = useRef(pathname);

  // Replay the staggered fade-up animation on route change
  useEffect(() => {
    if (pathname === prevPathRef.current) return;
    prevPathRef.current = pathname;

    // Scroll main content area to top on navigation
    mainRef.current?.scrollTo({ top: 0 });

    const el = containerRef.current;
    if (!el || prefersReducedMotion())
      return;

    // Remove and re-add the animation class to retrigger child staggers
    el.classList.remove("animate-route-in");
    void el.offsetWidth;
    el.classList.add("animate-route-in");
  }, [pathname]);

  // Full-bleed pages manage their own padding; skip the default p-8
  const isFullBleed = pathname.startsWith("/builder");
  // Padding lives on the inner grid wrapper (not on <main>) so that
  // padding-bottom is part of the scrollable content — otherwise Chrome and
  // Safari crop the bottom padding when content overflows and the user
  // scrolls to the bottom of the page.
  const innerPadding = isFullBleed ? "" : "p-8";

  return (
    <div className="flex h-screen overflow-hidden">
      {sidebar}
      <div className="flex flex-1 flex-col overflow-hidden">
        <main
          ref={mainRef}
          aria-label="Main content"
          className="flex-1 overflow-y-auto overscroll-contain"
          style={{ scrollbarGutter: "stable" }}
        >
          {/*
            Auto-adapting wrapper:

            • Default: `min-h-full` so the wrapper grows with content. Pages that
              flow naturally (Home, Activity Log, Dashboard, …) push the wrapper
              past viewport when their content is taller, <main> scrolls, and
              because padding lives on the wrapper, padding-bottom stays below
              the last element when scrolled.
            • When a page's root uses `h-full` (Incentive Builder, Manage
              Incentives, Activity Review, Profile, …), the `has-[>.h-full]:h-full`
              variant switches the wrapper to a definite 100% height. This lets
              the page's `h-full` chain resolve all the way down so its internal
              flex-1 scroll regions work, and <main> never scrolls.

            No route list, no hardcoding — adding a new dynamically-configured
            page just means the page author picks `h-full` (fit viewport) or not
            (natural scroll), and the wrapper adapts automatically.
          */}
          <div
            ref={containerRef}
            className={`flex flex-col min-h-full has-[>.h-full]:h-full animate-route-in ${innerPadding}`}
          >
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

export default BaseLayout;
