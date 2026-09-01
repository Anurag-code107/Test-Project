import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "@/hooks/useAuth";
import { useBranding } from "@/hooks/useBrandingApi";
import {
  BRANDING_COLOR_KEYS,
  DEFAULT_BRANDING,
  brandingKeyToCssVar,
  type BrandingConfig,
} from "@/types/branding.types";
import webLogo from "@/assets/web_logo.png";

interface BrandingContextValue {
  branding: BrandingConfig;
  logoSrc: string;
  isLoading: boolean;
  /**
   * Preview override applied platform-wide while a Client Admin edits
   * branding settings. Pass a config to preview, or `null` to revert to the
   * saved branding. Cleared on logout and on save.
   */
  setDraftBranding: (draft: BrandingConfig | null) => void;
}

const BrandingContext = createContext<BrandingContextValue | null>(null);

interface BrandingProviderProps {
  children: ReactNode;
}

export function BrandingProvider({ children }: BrandingProviderProps) {
  const { isAuthenticated } = useAuth();
  const { data, isLoading } = useBranding(isAuthenticated);
  const [draft, setDraft] = useState<BrandingConfig | null>(null);

  const branding = draft ?? data ?? DEFAULT_BRANDING;

  useEffect(() => {
    applyBranding(branding);
  }, [branding]);

  useEffect(() => {
    if (!isAuthenticated) {
      setDraft(null);
      applyBranding(DEFAULT_BRANDING);
    }
  }, [isAuthenticated]);

  const setDraftBranding = useCallback((next: BrandingConfig | null) => {
    setDraft(next);
  }, []);

  const value = useMemo<BrandingContextValue>(
    () => ({
      branding,
      logoSrc: branding.logoUrl ?? webLogo,
      isLoading,
      setDraftBranding,
    }),
    [branding, isLoading, setDraftBranding],
  );

  return (
    <BrandingContext.Provider value={value}>
      {children}
    </BrandingContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useBrandingContext(): BrandingContextValue {
  const context = useContext(BrandingContext);
  if (!context) {
    throw new Error(
      "useBrandingContext must be used within a BrandingProvider",
    );
  }
  return context;
}

function applyBranding(branding: BrandingConfig) {
  const root = document.documentElement;
  for (const key of BRANDING_COLOR_KEYS) {
    root.style.setProperty(brandingKeyToCssVar(key), branding[key]);
  }
  root.style.setProperty("--popover", branding.card);
  root.style.setProperty("--popover-foreground", branding.cardForeground);
  root.style.setProperty("--input", branding.border);
  root.style.setProperty("--ring", branding.primary);
  root.style.setProperty(
    "--primary-foreground",
    contrastForeground(branding.primary),
  );
  root.style.setProperty(
    "--secondary-foreground",
    contrastForeground(branding.secondary),
  );
  root.style.setProperty(
    "--accent-foreground",
    contrastForeground(branding.accent),
  );
  root.style.setProperty(
    "--destructive-foreground",
    contrastForeground(branding.destructive),
  );
  root.style.setProperty("--font-heading", branding.headingFont);
  root.style.setProperty("--font-body", branding.bodyFont);
}

/**
 * Given a brand color in "H S% L%" format, return a foreground color
 * ("0 0% 100%" white or "200 15% 15%" near-black) with sufficient contrast.
 * Threshold tuned so medium-saturation blues/reds (L≈55–60) still pick white.
 */
function contrastForeground(hslString: string): string {
  const parts = hslString.trim().split(/\s+/);
  const lightness = parseFloat((parts[2] ?? "").replace("%", ""));
  if (Number.isNaN(lightness)) return "0 0% 100%";
  return lightness < 65 ? "0 0% 100%" : "200 15% 15%";
}
