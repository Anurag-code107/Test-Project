import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

vi.mock("@/data/mockActivitySubmissions", () => ({
  mockActivitySubmissions: [],
}));

vi.mock("@/components/LocationFilter", () => ({
  LocationFilter: ({ value }: { value: string }) => (
    <div data-testid="location-filter">{value}</div>
  ),
}));

import { useAuth } from "@/hooks/useAuth";
import { ApprovalsWidget } from "@/components/home/widgets/ApprovalsWidget";

const mockUseAuth = vi.mocked(useAuth);

describe("ApprovalsWidget", () => {
  it("renders the approver greeting with the user's first name", () => {
    mockUseAuth.mockReturnValue({
      user: { firstName: "Jordan", lastName: "Smith" },
    } as unknown as ReturnType<typeof useAuth>);

    render(<ApprovalsWidget />);

    expect(screen.getByText(/Good Morning, Jordan/)).toBeDefined();
  });

  it("falls back to 'Approver' when the user is missing", () => {
    mockUseAuth.mockReturnValue({
      user: null,
    } as unknown as ReturnType<typeof useAuth>);

    render(<ApprovalsWidget />);

    expect(screen.getByText(/Good Morning, Approver/)).toBeDefined();
  });

  it("renders the three stats (Pending Review, Approved, Denied)", () => {
    mockUseAuth.mockReturnValue({
      user: { firstName: "Jordan" },
    } as unknown as ReturnType<typeof useAuth>);

    render(<ApprovalsWidget />);

    expect(screen.getAllByText("Pending Review").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Approved").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Denied").length).toBeGreaterThan(0);
  });

  it("renders inside a full-width container", () => {
    mockUseAuth.mockReturnValue({
      user: { firstName: "Jordan" },
    } as unknown as ReturnType<typeof useAuth>);

    const { container } = render(<ApprovalsWidget />);
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("w-full");
  });
});
