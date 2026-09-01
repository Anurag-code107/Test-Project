import { useState, useRef, useCallback, useEffect } from "react";
import { createPortal } from "react-dom";
import { useLocation } from "react-router-dom";
import { cn } from "@/lib/utils";
import { GuardedNavLink } from "@/components/layout/sidebars/GuardedNavLink";
import { SidebarTooltip } from "@/components/layout/sidebars/SidebarTooltip";

interface FlyoutItem {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}

interface SidebarFlyoutProps {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  items: FlyoutItem[];
  isGroupActive: boolean;
}

/**
 * Collapsed sidebar group that opens a click-triggered flyout panel
 * showing the group title and its sub-item links.
 *
 * The flyout is portalled to <body> so it escapes overflow:hidden,
 * and closes on outside click, Escape, or navigation.
 */
export function SidebarFlyout({
  icon: Icon,
  label,
  items,
  isGroupActive,
}: SidebarFlyoutProps) {
  const [open, setOpen] = useState(false);
  const [suppressTooltip, setSuppressTooltip] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(
    null,
  );
  const location = useLocation();

  /* ── Position the panel next to the trigger ── */
  const openFlyout = useCallback(() => {
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect();
      setCoords({
        top: rect.top,
        left: rect.right + 8,
      });
    }
    setOpen(true);
  }, []);

  /* ── Close on outside click ── */
  useEffect(() => {
    if (!open) return;

    function handlePointerDown(e: PointerEvent) {
      if (
        panelRef.current &&
        !panelRef.current.contains(e.target as Node) &&
        triggerRef.current &&
        !triggerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  /* ── Close on navigation (suppress tooltip so it doesn't linger) ── */
  const prevPathRef = useRef(location.pathname);
  useEffect(() => {
    if (location.pathname !== prevPathRef.current) {
      prevPathRef.current = location.pathname;
      if (open) {
        setOpen(false);
        setSuppressTooltip(true);
        setTimeout(() => setSuppressTooltip(false), 300);
      }
    }
  }, [location.pathname, open]);

  return (
    <>
      {/* Icon trigger — tooltip when closed, no tooltip when flyout is open */}
      <SidebarTooltip label={label} enabled={!open && !suppressTooltip}>
        <button
          ref={triggerRef}
          type="button"
          onClick={open ? () => setOpen(false) : openFlyout}
          className={cn(
            "group relative flex items-center justify-center rounded-lg px-2 py-2.5 text-sm transition-colors duration-150 w-full",
            isGroupActive || open
              ? "text-primary font-medium bg-primary/5"
              : "text-muted-foreground hover:text-foreground hover:bg-muted",
          )}
        >
          {/* Active indicator bar */}
          <span
            className={cn(
              "absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full transition-[height,background-color] duration-200",
              isGroupActive
                ? "h-4 bg-primary"
                : "h-0 bg-transparent",
            )}
          />
          <Icon className="h-5 w-5 shrink-0" />
        </button>
      </SidebarTooltip>

      {/* Flyout panel */}
      {open &&
        coords &&
        createPortal(
          <div
            ref={panelRef}
            className="sidebar-flyout"
            style={{
              position: "fixed",
              top: coords.top,
              left: coords.left,
            }}
          >
            <div className="sidebar-flyout-content">
              {/* Group title */}
              <p className="px-2.5 pt-1.5 pb-1 text-xs font-semibold tracking-[0.04em] uppercase text-muted-foreground">
                {label}
              </p>

              {/* Sub-item links */}
              <div className="space-y-0.5">
                {items.map((item) => (
                  <FlyoutLink key={item.to} {...item} />
                ))}
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

/* ── Individual link inside the flyout ── */
function FlyoutLink({ to, icon: Icon, label }: FlyoutItem) {
  return (
    <GuardedNavLink
      to={to}
      className={({ isActive }) =>
        cn(
          "flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm transition-colors duration-150 w-full",
          isActive
            ? "text-primary font-medium bg-primary/5"
            : "text-foreground hover:text-foreground hover:bg-muted",
        )
      }
    >
      <Icon className="h-4 w-4 shrink-0" />
      <span className="truncate">{label}</span>
    </GuardedNavLink>
  );
}
