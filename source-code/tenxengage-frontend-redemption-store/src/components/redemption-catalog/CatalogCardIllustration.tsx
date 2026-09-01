import { useState } from "react";
import type { CatalogCategory } from "@/types/redemption-catalog.types";

interface Props {
  category: CatalogCategory;
  imageUrl?: string | null;
  catalogItemId?: string;
  /**
   * Brand image from the item's gift-card SKU. Used when the client admin uploaded no image of their
   * own — or when that upload fails to load — before falling back to the category illustration.
   */
  providerImageUrl?: string | null;
}

function CashIllustration() {
  return (
    <svg viewBox="0 0 96 120" fill="none" className="w-full h-full" aria-hidden="true">
      {/* Flowing curve */}
      <path
        d="M-5 90 C20 65, 55 95, 100 70"
        stroke="hsl(217 91% 60% / 0.18)"
        strokeWidth="1"
      />
      {/* Coin stack — bottom */}
      <ellipse cx="48" cy="72" rx="22" ry="5.5"
        fill="hsl(217 91% 60% / 0.08)"
        stroke="hsl(217 91% 60% / 0.18)" strokeWidth="0.8" />
      <rect x="26" y="54" width="44" height="18" rx="2"
        fill="hsl(217 91% 60% / 0.06)"
        stroke="hsl(217 91% 60% / 0.14)" strokeWidth="0.8" />
      {/* Coin stack — middle */}
      <ellipse cx="48" cy="54" rx="22" ry="5.5"
        fill="hsl(217 91% 60% / 0.10)"
        stroke="hsl(217 91% 60% / 0.18)" strokeWidth="0.8" />
      <rect x="26" y="38" width="44" height="16" rx="2"
        fill="hsl(217 91% 60% / 0.08)"
        stroke="hsl(217 91% 60% / 0.14)" strokeWidth="0.8" />
      {/* Coin stack — top */}
      <ellipse cx="48" cy="38" rx="22" ry="5.5"
        fill="hsl(217 91% 60% / 0.13)"
        stroke="hsl(217 91% 60% / 0.22)" strokeWidth="0.8" />
      {/* Dollar sign on top coin */}
      <text x="44" y="42" fontSize="9" fill="hsl(217 91% 60% / 0.35)"
        fontFamily="-apple-system, sans-serif" fontWeight="600">$</text>
      {/* Decorative dots */}
      <circle cx="18" cy="36" r="2" fill="hsl(217 91% 60% / 0.15)" />
      <circle cx="80" cy="88" r="1.5" fill="hsl(217 91% 60% / 0.12)" />
      <circle cx="88" cy="44" r="1.5" fill="hsl(217 91% 60% / 0.10)" />
    </svg>
  );
}

function NonCashIllustration() {
  return (
    <svg viewBox="0 0 96 120" fill="none" className="w-full h-full" aria-hidden="true">
      {/* Flowing curve */}
      <path
        d="M-5 85 C25 60, 65 90, 100 65"
        stroke="hsl(147 50% 42% / 0.16)"
        strokeWidth="1"
      />
      {/* Gift box body */}
      <rect x="26" y="58" width="44" height="30" rx="3"
        fill="hsl(147 50% 42% / 0.06)"
        stroke="hsl(147 50% 42% / 0.18)" strokeWidth="0.8" />
      {/* Gift box lid */}
      <rect x="22" y="50" width="52" height="10" rx="2"
        fill="hsl(147 50% 42% / 0.10)"
        stroke="hsl(147 50% 42% / 0.20)" strokeWidth="0.8" />
      {/* Ribbon — vertical */}
      <line x1="48" y1="50" x2="48" y2="88"
        stroke="hsl(147 50% 42% / 0.22)" strokeWidth="1.5" />
      {/* Ribbon — horizontal on lid */}
      <line x1="22" y1="55" x2="74" y2="55"
        stroke="hsl(147 50% 42% / 0.16)" strokeWidth="1" />
      {/* Bow — left loop */}
      <path d="M48 50 C40 40, 28 42, 30 49"
        stroke="hsl(147 50% 42% / 0.24)" strokeWidth="1.2" fill="none" />
      {/* Bow — right loop */}
      <path d="M48 50 C56 40, 68 42, 66 49"
        stroke="hsl(147 50% 42% / 0.24)" strokeWidth="1.2" fill="none" />
      {/* Decorative dots */}
      <circle cx="18" cy="42" r="2" fill="hsl(147 50% 42% / 0.14)" />
      <circle cx="80" cy="82" r="1.5" fill="hsl(147 50% 42% / 0.10)" />
      <circle cx="76" cy="40" r="1.5" fill="hsl(147 50% 42% / 0.10)" />
    </svg>
  );
}

export function CatalogCardIllustration({
  category,
  imageUrl,
  catalogItemId,
  providerImageUrl,
}: Props) {
  // Display precedence: the client admin's upload, then the SKU's brand image, then the illustration.
  // The uploaded image is always read through the API proxy — `imageUrl` only signals that one exists.
  const proxyUrl = imageUrl && catalogItemId
    ? `/api/v1/admin/redemption-catalog/${catalogItemId}/image`
    : null;
  const candidates = [proxyUrl, providerImageUrl || null].filter((u): u is string => !!u);

  // Track failures by URL rather than by index: a src that 404s or is a dead vendor link drops out of
  // the running, and a later prop change that introduces a new URL gets its own attempt.
  const [failed, setFailed] = useState<string[]>([]);
  const src = candidates.find((u) => !failed.includes(u));

  const tint = category === "CASH" ? "bg-[hsl(217_91%_97%)]" : "bg-[hsl(147_50%_97%)]";

  if (src) {
    const isUpload = src === proxyUrl;
    return (
      <img
        src={src}
        alt=""
        aria-hidden="true"
        // An uploaded image is artwork sized for the slot, so it fills it. A brand logo is usually wide
        // and transparent — contain it over the category tint so it isn't cropped or floating.
        className={isUpload ? "w-full h-full object-cover" : `w-full h-full object-contain p-3 ${tint}`}
        onError={() => setFailed((prev) => (prev.includes(src) ? prev : [...prev, src]))}
        data-testid={isUpload ? "catalog-card-custom-image" : "catalog-card-provider-image"}
      />
    );
  }

  return (
    <div
      className={`w-full h-full flex items-center justify-center ${tint}`}
      data-testid={`catalog-card-illustration-${category.toLowerCase()}`}
    >
      {category === "CASH" ? <CashIllustration /> : <NonCashIllustration />}
    </div>
  );
}
