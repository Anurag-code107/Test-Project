import { useMemo } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Loader2, Bell, BellOff } from "lucide-react";
import { toast } from "sonner";

import {
  useGlobalNotificationSetting,
  useUpdateGlobalNotificationSetting,
  useNotificationTypes,
  useNotificationPreferences,
  useBulkUpdatePreferences,
} from "@/hooks/useNotificationApi";
import type {
  NotificationCategory,
  NotificationTypeResponse,
  UserNotificationPreferenceResponse,
} from "@/types/notification.types";

const CATEGORY_ORDER: NotificationCategory[] = [
  "INCENTIVE",
  "BUDGET",
  "CLAIMS",
  "REWARDS",
  "DATA",
  "INTEGRATION",
  "USER_MANAGEMENT",
];

const categoryLabels: Record<NotificationCategory, string> = {
  INCENTIVE: "Incentive",
  BUDGET: "Budget",
  CLAIMS: "Claims",
  REWARDS: "Rewards",
  DATA: "Data",
  INTEGRATION: "Integration",
  USER_MANAGEMENT: "User Management",
};

export function NotificationPreferencesPanel() {
  const { data: globalSetting, isLoading: isLoadingGlobal } =
    useGlobalNotificationSetting();
  const updateGlobal = useUpdateGlobalNotificationSetting();

  const { data: notificationTypes, isLoading: isLoadingTypes } =
    useNotificationTypes();
  const { data: preferences, isLoading: isLoadingPrefs } =
    useNotificationPreferences();
  const bulkUpdate = useBulkUpdatePreferences();

  const isLoading = isLoadingGlobal || isLoadingTypes || isLoadingPrefs;

  // Build a map of notificationTypeId -> preference for fast lookup
  const preferenceMap = useMemo(() => {
    const map = new Map<string, UserNotificationPreferenceResponse>();
    if (preferences) {
      for (const pref of preferences) {
        map.set(pref.notificationTypeId, pref);
      }
    }
    return map;
  }, [preferences]);

  // Group notification types by category
  const groupedTypes = useMemo(() => {
    if (!notificationTypes)
      return new Map<NotificationCategory, NotificationTypeResponse[]>();

    const map = new Map<NotificationCategory, NotificationTypeResponse[]>();
    for (const nt of notificationTypes) {
      const cat = nt.category as NotificationCategory;
      if (!map.has(cat)) {
        map.set(cat, []);
      }
      map.get(cat)!.push(nt);
    }
    return map;
  }, [notificationTypes]);

  const handleGlobalToggle = (enabled: boolean) => {
    updateGlobal.mutate(enabled, {
      onSuccess: () => {
        toast.success(
          enabled ? "Notifications enabled" : "Notifications disabled",
        );
      },
      onError: () => {
        toast.error("Failed to update notification setting");
      },
    });
  };

  const handleTypeToggle = (notificationTypeId: string, receiving: boolean) => {
    // receiving = true means user wants notifications (optedOut = false)
    // receiving = false means user does not want notifications (optedOut = true)
    bulkUpdate.mutate(
      {
        preferences: [
          {
            notificationTypeId,
            optedOut: !receiving,
          },
        ],
      },
      {
        onSuccess: () => {
          toast.success("Preference updated");
        },
        onError: () => {
          toast.error("Failed to update preference");
        },
      },
    );
  };

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-16">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </CardContent>
      </Card>
    );
  }

  const notificationsEnabled = globalSetting?.notificationsEnabled ?? true;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg font-semibold">
          Notification Settings
        </CardTitle>
        <CardDescription>
          Control how and when you receive notifications
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Global toggle */}
        <div className="flex items-center justify-between rounded-lg border p-4">
          <div className="flex items-center gap-3">
            {notificationsEnabled ? (
              <Bell className="h-5 w-5 text-primary" />
            ) : (
              <BellOff className="h-5 w-5 text-muted-foreground" />
            )}
            <div>
              <Label
                htmlFor="global-notifications"
                className="text-sm font-medium"
              >
                Enable Notifications
              </Label>
              <p className="text-xs text-muted-foreground mt-0.5">
                Turn off to silence all notifications
              </p>
            </div>
          </div>
          <Switch
            id="global-notifications"
            checked={notificationsEnabled}
            onCheckedChange={handleGlobalToggle}
            disabled={updateGlobal.isPending}
          />
        </div>

        {/* Divider */}
        <div className="border-t border-border" />

        {/* Per-type preferences grouped by category */}
        {notificationsEnabled && (
          <div className="space-y-6">
            {CATEGORY_ORDER.map((category) => {
              const types = groupedTypes.get(category);
              if (!types || types.length === 0) return null;

              return (
                <div key={category} className="space-y-3">
                  <h3 className="text-sm font-semibold text-foreground tracking-tight">
                    {categoryLabels[category]}
                  </h3>
                  <div className="space-y-2">
                    {types.map((nt) => {
                      const pref = preferenceMap.get(nt.id);
                      // optedOut true = not receiving; switch should be OFF
                      // optedOut false / undefined = receiving; switch should be ON
                      const isReceiving = pref ? !pref.optedOut : true;

                      return (
                        <div
                          key={nt.id}
                          className="flex items-center justify-between rounded-lg border border-border px-4 py-3"
                        >
                          <div className="min-w-0 flex-1 mr-4">
                            <Label
                              htmlFor={`pref-${nt.id}`}
                              className="text-sm font-medium text-foreground"
                            >
                              {nt.title}
                            </Label>
                            <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                              {nt.description}
                            </p>
                          </div>
                          <Switch
                            id={`pref-${nt.id}`}
                            checked={isReceiving}
                            onCheckedChange={(checked) =>
                              handleTypeToggle(nt.id, checked)
                            }
                            disabled={bulkUpdate.isPending}
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
