import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ShortfallBadge } from "@/components/redemption-catalog/ShortfallBadge";

describe("ShortfallBadge", () => {
  it("renders formatted shortfall amount", () => {
    render(<ShortfallBadge shortfallAmount="25" currencyId="points" />);
    expect(screen.getByTestId("shortfall-badge")).toBeDefined();
    expect(screen.getByText(/25 pts short/i)).toBeDefined();
  });
});
