# Catalog Card Image & Horizontal Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `imageUrl` to the partner catalog browse response, create category-themed inline SVG illustrations as defaults, and redesign `CatalogItemCard` from a vertical card to a horizontal layout (image left, content right).

**Architecture:** Three layers: (1) BE adds `imageUrl` to `CatalogBrowseItemResponse` and contracts YAML; (2) FE creates a `CatalogCardIllustration` component that renders a custom image when `imageUrl` is set or a category-themed inline SVG otherwise; (3) `CatalogItemCard` is redesigned to a flex-row layout consuming the illustration component. No new API endpoints — pure extension of existing DTOs.

**Tech Stack:** Java 21 records (BE), React 18 + TypeScript, Tailwind CSS, Vitest + Testing Library (FE), OpenAPI YAML (contracts)

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/tenxengage/app/dto/response/CatalogBrowseItemResponse.java` | Modify | Add `imageUrl` field + map in `from()` |
| `endpoints/redemption-catalog.yaml` (contracts) | Modify | Add `imageUrl` to `CatalogBrowseItemResponse` schema |
| `src/types/redemption-catalog.types.ts` (FE) | Modify | Add `imageUrl?: string \| null` to interface |
| `src/components/redemption-catalog/CatalogCardIllustration.tsx` | **Create** | Inline SVG illustrations + custom image renderer |
| `src/components/redemption-catalog/CatalogItemCard.tsx` | Modify | Horizontal layout — illustration left, data right |
| `src/components/redemption-catalog/CatalogBrowseGrid.tsx` | Modify | Skeleton shape updated to match horizontal card |
| `src/components/redemption-catalog/__tests__/CatalogItemCard.test.tsx` | Modify | Add `imageUrl` to fixture; add illustration tests |

---

## Task 1: BE — Add imageUrl to CatalogBrowseItemResponse

**Files:**
- Modify: `src/main/java/com/tenxengage/app/dto/response/CatalogBrowseItemResponse.java`

- [ ] **Step 1: Add `imageUrl` field to the record**

Replace the current record declaration (lines 13–25) with:

```java
public record CatalogBrowseItemResponse(
        UUID id,
        String name,
        String description,
        String imageUrl,
        RedemptionCategory category,
        String currencyId,
        BigDecimal effectiveMinTransactionAmount,
        RedemptionProcessingMode effectiveProcessingMode,
        String estimatedPayoutTimeline,
        boolean canAfford,
        BigDecimal shortfallAmount,
        String[] geographicScope
) {
```

- [ ] **Step 2: Map `imageUrl` in the `from()` factory**

In the `from()` method, the `return new CatalogBrowseItemResponse(...)` call currently passes 11 arguments (lines 49–61). Replace it with:

```java
        return new CatalogBrowseItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getImageUrl(),
                item.getCategory(),
                item.getCurrencyId(),
                effectiveMinTxAmount,
                effectiveMode,
                buildPayoutTimeline(effectiveMode, batchCadence),
                canAfford,
                shortfallAmount,
                item.getGeographicScope()
        );
```

- [ ] **Step 3: Verify compilation**

```bash
cd ../tenxengage-backend
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL` — no errors.

- [ ] **Step 4: Run existing service + controller tests to confirm no regressions**

```bash
./gradlew test --tests "com.tenxengage.app.service.*CatalogBrowse*" --tests "com.tenxengage.app.controller.*Catalog*"
```

Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tenxengage/app/dto/response/CatalogBrowseItemResponse.java
git commit -m "feat(BE): add imageUrl to CatalogBrowseItemResponse"
```

---

## Task 2: Contracts — Add imageUrl to browse response schema

**Files:**
- Modify: `endpoints/redemption-catalog.yaml` in `tenxengage-contracts` repo

- [ ] **Step 1: Add `imageUrl` property to `CatalogBrowseItemResponse` schema**

In `endpoints/redemption-catalog.yaml`, inside the `CatalogBrowseItemResponse.properties` block (after `description`, before `category` — currently around line 851), add:

