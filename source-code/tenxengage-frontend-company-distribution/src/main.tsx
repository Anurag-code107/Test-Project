import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/contexts/AuthContext";
import { BrandingProvider } from "@/contexts/BrandingContext";
import { GuidedTourProvider } from "@/contexts/GuidedTourContext";
import { NavigationGuardProvider } from "@/contexts/NavigationGuardContext";
import { GuidedTourOverlay } from "@/components/guided-tour/GuidedTourOverlay";
import { Toaster } from "sonner";
import App from "./App";
import "./index.css";
import { lazy, Suspense } from "react";

const BUG_REPORTER_ENABLED = import.meta.env.DEV || import.meta.env.VITE_ENABLE_BUG_REPORTER === 'true';

const DevBugReporter = BUG_REPORTER_ENABLED
  ? lazy(() => import("@/dev/bug-reporter").then(m => ({ default: m.DevBugReporter })))
  : null;
import { initScrollbarAutoHide } from "@/lib/scrollbar-auto-hide";

initScrollbarAutoHide();

if (BUG_REPORTER_ENABLED) {
  import("@/dev/bug-reporter").then(({ installConsoleInterceptor, installNetworkInterceptor }) => {
    installConsoleInterceptor();
    installNetworkInterceptor();
  });
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000,
    },
  },
});

if (BUG_REPORTER_ENABLED) {
  // Expose queryClient for DevBugReporter context probe
  (window as unknown as { __QUERY_CLIENT__: typeof queryClient }).__QUERY_CLIENT__ = queryClient;
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrandingProvider>
            <NavigationGuardProvider>
              <GuidedTourProvider>
                <App />
                <GuidedTourOverlay />
                <Toaster richColors position="top-right" />
                {DevBugReporter && <Suspense fallback={null}><DevBugReporter /></Suspense>}
              </GuidedTourProvider>
            </NavigationGuardProvider>
          </BrandingProvider>
        </AuthProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
