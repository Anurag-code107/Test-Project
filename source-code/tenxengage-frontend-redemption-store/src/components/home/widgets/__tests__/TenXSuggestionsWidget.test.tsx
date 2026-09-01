import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/components/recommendations/TenXSuggestionsSection", () => ({
  TenXSuggestionsSection: () => <div data-testid="tenx-suggestions-section" />,
}));

import { TenXSuggestionsWidget } from "@/components/home/widgets/TenXSuggestionsWidget";

describe("TenXSuggestionsWidget", () => {
  it("renders the TenXSuggestionsSection inside a full-width wrapper", () => {
    const { container } = render(<TenXSuggestionsWidget />);
    expect(screen.getByTestId("tenx-suggestions-section")).toBeDefined();
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("w-full");
  });
});
