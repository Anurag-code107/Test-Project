import { Outlet, NavLink, useLocation } from "react-router-dom";
import { useEffect, useRef } from "react";
import { Home, Building2, Layers, LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";

const navItems = [
  { to: "/dashboard", icon: Home, label: "Dashboard" },
  { to: "/clients", icon: Building2, label: "Manage Clients" },
  { to: "/subscriptions", icon: Layers, label: "Subscriptions" },
];

function AdminLayout() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const mainRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const prevPathRef = useRef(pathname);

  // Replay the staggered fade-up animation on route change
  useEffect(() => {
    if (pathname === prevPathRef.current) return;
    prevPathRef.current = pathname;

    mainRef.current?.scrollTo({ top: 0 });

    const el = containerRef.current;
    if (!el || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    el.classList.remove("animate-route-in");
    void el.offsetWidth;
    el.classList.add("animate-route-in");
  }, [pathname]);

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Sidebar */}
      <aside className="flex h-full w-[240px] shrink-0 flex-col border-r border-[hsl(195_15%_92%)] bg-[hsl(210_20%_99%)]">
        {/* Logo */}
        <div className="shrink-0 p-4">
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground text-sm font-bold">
              tX
            </div>
            <span className="text-sm font-semibold tracking-tight text-foreground">
              TenX Admin
            </span>
          </div>
        </div>

        {/* Section label */}
        <div className="px-4 pt-2 pb-1">
          <p className="text-xs font-semibold tracking-[0.08em] uppercase text-[hsl(200_10%_65%)]">
            Platform Admin
          </p>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto px-2 pb-2 space-y-0.5">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "group relative flex items-center gap-3 pl-3 pr-3 py-2 rounded-lg text-sm transition-colors duration-150 w-full",
                  isActive
                    ? "text-[hsl(217_91%_55%)] font-medium bg-[hsl(217_91%_60%/0.06)]"
                    : "text-[hsl(200_10%_46%)] hover:text-[hsl(200_15%_20%)] hover:bg-[hsl(200_10%_46%/0.04)]"
                )
              }
            >
              {({ isActive }) => (
                <>
                  {/* Active indicator bar */}
                  <span
                    className={cn(
                      "absolute left-0 top-1/2 -translate-y-1/2 w-[3px] rounded-r-full transition-[height,background-color] duration-200",
                      isActive ? "h-4 bg-[hsl(217_91%_60%)]" : "h-0 bg-transparent"
                    )}
                  />
                  <item.icon className="h-[18px] w-[18px] shrink-0 transition-colors duration-150" />
                  <span className="truncate">{item.label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* Profile / Logout */}
        <div className="shrink-0 border-t border-[hsl(195_15%_92%)] p-3">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary text-xs font-semibold">
              {user?.firstName?.charAt(0) ?? ""}
              {user?.lastName?.charAt(0) ?? ""}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-foreground">
                {user?.firstName} {user?.lastName}
              </p>
              <p className="truncate text-xs text-muted-foreground">
                {user?.email}
              </p>
            </div>
            <button
              type="button"
              onClick={logout}
              className="rounded-md p-1.5 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
              title="Sign out"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main ref={mainRef} className="flex-1 overflow-y-auto">
        <div ref={containerRef} className="h-full animate-route-in">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default AdminLayout;
