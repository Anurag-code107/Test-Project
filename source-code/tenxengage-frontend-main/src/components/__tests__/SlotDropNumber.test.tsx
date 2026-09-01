import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, act } from "@testing-library/react";
import { SlotDropNumber } from "@/components/SlotDropNumber";

// Helper: count the per-digit strip children for the first reel in the DOM.
// Each digit reel renders `spins * 10 + target + 1` inner spans, so this is a
// proxy for the effective spins value.
function firstReelChildCount(container: HTMLElement): number {
  const strip = container.querySelector(
    ".inline-block.overflow-hidden > span",
  ) as HTMLElement | null;
  return strip ? strip.children.length : 0;
}

describe("SlotDropNumber", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // Stable random so the digit strips are deterministic — not asserted
    // directly but keeps test runs reproducible.
    vi.spyOn(Math, "random").mockReturnValue(0);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("does not apply will-change-transform as a static class on the reel strip", () => {
    // Regression guard for the core BUG-016 symptom: the previous
    // implementation left `will-change-transform` on the reel strip
    // permanently, which promoted every digit to its own compositor
    // layer and saturated the GPU. The static className should no
    // longer contain it — will-change is set imperatively during the
    // animation window and reset afterward.
    const { container } = render(<SlotDropNumber value="$9" />);
    const strip = container.querySelector(
      ".inline-block.overflow-hidden > span",
    ) as HTMLElement | null;
    expect(strip).not.toBeNull();
    expect(strip!.className).not.toMatch(/will-change-transform/);
  });

  it("defaults to the shorter spins=2 reel depth", () => {
    // For value "$9": isDigit count = 1, digits.length = 2*10 + 9+1 = 30.
    // If a future change bumps the default back to 6, this assertion will
    // fail (expected 30, would see 70).
    const { container } = render(<SlotDropNumber value="$9" />);
    expect(firstReelChildCount(container)).toBe(30);
  });

  it("respects an explicit spins prop for emphasis callers (e.g. Total Earnings)", () => {
    // Total Earnings banner passes spins={6}. For "$9" this yields
    // 6*10 + 9+1 = 70 digit strip children.
    const { container } = render(<SlotDropNumber value="$9" spins={6} />);
    expect(firstReelChildCount(container)).toBe(70);
  });

  it("defers the reel animation until the element intersects the viewport", () => {
    // Stub IntersectionObserver to never report intersection. The animation
    // machinery must wait — the reel's inline transition should stay at the
    // initial "none" value (or empty / unset) instead of rolling over to the
    // real 300ms+ transform transition.
    const observeCalls: Element[] = [];
    class IOStub {
      observe(el: Element) {
        observeCalls.push(el);
      }
      unobserve() {}
      disconnect() {}
      takeRecords() {
        return [];
      }
    }
    vi.stubGlobal("IntersectionObserver", IOStub);

    const { container } = render(<SlotDropNumber value="$9" />);

    // Flush any pending effects + rAFs — the offscreen element should still
    // not be animating.
    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(observeCalls.length).toBe(1);

    const strip = container.querySelector(
      ".inline-block.overflow-hidden > span",
    ) as HTMLElement | null;
    expect(strip).not.toBeNull();
    // The runAnimation path always sets a real `transform ...ms ...` transition
    // string. Before intersection, that should never have been applied.
    expect(strip!.style.transition).not.toMatch(/transform \d+ms/);
    // And will-change should not have been flipped on — no compositor layer
    // should be promoted for an element the user can't see.
    expect(strip!.style.willChange).not.toBe("transform");
  });

  it("runs the animation immediately when IntersectionObserver is unavailable (jsdom fallback)", () => {
    // Guard the fallback path — removing the `typeof IntersectionObserver
    // === "undefined"` branch would regress to never-animating in
    // environments without the API (including this test environment).
    vi.stubGlobal("IntersectionObserver", undefined);

    const { container } = render(<SlotDropNumber value="$9" />);

    // Advance past the double-rAF that defers the transform write.
    act(() => {
      vi.advanceTimersByTime(100);
    });

    const strip = container.querySelector(
      ".inline-block.overflow-hidden > span",
    ) as HTMLElement | null;
    expect(strip).not.toBeNull();
    // will-change is flipped on for the rolling window.
    expect(strip!.style.willChange).toBe("transform");

    // After the longest reel + pulse completes, will-change must be released
    // back to "auto" — that's the whole point of BUG-016's fix.
    act(() => {
      // Default durationMs=900, staggerMs=160, plus the 220ms pulse buffer.
      // One reel here, so idx=0: end ≈ 900 + 220 = 1120ms. Pad generously.
      vi.advanceTimersByTime(2000);
    });
    expect(strip!.style.willChange).toBe("auto");
  });
});