```yaml
        imageUrl:
          type: string
          nullable: true
          description: >
            URL of the catalog item's uploaded image.
            Null when no custom image has been uploaded — FE falls back to
            the category-themed inline SVG illustration.
```

The relevant section after the change should read:

```yaml
    CatalogBrowseItemResponse:
      type: object
      description: >
        Partner browse response. Never includes providerItemId, syncMetadata,
        xoxodayLastSyncedAt, minWalletBalance, or client_id.
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
        imageUrl:
          type: string
          nullable: true
          description: >
            URL of the catalog item's uploaded image.
            Null when no custom image has been uploaded — FE falls back to
            the category-themed inline SVG illustration.
        category:
          type: string
          enum: [CASH, NON_CASH]
        # ... rest unchanged
```

- [ ] **Step 2: Commit contracts**

```bash
cd ../tenxengage-contracts
git add endpoints/redemption-catalog.yaml
git commit -m "contracts: add imageUrl to CatalogBrowseItemResponse schema"
```

---

## Task 3: FE — Add imageUrl to TypeScript type

**Files:**
- Modify: `src/types/redemption-catalog.types.ts` (line 126–142)

- [ ] **Step 1: Add `imageUrl` to the interface**

In `src/types/redemption-catalog.types.ts`, update `CatalogBrowseItemResponse` (currently starting at line 126):

```ts
export interface CatalogBrowseItemResponse {
  id: string;
  name: string;
  description?: string;
  imageUrl?: string | null;
  category: CatalogCategory;
  currencyId: string;
  effectiveMinTransactionAmount: string;
  effectiveProcessingMode: ProcessingMode;
  estimatedPayoutTimeline: string;
  canAfford: boolean;
  shortfallAmount: string;
  geographicScope: string[];
  isReturnable: boolean;
  effectiveReturnWindowDays: number;
  createdAt: string;
  updatedAt: string;
}
```

- [ ] **Step 2: Verify TypeScript compiles cleanly**

```bash
cd ../tenxengage-frontend
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/types/redemption-catalog.types.ts
git commit -m "feat(FE): add imageUrl to CatalogBrowseItemResponse type"
```

---

## Task 4: FE — Create CatalogCardIllustration component

**Files:**
- Create: `src/components/redemption-catalog/CatalogCardIllustration.tsx`

This component owns three responsibilities:
1. If `imageUrl` is set → render `<img>` filling the slot
2. If `category === "CASH"` → render blue coin-stack inline SVG
3. If `category === "NON_CASH"` → render green gift-box inline SVG

- [ ] **Step 1: Create the file with both SVGs and the export**

Create `src/components/redemption-catalog/CatalogCardIllustration.tsx`:

```tsx
import type { CatalogCategory } from "@/types/redemption-catalog.types";

interface Props {
  category: CatalogCategory;
  imageUrl?: string | null;
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

export function CatalogCardIllustration({ category, imageUrl }: Props) {
  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt=""
        aria-hidden="true"
        className="w-full h-full object-cover"
        data-testid="catalog-card-custom-image"
      />
    );
  }

  return (
    <div
      className={[
        "w-full h-full flex items-center justify-center",
        category === "CASH"
          ? "bg-[hsl(217_91%_97%)]"
          : "bg-[hsl(147_50%_97%)]",
      ].join(" ")}
      data-testid={`catalog-card-illustration-${category.toLowerCase()}`}
    >
      {category === "CASH" ? <CashIllustration /> : <NonCashIllustration />}
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/components/redemption-catalog/CatalogCardIllustration.tsx
git commit -m "feat(FE): add CatalogCardIllustration — category SVGs + custom image fallback"
```

---

## Task 5: FE — Redesign CatalogItemCard + update tests

**Files:**
- Modify: `src/components/redemption-catalog/CatalogItemCard.tsx`
- Modify: `src/components/redemption-catalog/__tests__/CatalogItemCard.test.tsx`

The existing card is a vertical shadcn `<Card>`. Replace with a `flex-row` `<div>`: illustration on the left (fixed `w-32`), content on the right.

