import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useFeatures } from "@/hooks/useFeatures";

vi.mock("@/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "@/hooks/useAuth";

const mockUseAuth = vi.mocked(useAuth);

function setEnabled(features: string[]) {
  mockUseAuth.mockReturnValue({
    enabledFeatures: features,
  } as unknown as ReturnType<typeof useAuth>);
}

describe("useFeatures", () => {
  it("has() returns true for an enabled feature", () => {
    setEnabled(["audit_log", "deal_qualifier"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.has("audit_log")).toBe(true);
  });

  it("has() returns false for a feature not in enabledFeatures (fail-closed)", () => {
    setEnabled(["deal_qualifier"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.has("audit_log")).toBe(false);
  });

  it("has() returns false for a typoed / unknown key (fail-closed default)", () => {
    setEnabled(["audit_log"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.has("audtit_log")).toBe(false);
    expect(result.current.has("does_not_exist")).toBe(false);
  });

  it("has() returns false when enabledFeatures is empty", () => {
    setEnabled([]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.has("audit_log")).toBe(false);
  });

  it("hasAny() returns true if at least one matches", () => {
    setEnabled(["deal_qualifier"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.hasAny("audit_log", "deal_qualifier")).toBe(true);
  });

  it("hasAny() returns false when none match", () => {
    setEnabled(["audit_log"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.hasAny("deal_qualifier", "white_labeling")).toBe(
      false,
    );
  });

  it("hasAll() returns true only when every key is enabled", () => {
    setEnabled(["audit_log", "deal_qualifier"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.hasAll("audit_log", "deal_qualifier")).toBe(true);
    expect(result.current.hasAll("audit_log", "white_labeling")).toBe(false);
  });

  it("exposes the underlying feature set", () => {
    setEnabled(["audit_log", "deal_qualifier"]);
    const { result } = renderHook(() => useFeatures());
    expect(result.current.features.has("audit_log")).toBe(true);
    expect(result.current.features.size).toBe(2);
  });
});
