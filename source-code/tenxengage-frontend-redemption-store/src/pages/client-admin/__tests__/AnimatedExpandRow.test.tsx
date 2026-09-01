import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, act } from "@testing-library/react";
import { AnimatedExpandRow } from "@/pages/client-admin/ManageClaimsPage";

// jsdom doesn't provide ResizeObserver. Stub it so the component's
// observer.observe(content) call doesn't throw. A bare no-op is enough —
// the tests below don't rely on observer callbacks firing.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  vi.stubGlobal("ResizeObserver", ResizeObserverStub);
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function renderInTable(ui: React.ReactNode) {
  return render(
    <table>
      <tbody>{ui}</tbody>
    </table>,
  );
}

describe("AnimatedExpandRow", () => {
  it("renders nothing when never expanded", () => {
    const { container } = renderInTable(
      <AnimatedExpandRow isExpanded={false} colSpan={3}>
        <div data-testid="expanded-body">body</div>
      </AnimatedExpandRow>,
    );

    // No <tr> from AnimatedExpandRow at all — component returns null before
    // it has ever been expanded.
    expect(container.querySelector('[data-testid="expanded-body"]')).toBeNull();
  });

  it("mounts and programs the height transition synchronously on first expand", () => {
    // This is the regression-critical behavior. The previous implementation
    // split the effect across two useEffects gated on an intermediate state,
    // so the transition was only programmed on the second render — after
    // the browser had already painted the row at height 0. The current
    // implementation uses a single useLayoutEffect, which runs between DOM
    // commit and first paint. In jsdom that manifests as: immediately after
    // render, the wrapper's inline transition is set to the 300ms height
    // easing curve (not "none" and not the initial unanimated default).
    const { container, getByTestId } = renderInTable(
      <AnimatedExpandRow isExpanded={true} colSpan={3}>
        <div data-testid="expanded-body">body</div>
      </AnimatedExpandRow>,
    );

    // Child rendered on the same tick — not deferred behind an intermediate render.
    expect(getByTestId("expanded-body")).toBeDefined();

    const wrapper = container.querySelector(
      ".overflow-hidden",
    ) as HTMLElement | null;
    expect(wrapper).not.toBeNull();
    expect(wrapper!.style.transition).toMatch(/height 300ms/);
  });

  it("keeps the row mounted during the 300ms collapse animation, then unmounts", () => {
    const { container, rerender } = renderInTable(
      <AnimatedExpandRow isExpanded={true} colSpan={3}>
        <div data-testid="expanded-body">body</div>
      </AnimatedExpandRow>,
    );

    expect(
      container.querySelector('[data-testid="expanded-body"]'),
    ).not.toBeNull();

    // Collapse — the row must stay mounted so the height:0 transition can play out.
    rerender(
      <table>
        <tbody>
          <AnimatedExpandRow isExpanded={false} colSpan={3}>
            <div data-testid="expanded-body">body</div>
          </AnimatedExpandRow>
        </tbody>
      </table>,
    );

    // Immediately after the collapse toggle, body is still in the DOM (animation in progress).
    expect(
      container.querySelector('[data-testid="expanded-body"]'),
    ).not.toBeNull();
    const wrapper = container.querySelector(
      ".overflow-hidden",
    ) as HTMLElement | null;
    expect(wrapper!.style.height).toBe("0px");

    // After the 300ms transition window elapses, the row unmounts.
    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(
      container.querySelector('[data-testid="expanded-body"]'),
    ).toBeNull();
  });

  it("cancels a pending unmount when re-expanded mid-collapse", () => {
    // Spam-click scenario: expand → collapse → expand before 300ms. The
    // useLayoutEffect cleanup must clear the pending setShouldRender(false)
    // timer so the second expand doesn't unmount the row out from under itself.
    const { container, rerender } = renderInTable(
      <AnimatedExpandRow isExpanded={true} colSpan={3}>
        <div data-testid="expanded-body">body</div>
      </AnimatedExpandRow>,
    );

    rerender(
      <table>
        <tbody>
          <AnimatedExpandRow isExpanded={false} colSpan={3}>
            <div data-testid="expanded-body">body</div>
          </AnimatedExpandRow>
        </tbody>
      </table>,
    );

    // Re-expand before the 300ms unmount timer fires.
    act(() => {
      vi.advanceTimersByTime(150);
    });
    rerender(
      <table>
        <tbody>
          <AnimatedExpandRow isExpanded={true} colSpan={3}>
            <div data-testid="expanded-body">body</div>
          </AnimatedExpandRow>
        </tbody>
      </table>,
    );

    // Let the original unmount timer's would-be deadline pass.
    act(() => {
      vi.advanceTimersByTime(300);
    });

    // Row must still be present — the collapse timer should have been cleared
    // by the effect cleanup when we re-expanded.
    expect(
      container.querySelector('[data-testid="expanded-body"]'),
    ).not.toBeNull();
  });
});
