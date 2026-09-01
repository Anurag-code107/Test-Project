import { useState, useEffect, useCallback, useMemo } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { cn } from "@/lib/utils";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Check,
  Lock,
  User,
  FileText,
  Settings,
  Sparkles,
  Loader2,
  AlertTriangle,
  Eye,
  EyeOff,
  ArrowRight,
  ExternalLink,
} from "lucide-react";
import webLogo from "@/assets/web_logo.png";
import type {
  OnboardingStatusResponse,
  LegalPolicyResponse,
} from "@/types/onboarding.types";
import {
  validateOnboardingToken,
  setPassword,
  completeProfile,
  acceptPolicies,
  setConsent,
  completeOnboarding,
  getPolicies,
  getConsentPreferences,
} from "@/services/onboarding.service";

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

const STEPS = [
  { label: "Set Password", icon: Lock },
  { label: "Your Profile", icon: User },
  { label: "Legal Policies", icon: FileText },
  { label: "Preferences", icon: Settings },
  { label: "Welcome", icon: Sparkles },
] as const;

const COUNTRIES = [
  { code: "US", name: "United States" },
  { code: "CA", name: "Canada" },
  { code: "GB", name: "United Kingdom" },
  { code: "DE", name: "Germany" },
  { code: "FR", name: "France" },
  { code: "ES", name: "Spain" },
  { code: "IT", name: "Italy" },
  { code: "NL", name: "Netherlands" },
  { code: "SE", name: "Sweden" },
  { code: "NO", name: "Norway" },
  { code: "DK", name: "Denmark" },
  { code: "FI", name: "Finland" },
  { code: "PL", name: "Poland" },
  { code: "IE", name: "Ireland" },
  { code: "BE", name: "Belgium" },
  { code: "CH", name: "Switzerland" },
  { code: "AT", name: "Austria" },
  { code: "PT", name: "Portugal" },
  { code: "CZ", name: "Czech Republic" },
  { code: "RO", name: "Romania" },
  { code: "AU", name: "Australia" },
  { code: "JP", name: "Japan" },
  { code: "CN", name: "China" },
  { code: "IN", name: "India" },
  { code: "KR", name: "South Korea" },
  { code: "SG", name: "Singapore" },
  { code: "BR", name: "Brazil" },
  { code: "MX", name: "Mexico" },
  { code: "AR", name: "Argentina" },
] as const;

const CONSENT_LABELS: Record<string, { label: string; description: string }> = {
  AI_RECOMMENDATIONS: {
    label: "AI Recommendations",
    description:
      "Allow us to use AI to personalize incentive recommendations and insights for you.",
  },
  MARKETING: {
    label: "Marketing Communications",
    description:
      "Receive product updates, tips, and promotional content via email.",
  },
  ANALYTICS: {
    label: "Usage Analytics",
    description:
      "Help us improve by allowing anonymous usage analytics collection.",
  },
};

/* ------------------------------------------------------------------ */
/*  Zod Schemas                                                        */
/* ------------------------------------------------------------------ */

