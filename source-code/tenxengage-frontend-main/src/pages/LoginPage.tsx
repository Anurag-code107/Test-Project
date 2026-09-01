import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuth } from "@/hooks/useAuth";
import { loginSchema, type LoginFormValues } from "@/utils/validators";
import { LoginTransition } from "@/components/LoginTransition";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Eye, EyeOff, ArrowRight } from "lucide-react";
import webLogo from "@/assets/web_logo.png";

/* ------------------------------------------------------------------ */
/*  Animated background — gentle flowing line art                      */
/* ------------------------------------------------------------------ */
function AnimatedBackground() {
  return (
    <div
      className="pointer-events-none fixed inset-0 overflow-hidden select-none"
      style={{
        maskImage:
          "radial-gradient(ellipse 32% 52% at 50% 50%, transparent 0%, transparent 60%, black 100%)",
        WebkitMaskImage:
          "radial-gradient(ellipse 32% 52% at 50% 50%, transparent 0%, transparent 60%, black 100%)",
      }}
    >
      <svg
        className="absolute inset-0 h-full w-full md:opacity-100 opacity-50"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* Flowing curves — primary blue (217 91% 60%) */}
        <path
          d="M-60 680 C200 620, 400 750, 620 680 S980 520, 1200 620 S1500 740, 1540 680"
          stroke="hsl(217 91% 60% / 0.20)"
          strokeWidth="2"
          className="login-line login-line-1"
        />
        <path
          d="M-40 720 C180 660, 420 790, 660 720 S1020 560, 1240 660 S1520 780, 1560 720"
          stroke="hsl(199 89% 48% / 0.14)"
          strokeWidth="1.5"
          className="login-line login-line-2"
        />
        {/* Top curves — mix of primary-light and green accent */}
        <path
          d="M-80 200 C160 260, 380 140, 580 200 S860 320, 1100 220 S1400 140, 1540 200"
          stroke="hsl(95 55% 50% / 0.14)"
          strokeWidth="2"
          className="login-line login-line-3"
        />
        <path
          d="M-40 240 C200 300, 420 180, 620 240 S900 360, 1140 260 S1440 180, 1560 240"
          stroke="hsl(199 89% 48% / 0.10)"
          strokeWidth="1.5"
          className="login-line login-line-4"
        />
        {/* Mid-screen curve */}
        <path
          d="M-60 460 C240 400, 500 540, 720 460 S1040 340, 1280 440 S1480 520, 1540 460"
          stroke="hsl(217 91% 60% / 0.09)"
          strokeWidth="1"
          className="login-line login-line-2"
        />

        {/* Geometric shapes — themed colors */}
        <circle
          cx="180"
          cy="300"
          r="70"
          stroke="hsl(217 91% 60% / 0.14)"
          strokeWidth="1.5"
          className="login-shape login-shape-1"
        />
        <circle
          cx="1260"
          cy="600"
          r="90"
          stroke="hsl(95 55% 50% / 0.11)"
          strokeWidth="1.5"
          className="login-shape login-shape-2"
        />
        <rect
          x="1080"
          y="130"
          width="120"
          height="120"
          rx="18"
          stroke="hsl(199 89% 48% / 0.13)"
          strokeWidth="1.5"
          className="login-shape login-shape-3"
          transform="rotate(12 1140 190)"
        />
        <rect
          x="220"
          y="570"
          width="85"
          height="85"
          rx="14"
          stroke="hsl(217 91% 60% / 0.12)"
          strokeWidth="1.5"
          className="login-shape login-shape-4"
          transform="rotate(-8 262 612)"
        />
        {/* Diamond */}
        <rect
          x="700"
          y="60"
          width="50"
          height="50"
          rx="8"
          stroke="hsl(95 55% 50% / 0.10)"
          strokeWidth="1"
          className="login-shape login-shape-1"
          transform="rotate(45 725 85)"
        />
        <circle
          cx="900"
          cy="780"
          r="45"
          stroke="hsl(199 89% 48% / 0.10)"
          strokeWidth="1"
          className="login-shape login-shape-2"
        />

        {/* Dots — mixed theme colors */}
        <circle
          cx="420"
          cy="160"
          r="4"
          fill="hsl(217 91% 60% / 0.22)"
          className="login-dot login-dot-1"
        />
        <circle
          cx="980"
          cy="740"
          r="3.5"
          fill="hsl(95 55% 50% / 0.20)"
          className="login-dot login-dot-2"
        />
        <circle
          cx="1300"
          cy="280"
          r="3"
          fill="hsl(199 89% 48% / 0.18)"
          className="login-dot login-dot-3"
        />
        <circle
          cx="120"
          cy="480"
          r="3.5"
          fill="hsl(217 91% 60% / 0.18)"
          className="login-dot login-dot-4"
        />
        <circle
          cx="760"
          cy="120"
          r="3"
          fill="hsl(95 55% 50% / 0.16)"
          className="login-dot login-dot-5"
        />
        <circle
          cx="600"
          cy="800"
          r="4"
          fill="hsl(199 89% 48% / 0.18)"
          className="login-dot login-dot-6"
        />
        <circle
          cx="340"
          cy="420"
          r="3"
          fill="hsl(95 55% 50% / 0.15)"
          className="login-dot login-dot-3"
        />
        <circle
          cx="1100"
          cy="480"
          r="3.5"
          fill="hsl(217 91% 60% / 0.17)"
          className="login-dot login-dot-1"
        />
      </svg>
    </div>
  );
}

