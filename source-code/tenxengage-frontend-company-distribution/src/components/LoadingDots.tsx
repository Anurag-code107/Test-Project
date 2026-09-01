import { cn } from "@/lib/utils";

interface LoadingDotsProps {
  /** Tailwind color class for the dots (e.g. "bg-emerald-500"). Defaults to emerald. */
  dotClassName?: string;
  /** Optional wrapper class (e.g. for height/alignment). */
  className?: string;
  /** Size preset — "sm" (1.5px dots) or "md" (2px dots). */
  size?: "sm" | "md";
  /** Accessible label announced to screen readers. */
  label?: string;
}

export function LoadingDots({
  dotClassName = "bg-emerald-500 dark:bg-emerald-400",
  className,
  size = "sm",
  label = "Loading",
}: LoadingDotsProps) {
  const dotSize = size === "sm" ? "h-1.5 w-1.5" : "h-2 w-2";
  return (
    <span
      className={cn("inline-flex items-center gap-1 align-middle", className)}
      role="status"
      aria-label={label}
    >
      <span
        className={cn(
          "rounded-full animate-pulse [animation-delay:0ms]",
          dotSize,
          dotClassName,
        )}
      />
      <span
        className={cn(
          "rounded-full animate-pulse [animation-delay:200ms]",
          dotSize,
          dotClassName,
        )}
      />
      <span
        className={cn(
          "rounded-full animate-pulse [animation-delay:400ms]",
          dotSize,
          dotClassName,
        )}
      />
    </span>
  );
}
