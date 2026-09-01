import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StalenessBanner } from "@/components/analytics/advanced/StalenessBanner";

describe("StalenessBanner", () => {
  it("renders when isStale=true with a known lastRefreshedAt", () => {
    render(
      <StalenessBanner
        isStale={true}
        lastRefreshedAt="2026-06-19T10:00:00Z"
      />,
    );
    const alert = screen.getByRole("alert");
    expect(alert).toBeDefined();
    expect(alert.textContent).toContain("may be outdated");
  });

  it("renders 'not been refreshed yet' copy when lastRefreshedAt=null", () => {
    render(
      <StalenessBanner
        isStale={true}
        lastRefreshedAt={null}
      />,
    );
    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("has not been refreshed yet");
  });

  it("does not render when isStale=false", () => {
    render(
      <StalenessBanner
        isStale={false}
        lastRefreshedAt="2026-06-22T10:00:00Z"
      />,
    );
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("dismiss button hides the banner", async () => {
    const user = userEvent.setup();
    render(
      <StalenessBanner
        isStale={true}
        lastRefreshedAt="2026-06-19T10:00:00Z"
      />,
    );
    expect(screen.getByRole("alert")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Dismiss staleness warning" }));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("dismiss button has the correct aria-label", () => {
    render(
      <StalenessBanner
        isStale={true}
        lastRefreshedAt={null}
      />,
    );
    expect(
      screen.getByRole("button", { name: "Dismiss staleness warning" }),
    ).toBeDefined();
  });
});
