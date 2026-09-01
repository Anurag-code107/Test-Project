import { useState, useEffect, useRef } from "react";

interface FlowTransitionProps {
  /** Change this value to trigger a transition */
  transitionKey: string;
  children: React.ReactNode;
  /** "forward" slides left→right, "backward" slides right→left */
  direction?: "forward" | "backward";
}

/**
 * Cross-fade + directional slide transition for multi-step flows.
 *
 * Old content quickly fades out, then new content slides in from
 * the travel direction — like turning a page.
 * Uses transform + opacity only (no layout animations).
 * Respects prefers-reduced-motion.
 */
export function FlowTransition({
  transitionKey,
  children,
  direction = "forward",
}: FlowTransitionProps) {
  const EXIT_MS = 150; // fast exit — old content vanishes quickly
  const ENTER_MS = 400; // smooth enter — new content is the main event

  const [displayed, setDisplayed] = useState(children);
  const [phase, setPhase] = useState<"idle" | "exit" | "pre-enter" | "enter">(
    "idle",
  );
  const prevKeyRef = useRef(transitionKey);
  const childrenRef = useRef(children);
  const directionRef = useRef(direction);
  const timeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const rafRef = useRef<number>();

  // Always track the latest children and direction
  childrenRef.current = children;
  directionRef.current = direction;

  // Keep displayed in sync when key hasn't changed
  useEffect(() => {
    if (transitionKey === prevKeyRef.current) {
      setDisplayed(children);
    }
  }, [transitionKey, children]);

  // Trigger transition when key changes
  useEffect(() => {
    if (transitionKey === prevKeyRef.current) return;

    // Exit phase — old content fades out quickly
    setPhase("exit");

    clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => {
      // Swap content and snap to enter start position (no transition)
      prevKeyRef.current = transitionKey;
      setDisplayed(childrenRef.current);
      setPhase("pre-enter");

      // Next frame: animate to final position
      rafRef.current = requestAnimationFrame(() => {
        rafRef.current = requestAnimationFrame(() => {
          setPhase("enter");

          timeoutRef.current = setTimeout(() => {
            setPhase("idle");
          }, ENTER_MS);
        });
      });
    }, EXIT_MS);

    return () => {
      clearTimeout(timeoutRef.current);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [transitionKey]);

  const isForward = directionRef.current === "forward";

  // Exit: small slide out in the direction of travel
  // Enter: bigger slide in from the opposite side
  const exitTranslate = isForward ? "-16px" : "16px";
  const enterTranslate = isForward ? "40px" : "-40px";

  const exitTransition = `opacity ${EXIT_MS}ms ease-out, transform ${EXIT_MS}ms ease-out`;
  const enterTransition = `opacity ${ENTER_MS}ms cubic-bezier(0.16, 1, 0.3, 1), transform ${ENTER_MS}ms cubic-bezier(0.16, 1, 0.3, 1)`;

  let style: React.CSSProperties;
  switch (phase) {
    case "exit":
      style = {
        opacity: 0,
        transform: `translateX(${exitTranslate})`,
        transition: exitTransition,
      };
      break;
    case "pre-enter":
      // Snap to enter start position — no transition so it's instant
      style = {
        opacity: 0,
        transform: `translateX(${enterTranslate})`,
        transition: "none",
      };
      break;
    case "enter":
      style = {
        opacity: 1,
        transform: "translateX(0)",
        transition: enterTransition,
      };
      break;
    default:
      style = {
        opacity: 1,
        transform: "translateX(0)",
      };
  }

  return (
    <div
      className="no-route-animation h-full motion-reduce:transition-none"
      style={style}
    >
      {displayed}
    </div>
  );
}
