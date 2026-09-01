import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { uploadCatalogItemImage } from "@/services/redemption-catalog-admin.service";

const MAX_BYTES = 5 * 1024 * 1024;
const ALLOWED = ["image/png", "image/jpeg", "image/webp"];

interface Props {
  itemId: string | null;
  currentImageUrl: string | null;
  onUploaded: (url: string) => void;
  onFilePicked?: (file: File) => void;
  onRemove?: () => void;
  /**
   * Brand image of the selected gift-card SKU. Previewed in place of an upload so the admin sees the
   * image the card will actually carry — an upload here overrides it.
   */
  fallbackImageUrl?: string | null;
}

function imageProxyUrl(itemId: string, bust?: number) {
  return `/api/v1/admin/redemption-catalog/${itemId}/image${bust ? `?v=${bust}` : ""}`;
}

export function CatalogImageUpload({
  itemId,
  currentImageUrl,
  onUploaded,
  onFilePicked,
  onRemove,
  fallbackImageUrl,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const initialPreview = itemId && currentImageUrl ? imageProxyUrl(itemId) : null;
  const [preview, setPreview] = useState<string | null>(initialPreview);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Keyed by URL so picking a different SKU gets a fresh attempt after a dead vendor link.
  const [brokenSkuImage, setBrokenSkuImage] = useState<string | null>(null);
  // Shown only when there's nothing uploaded — the upload always wins.
  const skuPreview =
    !preview && fallbackImageUrl && brokenSkuImage !== fallbackImageUrl ? fallbackImageUrl : null;

  useEffect(() => {
    return () => {
      if (preview?.startsWith("blob:")) {
        URL.revokeObjectURL(preview);
      }
    };
  }, [preview]);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);

    if (!ALLOWED.includes(file.type)) {
      setError("Allowed formats: PNG, JPEG, WebP");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("File exceeds 5 MB limit");
      return;
    }

    if (!itemId) {
      setPreview(URL.createObjectURL(file));
      onFilePicked?.(file);
      return;
    }

    setUploading(true);
    try {
      await uploadCatalogItemImage(itemId, file);
      const proxyUrl = imageProxyUrl(itemId, Date.now());
      setPreview(proxyUrl);
      onUploaded(proxyUrl);
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function handleRemove() {
    setPreview(null);
    onRemove?.();
  }

  return (
    <div className="space-y-2">
      <Label htmlFor="catalog-image-upload">
        Image{" "}
        <span className="text-muted-foreground text-xs">
          (optional — PNG/JPEG/WebP, max 5 MB)
        </span>
      </Label>
      {preview ? (
        <div className="flex items-start gap-3">
          <img
            src={preview}
            alt="Catalog item preview"
            className="h-20 w-20 rounded border object-cover"
          />
          <div className="flex flex-col gap-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => inputRef.current?.click()}
              disabled={uploading}
            >
              {uploading ? "Uploading…" : "Change"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={handleRemove}
              disabled={uploading}
            >
              Remove
            </Button>
          </div>
        </div>
      ) : skuPreview ? (
        <div className="flex items-start gap-3">
          <img
            src={skuPreview}
            alt="Gift-card brand image"
            className="h-20 w-20 rounded border bg-muted/40 object-contain p-1"
            onError={() => setBrokenSkuImage(skuPreview)}
            data-testid="catalog-image-sku-preview"
          />
          <div className="flex flex-col items-start gap-1">
            <p className="text-xs text-muted-foreground">
              Using the gift-card brand image. Upload one to override it.
            </p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => inputRef.current?.click()}
              disabled={uploading}
            >
              {uploading ? "Uploading…" : "Upload image"}
            </Button>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => inputRef.current?.click()}
          disabled={uploading}
        >
          {uploading ? "Uploading…" : "Upload image"}
        </Button>
      )}
      <input
        ref={inputRef}
        id="catalog-image-upload"
        aria-label="Upload image"
        type="file"
        accept="image/png,image/jpeg,image/webp"
        className="hidden"
        onChange={handleFileChange}
      />
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