- [ ] **Step 1: Write the new failing tests first**

Replace the content of `src/components/redemption-catalog/__tests__/CatalogItemCard.test.tsx`:

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { CatalogItemCard } from "@/components/redemption-catalog/CatalogItemCard";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

vi.mock("@/components/redemption-catalog/ShortfallBadge", () => ({
  ShortfallBadge: ({ shortfallAmount, currencyId }: { shortfallAmount: string; currencyId: string }) => (
    <span data-testid="shortfall-badge">{shortfallAmount} {currencyId} short</span>
  ),
}));

vi.mock("@/components/redemption-catalog/CatalogCardIllustration", () => ({
  CatalogCardIllustration: ({ category, imageUrl }: { category: string; imageUrl?: string | null }) => (
    <div data-testid={imageUrl ? "catalog-card-custom-image" : `catalog-card-illustration-${category.toLowerCase()}`} />
  ),
}));

const BASE_ITEM: CatalogBrowseItemResponse = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  imageUrl: null,
  effectiveMinTransactionAmount: "50",
  effectiveProcessingMode: "INSTANT",
  estimatedPayoutTimeline: "Instant delivery",
  canAfford: true,
  shortfallAmount: "0",
  geographicScope: ["US"],
  isReturnable: false,
  effectiveReturnWindowDays: 0,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

