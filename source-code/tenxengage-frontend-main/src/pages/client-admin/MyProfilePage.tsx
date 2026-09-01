import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  Shield,
  Mail,
  Clock,
  Eye,
  EyeOff,
  Camera,
  Users,
  ExternalLink,
  ChevronDown,
  Save,
  Loader2,
} from "lucide-react";
import { useState, useRef, useEffect, useCallback } from "react";
import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import { toast } from "sonner";
import { useProfileFields, useUpdateProfile } from "@/hooks/useProfileApi";
import { NotificationPreferencesPanel } from "@/components/NotificationPreferencesPanel";
import { ProfilePrivacySection } from "@/components/settings/ProfilePrivacySection";
import { PermissionGate } from "@/components/PermissionGate";
import { PageBanner } from "@/components/PageBanner";
import type { ProfileFieldResponse } from "@/types/profile.types";

function MyProfilePage() {
  const { user, refreshUser } = useAuth();
  const { can } = usePermissions();
  const canEditProfile = can("action.profile.edit");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [canScrollDown, setCanScrollDown] = useState(false);
  const [canScrollDownNotifications, setCanScrollDownNotifications] =
    useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  // Radix Tabs only mounts the active panel, so the scroll element for the
  // Notifications tab doesn't exist until that tab is selected. Use a state
  // ref so the effect below re-runs when the element actually mounts.
  const [notificationsScrollEl, setNotificationsScrollEl] =
    useState<HTMLDivElement | null>(null);

  // Dynamic profile fields
  const { data: profileFields, isLoading: fieldsLoading } = useProfileFields();
  const updateProfile = useUpdateProfile();
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});
  const [fieldsDirty, setFieldsDirty] = useState(false);

  // Initialize field values when data arrives
  useEffect(() => {
    if (profileFields) {
      const initial: Record<string, string> = {};
      for (const field of profileFields) {
        initial[field.fieldName] = field.value ?? "";
      }
      setFieldValues(initial);
      setFieldsDirty(false);
    }
  }, [profileFields]);

  const handleFieldChange = useCallback((fieldName: string, value: string) => {
    setFieldValues((prev) => ({ ...prev, [fieldName]: value }));
    setFieldsDirty(true);
  }, []);

  const handleSaveFields = () => {
    if (!profileFields) return;

    const customFields: Record<string, string> = {};
    for (const field of profileFields) {
      if (field.editable) {
        const newValue = fieldValues[field.fieldName] ?? "";
        const originalValue = field.value ?? "";
        if (newValue !== originalValue) {
          customFields[field.fieldName] = newValue;
        }
      }
    }

    const changedCount = Object.keys(customFields).length;
    if (changedCount === 0) return;

    updateProfile.mutate(
      { customFields },
      {
        onSuccess: () => {
          toast.success("Profile updated", {
            description:
              changedCount === 1
                ? "1 field saved."
                : `${changedCount} fields saved.`,
          });
          setFieldsDirty(false);
          // Pull the fresh user (firstName/lastName/avatar) into AuthContext
          // so the page header, sidebar, and any other useAuth() consumer
          // reflect the change without a hard refresh.
          void refreshUser();
        },
        onError: (err: unknown) => {
          const apiMessage =
            err && typeof err === "object" && "response" in err
              ? (err as { response?: { data?: { errorMessage?: string } } })
                  .response?.data?.errorMessage
              : undefined;
          toast.error("Update failed", {
            description:
              apiMessage ?? "Could not save your profile. Please try again.",
          });
        },
      },
    );
  };

  const handleAvatarUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    setAvatarUrl(url);
  };

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    const checkScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = el;
      const isScrollable = scrollHeight - clientHeight > 16;
      const atBottom = Math.ceil(scrollTop + clientHeight) >= scrollHeight - 8;
      setCanScrollDown(isScrollable && !atBottom);
    };

    el.addEventListener("scroll", checkScroll, { passive: true });
    window.addEventListener("resize", checkScroll);
    checkScroll();
    const t1 = setTimeout(checkScroll, 100);
    const t2 = setTimeout(checkScroll, 250);

    return () => {
      el.removeEventListener("scroll", checkScroll);
      window.removeEventListener("resize", checkScroll);
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, []);

  // Track scrollability on the Notifications tab so we can show a
  // "Scroll for more" hint the same way the Profile tab does. The effect
  // re-runs whenever the Notifications panel mounts/unmounts (via Radix Tabs).
  useEffect(() => {
    const el = notificationsScrollEl;
    if (!el) {
      setCanScrollDownNotifications(false);
      return;
    }

    const checkScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = el;
      const isScrollable = scrollHeight - clientHeight > 16;
      const atBottom = Math.ceil(scrollTop + clientHeight) >= scrollHeight - 8;
      setCanScrollDownNotifications(isScrollable && !atBottom);
    };

    el.addEventListener("scroll", checkScroll, { passive: true });
    window.addEventListener("resize", checkScroll);
    // ResizeObserver catches content being populated async (notification
    // types/preferences loading in after the tab mounts).
    const ro = new ResizeObserver(checkScroll);
    ro.observe(el);
    checkScroll();

    return () => {
      el.removeEventListener("scroll", checkScroll);
      window.removeEventListener("resize", checkScroll);
      ro.disconnect();
    };
  }, [notificationsScrollEl]);

  const scrollToBottom = () => {
    scrollRef.current?.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  };

  const scrollNotificationsToBottom = () => {
    notificationsScrollEl?.scrollTo({
      top: notificationsScrollEl.scrollHeight,
      behavior: "smooth",
    });
  };

  const firstName = user?.firstName ?? "";
  const lastName = user?.lastName ?? "";
  const fullName = `${firstName} ${lastName}`.trim() || "User";
  const initials = `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
  const roleLabel = user?.clientRoleName ?? "User";
  const email = user?.email ?? "";
  const clientName = user?.clientName ?? "";

  const hasEditableFields =
    canEditProfile && profileFields?.some((f) => f.editable);

  return (
    <div className="flex flex-col h-full gap-6">
      <PageBanner
        theme="profile"
        title="My Profile"
        subtitle="Manage your profile and preferences"
      />

      <Tabs
        defaultValue="profile"
        className="flex flex-col flex-1 min-h-0 gap-6"
      >
        <TabsList className="self-start shrink-0">
          <TabsTrigger value="profile">My Profile</TabsTrigger>
          <TabsTrigger value="notifications">Notifications</TabsTrigger>
          <PermissionGate permission="action.profile.export_data">
            <TabsTrigger value="privacy">Privacy & Data</TabsTrigger>
          </PermissionGate>
          <TabsTrigger value="help">Support</TabsTrigger>
        </TabsList>

        <TabsContent
          value="profile"
          className="mt-0 flex-1 min-h-0 data-[state=active]:flex flex-col"
        >
          <div className="relative flex-1 min-h-0 flex flex-col border border-border rounded-lg shadow-sm overflow-hidden">
            <div
              ref={scrollRef}
              className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6"
            >
              {/* Profile Header */}
              <Card>
                <CardContent className="pt-6">
                  <div className="flex items-start gap-5">
                    <div className="relative group">
                      <Avatar className="h-20 w-20 border border-border">
                        {avatarUrl ? (
                          <AvatarImage src={avatarUrl} alt="Profile" />
                        ) : (
                          <AvatarFallback className="text-2xl font-semibold bg-primary/10 text-primary">
                            {initials}
                          </AvatarFallback>
                        )}
                      </Avatar>
                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        className="absolute bottom-0 right-0 p-1.5 rounded-full bg-primary text-primary-foreground shadow-md hover:bg-primary/90 transition-colors"
                      >
                        <Camera className="h-3.5 w-3.5" />
                      </button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        onChange={handleAvatarUpload}
                        className="hidden"
                      />
                    </div>
                    <div className="space-y-1.5">
                      <h2 className="text-2xl font-semibold text-foreground">
                        {fullName}
                      </h2>
                      <Badge
                        className="bg-primary/10 text-primary border-primary/20"
                        variant="outline"
                      >
                        {roleLabel}
                      </Badge>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Mail className="h-4 w-4" />
                        <span>{email}</span>
                      </div>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Clock className="h-4 w-4" />
                        <span>
                          Last Login:{" "}
                          <span className="text-primary font-medium">
                            Today
                          </span>
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-6">
                    <div className="flex items-center gap-4 p-4 rounded-lg border bg-primary/5 border-primary/20">
                      <div className="p-2.5 rounded-lg bg-primary text-primary-foreground">
                        <Users className="h-5 w-5" />
                      </div>
                      <div>
                        <p className="text-xs font-medium text-primary">
                          Client
                        </p>
                        <p className="font-semibold text-foreground">
                          {clientName}
                        </p>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Dynamic Profile Fields */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-xl">Profile Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-5">
                  {fieldsLoading ? (
                    <div className="flex items-center justify-center py-8">
                      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                      <span className="ml-2 text-sm text-muted-foreground">
                        Loading profile fields...
                      </span>
                    </div>
                  ) : profileFields && profileFields.length > 0 ? (
                    <>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                        {profileFields.map((field) => (
                          <ProfileField
                            key={field.fieldId ?? field.fieldName}
                            field={field}
                            value={fieldValues[field.fieldName] ?? ""}
                            onChange={handleFieldChange}
                          />
                        ))}
                      </div>
                      {hasEditableFields && (
                        <div className="flex justify-end pt-2">
                          <Button
                            onClick={handleSaveFields}
                            disabled={!fieldsDirty || updateProfile.isPending}
                          >
                            {updateProfile.isPending ? (
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                              <Save className="mr-2 h-4 w-4" />
                            )}
                            Save Changes
                          </Button>
                        </div>
                      )}
                    </>
                  ) : (
                    <p className="text-sm text-muted-foreground py-4">
                      No profile fields configured.
                    </p>
                  )}
                </CardContent>
              </Card>

              {/* Password & Security */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-xl">Password & Security</CardTitle>
                </CardHeader>
                <CardContent className="space-y-5">
                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground uppercase tracking-wide">
                      Current Password
                    </label>
                    <div className="relative">
                      <Input
                        type={showCurrentPassword ? "text" : "password"}
                        placeholder="Enter Current Password"
                        className="pr-10"
                      />
                      <button
                        type="button"
                        onClick={() =>
                          setShowCurrentPassword(!showCurrentPassword)
                        }
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      >
                        {showCurrentPassword ? (
                          <EyeOff className="h-4 w-4" />
                        ) : (
                          <Eye className="h-4 w-4" />
                        )}
                      </button>
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="text-xs font-semibold text-foreground uppercase tracking-wide">
                      New Password
                    </label>
                    <div className="relative">
                      <Input
                        type={showNewPassword ? "text" : "password"}
                        placeholder="Enter New Password"
                        className="pr-10"
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPassword(!showNewPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      >
                        {showNewPassword ? (
                          <EyeOff className="h-4 w-4" />
                        ) : (
                          <Eye className="h-4 w-4" />
                        )}
                      </button>
                    </div>
                  </div>
                  <div className="flex justify-end">
                    <Button>Update</Button>
                  </div>
                </CardContent>
              </Card>
            </div>

            {canScrollDown && (
              <button
                onClick={scrollToBottom}
                className="absolute bottom-3 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 text-primary animate-bounce cursor-pointer z-10"
                aria-label="Scroll down for more"
              >
                <span className="text-xs font-medium text-muted-foreground">
                  Scroll for more
                </span>
                <div className="bg-primary/10 border border-primary/30 rounded-full p-1.5">
                  <ChevronDown className="h-4 w-4" />
                </div>
              </button>
            )}

            {canScrollDown && (
              <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-background to-transparent rounded-b-lg pointer-events-none" />
            )}
          </div>
        </TabsContent>

        <TabsContent
          value="notifications"
          className="mt-0 flex-1 min-h-0 data-[state=active]:flex flex-col"
        >
          <div className="relative flex-1 min-h-0 flex flex-col">
            <div
              ref={setNotificationsScrollEl}
              className="flex-1 min-h-0 overflow-y-auto"
            >
              <NotificationPreferencesPanel />
            </div>

            {canScrollDownNotifications && (
              <button
                onClick={scrollNotificationsToBottom}
                className="absolute bottom-3 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 text-primary animate-bounce cursor-pointer z-10"
                aria-label="Scroll down for more"
              >
                <span className="text-xs font-medium text-muted-foreground">
                  Scroll for more
                </span>
                <div className="bg-primary/10 border border-primary/30 rounded-full p-1.5">
                  <ChevronDown className="h-4 w-4" />
                </div>
              </button>
            )}

            {canScrollDownNotifications && (
              <div className="absolute bottom-0 left-0 right-0 h-16 bg-gradient-to-t from-background to-transparent rounded-b-lg pointer-events-none" />
            )}
          </div>
        </TabsContent>

        <TabsContent value="privacy" className="mt-0 space-y-6">
          <ProfilePrivacySection />
        </TabsContent>

        <TabsContent value="help" className="mt-0 space-y-6">
          <Card>
            <CardHeader>
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-primary/10">
                  <Shield className="h-6 w-6 text-primary" />
                </div>
                <CardTitle>Support</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-3">
                <Button
                  variant="outline"
                  className="w-full justify-between h-auto py-3"
                >
                  <span>Platform Terms & Conditions</span>
                  <ExternalLink className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  className="w-full justify-between h-auto py-3"
                >
                  <span>Privacy Policy</span>
                  <ExternalLink className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  className="w-full justify-between h-auto py-3"
                >
                  <span>Incentive Program Guidelines</span>
                  <ExternalLink className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  className="w-full justify-between h-auto py-3"
                >
                  <span>Support Portal</span>
                  <ExternalLink className="h-4 w-4" />
                </Button>
              </div>
              <div className="pt-4 border-t">
                <h4 className="font-semibold mb-2">Need Help?</h4>
                <p className="text-sm text-muted-foreground mb-3">
                  Our Support Team Is Here To Assist You.
                </p>
                <div className="space-y-2">
                  <p className="text-sm">
                    <span className="text-muted-foreground">Email:</span>{" "}
                    <a
                      href="mailto:support@vendor.com"
                      className="text-primary hover:underline"
                    >
                      support@vendor.com
                    </a>
                  </p>
                  <p className="text-sm">
                    <span className="text-muted-foreground">Phone:</span> +1
                    (555) 123-4567
                  </p>
                  <p className="text-sm">
                    <span className="text-muted-foreground">Hours:</span>{" "}
                    Mon-Fri, 9AM-6PM EST
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ── Per-field renderer ──────────────────────────────────────────────────────

function ProfileField({
  field,
  value,
  onChange,
}: {
  field: ProfileFieldResponse;
  value: string;
  onChange: (fieldName: string, value: string) => void;
}) {
  const isEditable = field.editable;

  const fieldId = `field-${field.fieldId ?? field.fieldName}`;

  const readOnlyInput = (
    <Input id={fieldId} value={value} readOnly className="bg-muted/30" />
  );

  if (field.dataType === "BOOLEAN") {
    return (
      <div className="flex items-center gap-3 col-span-1">
        <Switch
          id={`field-${field.fieldId}`}
          checked={value === "true"}
          onCheckedChange={(checked) =>
            onChange(field.fieldName, String(checked))
          }
          disabled={!isEditable}
        />
        <Label htmlFor={`field-${field.fieldId}`} className="cursor-pointer">
          {field.fieldName}
        </Label>
      </div>
    );
  }

  if (field.dataType === "LIST" && field.sampleValues) {
    return (
      <div className="space-y-2">
        <Label htmlFor={`field-${field.fieldId}`}>{field.fieldName}</Label>
        {isEditable ? (
          <Select
            value={value}
            onValueChange={(v) => onChange(field.fieldName, v)}
          >
            <SelectTrigger id={`field-${field.fieldId}`}>
              <SelectValue placeholder={`Select ${field.fieldName}...`} />
            </SelectTrigger>
            <SelectContent>
              {field.sampleValues.map((opt) => (
                <SelectItem key={opt} value={opt}>
                  {opt}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          readOnlyInput
        )}
      </div>
    );
  }

  const inputType =
    field.dataType === "NUMBER" || field.dataType === "CURRENCY"
      ? "number"
      : field.dataType === "DATE"
        ? "date"
        : "text";

  return (
    <div className="space-y-2">
      <Label htmlFor={`field-${field.fieldId}`}>{field.fieldName}</Label>
      {isEditable ? (
        <Input
          id={`field-${field.fieldId}`}
          type={inputType}
          value={value}
          onChange={(e) => onChange(field.fieldName, e.target.value)}
          placeholder={field.fieldName}
        />
      ) : (
        readOnlyInput
      )}
    </div>
  );
}

export default MyProfilePage;
