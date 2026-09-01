import { describe, it, expect } from "vitest";
import { sidebarConfig } from "../sidebarConfigs";

describe("sidebarConfig", () => {
  it('topLabel is "tenXengage"', () => {
    expect(sidebarConfig.topLabel).toBe("tenXengage");
  });

  it("has primary navigation items", () => {
    expect(sidebarConfig.primaryItems.length).toBeGreaterThan(0);
  });

  it("has configuration sections", () => {
    expect(sidebarConfig.sections!.length).toBeGreaterThan(0);
  });
});
