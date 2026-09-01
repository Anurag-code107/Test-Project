import { useState, useEffect, useRef } from "react";

interface FlipTransitionProps {
  transitionKey: string;
  children: React.ReactNode;
  reverse?: boolean;
}

export function FlipTransition({
  transitionKey,
  children,
  reverse = false,
}: FlipTransitionProps) {
  const [displayedChildren, setDisplayedChildren] = useState(children);
  const [animClass, setAnimClass] = useState("");
  const prevKeyRef = useRef(transitionKey);
  const childrenRef = useRef(children);
  const reverseRef = useRef(reverse);
  const timeoutRef = useRef<ReturnType<typeof setTimeout>>();

  // Always track latest children and reverse prop
  childrenRef.current = children;
  reverseRef.current = reverse;

  // When key matches current, keep children in sync (no animation)
  useEffect(() => {
    if (transitionKey === prevKeyRef.current) {
      setDisplayedChildren(children);
    }
  }, [transitionKey, children]);

  // When key changes, trigger the flip animation
  useEffect(() => {
    if (transitionKey === prevKeyRef.current) return;

    const isReverse = reverseRef.current;

    // Start flip-out
    setAnimClass(isReverse ? "animate-flip-out-reverse" : "animate-flip-out");

    clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => {
      // Swap content at the midpoint
      prevKeyRef.current = transitionKey;
      setDisplayedChildren(childrenRef.current);
      setAnimClass(isReverse ? "animate-flip-in-reverse" : "animate-flip-in");

      timeoutRef.current = setTimeout(() => {
        setAnimClass("");
      }, 250);
    }, 250);

    return () => clearTimeout(timeoutRef.current);
  }, [transitionKey]);

  return (
    <div style={{ perspective: "1200px" }} className="h-full">
      <div
        className={`h-full ${animClass}`}
        style={{ transformStyle: "preserve-3d" }}
      >
        {displayedChildren}
      </div>
    </div>
  );
}