const passwordSchema = z
  .object({
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type PasswordFormValues = z.infer<typeof passwordSchema>;

const profileSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  phone: z.string().optional(),
  countryCode: z.string().min(1, "Please select a country"),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

/* ------------------------------------------------------------------ */
/*  Progress Indicator                                                 */
/* ------------------------------------------------------------------ */

function ProgressIndicator({
  currentStep,
  consentStepVisible,
}: {
  currentStep: number;
  consentStepVisible: boolean;
}) {
  const visibleSteps = consentStepVisible
    ? STEPS
    : STEPS.filter((_, i) => i !== 3);

  // Map the raw currentStep (0-based from API) to a display index.
  // Steps are 1-indexed for display. Step 0 from API means "Set Password" (display index 0).
  const getDisplayIndex = (apiStep: number) => {
    if (!consentStepVisible && apiStep >= 4) return apiStep - 1;
    return apiStep;
  };

  const displayIndex = getDisplayIndex(currentStep);

  return (
    <div className="flex items-center justify-center gap-0 w-full max-w-lg mx-auto mb-8">
      {visibleSteps.map((step, i) => {
        const isCompleted = i < displayIndex;
        const isCurrent = i === displayIndex;
        const StepIcon = step.icon;

        return (
          <div key={step.label} className="flex items-center">
            <div className="flex flex-col items-center">
              {/* Circle */}
              <div
                className={cn(
                  "flex h-9 w-9 items-center justify-center rounded-full border-2 transition-all duration-500",
                  isCompleted &&
                    "border-primary bg-primary text-primary-foreground",
                  isCurrent && "border-primary bg-primary/10 text-primary",
                  !isCompleted &&
                    !isCurrent &&
                    "border-muted-foreground/25 bg-background text-muted-foreground/40",
                )}
              >
                {isCompleted ? (
                  <Check className="h-4 w-4" />
                ) : (
                  <StepIcon className="h-4 w-4" />
                )}
              </div>
              {/* Label */}
              <span
                className={cn(
                  "mt-1.5 text-[10px] font-medium whitespace-nowrap transition-colors duration-300",
                  isCompleted && "text-primary",
                  isCurrent && "text-primary",
                  !isCompleted && !isCurrent && "text-muted-foreground/50",
                )}
              >
                {step.label}
              </span>
            </div>

            {/* Connector line */}
            {i < visibleSteps.length - 1 && (
              <div
                className={cn(
                  "h-0.5 w-8 sm:w-12 mx-1 sm:mx-2 -mt-4 transition-colors duration-500",
                  i < displayIndex ? "bg-primary" : "bg-muted-foreground/15",
                )}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Step 1: Set Password                                               */
/* ------------------------------------------------------------------ */

function SetPasswordStep({
  token,
  onComplete,
}: {
  token: string;
  onComplete: (status: OnboardingStatusResponse) => void;
}) {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  const onSubmit = async (data: PasswordFormValues) => {
    setApiError(null);
    try {
      const status = await setPassword({ token, password: data.password });
      onComplete(status);
    } catch {
      setApiError("Failed to set password. Please try again.");
    }
  };

  return (
    <Card className="w-full animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-xl">Create Your Password</CardTitle>
        <CardDescription>
          Choose a strong password to secure your account
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          {apiError && (
            <div className="rounded-lg bg-destructive/6 border border-destructive/12 px-4 py-3 text-sm text-destructive font-medium">
              {apiError}
            </div>
          )}

          {/* Password */}
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Min. 8 characters"
                autoComplete="new-password"
                className="h-11 pr-10"
                {...register("password")}
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
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
              <p className="text-xs text-destructive">
                {errors.password.message}
              </p>
            )}
          </div>

          {/* Confirm Password */}
          <div className="space-y-2">
            <Label htmlFor="confirmPassword">Confirm Password</Label>
            <div className="relative">
              <Input
                id="confirmPassword"
                type={showConfirm ? "text" : "password"}
                placeholder="Re-enter your password"
                autoComplete="new-password"
                className="h-11 pr-10"
                {...register("confirmPassword")}
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                onClick={() => setShowConfirm(!showConfirm)}
                tabIndex={-1}
              >
                {showConfirm ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
            {errors.confirmPassword && (
              <p className="text-xs text-destructive">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <Button type="submit" className="w-full h-11" disabled={isSubmitting}>
            {isSubmitting ? (
              <Loader2 className="h-4 w-4 animate-spin mr-2" />
            ) : null}
            Set Password & Continue
            {!isSubmitting && <ArrowRight className="h-4 w-4 ml-2" />}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Step 2: Complete Profile                                           */
/* ------------------------------------------------------------------ */

function CompleteProfileStep({
  token,
  status,
  onComplete,
}: {
  token: string;
  status: OnboardingStatusResponse;
  onComplete: (status: OnboardingStatusResponse) => void;
}) {
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      firstName: status.firstName || "",
      lastName: status.lastName || "",
      phone: "",
      countryCode: "",
    },
  });

  const onSubmit = async (data: ProfileFormValues) => {
    setApiError(null);
    try {
      const result = await completeProfile({
        token,
        firstName: data.firstName,
        lastName: data.lastName,
        phone: data.phone ?? "",
        countryCode: data.countryCode,
      });
      onComplete(result);
    } catch {
      setApiError("Failed to save profile. Please try again.");
    }
  };

  return (
    <Card className="w-full animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-xl">Complete Your Profile</CardTitle>
        <CardDescription>Tell us a bit about yourself</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          {apiError && (
            <div className="rounded-lg bg-destructive/6 border border-destructive/12 px-4 py-3 text-sm text-destructive font-medium">
              {apiError}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* First Name */}
            <div className="space-y-2">
              <Label htmlFor="firstName">First Name</Label>
              <Input
                id="firstName"
                className="h-11"
                placeholder="Jane"
                {...register("firstName")}
              />
              {errors.firstName && (
                <p className="text-xs text-destructive">
                  {errors.firstName.message}
                </p>
              )}
            </div>

            {/* Last Name */}
            <div className="space-y-2">
              <Label htmlFor="lastName">Last Name</Label>
              <Input
                id="lastName"
                className="h-11"
                placeholder="Smith"
                {...register("lastName")}
              />
              {errors.lastName && (
                <p className="text-xs text-destructive">
                  {errors.lastName.message}
                </p>
              )}
            </div>
          </div>

          {/* Phone */}
          <div className="space-y-2">
            <Label htmlFor="phone">
              Phone Number{" "}
              <span className="text-muted-foreground font-normal">
                (optional)
              </span>
            </Label>
            <Input
              id="phone"
              type="tel"
              className="h-11"
              placeholder="+1 (555) 123-4567"
              {...register("phone")}
            />
          </div>

          {/* Country */}
          <div className="space-y-2">
            <Label>Country</Label>
            <Select
              onValueChange={(value) =>
                setValue("countryCode", value, { shouldValidate: true })
              }
            >
              <SelectTrigger className="h-11">
                <SelectValue placeholder="Select your country" />
              </SelectTrigger>
              <SelectContent>
                {COUNTRIES.map((c) => (
                  <SelectItem key={c.code} value={c.code}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.countryCode && (
              <p className="text-xs text-destructive">
                {errors.countryCode.message}
              </p>
            )}
          </div>

          <Button type="submit" className="w-full h-11" disabled={isSubmitting}>
            {isSubmitting ? (
              <Loader2 className="h-4 w-4 animate-spin mr-2" />
            ) : null}
            Continue
            {!isSubmitting && <ArrowRight className="h-4 w-4 ml-2" />}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Step 3: Legal Policies                                             */
/* ------------------------------------------------------------------ */

function LegalPoliciesStep({
  token,
  onComplete,
}: {
  token: string;
  onComplete: (status: OnboardingStatusResponse) => void;
}) {
  const [policies, setPolicies] = useState<LegalPolicyResponse[]>([]);
  const [accepted, setAccepted] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPolicies(token)
      .then((data) => {
        setPolicies(data);
        // Pre-check any already-accepted policies
        const preAccepted = new Set(
          data.filter((p) => p.accepted).map((p) => p.id),
        );
        setAccepted(preAccepted);
      })
      .catch(() => setError("Failed to load policies."))
      .finally(() => setLoading(false));
  }, [token]);

  const allAccepted =
    policies.length > 0 && policies.every((p) => accepted.has(p.id));

  const togglePolicy = (policyId: string) => {
    setAccepted((prev) => {
      const next = new Set(prev);
      if (next.has(policyId)) {
        next.delete(policyId);
      } else {
        next.add(policyId);
      }
      return next;
    });
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const status = await acceptPolicies({
        token,
        policyIds: Array.from(accepted),
      });
      onComplete(status);
    } catch {
      setError("Failed to accept policies. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Card className="w-full animate-in fade-in duration-300">
        <CardContent className="py-16 flex flex-col items-center gap-3">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">Loading policies...</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="w-full animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-xl">Review Legal Policies</CardTitle>
        <CardDescription>
          Please review and accept the following policies to continue
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <div className="rounded-lg bg-destructive/6 border border-destructive/12 px-4 py-3 text-sm text-destructive font-medium">
            {error}
          </div>
        )}

        <div className="space-y-3">
          {policies.map((policy) => (
            <div
              key={policy.id}
              className={cn(
                "flex items-start gap-3 rounded-lg border p-4 transition-colors",
                accepted.has(policy.id)
                  ? "border-primary/30 bg-primary/[0.03]"
                  : "border-border hover:border-muted-foreground/30",
              )}
            >
              <Checkbox
                id={`policy-${policy.id}`}
                checked={accepted.has(policy.id)}
                onCheckedChange={() => togglePolicy(policy.id)}
                className="mt-0.5"
              />
              <div className="flex-1 min-w-0">
                <label
                  htmlFor={`policy-${policy.id}`}
                  className="text-sm font-medium text-foreground cursor-pointer leading-tight"
                >
                  {policy.title}
                  <span className="ml-2 text-xs text-muted-foreground font-normal">
                    v{policy.version}
                  </span>
                </label>
                {policy.summary && (
                  <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
                    {policy.summary}
                  </p>
                )}
                {policy.contentUrl && (
                  <a
                    href={policy.contentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 mt-1.5 text-xs text-primary hover:text-primary/80 transition-colors"
                  >
                    Read full policy
                    <ExternalLink className="h-3 w-3" />
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>

        <Button
          onClick={handleSubmit}
          className="w-full h-11"
          disabled={!allAccepted || submitting}
        >
          {submitting ? (
            <Loader2 className="h-4 w-4 animate-spin mr-2" />
          ) : null}
          Accept & Continue
          {!submitting && <ArrowRight className="h-4 w-4 ml-2" />}
        </Button>

        {!allAccepted && policies.length > 0 && (
          <p className="text-xs text-muted-foreground text-center">
            All policies must be accepted to continue
          </p>
        )}
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Step 4: Consent Preferences                                        */
/* ------------------------------------------------------------------ */

function ConsentPreferencesStep({
  token,
  status,
  onComplete,
}: {
  token: string;
  status: OnboardingStatusResponse;
  onComplete: (status: OnboardingStatusResponse) => void;
}) {
  const [consents, setConsents] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const config = status.complianceConfig;

  // Determine which consent types are visible based on complianceConfig
  const visibleTypes = useMemo(() => {
    if (!config) return [];
    const types: string[] = [];
    if (config.consentAiVisible) types.push("AI_RECOMMENDATIONS");
    if (config.consentMarketingVisible) types.push("MARKETING");
    if (config.consentAnalyticsVisible) types.push("ANALYTICS");
    return types;
  }, [config]);

  useEffect(() => {
    getConsentPreferences(token)
      .then((data) => {
        // Initialize all visible consents to OFF (opt-in)
        const initial: Record<string, boolean> = {};
        for (const type of visibleTypes) {
          const existing = data.find((p) => p.consentType === type);
          initial[type] = existing?.granted ?? false;
        }
        setConsents(initial);
      })
      .catch(() => setError("Failed to load preferences."))
      .finally(() => setLoading(false));
  }, [token, visibleTypes]);

  const handleSave = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const result = await setConsent({ token, consents });
      onComplete(result);
    } catch {
      setError("Failed to save preferences. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSkip = async () => {
    setSubmitting(true);
    setError(null);
    try {
      // Submit all visible consents as false (declined)
      const declined: Record<string, boolean> = {};
      for (const type of visibleTypes) {
        declined[type] = false;
      }
      const result = await setConsent({ token, consents: declined });
      onComplete(result);
    } catch {
      setError("Failed to continue. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Card className="w-full animate-in fade-in duration-300">
        <CardContent className="py-16 flex flex-col items-center gap-3">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            Loading preferences...
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="w-full animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-xl">Your Preferences</CardTitle>
        <CardDescription>
          Choose which optional features you'd like to enable. You can change
          these anytime in your settings.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <div className="rounded-lg bg-destructive/6 border border-destructive/12 px-4 py-3 text-sm text-destructive font-medium">
            {error}
          </div>
        )}

        <div className="space-y-3">
          {visibleTypes.map((type) => {
            const meta = CONSENT_LABELS[type];
            if (!meta) return null;
            return (
              <div
                key={type}
                className={cn(
                  "flex items-center justify-between gap-4 rounded-lg border p-4 transition-colors",
                  consents[type]
                    ? "border-primary/30 bg-primary/[0.03]"
                    : "border-border",
                )}
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground">
                    {meta.label}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground leading-relaxed">
                    {meta.description}
                  </p>
                </div>
                <Switch
                  checked={consents[type] ?? false}
                  onCheckedChange={(checked) =>
                    setConsents((prev) => ({ ...prev, [type]: checked }))
                  }
                />
              </div>
            );
          })}
        </div>

        <div className="flex gap-3">
          <Button
            variant="outline"
            onClick={handleSkip}
            className="flex-1 h-11"
            disabled={submitting}
          >
            Skip
          </Button>
          <Button
            onClick={handleSave}
            className="flex-1 h-11"
            disabled={submitting}
          >
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin mr-2" />
            ) : null}
            Save Preferences
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Step 5: Welcome / Complete                                         */
/* ------------------------------------------------------------------ */

function WelcomeStep({
  token,
  status,
}: {
  token: string;
  status: OnboardingStatusResponse;
}) {
  const navigate = useNavigate();
  const [completing, setCompleting] = useState(false);

  const handleGoToDashboard = async () => {
    setCompleting(true);
    try {
      await completeOnboarding(token);
    } catch {
      // Even if the call fails, let them proceed to login
    }
    navigate("/login", { replace: true });
  };

  return (
    <Card className="w-full animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardContent className="py-12 flex flex-col items-center text-center space-y-6">
        {/* Success icon */}
        <div className="relative">
          <div className="h-16 w-16 rounded-full bg-primary/10 flex items-center justify-center">
            <Sparkles className="h-8 w-8 text-primary" />
          </div>
          <div className="absolute -top-1 -right-1 h-6 w-6 rounded-full bg-primary flex items-center justify-center">
            <Check className="h-3.5 w-3.5 text-primary-foreground" />
          </div>
        </div>

        <div className="space-y-2">
          <h2 className="text-2xl font-semibold text-foreground">
            Welcome, {status.firstName || "there"}!
          </h2>
          <p className="text-muted-foreground leading-relaxed max-w-sm">
            Your account is all set up and ready to go. You can now sign in and
            start exploring tenXengage.
          </p>
        </div>

        <Button
          onClick={handleGoToDashboard}
          className="h-11 px-8"
          disabled={completing}
        >
          {completing ? (
            <Loader2 className="h-4 w-4 animate-spin mr-2" />
          ) : null}
          Go to Dashboard
          {!completing && <ArrowRight className="h-4 w-4 ml-2" />}
        </Button>
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Error / Loading States                                             */
/* ------------------------------------------------------------------ */

function OnboardingError({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center space-y-6 w-full max-w-md mx-auto">
      <img src={webLogo} alt="tenXengage" className="h-10 object-contain" />
      <Card className="w-full">
        <CardContent className="p-8 text-center space-y-4">
          <div className="h-14 w-14 rounded-full bg-destructive/10 flex items-center justify-center mx-auto">
            <AlertTriangle className="h-7 w-7 text-destructive" />
          </div>
          <h1 className="text-lg font-semibold text-foreground">
            Invalid or Expired Link
          </h1>
          <p className="text-muted-foreground text-sm leading-relaxed">
            {message}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function OnboardingLoading() {
  return (
    <div className="flex flex-col items-center gap-4">
      <img src={webLogo} alt="tenXengage" className="h-10 object-contain" />
      <Loader2 className="h-7 w-7 animate-spin text-muted-foreground" />
      <p className="text-sm text-muted-foreground">
        Setting up your onboarding...
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Main Page Component                                                */
/* ------------------------------------------------------------------ */

function OnboardingPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [status, setStatus] = useState<OnboardingStatusResponse | null>(null);
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [pageState, setPageState] = useState<"loading" | "error" | "ready">(
    "loading",
  );
  const [errorMessage, setErrorMessage] = useState("");

  // Determine if the consent step should be visible
  const consentStepVisible = useMemo(() => {
    if (!status?.complianceConfig) return false;
    const c = status.complianceConfig;
    return (
      c.consentAiVisible ||
      c.consentMarketingVisible ||
      c.consentAnalyticsVisible
    );
  }, [status]);

  useEffect(() => {
    if (!token) {
      setErrorMessage(
        "No onboarding token was provided. Please check the link you received in your invitation email.",
      );
      setPageState("error");
      return;
    }

    validateOnboardingToken(token)
      .then((data) => {
        setStatus(data);
        setCurrentStep(data.currentStep);
        if (data.completed) {
          // Already completed -- jump to welcome
          setCurrentStep(5);
        }
        setPageState("ready");
      })
      .catch(() => {
        setErrorMessage(
          "This onboarding link is invalid or has expired. Please contact your administrator for a new invitation.",
        );
        setPageState("error");
      });
  }, [token]);

  const handleStepComplete = useCallback(
    (newStatus: OnboardingStatusResponse) => {
      setStatus(newStatus);

      let nextStep = newStatus.currentStep;

      // If consent step is not visible and we just finished policies (step 3 -> step 4),
      // skip ahead to step 5 (welcome)
      if (!consentStepVisible && nextStep === 4) {
        // We need the consent step to not show, so the backend may still return step 4.
        // We skip directly to complete step.
        nextStep = 5;
      }

      setCurrentStep(nextStep);
    },
    [consentStepVisible],
  );

  // Resolve step 4 skipping: if consent step is invisible and backend says step 4,
  // treat it as step 5
  const effectiveStep = useMemo(() => {
    if (!consentStepVisible && currentStep === 4) return 5;
    return currentStep;
  }, [consentStepVisible, currentStep]);

  const renderStep = () => {
    if (!token || !status) return null;

    switch (effectiveStep) {
      case 0:
      case 1:
        return (
          <SetPasswordStep token={token} onComplete={handleStepComplete} />
        );
      case 2:
        return (
          <CompleteProfileStep
            token={token}
            status={status}
            onComplete={handleStepComplete}
          />
        );
      case 3:
        return (
          <LegalPoliciesStep token={token} onComplete={handleStepComplete} />
        );
      case 4:
        return (
          <ConsentPreferencesStep
            token={token}
            status={status}
            onComplete={handleStepComplete}
          />
        );
      case 5:
      default:
        return <WelcomeStep token={token} status={status} />;
    }
  };

  return (
    <div
      className="relative flex min-h-screen items-center justify-center px-4 py-8"
      style={{
        background:
          "radial-gradient(ellipse 70% 60% at 50% 45%, hsl(210 30% 99%) 0%, hsl(210 20% 96.5%) 55%, hsl(210 18% 94.5%) 100%)",
      }}
    >
      <div className="w-full max-w-[600px] mx-auto">
        {pageState === "loading" && <OnboardingLoading />}

        {pageState === "error" && <OnboardingError message={errorMessage} />}

        {pageState === "ready" && status && (
          <>
            {/* Logo */}
            <div className="flex justify-center mb-6">
              <img
                src={webLogo}
                alt="tenXengage"
                className="h-10 object-contain"
              />
            </div>

            {/* Progress -- only show if not on welcome step */}
            {effectiveStep < 5 && (
              <ProgressIndicator
                currentStep={effectiveStep}
                consentStepVisible={consentStepVisible}
              />
            )}

            {/* Step Content */}
            {renderStep()}

            {/* Footer */}
            <div className="mt-8 text-center">
              <p className="text-xs text-muted-foreground">
                &copy; {new Date().getFullYear()} tenXengage. All rights
                reserved.
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default OnboardingPage;
