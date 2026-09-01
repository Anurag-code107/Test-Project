import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { CatalogCardIllustration } from "../CatalogCardIllustration";

const BRAND_URL = "https://cdn.example.com/brands/sling.png";
const PROXY_URL = "/api/v1/admin/redemption-catalog/item-1/image";

describe("CatalogCardIllustration", () => {
  it("prefers the client-uploaded image over the SKU brand image", () => {
    render(
      <CatalogCardIllustration
        category="CASH"
        imageUrl="catalog/item-1/logo.png"
        catalogItemId="item-1"
        providerImageUrl={BRAND_URL}
      />,
    );

    // The uploaded image is read through the API proxy, never from the raw stored value.
    expect(screen.getByTestId("catalog-card-custom-image").getAttribute("src")).toBe(PROXY_URL);
    expect(screen.queryByTestId("catalog-card-provider-image")).toBeNull();
  });

  it("uses the SKU brand image when no image was uploaded", () => {
    render(
      <CatalogCardIllustration
        category="CASH"
        imageUrl={null}
        catalogItemId="item-1"
        providerImageUrl={BRAND_URL}
      />,
    );

    expect(screen.getByTestId("catalog-card-provider-image").getAttribute("src")).toBe(BRAND_URL);
    expect(screen.queryByTestId("catalog-card-illustration-cash")).toBeNull();
  });

  it("falls back to the illustration when neither image exists", () => {
    render(<CatalogCardIllustration category="NON_CASH" imageUrl={null} catalogItemId="item-1" />);

    expect(screen.getByTestId("catalog-card-illustration-non_cash")).toBeDefined();
  });

  it("falls through from a broken upload to the SKU brand image", () => {
    render(
      <CatalogCardIllustration
        category="CASH"
        imageUrl="catalog/item-1/logo.png"
        catalogItemId="item-1"
        providerImageUrl={BRAND_URL}
      />,
    );

    fireEvent.error(screen.getByTestId("catalog-card-custom-image"));

    expect(screen.getByTestId("catalog-card-provider-image").getAttribute("src")).toBe(BRAND_URL);
  });

  it("falls through to the illustration when both images fail to load", () => {
    render(
      <CatalogCardIllustration
        category="CASH"
        imageUrl="catalog/item-1/logo.png"
        catalogItemId="item-1"
        providerImageUrl={BRAND_URL}
      />,
    );

    fireEvent.error(screen.getByTestId("catalog-card-custom-image"));
    fireEvent.error(screen.getByTestId("catalog-card-provider-image"));

    expect(screen.getByTestId("catalog-card-illustration-cash")).toBeDefined();
  });

  it("falls back to the illustration when a dead vendor link is the only candidate", () => {
    render(
      <CatalogCardIllustration category="CASH" imageUrl={null} providerImageUrl={BRAND_URL} />,
    );

    fireEvent.error(screen.getByTestId("catalog-card-provider-image"));

    expect(screen.getByTestId("catalog-card-illustration-cash")).toBeDefined();
  });

  it("ignores an uploaded image with no catalogItemId to build the proxy URL from", () => {
    render(
      <CatalogCardIllustration
        category="CASH"
        imageUrl="catalog/orphan/logo.png"
        providerImageUrl={BRAND_URL}
      />,
    );

    expect(screen.getByTestId("catalog-card-provider-image").getAttribute("src")).toBe(BRAND_URL);
  });
});
