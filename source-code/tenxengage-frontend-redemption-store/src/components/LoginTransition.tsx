import { useEffect, useState } from "react";
import { useBrandingContext } from "@/contexts/BrandingContext";

interface LoginTransitionProps {
  onComplete: () => void;
}

/**
 * Branded splash screen shown between successful login and dashboard load.
 * Displays the logo with a subtle breathe animation and a progress indicator,
 * then fades out before handing off to the dashboard.
 */
export function LoginTransition({ onComplete }: LoginTransitionProps) {
  const [phase, setPhase] = useState<"enter" | "active" | "exit">("enter");
  const { logoSrc } = useBrandingContext();

  useEffect(() => {
    // Enter → active (logo + progress appear)
    const enterTimer = requestAnimationFrame(() => setPhase("active"));

    // Start exit fade quickly — no artificial loading time
    const exitTimer = setTimeout(() => setPhase("exit"), 400);

    // Navigate after exit animation completes (0.4s + 0.4s fade)
    const completeTimer = setTimeout(() => onComplete(), 800);

    return () => {
      cancelAnimationFrame(enterTimer);
      clearTimeout(exitTimer);
      clearTimeout(completeTimer);
    };
  }, [onComplete]);

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center"
      style={{
        background:
          "radial-gradient(ellipse 70% 60% at 50% 45%, hsl(210 30% 99%) 0%, hsl(210 20% 96.5%) 55%, hsl(210 18% 94.5%) 100%)",
        opacity: phase === "exit" ? 0 : 1,
        transition: "opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
      }}
    >
      {/* Logo — breathe animation */}
      <div
        className="login-transition-logo"
        style={{
          opacity: phase === "enter" ? 0 : 1,
          transform:
            phase === "enter"
              ? "scale(0.92) translateY(8px)"
              : "scale(1) translateY(0)",
          transition:
            "opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1), transform 0.6s cubic-bezier(0.16, 1, 0.3, 1)",
        }}
      >
        <img
          src={logoSrc}
          alt="Logo"
          className="block w-[310px] sm:w-[354px] h-auto min-h-14 sm:min-h-16 max-h-[140px] sm:max-h-[160px] object-contain"
        />
      </div>

      {/* Progress bar */}
      <div
        className="mt-10"
        style={{
          opacity: phase === "enter" ? 0 : 1,
          transition: "opacity 0.5s cubic-bezier(0.16, 1, 0.3, 1) 0.2s",
        }}
      >
        <div className="login-transition-track">
          <div className="login-transition-bar" />
        </div>
      </div>

      {/* Subtle message */}
      <p
        className="mt-5 text-sm text-[hsl(200_10%_56%)] font-medium tracking-wide"
        style={{
          opacity: phase === "enter" ? 0 : 0.8,
          transform: phase === "enter" ? "translateY(6px)" : "translateY(0)",
          transition: "opacity 0.5s ease 0.35s, transform 0.5s ease 0.35s",
        }}
      >
        Setting things up…
      </p>
    </div>
  );
}
