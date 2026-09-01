import {
  useEffect,
  useLayoutEffect,
  useState,
  useCallback,
  useRef,
} from "react";
import { createPortal } from "react-dom";
import { useNavigate, useLocation } from "react-router-dom";
import { useGuidedTour } from "@/contexts/GuidedTourContext";
import type { TourStep, TourStepAction } from "@/data/guidedTours";

interface SpotlightRect {
  top: number;
  left: number;
  width: number;
  height: number;
}

const MAX_RETRIES = 30;

export function GuidedTourOverlay() {
  const { isActive, currentTour, currentStepIndex, advance, endTour } =
    useGuidedTour();
  const navigate = useNavigate();
  const location = useLocation();
  const [spotlight, setSpotlight] = useState<SpotlightRect | null>(null);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [showContent, setShowContent] = useState(false);
  const [isWaitingForElement, setIsWaitingForElement] = useState(false);
  const [elementNotFound, setElementNotFound] = useState(false);
  const retryRef = useRef<NodeJS.Timeout | null>(null);
  const pointerBlockEnabled = useRef(true);
  const messageRef = useRef<HTMLDivElement>(null);
  const [messageMeasured, setMessageMeasured] = useState(false);

  const currentStep: TourStep | null =
    currentTour?.steps[currentStepIndex] ?? null;
  const isLastStep = currentTour
    ? currentStepIndex === currentTour.steps.length - 1
    : false;

  // Find the nearest scrollable ancestor of an element
  const findScrollableAncestor = useCallback((el: Element): Element | null => {
    let current = el.parentElement;
    while (current && current !== document.body) {
      const style = window.getComputedStyle(current);
      const overflowY = style.overflowY;
      if (
        (overflowY === "auto" || overflowY === "scroll") &&
        current.scrollHeight > current.clientHeight
      ) {
        return current;
      }
      current = current.parentElement;
    }
    return null;
  }, []);

  const scrollElementIntoView = useCallback(
    (el: Element) => {
      const rect = el.getBoundingClientRect();
      const viewportHeight = window.innerHeight;
      const elementHeight = rect.height;
      // Reserve space for the message card (200px) + gap (52px) + padding
      const margin = 280;

      // First, scroll any nested scrollable ancestor (e.g. table body with overflow-y)
      const nestedScroller = findScrollableAncestor(el);
      if (nestedScroller) {
        const scrollerRect = nestedScroller.getBoundingClientRect();
        const isOutsideScroller =
          rect.bottom > scrollerRect.bottom || rect.top < scrollerRect.top;
        if (isOutsideScroller) {
          const elTopInScroller =
            rect.top - scrollerRect.top + nestedScroller.scrollTop;
          const targetScroll =
            elTopInScroller -
            nestedScroller.clientHeight / 2 +
            elementHeight / 2;
          nestedScroller.scrollTo({
            top: Math.max(0, targetScroll),
            behavior: "smooth",
          });
        }
      }

      // For tall elements that exceed available viewport space, cap the effective
      // height so we scroll to show the top portion + room for the message card.
      const maxVisibleHeight = viewportHeight - 80 - margin;
      const effectiveBottom =
        rect.top + Math.min(elementHeight, maxVisibleHeight);

      const needsScroll =
        effectiveBottom + margin > viewportHeight || rect.top < 80;

      if (needsScroll) {
        const scrollContainer = document.querySelector("main");
        if (scrollContainer) {
          const containerRect = scrollContainer.getBoundingClientRect();
          const elementTopRelativeToContainer =
            rect.top - containerRect.top + scrollContainer.scrollTop;

          let targetScroll: number;
          if (elementHeight + margin > viewportHeight - 80) {
            // Element is taller than viewport — position top near the top of screen
            targetScroll = elementTopRelativeToContainer - 60;
          } else {
            targetScroll =
              elementTopRelativeToContainer -
              Math.max(40, (viewportHeight - elementHeight - margin) / 3);
          }

          scrollContainer.scrollTo({
            top: Math.max(0, targetScroll),
            behavior: "smooth",
          });
        } else {
          el.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      }
    },
    [findScrollableAncestor],
  );

  const findAndSpotlight = useCallback(
    (selector: string) => {
      const el = document.querySelector(selector);
      if (el) {
        const rect = el.getBoundingClientRect();
        const viewportHeight = window.innerHeight;
        const margin = 280;

        // Check if element is hidden inside a nested scroll container
        const nestedScroller = findScrollableAncestor(el);
        const hiddenInScroller = nestedScroller
          ? rect.bottom > nestedScroller.getBoundingClientRect().bottom ||
            rect.top < nestedScroller.getBoundingClientRect().top
          : false;

        const needsScroll =
          rect.bottom + margin > viewportHeight ||
          rect.top < 80 ||
          hiddenInScroller;

        if (needsScroll) {
          scrollElementIntoView(el);
          // Give extra time when scrolling nested containers
          const scrollDelay = hiddenInScroller ? 400 : 250;
          setTimeout(() => {
            const finalRect = el.getBoundingClientRect();
            const padding = 8;
            setSpotlight({
              top: finalRect.top - padding,
              left: finalRect.left - padding,
              width: finalRect.width + padding * 2,
              height: finalRect.height + padding * 2,
            });
            setIsWaitingForElement(false);
            setIsTransitioning(false);
          }, scrollDelay);
        } else {
          const padding = 8;
          setSpotlight({
            top: rect.top - padding,
            left: rect.left - padding,
            width: rect.width + padding * 2,
            height: rect.height + padding * 2,
          });
          setIsWaitingForElement(false);
          setIsTransitioning(false);
        }
        return true;
      }
      return false;
    },
    [scrollElementIntoView, findScrollableAncestor],
  );

  // Close any open Radix dialogs/sheets
  const closeOpenDialogs = useCallback(() => {
    // Find all Radix portal dismiss buttons and click the topmost one
    const dialogs = document.querySelectorAll('[role="dialog"]');
    dialogs.forEach((dialog) => {
      const closeBtn = dialog.querySelector(
        'button[class*="absolute"][class*="right"]',
      ) as HTMLElement;
      if (closeBtn) {
        closeBtn.click();
      } else {
        // Fallback: find any close button with X icon
        const xBtn = dialog.querySelector(
          "button:has(svg.lucide-x)",
        ) as HTMLElement;
        if (xBtn) xBtn.click();
      }
    });
    // Also dismiss Radix overlays by triggering escape on them
    const overlays = document.querySelectorAll(
      "[data-radix-portal] [data-state='open']",
    );
    if (overlays.length > 0 && dialogs.length === 0) {
      document.dispatchEvent(
        new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
      );
    }
  }, []);

  // Activate an element during tours. Temporarily disables the pointer blocker
  // so that Radix components (Tabs, Dialogs) can process the events.
  const activateElement = useCallback((el: HTMLElement) => {
    // Temporarily allow pointer events through so Radix handlers fire
    pointerBlockEnabled.current = false;

    // Radix Tabs activates on pointerdown + focus, not click.
    // Dispatch a full pointerdown → focus → click sequence.
    el.dispatchEvent(
      new PointerEvent("pointerdown", { bubbles: true, cancelable: true }),
    );
    el.focus();
    el.click();

    // Re-enable the blocker after a tick so React/Radix can process
    setTimeout(() => {
      pointerBlockEnabled.current = true;
    }, 50);
  }, []);

  // Execute a pre-action (click/openDrawer/switchTab) before spotlighting
  const executePreAction = useCallback(
    (action: TourStepAction): Promise<boolean> => {
      return new Promise((resolve) => {
        // First close any open drawer/dialog from previous steps
        if (action.type !== "openDrawer") {
          closeOpenDialogs();
        }

        // Small delay after closing for React to process
        setTimeout(() => {
          const el = document.querySelector(action.selector) as HTMLElement;
          if (!el) {
            resolve(false);
            return;
          }

          activateElement(el);

          if (!action.waitFor) {
            setTimeout(() => resolve(true), 300);
            return;
          }

          const timeout = action.waitTimeout ?? 2000;
          const start = Date.now();
          const poll = () => {
            if (document.querySelector(action.waitFor!)) {
              setTimeout(() => resolve(true), 150);
              return;
            }
            if (Date.now() - start > timeout) {
              resolve(false);
              return;
            }
            setTimeout(poll, 100);
          };
          setTimeout(poll, 100);
        }, 100);
      });
    },
    [closeOpenDialogs, activateElement],
  );

  // Handle step changes
  useEffect(() => {
    if (!isActive || !currentStep) return;

    let cancelled = false;

    setShowContent(false);
    setIsTransitioning(true);
    setIsWaitingForElement(false);
    setElementNotFound(false);
    setMessageMeasured(false);
    setSpotlight(null);

    if (retryRef.current) clearTimeout(retryRef.current);

    const needsNavigation =
      currentStep.route && location.pathname !== currentStep.route;

    if (needsNavigation && currentStep.route) {
      navigate(currentStep.route);
    }

    const delay = currentStep.delay ?? (needsNavigation ? 300 : 50);

    const tryFind = (attempts = 0) => {
      retryRef.current = setTimeout(
        () => {
          if (cancelled) return;

          if (findAndSpotlight(currentStep.targetSelector)) {
            setShowContent(true);
            return;
          }

          if (attempts >= 3 && !showContent) {
            setIsWaitingForElement(true);
            setShowContent(true);
            setIsTransitioning(false);
          }

          if (attempts < MAX_RETRIES) {
            tryFind(attempts + 1);
          } else {
            setSpotlight(null);
            setShowContent(true);
            setIsWaitingForElement(false);
            setElementNotFound(true);
            setIsTransitioning(false);
          }
        },
        attempts === 0 ? delay : 100,
      );
    };

    // If step has a preAction, execute it before trying to find the target
    const runStep = async () => {
      if (currentStep.preAction) {
        // Wait for navigation to settle first
        await new Promise((r) => setTimeout(r, needsNavigation ? 400 : 100));
        if (cancelled) return;
        await executePreAction(currentStep.preAction);
        if (cancelled) return;
      }
      tryFind();
    };

    runStep();

    return () => {
      cancelled = true;
      if (retryRef.current) clearTimeout(retryRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isActive, currentStepIndex, currentStep, currentTour?.id]);

  // Update spotlight position on scroll/resize
  useEffect(() => {
    if (!isActive || !currentStep || !showContent || isWaitingForElement)
      return;

    const updatePosition = () => {
      const el = document.querySelector(currentStep.targetSelector);
      if (el) {
        const rect = el.getBoundingClientRect();
        const padding = 8;
        setSpotlight({
          top: rect.top - padding,
          left: rect.left - padding,
          width: rect.width + padding * 2,
          height: rect.height + padding * 2,
        });
      }
    };

    window.addEventListener("scroll", updatePosition, true);
    window.addEventListener("resize", updatePosition);

    return () => {
      window.removeEventListener("scroll", updatePosition, true);
      window.removeEventListener("resize", updatePosition);
    };
  }, [isActive, currentStep, showContent, isWaitingForElement]);

  // After message card renders, mark as measured so position recalculates with real height
  useLayoutEffect(() => {
    if (showContent && messageRef.current && !messageMeasured) {
      setMessageMeasured(true);
    }
  }, [showContent, messageMeasured]);

  const navigateHome = useCallback(() => {
    closeOpenDialogs();
    // Give React a tick to process dialog close before navigating
    setTimeout(() => {
      navigate("/");
    }, 50);
  }, [navigate, closeOpenDialogs]);

  // Handle click to advance — fade out first, then move to the next step
  const handleClick = useCallback(() => {
    if (isTransitioning || isWaitingForElement) return;

    // Start exit fade immediately
    setIsTransitioning(true);

    // After the fade-out completes, advance or end the tour
    setTimeout(() => {
      if (isLastStep) {
        endTour();
        navigateHome();
      } else {
        // If the current step opened a drawer and the next step doesn't target it,
        // close the drawer before advancing
        const nextStep = currentTour?.steps[currentStepIndex + 1];
        const currentTargetsDialog =
          currentStep?.targetSelector.includes('[role="dialog"]');
        const nextTargetsDialog =
          nextStep?.targetSelector.includes('[role="dialog"]');

        if (currentTargetsDialog && !nextTargetsDialog) {
          closeOpenDialogs();
        }

        advance();
      }
    }, 200);
  }, [
    isTransitioning,
    isWaitingForElement,
    isLastStep,
    advance,
    endTour,
    navigateHome,
    closeOpenDialogs,
    currentStep,
    currentTour,
    currentStepIndex,
  ]);

  // Block pointer events from reaching Radix's DismissableLayer and dialog content
  // during tours. We intercept in the capture phase to prevent Radix from dismissing
  // dialogs on "outside" clicks, and to prevent interaction with dialog content.
  // We also manually trigger our advance handler since stopImmediatePropagation
  // prevents React's synthetic onClick from firing.
  useEffect(() => {
    if (!isActive) return;
    const blockPointer = (e: PointerEvent | MouseEvent) => {
      // Skip blocking when executePreAction temporarily disables it
      if (!pointerBlockEnabled.current) return;
      e.stopImmediatePropagation();
      e.preventDefault();
    };
    // Use a native click handler (capture phase) to advance the tour,
    // since React's onClick won't fire after stopImmediatePropagation on pointerdown.
    const handleNativeClick = (e: MouseEvent) => {
      if (!pointerBlockEnabled.current) return;
      e.stopImmediatePropagation();
      e.preventDefault();
      handleClick();
    };
    document.addEventListener("pointerdown", blockPointer, true);
    document.addEventListener("mousedown", blockPointer as EventListener, true);
    document.addEventListener("click", handleNativeClick, true);
    return () => {
      document.removeEventListener("pointerdown", blockPointer, true);
      document.removeEventListener(
        "mousedown",
        blockPointer as EventListener,
        true,
      );
      document.removeEventListener("click", handleNativeClick, true);
    };
  }, [isActive, handleClick]);

  // Disable pointer events on all dialogs/drawers during the tour
  useEffect(() => {
    if (!isActive) return;
    const style = document.createElement("style");
    style.setAttribute("data-tour-blocker", "");
    style.textContent = `
      [role="dialog"],
      [role="dialog"] *,
      [data-radix-portal] > *,
      [data-radix-portal] > * * {
        cursor: pointer !important;
        user-select: none !important;
        -webkit-user-select: none !important;
      }
    `;
    document.head.appendChild(style);
    return () => {
      style.remove();
    };
  }, [isActive]);

  // Handle escape key
  useEffect(() => {
    if (!isActive) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopImmediatePropagation();
        e.preventDefault();
        endTour();
        navigateHome();
      }
    };
    // Use capture phase to intercept before Radix dialog handlers
    window.addEventListener("keydown", handleKey, true);
    return () => window.removeEventListener("keydown", handleKey, true);
  }, [isActive, endTour, navigateHome]);

  if (!isActive || !currentStep) return null;

  const getMessagePosition = () => {
    if (!spotlight) {
      return { top: "50%", left: "50%", transform: "translate(-50%, -50%)" };
    }

    const arrowDir = currentStep.arrowDirection ?? "top";
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const msgWidth = 360;
    // Use actual rendered height if available, otherwise estimate
    const msgHeight = messageRef.current?.offsetHeight ?? 200;
    const gap = 52;

    const spotRight = spotlight.left + spotlight.width;
    const spotBottom = spotlight.top + spotlight.height;
    // For tall elements that exceed viewport, use the visible bottom
    const visibleSpotBottom = Math.min(spotBottom, viewportHeight - 16);
    // For elements that start above viewport, use visible top
    const visibleSpotTop = Math.max(spotlight.top, 16);
    // Vertical center of the visible portion of the spotlight
    const visibleCenterY = (visibleSpotTop + visibleSpotBottom) / 2;

    // Calculate candidate positions for each side
    const candidates: { top: number; left: number; fits: boolean }[] = [];

    // Below spotlight (visible bottom)
    const belowTop = visibleSpotBottom + gap;
    candidates.push({
      top: belowTop,
      left: spotlight.left + spotlight.width / 2 - msgWidth / 2,
      fits: belowTop + msgHeight < viewportHeight - 16,
    });

    // Above spotlight (visible top)
    const aboveTop = visibleSpotTop - msgHeight - gap;
    candidates.push({
      top: aboveTop,
      left: spotlight.left + spotlight.width / 2 - msgWidth / 2,
      fits: aboveTop > 16,
    });

    // Right of spotlight
    candidates.push({
      top: visibleCenterY - msgHeight / 2,
      left: spotRight + gap,
      fits: spotRight + gap + msgWidth < viewportWidth - 16,
    });

    // Left of spotlight
    candidates.push({
      top: visibleCenterY - msgHeight / 2,
      left: spotlight.left - msgWidth - gap,
      fits: spotlight.left - msgWidth - gap > 16,
    });

    // Preferred direction index: top→0 (below), bottom→1 (above), left→2 (right), right→3 (left)
    const preferredIndex =
      arrowDir === "top"
        ? 0
        : arrowDir === "bottom"
          ? 1
          : arrowDir === "left"
            ? 2
            : 3;

    // Try preferred direction first, then fall through to others
    let chosen = candidates[preferredIndex]!;
    if (!chosen.fits) {
      chosen = candidates.find((c) => c.fits) ?? chosen;
    }

    let { top, left } = chosen!;

    // Clamp to viewport — this is the final safety net
    left = Math.max(16, Math.min(left, viewportWidth - msgWidth - 16));
    top = Math.max(16, Math.min(top, viewportHeight - msgHeight - 16));

    return { top: `${top}px`, left: `${left}px` };
  };

  const getArrowStyle = () => {
    if (!spotlight) return null;

    const msgPos = getMessagePosition();
    const msgTop = parseFloat(msgPos.top as string) || 0;
    const msgLeft = parseFloat(msgPos.left as string) || 0;
    const msgWidth = 360;
    const msgHeight = 200;

    const msgCenterX = msgLeft + msgWidth / 2;
    const msgCenterY = msgTop + msgHeight / 2;
    const spotCenterX = spotlight.left + spotlight.width / 2;
    const spotCenterY = spotlight.top + spotlight.height / 2;

    const dx = spotCenterX - msgCenterX;
    const dy = spotCenterY - msgCenterY;

    if (Math.abs(dy) > Math.abs(dx)) {
      if (dy > 0) {
        return {
          top: msgTop + msgHeight + 2,
          left: Math.max(
            msgLeft + 20,
            Math.min(spotCenterX - 16, msgLeft + msgWidth - 52),
          ),
          char: "\u25BC",
        };
      } else {
        return {
          top: msgTop - 34,
          left: Math.max(
            msgLeft + 20,
            Math.min(spotCenterX - 16, msgLeft + msgWidth - 52),
          ),
          char: "\u25B2",
        };
      }
    } else {
      if (dx > 0) {
        return {
          top: Math.max(
            msgTop + 20,
            Math.min(spotCenterY - 16, msgTop + msgHeight - 52),
          ),
          left: msgLeft + msgWidth + 2,
          char: "\u25B6",
        };
      } else {
        return {
          top: Math.max(
            msgTop + 20,
            Math.min(spotCenterY - 16, msgTop + msgHeight - 52),
          ),
          left: msgLeft - 34,
          char: "\u25C0",
        };
      }
    }
  };

  const msgPos = getMessagePosition();
  const arrowStyle = getArrowStyle();
  const stepLabel = currentTour
    ? `Step ${currentStepIndex + 1} of ${currentTour.steps.length}`
    : "";

  // Render via portal to document.body so we're at the same stacking level
  // as Radix portals. z-[10000] ensures we sit above Radix's z-50.
  return createPortal(
    <div
      className="fixed inset-0"
      style={{
        zIndex: 10000,
        cursor: isWaitingForElement && !elementNotFound ? "default" : "pointer",
      }}
    >
      {/* Dark overlay with spotlight cutout */}
      {spotlight ? (
        <div
          className="fixed transition-[top,left,width,height] duration-500 ease-in-out"
          style={{
            top: spotlight.top,
            left: spotlight.left,
            width: spotlight.width,
            height: spotlight.height,
            boxShadow: "0 0 0 9999px rgba(0, 0, 0, 0.55)",
            borderRadius: "8px",
            border: "2px solid hsl(var(--primary))",
            animation: showContent
              ? "tour-pulse 2s ease-in-out infinite"
              : undefined,
            pointerEvents: "none",
          }}
        />
      ) : (
        <div className="absolute inset-0 bg-black/55" />
      )}

      {/* Animated arrow — only when spotlight is visible (not loading) */}
      {showContent && !isWaitingForElement && arrowStyle && (
        <div
          className="fixed pointer-events-none flex items-center justify-center"
          style={{
            top: arrowStyle.top,
            left: arrowStyle.left,
            width: 32,
            height: 32,
            zIndex: 10002,
            animation: "tour-bounce 1.2s ease-in-out infinite",
            opacity: isTransitioning ? 0 : 1,
            transition: "opacity 200ms ease-out",
          }}
        >
          <span
            style={{
              fontSize: "32px",
              lineHeight: 1,
              color: "hsl(var(--primary))",
              filter:
                "drop-shadow(0 0 10px hsl(var(--primary) / 0.9)) drop-shadow(0 0 20px hsl(var(--primary) / 0.5))",
            }}
          >
            {arrowStyle.char}
          </span>
        </div>
      )}

      {/* Message card — shown immediately, even while waiting for element */}
      {showContent && (
        <div
          ref={messageRef}
          className="fixed bg-card border border-primary/30 rounded-xl shadow-2xl p-5 pointer-events-none max-w-[360px]"
          style={{
            ...msgPos,
            zIndex: 10001,
            animation: isTransitioning
              ? undefined
              : "tour-fade-in 0.3s ease-out",
            opacity: isTransitioning ? 0 : 1,
            transform: isTransitioning ? "translateY(8px)" : "translateY(0)",
            transition: "opacity 200ms ease-out, transform 200ms ease-out",
          }}
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-medium text-primary">
              {stepLabel}
            </span>
            <span className="text-xs text-muted-foreground">
              Press Esc to exit
            </span>
          </div>
          <h3 className="text-lg font-semibold text-foreground mb-2">
            {currentStep.title}
          </h3>

          {elementNotFound && currentStep.fallbackMessage ? (
            <>
              <p className="text-sm text-muted-foreground leading-relaxed">
                {currentStep.fallbackMessage}
              </p>
              <p className="text-xs text-primary mt-3 font-medium">
                {isLastStep
                  ? "Click anywhere to finish the tour"
                  : "Click anywhere to continue \u2192"}
              </p>
            </>
          ) : (
            <>
              <p className="text-sm text-muted-foreground leading-relaxed">
                {currentStep.message}
              </p>

              {isWaitingForElement ? (
                <div className="flex items-center gap-2 mt-3">
                  <div className="h-3.5 w-3.5 rounded-full border border-primary border-t-transparent animate-spin" />
                  <span className="text-xs text-muted-foreground">
                    Loading content, one moment...
                  </span>
                </div>
              ) : (
                <p className="text-xs text-primary mt-3 font-medium">
                  {isLastStep
                    ? "Click anywhere to finish the tour"
                    : "Click anywhere to continue \u2192"}
                </p>
              )}
            </>
          )}
        </div>
      )}

      {/* CSS animations */}
      <style>{`
        @keyframes tour-pulse {
          0%, 100% { border-color: hsl(var(--primary)); box-shadow: 0 0 0 9999px rgba(0,0,0,0.55), 0 0 15px 2px hsl(var(--primary) / 0.3); }
          50% { border-color: hsl(var(--primary) / 0.6); box-shadow: 0 0 0 9999px rgba(0,0,0,0.55), 0 0 25px 4px hsl(var(--primary) / 0.15); }
        }
        @keyframes tour-bounce {
          0%, 100% { transform: translateY(0); opacity: 1; }
          50% { transform: translateY(-8px); opacity: 0.8; }
        }
        @keyframes tour-fade-in {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>,
    document.body,
  );
}
