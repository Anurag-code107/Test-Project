import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Palette,
  Type,
  RotateCcw,
  Check,
  Eye,
  Sun,
  Moon,
  Image as ImageIcon,
  Upload,
  Trash2,
  Loader2,
} from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import {
  useBranding,
  useUpdateBranding,
  useUploadBrandingLogo,
  useRemoveBrandingLogo,
} from "@/hooks/useBrandingApi";
import { usePermissions } from "@/hooks/usePermissions";
import { useBrandingContext } from "@/contexts/BrandingContext";
import {
  DEFAULT_BRANDING,
  LOGO_ACCEPTED_MIME_TYPES,
  LOGO_MAX_SIZE_BYTES,
  type BrandingConfig,
} from "@/types/branding.types";
import webLogo from "@/assets/web_logo.png";

type LogoAction = "unchanged" | "replace" | "remove";

// ─── Presets ─────────────────────────────────────────────────────────────────

type ThemePreset = Omit<BrandingConfig, "logoUrl">;

const darkThemePreset: ThemePreset = {
  primary: "217 91% 60%",
  primaryLight: "199 89% 48%",
  secondary: "217 33% 17%",
  accent: "217 33% 17%",
  success: "142 71% 45%",
  warning: "38 92% 50%",
  destructive: "0 63% 31%",
  background: "222 47% 11%",
  foreground: "210 40% 98%",
  muted: "217 33% 17%",
  mutedForeground: "215 20% 65%",
  card: "222 47% 11%",
  cardForeground: "210 40% 98%",
  border: "217 33% 17%",
  headingFont: "Inter",
  bodyFont: "Inter",
};

const fontOptions = [
  "Inter",
  "Roboto",
  "Open Sans",
  "Lato",
  "Poppins",
  "Montserrat",
  "Source Sans 3",
  "Nunito",
  "Raleway",
  "Ubuntu",
  "Playfair Display",
  "Merriweather",
];

type ColorKey = Exclude<
  keyof BrandingConfig,
  "headingFont" | "bodyFont" | "logoUrl"
>;

const colorGroups: { title: string; keys: ColorKey[] }[] = [
  {
    title: "Primary Colors",
    keys: ["primary", "primaryLight", "secondary", "accent"],
  },
  { title: "Status Colors", keys: ["success", "warning", "destructive"] },
  {
    title: "Surface & Text",
    keys: [
      "background",
      "foreground",
      "muted",
      "mutedForeground",
      "card",
      "cardForeground",
      "border",
    ],
  },
];

const colorLabels: Record<ColorKey, { label: string; desc: string }> = {
  primary: {
    label: "Primary",
    desc: "Main brand color for buttons, links, accents",
  },
  primaryLight: {
    label: "Primary Light",
    desc: "Lighter variant for gradients",
  },
  secondary: { label: "Secondary", desc: "Supporting backgrounds" },
  accent: { label: "Accent", desc: "Emphasis for special elements" },
  success: { label: "Success", desc: "Positive states" },
  warning: { label: "Warning", desc: "Cautionary messages" },
  destructive: { label: "Destructive", desc: "Errors and destructive actions" },
  background: { label: "Background", desc: "Main page background" },
  foreground: { label: "Foreground", desc: "Main text color" },
  muted: { label: "Muted", desc: "Subdued backgrounds" },
  mutedForeground: { label: "Muted Text", desc: "Subdued text color" },
  card: { label: "Card", desc: "Card backgrounds" },
  cardForeground: { label: "Card Text", desc: "Card text color" },
  border: { label: "Border", desc: "Borders and dividers" },
};

// ─── Color Helpers ───────────────────────────────────────────────────────────

