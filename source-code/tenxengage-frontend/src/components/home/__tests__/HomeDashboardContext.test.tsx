import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  HomeDashboardProvider,
  useHomeDashboardState,
} from "@/components/home/HomeDashboardContext";

function Consumer() {
  const { selectedPartnerName, setSelectedPartnerName } =
    useHomeDashboardState();
  return (
    <div>
      <span data-testid="partner-name">{selectedPartnerName ?? "none"}</span>
      <button
        data-testid="set-acme"
        onClick={() => setSelectedPartnerName("Acme")}
      >
        set
      </button>
      <button data-testid="clear" onClick={() => setSelectedPartnerName(null)}>
        clear
      </button>
    </div>
  );
}

describe("HomeDashboardContext", () => {
  it("provides a default null state when used outside a provider", () => {
    render(<Consumer />);
    expect(screen.getByTestId("partner-name").textContent).toBe("none");
  });

  it("does not throw when setSelectedPartnerName is called without a provider", async () => {
    render(<Consumer />);
    await userEvent.click(screen.getByTestId("set-acme"));
    // No provider = no-op setter, partner stays null
    expect(screen.getByTestId("partner-name").textContent).toBe("none");
  });

  it("publishes and propagates state inside a provider", async () => {
    render(
      <HomeDashboardProvider>
        <Consumer />
      </HomeDashboardProvider>,
    );

    expect(screen.getByTestId("partner-name").textContent).toBe("none");
    await userEvent.click(screen.getByTestId("set-acme"));
    expect(screen.getByTestId("partner-name").textContent).toBe("Acme");
    await userEvent.click(screen.getByTestId("clear"));
    expect(screen.getByTestId("partner-name").textContent).toBe("none");
  });
});
