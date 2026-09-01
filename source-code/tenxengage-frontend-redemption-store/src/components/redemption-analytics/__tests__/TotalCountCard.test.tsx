import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { TotalCountCard } from "@/components/redemption-analytics/TotalCountCard";
// shape: contracts/models/redemption-analytics-summary.md

describe("TotalCountCard", () => {
  it("renders all 5 status rows", () => {
    render(
      <TotalCountCard
        data={{
          total: 100,
          byStatus: {
            PENDING: 10,
            PROCESSING: 20,
            COMPLETED: 50,
            FAILED: 15,
            CANCELLED: 5,
          },
          hasActivity: true,
        }}
      />,
    );
    expect(screen.getByText("Pending")).toBeDefined();
    expect(screen.getByText("Processing")).toBeDefined();
    expect(screen.getByText("Completed")).toBeDefined();
    expect(screen.getByText("Failed")).toBeDefined();
    expect(screen.getByText("Cancelled")).toBeDefined();
  });

  it("renders total count", () => {
    render(
      <TotalCountCard
        data={{
          total: 42,
          byStatus: {
            PENDING: 5,
            PROCESSING: 10,
            COMPLETED: 20,
            FAILED: 5,
            CANCELLED: 2,
          },
          hasActivity: true,
        }}
      />,
    );
    expect(screen.getByText("42")).toBeDefined();
  });

  it("renders empty state when hasActivity is false", () => {
    render(
      <TotalCountCard
        data={{
          total: 0,
          byStatus: {},
          hasActivity: false,
        }}
      />,
    );
    expect(screen.getByText("No redemptions in this period")).toBeDefined();
    // Status rows not rendered
    expect(screen.queryByText("Pending")).toBeNull();
  });
});