const hslToHex = (hsl: string): string => {
  const parts = hsl.split(" ").map((p) => parseFloat(p.replace("%", "")));
  if (parts.length !== 3) return "#3b82f6";
  const [h, s, l] = parts as [number, number, number];
  const sNorm = s / 100;
  const lNorm = l / 100;
  const c = (1 - Math.abs(2 * lNorm - 1)) * sNorm;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = lNorm - c / 2;
  let r = 0,
    g = 0,
    b = 0;
  if (h < 60) {
    r = c;
    g = x;
  } else if (h < 120) {
    r = x;
    g = c;
  } else if (h < 180) {
    g = c;
    b = x;
  } else if (h < 240) {
    g = x;
    b = c;
  } else if (h < 300) {
    r = x;
    b = c;
  } else {
    r = c;
    b = x;
  }
  const toHex = (n: number) => {
    const hex = Math.round((n + m) * 255).toString(16);
    return hex.length === 1 ? "0" + hex : hex;
  };
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
};

const hexToHsl = (hex: string): string => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!result) return "221 94% 56%";
  const r = parseInt(result[1]!, 16) / 255;
  const g = parseInt(result[2]!, 16) / 255;
  const b = parseInt(result[3]!, 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0,
    s = 0;
  const l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r:
        h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
        break;
      case g:
        h = ((b - r) / d + 2) / 6;
        break;
      case b:
        h = ((r - g) / d + 4) / 6;
        break;
    }
  }
  return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
};

// ─── Component ───────────────────────────────────────────────────────────────

