import { useState, useCallback, useEffect, useRef } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  Sparkles,
  ArrowRight,
  AlertCircle,
  Loader2,
  X,
  MapPin,
  Bot,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useGuidedTour } from "@/contexts/GuidedTourContext";
import { useFeatures } from "@/hooks/useFeatures";
import { findTourForQuery, getSuggestedTours } from "@/data/guidedTours";
import { cn } from "@/lib/utils";
import type { GuidedTour, TextGuideStep } from "@/data/guidedTours";

const responseMessages = [
  "Great question! Let me show you how…",
  "Now that's a good one! Let me walk you through it…",
  "I know just the thing — let me guide you!",
  "That's a great question! Follow me…",
];

type BubblePhase =
  | "hidden"
  | "entering"
  | "thinking"
  | "typing"
  | "visible"
  | "exiting";

function TypewriterText({
  text,
  onComplete,
}: {
  text: string;
  onComplete: () => void;
}) {
  const [displayed, setDisplayed] = useState("");
  const indexRef = useRef(0);
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  useEffect(() => {
    indexRef.current = 0;
    setDisplayed("");
    const interval = setInterval(() => {
      indexRef.current++;
      if (indexRef.current <= text.length) {
        setDisplayed(text.slice(0, indexRef.current));
      } else {
        clearInterval(interval);
        onCompleteRef.current();
      }
    }, 30);
    return () => clearInterval(interval);
  }, [text]);

  return (
    <span>
      {displayed}
      {displayed.length < text.length && (
        <span className="inline-block w-[2px] h-[1em] bg-primary ml-0.5 align-middle animate-[cursor-blink_0.6s_ease-in-out_infinite]" />
      )}
    </span>
  );
}

function ThinkingDots() {
  return (
    <span className="inline-flex items-center gap-1">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-primary"
          style={{
            animation: `bubble-thinking-dot 1.2s ease-in-out ${i * 0.2}s infinite`,
          }}
        />
      ))}
    </span>
  );
}

const defaultExamples = [
  "How do I set up a new incentive?",
  "How do I earn rewards?",
  "How do I submit a claim?",
  "Which deals qualify for incentives?",
];

