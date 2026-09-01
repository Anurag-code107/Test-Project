import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/hooks/useHomeApi", () => ({
  useProgramPerformance: vi.fn(),
  usePartnerSearch: vi.fn(),
}));

vi.mock("@/components/LocationFilter", () => ({
  LocationFilter: ({ value }: { value: string }) => (
    <div data-testid="location-filter">{value}</div>
  ),
}));

vi.mock("@/components/RewardBreakdownExpanded", () => ({
  RewardBreakdownExpanded: () => <div data-testid="reward-breakdown" />,
}));

import { useProgramPerformance, usePartnerSearch } from "@/hooks/useHomeApi";
import { ProgramPerformanceWidget } from "@/components/home/widgets/ProgramPerformanceWidget";

const mockUseProgramPerformance = vi.mocked(useProgramPerformance);
const mockUsePartnerSearch = vi.mocked(usePartnerSearch);

function stubHooks() {
  mockUseProgramPerformance.mockReturnValue({
    data: null,
    isLoading: false,
  } as unknown as ReturnType<typeof useProgramPerformance>);
  mockUsePartnerSearch.mockReturnValue({
    data: undefined,
    isLoading: false,
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  } as unknown as ReturnType<typeof usePartnerSearch>);
}

describe("ProgramPerformanceWidget", () => {
  it("renders the Program Performance section heading", () => {
    stubHooks();
    render(<ProgramPerformanceWidget />);
    expect(screen.getByText("Program Performance")).toBeDefined();
  });

  it("renders the partner filter input and location filter", () => {
    stubHooks();
    render(<ProgramPerformanceWidget />);
    expect(screen.getByPlaceholderText("Filter by partner")).toBeDefined();
    expect(screen.getByTestId("location-filter")).toBeDefined();
  });

  it("accepts an optional onPartnerChange callback without requiring it", () => {
    stubHooks();
    expect(() => render(<ProgramPerformanceWidget />)).not.toThrow();
    expect(() =>
      render(<ProgramPerformanceWidget onPartnerChange={() => {}} />),
    ).not.toThrow();
  });

  it("renders inside a full-width section", () => {
    stubHooks();
    const { container } = render(<ProgramPerformanceWidget />);
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("w-full");
  });
});
