export interface BrandingConfig {
  primary: string;
  primaryLight: string;
  secondary: string;
  accent: string;
  success: string;
  warning: string;
  destructive: string;
  background: string;
  foreground: string;
  muted: string;
  mutedForeground: string;
  card: string;
  cardForeground: string;
  border: string;
  headingFont: string;
  bodyFont: string;
  logoUrl: string | null;
}

export type UpdateBrandingRequest = Omit<BrandingConfig, "logoUrl">;

export const LOGO_MAX_SIZE_BYTES = 2 * 1024 * 1024;
export const LOGO_ACCEPTED_MIME_TYPES = [
  "image/png",
  "image/jpeg",
  "image/svg+xml",
  "image/webp",
] as const;

export const DEFAULT_BRANDING: BrandingConfig = {
  primary: "217 91% 60%",
  primaryLight: "199 89% 48%",
  secondary: "210 40% 96%",
  accent: "210 40% 96%",
  success: "142 76% 36%",
  warning: "38 92% 50%",
  destructive: "0 84% 60%",
  background: "0 0% 100%",
  foreground: "222 47% 11%",
  muted: "210 40% 96%",
  mutedForeground: "215 16% 47%",
  card: "0 0% 100%",
  cardForeground: "222 47% 11%",
  border: "214 32% 91%",
  headingFont: "Inter",
  bodyFont: "Inter",
  logoUrl: null,
};

export const BRANDING_COLOR_KEYS: ReadonlyArray<
  Exclude<keyof BrandingConfig, "headingFont" | "bodyFont" | "logoUrl">
> = [
  "primary",
  "primaryLight",
  "secondary",
  "accent",
  "success",
  "warning",
  "destructive",
  "background",
  "foreground",
  "muted",
  "mutedForeground",
  "card",
  "cardForeground",
  "border",
];

const CAMEL_TO_KEBAB_CACHE: Record<string, string> = {};

export function brandingKeyToCssVar(
  key: Exclude<keyof BrandingConfig, "headingFont" | "bodyFont" | "logoUrl">,
): string {
  if (!CAMEL_TO_KEBAB_CACHE[key]) {
    CAMEL_TO_KEBAB_CACHE[key] = `--${key.replace(/([A-Z])/g, "-$1").toLowerCase()}`;
  }
  return CAMEL_TO_KEBAB_CACHE[key]!;
}