export function AIAssistantInput() {
  const { user } = useAuth();
  const { startTour, isActive } = useGuidedTour();
  const { has } = useFeatures();
  const hasGuidedTours = has("guided_tours");
  const navigate = useNavigate();
  const location = useLocation();
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<GuidedTour[]>([]);
  const [textGuide, setTextGuide] = useState<TextGuideStep[] | null>(null);

  // Bubble animation state
  const [bubblePhase, setBubblePhase] = useState<BubblePhase>("hidden");
  const [bubbleText, setBubbleText] = useState("");
  const pendingTourRef = useRef<GuidedTour | null>(null);
  const timeoutsRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearBubbleTimeouts = useCallback(() => {
    timeoutsRef.current.forEach(clearTimeout);
    timeoutsRef.current = [];
  }, []);

  const schedule = useCallback((fn: () => void, ms: number) => {
    const id = setTimeout(fn, ms);
    timeoutsRef.current.push(id);
    return id;
  }, []);

  const handleTypingComplete = useCallback(() => {
    schedule(() => setBubblePhase("exiting"), 800);
    schedule(() => {
      const tour = pendingTourRef.current;
      if (!tour) {
        setBubblePhase("hidden");
        return;
      }

      const firstStepRoute = tour.steps[0]?.route;
      const needsNav = firstStepRoute && location.pathname !== firstStepRoute;

      if (needsNav) {
        navigate(firstStepRoute);
        schedule(() => {
          setBubblePhase("hidden");
          startTour(tour);
          pendingTourRef.current = null;
        }, 300);
      } else {
        setBubblePhase("hidden");
        startTour(tour);
        pendingTourRef.current = null;
      }
    }, 1200);
  }, [startTour, schedule, navigate, location.pathname]);

  const launchTourWithBubble = useCallback(
    (tour: GuidedTour) => {
      clearBubbleTimeouts();
      pendingTourRef.current = tour;

      const msg =
        responseMessages[Math.floor(Math.random() * responseMessages.length)];
      setBubbleText(msg ?? "");
      setBubblePhase("entering");

      schedule(() => setBubblePhase("thinking"), 100);
      schedule(() => setBubblePhase("typing"), 1200);
    },
    [clearBubbleTimeouts, schedule],
  );

  const isAnimating = bubblePhase !== "hidden";
  const isBusy = isLoading || isAnimating;

  const userRole = user?.clientRoleName ?? "user";

  if (!user || (isActive && !isAnimating)) return null;

  const examples = defaultExamples;

  const clearResults = () => {
    setError(null);
    setSuggestions([]);
    setTextGuide(null);
  };

  const handleSubmit = async () => {
    if (!query.trim() || isBusy) return;
    clearResults();
    setIsLoading(true);

    try {
      const result = await findTourForQuery(query, userRole);

      if (result.tour) {
        setQuery("");
        setIsLoading(false);
        launchTourWithBubble(result.tour);
        return;
      }

      if (result.textGuide && result.textGuide.length > 0) {
        setTextGuide(result.textGuide);
        return;
      }

      if (result.suggestions.length > 0) {
        setSuggestions(result.suggestions);
        setError("I found some related guides that might help:");
      } else {
        const fallback = getSuggestedTours(query, userRole);
        if (fallback.length > 0) {
          setSuggestions(fallback);
          setError(
            "I'm not sure how to help with that. Here are some guides you might find useful:",
          );
        } else {
          setError(
            "I'm not sure how to help with that. Try asking about incentives, rewards, claims, or navigating the platform!",
          );
        }
      }
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleExampleClick = async (example: string) => {
    if (isBusy) return;
    setQuery(example);
    clearResults();
    setIsLoading(true);

    try {
      const result = await findTourForQuery(example, userRole);
      if (result.tour) {
        setQuery("");
        setIsLoading(false);
        launchTourWithBubble(result.tour);
      } else if (result.textGuide && result.textGuide.length > 0) {
        setTextGuide(result.textGuide);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleSuggestionClick = (tour: GuidedTour) => {
    clearResults();
    setQuery("");
    startTour(tour);
  };

  const hasResults = suggestions.length > 0 || textGuide !== null;

  // Tier gate: if guided_tours is not enabled for this tenant, the AI
  // assistant input does not render at all. Placed after all hooks above
  // to comply with the rules of hooks.
  if (!hasGuidedTours) return null;

  return (
    <div className="w-full h-full relative flex flex-col">
      {/* Full-screen tour search overlay */}
      {isAnimating && (
        <div
          className={cn(
            "fixed inset-0 z-50 flex flex-col items-center justify-center gap-5 overflow-hidden",
            "transition-[opacity,background-color,backdrop-filter] duration-500 ease-out",
            bubblePhase === "entering" && "opacity-0 bg-background/0",
            (bubblePhase === "thinking" ||
              bubblePhase === "typing" ||
              bubblePhase === "visible") &&
              "opacity-100 bg-background/95 backdrop-blur-md",
            bubblePhase === "exiting" && "opacity-0 bg-background/0",
          )}
        >
          {/* Subtle radial glow behind the bot */}
          <div
            className={cn(
              "absolute inset-0 pointer-events-none transition-opacity duration-700",
              bubblePhase === "thinking" || bubblePhase === "typing"
                ? "opacity-100"
                : "opacity-0",
            )}
            style={{
              background:
                "radial-gradient(ellipse 50% 50% at 50% 45%, hsl(var(--primary) / 0.06) 0%, transparent 70%)",
            }}
          />

          <div className="relative">
            {/* Outer glow ring */}
            <div
              className={cn(
                "absolute -inset-5 rounded-full transition-opacity duration-500",
                bubblePhase === "thinking" ? "opacity-100" : "opacity-0",
              )}
              style={{
                background:
                  "radial-gradient(circle, hsl(var(--primary) / 0.2) 0%, transparent 70%)",
                animation:
                  bubblePhase === "thinking"
                    ? "bubble-glow-pulse 1.5s ease-in-out infinite"
                    : undefined,
              }}
            />
            {/* Bot avatar */}
            <div
              className={cn(
                "relative p-5 rounded-full bg-primary/8 border border-primary/15 transition-[transform,box-shadow] duration-500",
                bubblePhase === "entering" && "scale-0 rotate-[-180deg]",
                (bubblePhase === "thinking" ||
                  bubblePhase === "typing" ||
                  bubblePhase === "visible") &&
                  "scale-100 rotate-0",
                bubblePhase === "exiting" && "scale-110 rotate-12",
              )}
              style={{
                boxShadow:
                  bubblePhase === "thinking" || bubblePhase === "typing"
                    ? "0 0 30px hsl(var(--primary) / 0.2), 0 0 60px hsl(var(--primary) / 0.08)"
                    : undefined,
              }}
            >
              <Bot className="h-8 w-8 text-primary" />
            </div>
          </div>

          {/* Text area */}
          <div className="text-center min-h-[1.5rem] max-w-[340px] px-4">
            {bubblePhase === "thinking" && (
              <div className="flex items-center justify-center gap-1.5">
                <ThinkingDots />
              </div>
            )}
            {(bubblePhase === "typing" || bubblePhase === "visible") && (
              <p className="text-base font-medium text-foreground leading-relaxed">
                {bubblePhase === "typing" ? (
                  <TypewriterText
                    text={bubbleText}
                    onComplete={handleTypingComplete}
                  />
                ) : (
                  bubbleText
                )}
              </p>
            )}
          </div>

          {/* Animated progress line at bottom */}
          {(bubblePhase === "thinking" || bubblePhase === "typing") && (
            <div className="absolute bottom-0 left-0 right-0 h-[2px] overflow-hidden">
              <div
                className="h-full"
                style={{
                  background:
                    "linear-gradient(90deg, hsl(199 89% 48% / 0.4), hsl(217 91% 60% / 0.6), hsl(95 55% 50% / 0.4))",
                  animation:
                    "login-transition-progress 1.4s cubic-bezier(0.4, 0, 0.2, 1) infinite",
                }}
              />
            </div>
          )}
        </div>
      )}

      {/* CSS for bubble animations */}
      <style>{`
        @keyframes bubble-thinking-dot {
          0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
          40% { opacity: 1; transform: scale(1.2); }
        }
        @keyframes bubble-glow-pulse {
          0%, 100% { transform: scale(1); opacity: 0.6; }
          50% { transform: scale(1.3); opacity: 1; }
        }
        @keyframes cursor-blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0; }
        }
      `}</style>

      {/* ---- Clean inline AI bar ---- */}
      <div className="space-y-2.5 flex-1 flex flex-col rounded-xl border border-border p-4">
        {/* Input row */}
        <div className="group/ai-input ai-guide-border relative flex items-center gap-3 rounded-xl border border-border bg-background px-4 py-2.5 focus-within:border-primary/30 focus-within:shadow-[0_0_0_3px_hsl(var(--primary)/0.06)]">
          <div className="ai-guide-sparkle-icon flex items-center justify-center w-7 h-7 rounded-lg bg-primary/10 shrink-0 transition-[background-color,box-shadow] duration-300">
            <Sparkles className="h-3.5 w-3.5 text-primary transition-transform duration-300 group-hover/ai-input:rotate-12 group-hover/ai-input:scale-110" />
          </div>
          <div className="flex-1 relative min-h-0">
            {/* Shimmer placeholder — rendered BEFORE input, positioned absolutely.
                The input sits on top with position:relative z-10 so it captures clicks,
                but has a transparent background so the shimmer shows through. */}
            {!query && (() => {
              const text = "Ask a question about tenXengage and we'll guide you through it...";
              return (
                <span
                  className="ai-guide-placeholder pointer-events-none absolute inset-0 z-0 flex items-center text-sm leading-5 text-muted-foreground transition-opacity duration-200"
                  data-text={text}
                  aria-hidden="true"
                >
                  {text.split("").map((char, i) => (
                    <span
                      key={i}
                      className="ai-guide-letter"
                      style={{ animationDelay: `${i * 30}ms` }}
                    >
                      {char === " " ? " " : char}
                    </span>
                  ))}
                </span>
              );
            })()}
            <input
              type="text"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                clearResults();
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  handleSubmit();
                }
              }}
              placeholder=""
              disabled={isLoading}
              className={cn(
                "w-full relative z-10 text-sm leading-5 bg-transparent focus:outline-none",
                isLoading && "opacity-60",
              )}
            />
          </div>
          <button
            onClick={handleSubmit}
            disabled={!query.trim() || isLoading}
            className={cn(
              "shrink-0 p-1.5 rounded-lg transition-[background-color,color,box-shadow] duration-150",
              query.trim() && !isLoading
                ? "bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm"
                : "bg-transparent text-muted-foreground/40 cursor-not-allowed",
            )}
          >
            {isLoading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ArrowRight className="h-4 w-4" />
            )}
          </button>
        </div>

        {/* Loading indicator */}
        {isLoading && (
          <div className="flex items-center gap-2 text-sm text-muted-foreground px-1">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Finding the best guide for you...</span>
          </div>
        )}

        {/* Error (no suggestions) */}
        {error && !suggestions.length && !textGuide && (
          <div className="flex items-start gap-2 text-sm text-destructive bg-destructive/5 rounded-lg p-3 mx-1">
            <AlertCircle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Text guide response */}
        {textGuide && (
          <div className="rounded-xl border border-primary/15 bg-primary/5 p-4 space-y-3 mx-1">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MapPin className="h-3.5 w-3.5 text-primary" />
                <span className="text-sm font-semibold text-foreground">
                  Here's how to do that:
                </span>
              </div>
              <button
                onClick={() => {
                  setTextGuide(null);
                  setQuery("");
                }}
                className="p-1 rounded-md hover:bg-muted transition-colors"
              >
                <X className="h-3.5 w-3.5 text-muted-foreground" />
              </button>
            </div>
            <ol className="space-y-2.5">
              {textGuide.map((step, idx) => (
                <li key={idx} className="flex gap-3">
                  <span className="flex-shrink-0 flex items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-semibold mt-0.5 h-[20px] w-[20px]">
                    {idx + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground">
                      {step.title}
                    </p>
                    <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                      {step.description}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        )}

        {/* Suggested tours */}
        {suggestions.length > 0 && (
          <div className="space-y-2 mx-1">
            <p className="text-sm text-muted-foreground">{error}</p>
            <div className="flex flex-col gap-1.5">
              {suggestions.map((tour) => (
                <button
                  key={tour.id}
                  onClick={() => handleSuggestionClick(tour)}
                  className="flex items-center gap-2 text-sm px-3 py-2 rounded-lg border border-primary/15 bg-primary/5 text-foreground hover:bg-primary/10 hover:border-primary/25 transition-colors text-left"
                >
                  <Sparkles className="h-3 w-3 text-primary shrink-0" />
                  <span className="font-medium">{tour.name}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Example prompts — compact pills below the input */}
        {!hasResults && !isLoading && (
          <div className="flex flex-wrap gap-1.5 px-1">
            {examples.map((example, idx) => (
              <button
                key={idx}
                onClick={() => handleExampleClick(example)}
                disabled={isLoading}
                className={cn(
                  "text-xs px-3 py-1 rounded-full border border-border bg-background text-muted-foreground",
                  "hover:text-primary hover:border-primary/25 hover:bg-primary/5 transition-[color,border-color,background-color] duration-150",
                  isLoading && "opacity-50 cursor-not-allowed",
                )}
              >
                {example}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
