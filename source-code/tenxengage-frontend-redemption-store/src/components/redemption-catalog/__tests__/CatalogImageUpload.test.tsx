import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CatalogImageUpload } from "../CatalogImageUpload";

const mockUpload = vi.fn();
vi.mock("@/services/redemption-catalog-admin.service", () => ({
  uploadCatalogItemImage: (...args: unknown[]) => mockUpload(...args),
}));

describe("CatalogImageUpload", () => {
  beforeEach(() => {
    mockUpload.mockReset();
  });

  it("renders file input and no preview when no existing image", () => {
    render(
      <CatalogImageUpload itemId={null} currentImageUrl={null} onUploaded={vi.fn()} />,
    );
    expect(screen.getByLabelText(/upload image/i)).toBeDefined();
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("shows preview when existing imageUrl provided", () => {
    render(
      <CatalogImageUpload
        itemId="abc"
        currentImageUrl="catalog/abc/image-123.png"
        onUploaded={vi.fn()}
      />,
    );
    expect(screen.getByRole("img")).toBeDefined();
    expect(screen.getByRole("button", { name: /remove/i })).toBeDefined();
  });

  it("calls uploadCatalogItemImage on file select when itemId is set", async () => {
    mockUpload.mockResolvedValueOnce({ id: "abc", imageUrl: "catalog/abc/image-new.png" });
    const onUploaded = vi.fn();
    render(
      <CatalogImageUpload itemId="abc" currentImageUrl={null} onUploaded={onUploaded} />,
    );
    const file = new File(["img"], "photo.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/upload image/i), file);
    expect(mockUpload).toHaveBeenCalledWith("abc", file);
    expect(onUploaded).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/v1\/admin\/redemption-catalog\/abc\/image/),
    );
  });

  it("shows error when file exceeds 5 MB", async () => {
    render(
      <CatalogImageUpload itemId="abc" currentImageUrl={null} onUploaded={vi.fn()} />,
    );
    const bigFile = new File([new ArrayBuffer(6 * 1024 * 1024)], "big.png", {
      type: "image/png",
    });
    await userEvent.upload(screen.getByLabelText(/upload image/i), bigFile);
    expect(screen.getByText(/exceeds 5 MB/i)).toBeDefined();
    expect(mockUpload).not.toHaveBeenCalled();
  });

  it("previews the SKU brand image when nothing has been uploaded", () => {
    render(
      <CatalogImageUpload
        itemId={null}
        currentImageUrl={null}
        onUploaded={vi.fn()}
        fallbackImageUrl="https://cdn.example.com/brands/acme.png"
      />,
    );
    expect(screen.getByTestId("catalog-image-sku-preview").getAttribute("src")).toBe(
      "https://cdn.example.com/brands/acme.png",
    );
    // Uploading is still offered — the brand image is a default, not a lock-in.
    expect(screen.getByRole("button", { name: /upload image/i })).toBeDefined();
  });

  it("shows the uploaded image instead of the SKU brand image", () => {
    render(
      <CatalogImageUpload
        itemId="abc"
        currentImageUrl="catalog/abc/image-123.png"
        onUploaded={vi.fn()}
        fallbackImageUrl="https://cdn.example.com/brands/acme.png"
      />,
    );
    expect(screen.queryByTestId("catalog-image-sku-preview")).toBeNull();
    expect(screen.getByAltText(/catalog item preview/i)).toBeDefined();
  });

  it("drops back to the plain upload button when the SKU brand image is broken", () => {
    render(
      <CatalogImageUpload
        itemId={null}
        currentImageUrl={null}
        onUploaded={vi.fn()}
        fallbackImageUrl="https://cdn.example.com/brands/dead.png"
      />,
    );
    fireEvent.error(screen.getByTestId("catalog-image-sku-preview"));

    expect(screen.queryByTestId("catalog-image-sku-preview")).toBeNull();
    expect(screen.getByRole("button", { name: /upload image/i })).toBeDefined();
  });

  it("calls onFilePicked in create mode without calling upload endpoint", async () => {
    const onFilePicked = vi.fn();
    render(
      <CatalogImageUpload
        itemId={null}
        currentImageUrl={null}
        onUploaded={vi.fn()}
        onFilePicked={onFilePicked}
      />,
    );
    const file = new File(["img"], "photo.png", { type: "image/png" });
    await userEvent.upload(screen.getByLabelText(/upload image/i), file);
    expect(onFilePicked).toHaveBeenCalledWith(file);
    expect(mockUpload).not.toHaveBeenCalled();
  });
});
