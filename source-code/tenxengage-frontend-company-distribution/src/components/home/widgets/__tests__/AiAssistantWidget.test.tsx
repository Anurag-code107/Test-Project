import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";

vi.mock("@/components/guided-tour/AIAssistantInput", () => ({
  AIAssistantInput: () => <div data-testid="ai-assistant-input" />,
}));

import { AiAssistantWidget } from "@/components/home/widgets/AiAssistantWidget";

describe("AiAssistantWidget", () => {
  it("renders the AIAssistantInput inside a full-width, full-height flex wrapper", () => {
    const { container } = render(<AiAssistantWidget />);
    expect(screen.getByTestId("ai-assistant-input")).toBeDefined();
    const wrapper = container.firstChild as HTMLElement;
    expect(wrapper.className).toContain("w-full");
    expect(wrapper.className).toContain("h-full");
    expect(wrapper.className).toContain("flex");
  });
});
