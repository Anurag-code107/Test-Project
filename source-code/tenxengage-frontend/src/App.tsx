import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import ProtectedRoute from "@/components/ProtectedRoute";
import HomeRedirect from "@/components/HomeRedirect";
import { AppLoadingScreen } from "@/components/AppLoadingScreen";
import MockupRouter from "@/mockups/MockupRouter";

import LoginPage from "@/pages/LoginPage";
import NotFoundPage from "@/pages/NotFoundPage";
import ApprovalDecisionPage from "@/pages/ApprovalDecisionPage";
import OnboardingPage from "@/pages/OnboardingPage";

import AppLayout from "@/components/layout/AppLayout";
import HomePage from "@/pages/HomePage";
import IncentiveBuilderPage from "@/pages/client-admin/IncentiveBuilderPage";
import ManageIncentivesPage from "@/pages/client-admin/ManageIncentivesPage";
import ManageClaimsPage from "@/pages/client-admin/ManageClaimsPage";
import ReportBuilderPage from "@/pages/client-admin/ReportBuilderPage";
import ActivityLogPage from "@/pages/client-admin/ActivityLogPage";
import MyProfilePage from "@/pages/client-admin/MyProfilePage";
import UserSettingsPage from "@/pages/client-admin/UserSettingsPage";
import PlatformSettingsPage from "@/pages/client-admin/PlatformSettingsPage";
import BuilderConfigPage from "@/pages/client-admin/BuilderConfigPage";
import PartnerAdminRewardsPage from "@/pages/partner-admin/ManageRewardsPage";
import RedemptionStorePage from "@/pages/RedemptionStorePage";
import RedemptionConfirmationPage from "@/pages/redemption-flow/RedemptionConfirmationPage";
import ApprovalQueuePage from "@/pages/redemption/ApprovalQueuePage";
import TransactionHistoryPage from "@/pages/redemption-history/TransactionHistoryPage";
import TenantTransactionHistoryPage from "@/pages/redemption-history/TenantTransactionHistoryPage";
import RedemptionAnalyticsPage from "@/pages/redemption/analytics/RedemptionAnalyticsPage";
import TeamManagementPage from "@/pages/partner-admin/TeamManagementPage";
import ActivityApproverHomePage from "@/pages/activity-approver/HomePage";
import ViewIncentivesPage from "@/pages/shared/ViewIncentivesPage";
import DealQualifierPage from "@/pages/shared/DealQualifierPage";
import NotificationsPage from "@/pages/shared/NotificationsPage";
import BalanceExpirationSettingsPage from "@/pages/balanceExpiration/BalanceExpirationSettingsPage";
import BalanceBreakageReportPage from "@/pages/balanceExpiration/BalanceBreakageReportPage";

