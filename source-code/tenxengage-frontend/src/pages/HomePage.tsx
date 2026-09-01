import { PageBanner } from "@/components/PageBanner";
import { HomeDashboardTemplateRenderer } from "@/components/home/HomeDashboardTemplateRenderer";
import {
  HomeDashboardProvider,
  useHomeDashboardState,
} from "@/components/home/HomeDashboardContext";
import { useAuth } from "@/hooks/useAuth";
import { useHomeDashboardTemplate } from "@/hooks/useHomeDashboardTemplate";

const SUBTITLE_BY_TEMPLATE_NAME: Record<string, string> = {
  "Client Admin": "Partner performance overview",
  "Partner User": "Rewards and recommendations",
  Approver: "Proof-of-execution submissions",
};

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

function resolveSubtitle(templateName: string | undefined): string {
  if (templateName && SUBTITLE_BY_TEMPLATE_NAME[templateName]) {
    return SUBTITLE_BY_TEMPLATE_NAME[templateName];
  }
  return "Your home dashboard";
}

function HomePageContent() {
  const { user } = useAuth();
  const template = useHomeDashboardTemplate();
  const { selectedPartnerName } = useHomeDashboardState();
  const userName = user?.firstName ?? "there";
  const subtitle = resolveSubtitle(template?.name);

  return (
    <div className="space-y-6">
      <PageBanner
        theme="home"
        title={`${getGreeting()}, ${userName}`}
        subtitle={
          <>
            {subtitle}
            {selectedPartnerName && (
              <span className="text-foreground font-medium">
                {" — "}
                {selectedPartnerName}
              </span>
            )}
          </>
        }
      />

      <HomeDashboardTemplateRenderer template={template} />
    </div>
  );
}

export default function HomePage() {
  // Provider at the page level so PageBanner (rendered above the template
  // renderer, not inside it) can still read widget-published state.
  return (
    <HomeDashboardProvider>
      <HomePageContent />
    </HomeDashboardProvider>
  );
}
