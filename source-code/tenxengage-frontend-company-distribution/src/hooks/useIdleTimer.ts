import { useEffect, useRef, useCallback, useState } from "react";

const ACTIVITY_EVENTS: Array<keyof DocumentEventMap> = [
  "mousemove",
  "keydown",
  "scroll",
  "click",
  "touchstart",
];

const IDLE_KEY = "rc_last_activity";

interface UseIdleTimerOptions {
  timeoutMs: number;
  warningMs: number;
  onIdle: () => void;
  enabled?: boolean;
}

export function useIdleTimer({
  timeoutMs,
  warningMs,
  onIdle,
  enabled = true,
}: UseIdleTimerOptions) {
  const [showWarning, setShowWarning] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const idleTimerRef = useRef<number | undefined>();
  const warningTimerRef = useRef<number | undefined>();
  const countdownRef = useRef<number | undefined>();
  const onIdleRef = useRef(onIdle);
  onIdleRef.current = onIdle;

  const clearAllTimers = useCallback(() => {
    window.clearTimeout(idleTimerRef.current);
    window.clearTimeout(warningTimerRef.current);
    window.clearInterval(countdownRef.current);
  }, []);

  const resetTimers = useCallback(() => {
    if (!enabled) return;

    clearAllTimers();
    setShowWarning(false);

    localStorage.setItem(IDLE_KEY, Date.now().toString());

    const warningDelay = timeoutMs - warningMs;

    warningTimerRef.current = window.setTimeout(() => {
      const secondsLeft = Math.ceil(warningMs / 1000);
      setRemainingSeconds(secondsLeft);
      setShowWarning(true);

      countdownRef.current = window.setInterval(() => {
        setRemainingSeconds((prev) => {
          if (prev <= 1) {
            window.clearInterval(countdownRef.current);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }, warningDelay);

    idleTimerRef.current = window.setTimeout(() => {
      clearAllTimers();
      setShowWarning(false);
      onIdleRef.current();
    }, timeoutMs);
  }, [enabled, timeoutMs, warningMs, clearAllTimers]);

  const stayActive = useCallback(() => {
    resetTimers();
  }, [resetTimers]);

  useEffect(() => {
    if (!enabled) {
      clearAllTimers();
      setShowWarning(false);
      return;
    }

    const handleActivity = () => {
      if (!showWarning) {
        resetTimers();
      }
    };

    ACTIVITY_EVENTS.forEach((event) => {
      document.addEventListener(event, handleActivity, { passive: true });
    });

    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === IDLE_KEY) {
        resetTimers();
      }
    };
    window.addEventListener("storage", handleStorageChange);

    resetTimers();

    return () => {
      clearAllTimers();
      ACTIVITY_EVENTS.forEach((event) => {
        document.removeEventListener(event, handleActivity);
      });
      window.removeEventListener("storage", handleStorageChange);
    };
  }, [enabled, resetTimers, clearAllTimers, showWarning]);

  return { showWarning, remainingSeconds, stayActive };
}