describe("CatalogItemCard", () => {
  it("renders item name and payout timeline", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.getByTestId("item-name-item-1").textContent).toBe("Amazon Gift Card");
    expect(screen.getByTestId("payout-timeline-item-1").textContent).toContain("Instant delivery");
  });

  it("shows category-themed illustration when imageUrl is null", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.getByTestId("catalog-card-illustration-non_cash")).toBeDefined();
  });

  it("shows category-themed illustration for CASH when imageUrl is null", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, category: "CASH" }} />);
    expect(screen.getByTestId("catalog-card-illustration-cash")).toBeDefined();
  });

  it("shows custom image when imageUrl is provided", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, imageUrl: "https://cdn.example.com/img.jpg" }} />);
    expect(screen.getByTestId("catalog-card-custom-image")).toBeDefined();
  });

  it("renders ShortfallBadge when canAfford is false", () => {
    render(<CatalogItemCard item={{ ...BASE_ITEM, canAfford: false, shortfallAmount: "25" }} />);
    expect(screen.getByTestId("shortfall-badge")).toBeDefined();
  });

  it("does not render ShortfallBadge when canAfford is true", () => {
    render(<CatalogItemCard item={BASE_ITEM} />);
    expect(screen.queryByTestId("shortfall-badge")).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to confirm they fail (illustration tests fail because component unchanged)**

```bash
npm run test -- CatalogItemCard --run
```

Expected: 2 new illustration tests FAIL — `catalog-card-illustration-non_cash` not found.

- [ ] **Step 3: Rewrite CatalogItemCard with horizontal layout**

Replace the entire content of `src/components/redemption-catalog/CatalogItemCard.tsx`:

```tsx
import { Clock } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { getCurrency } from "@/config/currencies";
import { ShortfallBadge } from "./ShortfallBadge";
import { CatalogCardIllustration } from "./CatalogCardIllustration";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";

interface Props {
  item: CatalogBrowseItemResponse;
  onClick?: () => void;
}

export function CatalogItemCard({ item, onClick }: Props) {
  const formatted = getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount);

  return (
    <div
      className="flex overflow-hidden rounded-lg border bg-card cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
      data-testid={`catalog-item-card-${item.id}`}
    >
      {/* Left: illustration or custom image — fixed width */}
      <div className="w-32 flex-shrink-0 overflow-hidden">
        <CatalogCardIllustration category={item.category} imageUrl={item.imageUrl} />
      </div>

      {/* Right: item data */}
      <div className="flex-1 p-3 space-y-1.5 min-w-0">
        <p
          className="font-semibold text-sm leading-tight truncate"
          data-testid={`item-name-${item.id}`}
        >
          {item.name}
        </p>

        <div className="flex items-center gap-1.5">
          <Badge
            variant={item.category === "CASH" ? "default" : "secondary"}
            className="text-[10px] px-1.5 py-0 h-4 shrink-0"
          >
            {item.category === "CASH" ? "Cash" : "Non-Cash"}
          </Badge>
          <span className="text-[11px] text-muted-foreground truncate">{item.currencyId}</span>
        </div>

        <p className="text-sm text-muted-foreground font-medium">{formatted}</p>

        <div
          className="flex items-center gap-1 text-xs text-muted-foreground"
          data-testid={`payout-timeline-${item.id}`}
        >
          <Clock className="w-3 h-3 flex-shrink-0" />
          <span className="truncate">{item.estimatedPayoutTimeline}</span>
        </div>

        {!item.canAfford && (
          <ShortfallBadge
            shortfallAmount={item.shortfallAmount}
            currencyId={item.currencyId}
          />
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests — all 6 should pass**

```bash
npm run test -- CatalogItemCard --run
```

Expected: `Tests 6 passed (6)`.

- [ ] **Step 5: Commit**

```bash
git add src/components/redemption-catalog/CatalogItemCard.tsx \
        src/components/redemption-catalog/__tests__/CatalogItemCard.test.tsx
git commit -m "feat(FE): redesign CatalogItemCard — horizontal layout with illustration slot"
```

---

## Task 6: FE — Update CatalogBrowseGrid skeleton shape

**Files:**
- Modify: `src/components/redemption-catalog/CatalogBrowseGrid.tsx`

The existing skeleton is a single `h-36 rounded-lg` block. It needs to mirror the new horizontal card: a left image block + right content stubs.

- [ ] **Step 1: Replace the skeleton JSX in `CatalogBrowseGrid.tsx`**

Locate the `isLoading` early-return block (lines 26–34). Replace it with:

```tsx
  if (isLoading) {
    return (
      <div className="space-y-8" data-testid="catalog-browse-grid-skeleton">
        {Array.from({ length: 2 }).map((_, sectionIdx) => (
          <div key={sectionIdx}>
            <Skeleton className="h-4 w-16 mb-3" />
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex overflow-hidden rounded-lg border h-24">
                  <Skeleton className="w-32 flex-shrink-0 rounded-none" />
                  <div className="flex-1 p-3 space-y-2">
                    <Skeleton className="h-4 w-3/4" />
                    <Skeleton className="h-3 w-1/3" />
                    <Skeleton className="h-3 w-1/2" />
                    <Skeleton className="h-3 w-2/3" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }
```

- [ ] **Step 2: Run all catalog tests**

```bash
npm run test -- redemption-catalog --run
```

Expected: all green.

- [ ] **Step 3: Full test suite**

```bash
npm run test --run
```

Expected: all green, no regressions.

- [ ] **Step 4: Commit**

```bash
git add src/components/redemption-catalog/CatalogBrowseGrid.tsx
git commit -m "feat(FE): update CatalogBrowseGrid skeleton to match horizontal card shape"
```

---

## Self-Review

**Spec coverage:**
- ✅ `imageUrl` added to BE DTO + contracts + FE type
- ✅ Default illustration when no image (CASH = blue, NON_CASH = green)
- ✅ Custom image shown when `imageUrl` set
- ✅ Horizontal card layout (image left, data right)
- ✅ Skeleton updated to match new card shape
- ✅ Tests cover both illustration variants and custom image path

**Placeholder scan:** None — all steps contain complete code.

**Type consistency:**
- `CatalogBrowseItemResponse.imageUrl` — defined as `String imageUrl` in BE record, `imageUrl?: string | null` in TS type, `imageUrl` field in contracts YAML — consistent.
- `CatalogCardIllustration` props: `{ category: CatalogCategory; imageUrl?: string | null }` — consumed identically in `CatalogItemCard`.
- `data-testid` values: `catalog-card-illustration-${category.toLowerCase()}` and `catalog-card-custom-image` — consistent between mock in tests and real component.