/* ------------------------------------------------------------------
/*  Login page                                                         */
/* ------------------------------------------------------------------ */
function LoginPage() {
  const { login, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const logoRef = useRef<HTMLDivElement>(null);
  const hasRedirected = useRef(false);

  // Session restore: already authenticated on mount → redirect immediately (no transition)
  useEffect(() => {
    if (isAuthenticated && !isTransitioning && !hasRedirected.current) {
      hasRedirected.current = true;
      if (user) {
        navigate("/", { replace: true });
      }
    }
  }, [isAuthenticated, isTransitioning, user, navigate]);

  const handleTransitionComplete = useCallback(() => {
    hasRedirected.current = true;
    if (user) {
      navigate("/", { replace: true });
    }
  }, [user, navigate]);

  const handleLogoMouseEnter = useCallback(() => {
    const el = logoRef.current;
    if (!el) return;
    // Clear any settling inline styles
    el.style.transform = "";
    el.style.transition = "";
    el.classList.remove("is-settling");
    el.classList.add("is-floating");
  }, []);

  const handleLogoMouseLeave = useCallback(() => {
    const el = logoRef.current;
    if (!el) return;

    // 1. Capture current animated position
    const currentTransform = getComputedStyle(el).transform;

    // 2. Kill animation, freeze at captured position
    el.classList.remove("is-floating");
    el.classList.add("is-settling");
    el.style.transition = "none";
    el.style.transform = currentTransform === "none" ? "" : currentTransform;

    // 3. Force reflow so browser registers the frozen position
    void el.offsetHeight;

    // 4. Enable transition and animate to origin
    el.style.transition = "";
    el.style.transform = "translateY(0px) rotate(0deg)";

    // 5. Clean up when transition completes
    const onEnd = () => {
      el.style.transform = "";
      el.style.transition = "";
      el.classList.remove("is-settling");
      el.removeEventListener("transitionend", onEnd);
    };
    el.addEventListener("transitionend", onEnd);
  }, []);

  useEffect(() => {
    const t = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(t);
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(data: LoginFormValues) {
    setErrorMessage(null);
    try {
      setIsTransitioning(true);
      await login(data);
      // Transition is now showing; handleTransitionComplete will navigate after animation
    } catch (err: unknown) {
      setIsTransitioning(false);
      const apiMessage =
        err && typeof err === "object" && "response" in err
          ? (err as { response?: { data?: { errorMessage?: string } } })
              .response?.data?.errorMessage
          : undefined;
      setErrorMessage(
        apiMessage ??
          "Invalid email or password. Please check your credentials and try again.",
      );
    }
  }

  const stagger = (delay: number) => ({
    opacity: mounted ? 1 : 0,
    transform: mounted ? "translateY(0)" : "translateY(14px)",
    transitionTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
    transitionDelay: `${delay}ms`,
  });

  // Show branded transition after successful login
  if (isTransitioning) {
    return <LoginTransition onComplete={handleTransitionComplete} />;
  }

  return (
    <div
      className="relative flex min-h-screen items-center justify-center"
      style={{
        background:
          "radial-gradient(ellipse 70% 60% at 50% 45%, hsl(210 30% 99%) 0%, hsl(210 20% 96.5%) 55%, hsl(210 18% 94.5%) 100%)",
      }}
    >
      <AnimatedBackground />

      {/* Centered card */}
      <div className="relative z-10 w-full max-w-[400px] mx-4">
        {/* Logo — bigger with gentle float on hover */}
        <div
          className="flex justify-center mb-10 transition-all duration-700"
          style={stagger(0)}
        >
          <div
            ref={logoRef}
            className="login-logo-wrap"
            onMouseEnter={handleLogoMouseEnter}
            onMouseLeave={handleLogoMouseLeave}
          >
            <img
              src={webLogo}
              alt="tenXengage"
              className="h-12 sm:h-14 object-contain"
            />
          </div>
        </div>

        {/* Form card */}
        <div className="login-card rounded-2xl bg-white/92 backdrop-blur-md border border-[hsl(210_20%_90%)] shadow-[0_1px_2px_hsl(210_20%_80%/0.15),0_4px_12px_hsl(210_25%_70%/0.08),0_16px_48px_hsl(217_50%_60%/0.08)] px-8 py-10 sm:px-10 sm:py-12">
          {/* Heading */}
          <div
            className="flex flex-col items-center transition-all duration-700"
            style={stagger(60)}
          >
            <h2 className="text-[1.85rem] font-normal tracking-[-0.01em] text-[hsl(200_18%_18%)]">
              Hey, Welcome Back
            </h2>
            <p className="mt-2 text-sm text-[hsl(200_10%_50%)]">
              Sign in to pick up where you left off
            </p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-8 space-y-5">
            {errorMessage && (
              <div className="rounded-lg bg-[hsl(0_72%_55%/0.06)] border border-[hsl(0_72%_55%/0.12)] px-4 py-3 text-sm text-[hsl(0_72%_50%)] font-medium">
                {errorMessage}
              </div>
            )}

            {/* Email */}
            <div
              className="space-y-2 transition-all duration-700"
              style={stagger(120)}
            >
              <Label
                htmlFor="email"
                className="text-[hsl(200_15%_28%)] text-sm font-medium"
              >
                Email
              </Label>
              <Input
                id="email"
                type="email"
                placeholder="you@company.com"
                autoComplete="email"
                className="login-input h-11 bg-white border-[hsl(210_18%_90%)] placeholder:text-[hsl(200_10%_72%)] focus-visible:ring-[hsl(217_91%_60%/0.25)] focus-visible:border-[hsl(217_91%_60%/0.5)] focus-visible:shadow-[0_0_0_3px_hsl(217_91%_60%/0.08)]"
                {...register("email")}
              />
              {errors.email && (
                <p className="text-xs text-[hsl(0_72%_50%)]">
                  {errors.email.message}
                </p>
              )}
            </div>

            {/* Password */}
            <div
              className="space-y-2 transition-all duration-700"
              style={stagger(180)}
            >
              <div className="flex items-center justify-between">
                <Label
                  htmlFor="password"
                  className="text-[hsl(200_15%_28%)] text-sm font-medium"
                >
                  Password
                </Label>
                <button
                  type="button"
                  className="text-xs text-[hsl(217_91%_60%)] hover:text-[hsl(217_91%_50%)] transition-colors"
                >
                  Forgot password?
                </button>
              </div>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  className="login-input h-11 bg-white border-[hsl(210_18%_90%)] placeholder:text-[hsl(200_10%_72%)] focus-visible:ring-[hsl(217_91%_60%/0.25)] focus-visible:border-[hsl(217_91%_60%/0.5)] focus-visible:shadow-[0_0_0_3px_hsl(217_91%_60%/0.08)] pr-10"
                  {...register("password")}
                />
                <button
                  type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[hsl(200_10%_62%)] hover:text-[hsl(200_15%_30%)] transition-colors"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
              {errors.password && (
                <p className="text-xs text-[hsl(0_72%_50%)]">
                  {errors.password.message}
                </p>
              )}
            </div>

            {/* Submit */}
            <div
              className="pt-1 transition-all duration-700"
              style={stagger(240)}
            >
              <Button
                type="submit"
                className="login-btn w-full h-11 text-sm font-medium transition-all duration-200"
                disabled={isSubmitting}
              >
                {isSubmitting ? "Signing in..." : "Sign In"}
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </div>
          </form>
        </div>

        {/* Footer — frosted pill so text doesn't get lost */}
        <div className="mt-8 transition-all duration-700" style={stagger(360)}>
          <div className="inline-flex items-center justify-center gap-4 text-xs text-[hsl(200_10%_46%)] w-full">
            <div className="inline-flex items-center gap-4 rounded-full bg-white/50 backdrop-blur-sm px-5 py-2">
              <a
                href="#"
                className="hover:text-[hsl(200_15%_25%)] transition-colors"
              >
                Privacy
              </a>
              <span className="w-0.5 h-0.5 rounded-full bg-[hsl(200_10%_62%)]" />
              <a
                href="#"
                className="hover:text-[hsl(200_15%_25%)] transition-colors"
              >
                Terms
              </a>
              <span className="w-0.5 h-0.5 rounded-full bg-[hsl(200_10%_62%)]" />
              <a
                href="#"
                className="hover:text-[hsl(200_15%_25%)] transition-colors"
              >
                Support
              </a>
            </div>
          </div>
          <p className="text-center text-xs text-[hsl(200_10%_56%)] mt-3">
            &copy; {new Date().getFullYear()} tenXengage
          </p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