function App() {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  // Mockup routes bypass auth entirely — dev only, stripped from production builds
  if (import.meta.env.DEV && location.pathname.startsWith("/mockup/")) {
    return (
      <Routes>
        <Route path="/mockup/*" element={<MockupRouter />} />
      </Routes>
    );
  }

  if (isLoading) {
    return <AppLoadingScreen />;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          isAuthenticated ? <HomeRedirect /> : <Navigate to="/login" replace />
        }
      />

      {/* Permission-gated routes — all use unified AppLayout */}
      <Route element={<ProtectedRoute permission="module.home" />}>
        <Route element={<AppLayout />}>
          <Route path="/home" element={<HomePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.incentive_builder" />}>
        <Route element={<AppLayout />}>
          <Route path="/builder" element={<IncentiveBuilderPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.manage_incentives" />}>
        <Route element={<AppLayout />}>
          <Route path="/manage-incentives" element={<ManageIncentivesPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.manage_claims" />}>
        <Route element={<AppLayout />}>
          <Route path="/claims" element={<ManageClaimsPage />} />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            anyPermission={[
              "module.incentives.sales",
              "module.incentives.enablement",
              "module.incentives.journeys",
            ]}
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route path="/incentives" element={<ViewIncentivesPage />} />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            anyPermission={["module.rewards.claims", "module.rewards.balances"]}
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route path="/rewards" element={<PartnerAdminRewardsPage />} />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            permission="module.deal_qualifier"
            feature="deal_qualifier"
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route path="/deal-qualifier" element={<DealQualifierPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.reporting" />}>
        <Route element={<AppLayout />}>
          <Route path="/reporting" element={<ReportBuilderPage />} />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            permission="module.activity_log"
            feature="audit_log"
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route path="/activity-log" element={<ActivityLogPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.activity_review" />}>
        <Route element={<AppLayout />}>
          <Route
            path="/activity-review"
            element={<ActivityApproverHomePage />}
          />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.settings.profile" />}>
        <Route element={<AppLayout />}>
          <Route path="/settings/profile" element={<MyProfilePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.settings.team" />}>
        <Route element={<AppLayout />}>
          <Route path="/settings/team" element={<TeamManagementPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.settings.users" />}>
        <Route element={<AppLayout />}>
          <Route path="/settings/users" element={<UserSettingsPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.settings.tenx" />}>
        <Route element={<AppLayout />}>
          <Route path="/settings/platform" element={<PlatformSettingsPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="action.builder.manage" />}>
        <Route element={<AppLayout />}>
          <Route
            path="/settings/builder-config"
            element={<BuilderConfigPage />}
          />
        </Route>
      </Route>

      <Route
        path="/admin/redemption-catalog"
        element={<Navigate to="/settings/platform?tab=redemption-catalog" replace />}
      />
      <Route
        path="/settings/redemption/catalog"
        element={<Navigate to="/settings/platform?tab=redemption-catalog" replace />}
      />

      {/* Storefront + redeem-confirmation require BOTH module.redemption_store (so disabling the
          module hides the store — matches the BE gate on the store endpoints) AND a redeem
          capability (personal OR company) so a Client Admin who holds the module umbrella but
          cannot redeem still can't reach the storefront (CR-04). */}
      <Route
        element={
          <ProtectedRoute
            permission="module.redemption_store"
            anyPermission={["action.redemption.redeem", "action.redemption.redeem_company"]}
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route path="/redemption-store" element={<RedemptionStorePage />} />
          <Route path="/redemption/confirmation/:id" element={<RedemptionConfirmationPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="action.redemption.view_history" />}>
        <Route element={<AppLayout />}>
          <Route path="/redemption/history" element={<TransactionHistoryPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="action.redemption.approve" />}>
        <Route element={<AppLayout />}>
          <Route path="/redemption/approval-queue" element={<ApprovalQueuePage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="action.redemption.view_all_history" />}>
        <Route element={<AppLayout />}>
          <Route path="/redemption/admin/history" element={<TenantTransactionHistoryPage />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="action.redemption.view_analytics" />}>
        <Route element={<AppLayout />}>
          <Route path="/redemption/admin/analytics" element={<RedemptionAnalyticsPage />} />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            permission="action.redemption.expiration.configure"
            feature="reward_balance_expiration"
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route
            path="/settings/redemption/balance-expiration"
            element={<BalanceExpirationSettingsPage />}
          />
        </Route>
      </Route>

      <Route
        element={
          <ProtectedRoute
            permission="action.redemption.expiration.view_breakage"
            feature="reward_balance_expiration"
          />
        }
      >
        <Route element={<AppLayout />}>
          <Route
            path="/redemption/breakage"
            element={<BalanceBreakageReportPage />}
          />
        </Route>
      </Route>

      <Route element={<ProtectedRoute permission="module.notifications" />}>
        <Route element={<AppLayout />}>
          <Route path="/notifications" element={<NotificationsPage />} />
        </Route>
      </Route>

      {/* Dev mockups — unprotected, auto-discovered, stripped from production builds */}
      {import.meta.env.DEV && <Route path="/mockup/*" element={<MockupRouter />} />}

      {/* Public pages */}
      <Route path="/approvals/decide" element={<ApprovalDecisionPage />} />
      <Route path="/onboarding" element={<OnboardingPage />} />

      {/* Legacy URL redirects */}
      <Route path="/client-admin/*" element={<Navigate to="/home" replace />} />
      <Route
        path="/partner-admin/*"
        element={<Navigate to="/incentives" replace />}
      />
      <Route
        path="/partner-seller/*"
        element={<Navigate to="/incentives" replace />}
      />
      <Route
        path="/approver/*"
        element={<Navigate to="/activity-review" replace />}
      />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