export function BrandingSection() {
  const { toast } = useToast();
  const { can } = usePermissions();
  const { data: savedBranding } = useBranding();
  const { setDraftBranding } = useBrandingContext();
  const updateBranding = useUpdateBranding();
  const uploadLogo = useUploadBrandingLogo();
  const removeLogo = useRemoveBrandingLogo();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [draft, setDraft] = useState<BrandingConfig>(DEFAULT_BRANDING);
  const [hasChanges, setHasChanges] = useState(false);
  const [pendingLogoFile, setPendingLogoFile] = useState<File | null>(null);
  const [logoBlobUrl, setLogoBlobUrl] = useState<string | null>(null);
  const [logoAction, setLogoAction] = useState<LogoAction>("unchanged");

  // Hydrate draft from saved branding only when the user has no in-flight
  // edits — otherwise a refetch would clobber the preview they're working on.
  useEffect(() => {
    if (savedBranding && !hasChanges) {
      setDraft(savedBranding);
    }
  }, [savedBranding, hasChanges]);

  // Mirror the local draft into the platform-wide preview override so the
  // sidebar / loading screens / etc. reflect changes immediately.
  useEffect(() => {
    if (hasChanges) {
      setDraftBranding(draft);
    } else {
      setDraftBranding(null);
    }
  }, [draft, hasChanges, setDraftBranding]);

  // On unmount, clear the preview override and revoke any pending blob URL so
  // navigating away discards unsaved edits cleanly.
  useEffect(() => {
    return () => {
      setDraftBranding(null);
      if (logoBlobUrl) URL.revokeObjectURL(logoBlobUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const canEdit = can("action.branding.manage");

  const handleColorChange = (key: ColorKey, value: string) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
    setHasChanges(true);
  };

  const handleFontChange = (type: "heading" | "body", value: string) => {
    setDraft((prev) => ({
      ...prev,
      [type === "heading" ? "headingFont" : "bodyFont"]: value,
    }));
    setHasChanges(true);
  };

  const handleReset = () => {
    setDraft((prev) => ({ ...DEFAULT_BRANDING, logoUrl: prev.logoUrl }));
    setHasChanges(true);
    toast({ title: "Reverted to default theme — remember to save" });
  };

  const handleApplyTheme = (preset: ThemePreset) => {
    setDraft((prev) => ({ ...preset, logoUrl: prev.logoUrl }));
    setHasChanges(true);
    toast({ title: "Theme applied — remember to save" });
  };

  const handleLogoSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    if (
      !(LOGO_ACCEPTED_MIME_TYPES as readonly string[]).includes(file.type)
    ) {
      toast({
        title: "Unsupported file type",
        description: "Use a PNG, JPEG, SVG, or WebP image.",
        variant: "destructive",
      });
      return;
    }
    if (file.size > LOGO_MAX_SIZE_BYTES) {
      toast({
        title: "File too large",
        description: "Logo must be 2 MB or smaller.",
        variant: "destructive",
      });
      return;
    }

    if (logoBlobUrl) URL.revokeObjectURL(logoBlobUrl);
    const blobUrl = URL.createObjectURL(file);
    setPendingLogoFile(file);
    setLogoBlobUrl(blobUrl);
    setLogoAction("replace");
    setDraft((prev) => ({ ...prev, logoUrl: blobUrl }));
    setHasChanges(true);
    toast({ title: "Logo preview applied — remember to save" });
  };

  const handleLogoRemove = () => {
    if (logoBlobUrl) URL.revokeObjectURL(logoBlobUrl);
    setPendingLogoFile(null);
    setLogoBlobUrl(null);
    const willRemove = !!savedBranding?.logoUrl;
    setLogoAction(willRemove ? "remove" : "unchanged");
    setDraft((prev) => ({ ...prev, logoUrl: null }));
    setHasChanges(true);
    toast({
      title: willRemove
        ? "Logo will be removed — remember to save"
        : "Logo cleared",
    });
  };

  const handleSave = async () => {
    try {
      if (logoAction === "replace" && pendingLogoFile) {
        await uploadLogo.mutateAsync(pendingLogoFile);
      } else if (logoAction === "remove") {
        await removeLogo.mutateAsync();
      }

      await updateBranding.mutateAsync({
        primary: draft.primary,
        primaryLight: draft.primaryLight,
        secondary: draft.secondary,
        accent: draft.accent,
        success: draft.success,
        warning: draft.warning,
        destructive: draft.destructive,
        background: draft.background,
        foreground: draft.foreground,
        muted: draft.muted,
        mutedForeground: draft.mutedForeground,
        card: draft.card,
        cardForeground: draft.cardForeground,
        border: draft.border,
        headingFont: draft.headingFont,
        bodyFont: draft.bodyFont,
      });

      if (logoBlobUrl) URL.revokeObjectURL(logoBlobUrl);
      setLogoBlobUrl(null);
      setPendingLogoFile(null);
      setLogoAction("unchanged");
      setHasChanges(false);
      setDraftBranding(null);

      toast({
        title: "Branding settings saved and applied platform-wide",
      });
    } catch {
      toast({
        title: "Failed to save branding",
        description:
          "Please try again or contact support if the issue persists.",
        variant: "destructive",
      });
    }
  };

  const isSaving =
    updateBranding.isPending ||
    uploadLogo.isPending ||
    removeLogo.isPending;
  const saveDisabled = !canEdit || !hasChanges || isSaving;
  const logoBusy = isSaving;
  const logoSrc = draft.logoUrl ?? webLogo;

  return (
    <div className="space-y-5">
      {/* Default Themes */}
      <section className="rounded-xl border border-border p-4 space-y-3">
        <div className="flex items-center gap-2">
          <Palette className="h-4 w-4 text-primary" />
          <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
            Default Themes
          </span>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <button
            onClick={() => handleApplyTheme(DEFAULT_BRANDING)}
            className="p-4 rounded-xl border border-border hover:border-primary/25 hover:shadow-[0_2px_8px_hsl(var(--foreground)/0.04)] transition-[border-color,box-shadow] text-left"
          >
            <div className="flex items-center gap-2.5 mb-3">
              <div className="p-1.5 rounded-lg bg-[hsl(38_80%_50%/0.08)]">
                <Sun className="h-4 w-4 text-[hsl(38_80%_45%)]" />
              </div>
              <div>
                <span className="text-sm font-medium text-foreground">
                  Light Theme
                </span>
                <p className="text-xs text-muted-foreground">
                  Clean, bright interface
                </p>
              </div>
            </div>
            <div className="flex gap-1.5">
              {[
                DEFAULT_BRANDING.primary,
                DEFAULT_BRANDING.accent,
                DEFAULT_BRANDING.success,
                DEFAULT_BRANDING.warning,
                DEFAULT_BRANDING.background,
                DEFAULT_BRANDING.foreground,
              ].map((color, i) => (
                <div
                  key={i}
                  className="h-5 w-5 rounded-full border border-border"
                  style={{ backgroundColor: `hsl(${color})` }}
                />
              ))}
            </div>
          </button>
          <button
            onClick={() => handleApplyTheme(darkThemePreset)}
            className="p-4 rounded-xl border border-border hover:border-primary/25 hover:shadow-[0_2px_8px_hsl(var(--foreground)/0.04)] transition-[border-color,box-shadow] text-left"
          >
            <div className="flex items-center gap-2.5 mb-3">
              <div className="p-1.5 rounded-lg bg-[hsl(220_20%_18%)]">
                <Moon className="h-4 w-4 text-[hsl(210_20%_70%)]" />
              </div>
              <div>
                <span className="text-sm font-medium text-foreground">
                  Dark Theme
                </span>
                <p className="text-xs text-muted-foreground">
                  Sleek, dark interface
                </p>
              </div>
            </div>
            <div className="flex gap-1.5">
              {[
                darkThemePreset.primary,
                darkThemePreset.accent,
                darkThemePreset.success,
                darkThemePreset.warning,
                darkThemePreset.background,
                darkThemePreset.foreground,
              ].map((color, i) => (
                <div
                  key={i}
                  className="h-5 w-5 rounded-full border border-border"
                  style={{ backgroundColor: `hsl(${color})` }}
                />
              ))}
            </div>
          </button>
        </div>
      </section>

      {/* Logo */}
      <section className="rounded-xl border border-border p-4 space-y-4">
        <div className="flex items-center gap-2">
          <ImageIcon className="h-4 w-4 text-primary" />
          <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
            Logo
          </span>
        </div>

        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-4">
            <div className="h-16 w-32 rounded-lg border border-border bg-muted/30 flex items-center justify-center overflow-hidden shrink-0">
              <img
                src={logoSrc}
                alt="Tenant logo preview"
                className="max-h-12 max-w-[7rem] object-contain"
              />
            </div>
            <div className="space-y-0.5">
              <p className="text-sm font-medium text-foreground">
                {draft.logoUrl ? "Custom logo" : "Default tenXengage logo"}
              </p>
              <p className="text-xs text-muted-foreground">
                PNG, JPEG, SVG, or WebP — up to 2 MB. Shown in the sidebar and
                loading screens.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <input
              ref={fileInputRef}
              type="file"
              accept={LOGO_ACCEPTED_MIME_TYPES.join(",")}
              onChange={handleLogoSelect}
              className="hidden"
              disabled={!canEdit || logoBusy}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
              disabled={!canEdit || logoBusy}
              className="h-8 text-sm gap-1.5"
            >
              {uploadLogo.isPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Upload className="h-3.5 w-3.5" />
              )}
              {draft.logoUrl ? "Replace" : "Upload"}
            </Button>
            {draft.logoUrl && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={handleLogoRemove}
                disabled={!canEdit || logoBusy}
                className="h-8 text-sm gap-1.5 text-muted-foreground hover:text-destructive"
              >
                {removeLogo.isPending ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Trash2 className="h-3.5 w-3.5" />
                )}
                Remove
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* Colors */}
      <section className="rounded-xl border border-border p-4 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Palette className="h-4 w-4 text-primary" />
            <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
              Brand Colors
            </span>
          </div>
          <button
            onClick={handleReset}
            className="flex items-center gap-1.5 h-7 px-2.5 rounded-md text-xs text-muted-foreground hover:bg-primary/5 transition-colors"
          >
            <RotateCcw className="h-3 w-3" />
            Reset
          </button>
        </div>

        {colorGroups.map((group, groupIdx) => (
          <div
            key={group.title}
            className={
              groupIdx > 0 ? "pt-3 border-t border-border" : ""
            }
          >
            <span className="text-xs font-medium text-muted-foreground mb-2 block">
              {group.title}
            </span>
            <div className="space-y-1.5">
              {group.keys.map((key) => {
                const def = colorLabels[key];
                const hexValue = hslToHex(draft[key]);
                return (
                  <div
                    key={key}
                    className="flex items-center justify-between py-2 px-3 rounded-lg bg-muted/30 hover:bg-primary/5 transition-colors"
                  >
                    <div className="flex-1 min-w-0 mr-4">
                      <span className="text-sm font-medium text-foreground">
                        {def.label}
                      </span>
                      <p className="text-xs text-muted-foreground">
                        {def.desc}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <div
                        className="w-7 h-7 rounded-md border border-border"
                        style={{ backgroundColor: `hsl(${draft[key]})` }}
                      />
                      <Input
                        type="color"
                        value={hexValue}
                        onChange={(e) =>
                          handleColorChange(key, hexToHsl(e.target.value))
                        }
                        className="w-10 h-7 p-0 border-none cursor-pointer"
                      />
                      <Input
                        type="text"
                        value={draft[key]}
                        onChange={(e) => handleColorChange(key, e.target.value)}
                        placeholder="H S% L%"
                        className="w-[130px] h-7 text-xs font-mono border-border"
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </section>

      {/* Typography */}
      <section className="rounded-xl border border-border p-4 space-y-4">
        <div className="flex items-center gap-2">
          <Type className="h-4 w-4 text-primary" />
          <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
            Typography
          </span>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-sm font-medium text-foreground">
              Heading Font
            </Label>
            <Select
              value={draft.headingFont}
              onValueChange={(value) => handleFontChange("heading", value)}
            >
              <SelectTrigger className="h-8 text-sm border-border">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {fontOptions.map((font) => (
                  <SelectItem
                    key={font}
                    value={font}
                    className="text-sm"
                    style={{ fontFamily: font }}
                  >
                    {font}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              Used for page titles and section headers
            </p>
          </div>
          <div className="space-y-1.5">
            <Label className="text-sm font-medium text-foreground">
              Body Font
            </Label>
            <Select
              value={draft.bodyFont}
              onValueChange={(value) => handleFontChange("body", value)}
            >
              <SelectTrigger className="h-8 text-sm border-border">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {fontOptions.map((font) => (
                  <SelectItem
                    key={font}
                    value={font}
                    className="text-sm"
                    style={{ fontFamily: font }}
                  >
                    {font}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              Used for paragraphs and general text
            </p>
          </div>
        </div>

        <div className="p-4 rounded-lg bg-muted/30 border border-border">
          <div className="flex items-center gap-1.5 mb-2">
            <Eye className="h-3 w-3 text-muted-foreground" />
            <span className="text-xs font-semibold text-muted-foreground tracking-[0.06em] uppercase">
              Preview
            </span>
          </div>
          <h3
            className="text-lg font-semibold text-foreground"
            style={{
              fontFamily: `${draft.headingFont}, system-ui, sans-serif`,
            }}
          >
            Heading Font Preview
          </h3>
          <p
            className="text-sm text-muted-foreground mt-1"
            style={{ fontFamily: `${draft.bodyFont}, system-ui, sans-serif` }}
          >
            This is how your body text will appear throughout the platform. The
            quick brown fox jumps over the lazy dog.
          </p>
        </div>
      </section>

      {/* Save */}
      <div className="flex justify-end">
        <Button
          onClick={handleSave}
          disabled={saveDisabled}
          className="h-8 text-sm gap-1.5 bg-primary hover:bg-primary/90"
        >
          <Check className="h-3.5 w-3.5" />
          {updateBranding.isPending ? "Saving..." : "Save Branding Settings"}
        </Button>
      </div>
    </div>
  );
}
