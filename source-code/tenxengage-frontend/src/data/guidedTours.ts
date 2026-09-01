export interface TourStepAction {
  /** Semantic action type — all resolve to a DOM click */
  type: "click" | "openDrawer" | "switchTab";
  /** CSS selector of element to click */
  selector: string;
  /** CSS selector to wait for after the action (e.g. the drawer content) */
  waitFor?: string;
  /** ms to wait for waitFor element (default 2000) */
  waitTimeout?: number;
}

export interface TourStep {
  route?: string;
  targetSelector: string;
  title: string;
  message: string;
  arrowDirection?: "top" | "bottom" | "left" | "right";
  delay?: number;
  /** Action to perform before spotlighting (e.g. open drawer, switch tab) */
  preAction?: TourStepAction;
  /** Message shown when the target element can't be found after retries */
  fallbackMessage?: string;
}

export interface GuidedTour {
  id: string;
  name: string;
  keywords: string[];
  roles: string[];
  steps: TourStep[];
}

/**
 * Resolves `{rolePrefix}` placeholders in tour steps.
 * Routes are now flat (no role prefix), so placeholders resolve to empty string.
 */
function resolveRoutes(steps: TourStep[]): TourStep[] {
  return steps.map((step) => ({
    ...step,
    route: step.route?.replace("{rolePrefix}", ""),
    targetSelector: step.targetSelector.replace(/\{rolePrefix\}/g, ""),
  }));
}

export const guidedTours: GuidedTour[] = [
  // ==================== CLIENT ADMIN TOURS ====================
  {
    id: "admin-create-incentive",
    name: "Create a New Incentive",
    keywords: [
      "set up",
      "create",
      "new incentive",
      "build incentive",
      "setup",
      "incentive builder",
      "create incentive",
      "build",
      "configure",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/builder",
        targetSelector: 'a[href="/builder"]',
        title: "Incentive Builder",
        message:
          "The Incentive Builder is your workspace for creating new incentive programs. Navigate here to start building.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="builder-content"]',
        title: "How Would You Like To Get Started?",
        message:
          "You have three options: Create From Scratch using the guided 6-section workflow with AI assistance, Create From Existing to clone and customize a previous incentive, or Create From Template to upload a pre-filled Excel template.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="builder-type-picker"]',
        title: "Select an Incentive Type",
        message:
          "Choose from Sales Incentives for deal-based rewards, Enablement Incentives for training completions and proof-of-execution tasks, or Journeys for multi-step milestone-based programs. Let's select Sales Incentive to walk you through the builder.",
        arrowDirection: "top",
        preAction: {
          type: "click",
          selector: '[data-tour="create-from-scratch"]',
          waitFor: '[data-tour="builder-type-picker"]',
          waitTimeout: 2000,
        },
      },
      {
        targetSelector: '[data-tour="builder-setup-panel"]',
        title: "Setup Sections",
        message:
          "The right panel contains all the sections you need to configure your incentive: Basic Info, Timeline, Eligibility, Budget, Criteria, and Approval. Each section expands to reveal its fields and is tracked in the progress bar above.",
        arrowDirection: "left",
        preAction: {
          type: "click",
          selector: '[data-tour="type-sales-incentive"]',
          waitFor: '[data-tour="builder-setup-panel"]',
          waitTimeout: 2000,
        },
      },
      {
        targetSelector: '[data-tour="builder-ai-copilot"]',
        title: "AI Incentive Copilot",
        message:
          "The AI Copilot on the left guides you through building your incentive conversationally. Answer its questions and it will automatically fill in the setup sections for you — no manual data entry needed! You can also switch to Manual Mode using the toggle in the header.",
        arrowDirection: "right",
      },
    ],
  },
  {
    id: "admin-manage-incentives",
    name: "Browse My Incentives",
    keywords: [
      "manage incentives",
      "existing incentive",
      "view incentives",
      "incentive status",
      "active incentives",
      "browse incentives",
      "my incentives",
      "see incentives",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/manage-incentives",
        targetSelector: 'a[href="/manage-incentives"]',
        title: "Manage Incentives",
        message:
          "The Manage Incentives page shows all your incentive programs organized by type. You can view, edit, activate, or retire programs from here.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="manage-incentives-content"]',
        title: "Your Incentive Programs",
        message:
          "Browse your incentives by type — Sales, Enablement, and Journeys. Click any card to view details or make edits.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="card-actions"]',
        title: "Quick Actions",
        message:
          "Each incentive card has action buttons for quick management. Depending on the program's status, you'll see options like Edit, Activate, Submit for Approval, or View Approvals — right on the card.",
        arrowDirection: "top",
        fallbackMessage:
          "Each incentive card has action buttons like Edit, Activate, Submit, and Approvals. These appear at the bottom of every card based on the program's current status.",
      },
    ],
  },
  {
    id: "admin-incentive-details",
    name: "View Incentive Details",
    keywords: [
      "incentive details",
      "view details",
      "incentive detail",
      "open incentive",
      "incentive info",
      "program details",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/manage-incentives",
        targetSelector: 'a[href="/manage-incentives"]',
        title: "Manage Incentives",
        message:
          "First, navigate to the Manage Incentives page where all your programs are listed.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="manage-incentives-content"]',
        title: "Find Your Incentive",
        message:
          "Your incentives are organized by type — Sales, Enablement, and Journeys. Find the program you want to view.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        preAction: {
          type: "openDrawer",
          selector:
            '[data-tour="manage-incentives-content"] [data-tour="incentive-card"]:first-of-type',
          waitFor: '[role="dialog"]',
          waitTimeout: 2000,
        },
        targetSelector: '[role="dialog"]',
        title: "Incentive Details",
        message:
          "Clicking any incentive card opens the detail drawer. Here you can view the full program description, reward structure, eligibility rules, documents, and status. This is a read-only view of the program.",
        arrowDirection: "left",
        fallbackMessage:
          "Click any incentive card to open its detail drawer. You'll see the full program configuration, reward structure, and documents.",
      },
    ],
  },
  {
    id: "admin-edit-incentive",
    name: "Edit an Incentive",
    keywords: [
      "edit incentive",
      "modify incentive",
      "change incentive",
      "update incentive",
      "edit program",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/manage-incentives",
        targetSelector: 'a[href="/manage-incentives"]',
        title: "Manage Incentives",
        message:
          "Navigate to the Manage Incentives page to find the program you want to edit.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="manage-incentives-content"]',
        title: "Find Your Incentive",
        message:
          "Locate the incentive card you want to edit. Your programs are organized by type — Sales, Enablement, and Journeys.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="edit-button"]',
        title: "Edit Button",
        message:
          "Click the Edit button on any incentive card to open the Incentive Builder with that program's configuration pre-loaded. You can modify any section of the program from there.",
        arrowDirection: "top",
        fallbackMessage:
          "Each incentive card has an Edit button at the bottom. Click it to open the program in the Incentive Builder where you can modify its configuration.",
      },
    ],
  },
  {
    id: "admin-manage-claims",
    name: "Manage Partner Claims",
    keywords: [
      "manage claims",
      "claims",
      "partner claims",
      "review claims",
      "claim management",
      "claims submitted",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/claims",
        targetSelector: 'a[href="/claims"]',
        title: "Manage Claims",
        message:
          "The Manage Claims page shows all claim submissions from your partner network. Review, approve, or take action on partner reward claims.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="claims-table"]',
        title: "Claims Overview",
        message:
          "View all partner claims organized by partner. You can filter by status, search by PO number, and export data as needed.",
        arrowDirection: "top",
        delay: 300,
        fallbackMessage:
          "The claims table will appear here once claim data is loaded. It shows all partner claims organized by partner.",
      },
    ],
  },
  {
    id: "admin-partner-performance",
    name: "View Partner Performance",
    keywords: [
      "partner performance",
      "how are partners",
      "partner doing",
      "dashboard",
      "overview",
      "participation",
      "engagement",
      "metrics",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/home",
        targetSelector: '[data-tour="program-performance-section"]',
        title: "Program Performance",
        message:
          "The Program Performance section is your at-a-glance view of how your incentive programs and partner ecosystem are performing. It shows six key metrics covering rewards, budget, participation, and partner activity.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="metrics-cards"]',
        title: "Key Metrics",
        message:
          "These cards show your top metrics like Total Rewards Earned and Budget Utilized. Click any smaller card to swap it into the featured view with a full trend chart. All six metrics rotate through these positions.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="participation-section"]',
        title: "Additional Metrics",
        message:
          "These cards track partner participation and activity, including new partners enrolled, users earning rewards, and claims made. Click any card to promote it to the featured view above.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="region-filter"]',
        title: "Filter by Region",
        message:
          "Use the region filter to narrow your performance data to a specific geography like Americas, EMEAR, or APJ. By default it shows Global across all regions.",
        arrowDirection: "bottom",
      },
    ],
  },

  {
    id: "admin-build-report",
    name: "Build a Report",
    keywords: [
      "report",
      "reporting",
      "build report",
      "create report",
      "custom report",
      "export data",
      "download report",
      "data export",
      "report builder",
      "analytics",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/reporting",
        targetSelector: '[data-tour="report-builder-content"]',
        title: "Custom Report Builder",
        message:
          "The builder walks you through three steps: Choose a Dataset, Select Fields & Filters, then Preview & Export. You can also save reports as reusable templates.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="report-dataset-picker"]',
        title: "Choose a Dataset",
        message:
          "Start by selecting a dataset — each one represents a different area of your program data like transactions, training completions, partner users, or budget allocations. Click any card to proceed.",
        arrowDirection: "top",
        fallbackMessage:
          "The dataset picker shows all available data sources. Select one to begin building your report.",
      },
      {
        targetSelector: '[data-tour="report-templates"]',
        title: "Saved Templates",
        message:
          "Switch to the Templates tab to re-run previously saved reports, export them directly, or use them as a starting point for new reports.",
        arrowDirection: "top",
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-templates"]',
          waitFor: '[data-tour="report-templates"]',
          waitTimeout: 2000,
        },
        fallbackMessage:
          "The Templates tab stores your saved report configurations. You can re-run or export them anytime.",
      },
    ],
  },
  {
    id: "admin-manage-users",
    name: "Manage Users",
    keywords: [
      "manage users",
      "add user",
      "user settings",
      "internal users",
      "partner users",
      "user management",
      "permissions",
      "roles",
      "access",
      "invite user",
      "team members",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/settings/users",
        targetSelector: 'a[href="/settings/users"]',
        title: "User Settings",
        message:
          "The Settings dropdown in the sidebar contains User Settings. This is where you manage all internal and partner users, assign roles, and configure permissions.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="users-tab-content"]',
        title: "Users Tab",
        message:
          "The Users tab shows two sections: Internal Users (your team) and Partner Companies (external partners and their sellers). You can manage both from here.",
        arrowDirection: "top",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="add-user-button"]',
        title: "Add a User",
        message:
          "Click this button to invite a new internal user to the platform. You'll assign their name, email, and role during setup.",
        arrowDirection: "bottom",
        fallbackMessage:
          "The Add Internal User button lets you invite new team members. You'll find it at the top of the Internal Users section.",
      },
      {
        targetSelector: '[data-tour="partner-companies-section"]',
        title: "Partner Companies",
        message:
          "Below Internal Users, you'll find Partner Companies. Click any company to view and manage their individual users, or add new partner users directly.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-roles"]',
          waitFor: '[data-tour="roles-tab-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="roles-tab-content"]',
        title: "Roles Tab",
        message:
          "The Roles tab shows all role definitions in the system. Each role card displays its module access and permission count. You can edit permissions for any role or create custom roles from templates.",
        arrowDirection: "top",
        fallbackMessage:
          "The Roles tab shows all available roles and their permission levels. Use it to understand or customize role-based access.",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-permissions"]',
          waitFor: '[data-tour="permissions-tab-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="permissions-tab-content"]',
        title: "Permissions Tab",
        message:
          "The Permissions tab lets you override default role permissions at the company or individual user level. Select a partner company first, then toggle specific permissions on or off for that company or for individual users within it.",
        arrowDirection: "top",
        fallbackMessage:
          "The Permissions tab allows you to set permission overrides for specific partner companies or individual users.",
      },
    ],
  },

  // ==================== PARTNER SELLER TOURS ====================
  {
    id: "seller-earn-rewards",
    name: "How to Earn Rewards",
    keywords: [
      "earn",
      "rewards",
      "how do i earn",
      "get rewards",
      "make money",
      "incentive rewards",
      "earn rewards",
    ],
    roles: ["partner-seller", "partner-admin"],
    steps: [
      {
        route: "{rolePrefix}/incentives",
        targetSelector: 'a[href="{rolePrefix}/incentives"]',
        title: "View Incentives",
        message:
          "The View Incentives tab is where you discover all available incentive programs. This is your starting point for earning rewards.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="sales-section"]',
        title: "Sales Incentives",
        message:
          "Sales Incentives are reward programs tied to specific sales deals. When you close qualifying deals, you can claim rewards like cash, points, credits, or tickets.",
        arrowDirection: "top",
        fallbackMessage:
          "You don't have any active Sales Incentives right now. When available, they'll appear here on the Sales tab. Check back later or ask your admin about upcoming programs.",
      },
      {
        targetSelector: '[data-tour="incentive-claim-button"]',
        title: "Claim Button",
        message:
          'Click the "Claim" button on any Sales Incentive card to navigate to the Manage Rewards page where you can submit your claim and earn your rewards.',
        arrowDirection: "top",
        fallbackMessage:
          'Sales Incentive cards have a "Claim" button that takes you to the Manage Rewards page. You\'ll see it on any active Sales Incentive card.',
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-enablement"]',
          waitFor: '[data-tour="enablement-section"][data-state="active"]',
        },
        targetSelector: '[data-tour="enablement-section"]',
        title: "Enablement Incentives",
        message:
          "Enablement Incentives cover two types of programs. Training Incentives reward you for completing learning courses, while Activity Incentives reward you for proof-of-execution tasks like uploading evidence of completed activities.",
        arrowDirection: "top",
        fallbackMessage:
          "No Enablement Incentives are available right now. When your admin creates Training or Activity programs, they'll appear here on the Enablement tab.",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-journeys"]',
          waitFor: '[data-tour="journey-section"][data-state="active"]',
        },
        targetSelector: '[data-tour="journey-section"]',
        title: "Journeys",
        message:
          "Journeys are milestone-based incentive programs that guide you through a series of stages. Complete each stage's requirements — such as sales targets, training courses, or activities — to unlock rewards as you progress.",
        arrowDirection: "top",
        fallbackMessage:
          "No Journey programs are available right now. When your admin creates journey programs, they'll appear here on the Journeys tab.",
      },
      {
        route: "{rolePrefix}/rewards",
        targetSelector: 'a[href="{rolePrefix}/rewards"]',
        title: "Manage Rewards",
        message:
          "The Manage Rewards tab is where you submit claims for your qualifying deals and track your earnings.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="claims-table"]',
        title: "Your Eligible Deals",
        message:
          'The claims table shows all your deals. Find an eligible PO# with an "Unclaimed" status to submit a reward claim.',
        arrowDirection: "top",
        fallbackMessage:
          "The claims table will appear here once your deals are loaded. It shows all your PO numbers and their claim status.",
      },
      {
        targetSelector: '[data-tour="claim-button"]',
        title: "Claim Your Reward",
        message:
          'Click the "Claim Now" button on an eligible PO# row to officially submit your reward claim. That\'s how you earn rewards in tenXengage!',
        arrowDirection: "left",
        fallbackMessage:
          'Look for the "Claim Now" button next to eligible PO numbers in the table above. If you don\'t see any claimable deals, you may not have eligible POs at this time.',
      },
    ],
  },
  {
    id: "seller-view-earnings",
    name: "View Your Earnings",
    keywords: [
      "earned",
      "earnings",
      "what i've earned",
      "see earned",
      "my rewards",
      "view earnings",
      "how much",
      "balance",
      "earned so far",
      "transaction history",
    ],
    roles: ["partner-seller", "partner-admin"],
    steps: [
      {
        route: "{rolePrefix}/rewards",
        targetSelector: 'a[href="{rolePrefix}/rewards"]',
        title: "Manage Rewards",
        message:
          "Navigate to Manage Rewards to see a complete view of all your earned rewards, broken down by type and status.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="claims-table"]',
        title: "Claims History",
        message:
          "The Claims tab shows all your deals and their claim status. You can filter by status, search by PO number, and see reward breakdowns for each claim.",
        arrowDirection: "top",
        delay: 300,
        fallbackMessage:
          "The claims table will appear here once your data is loaded. It shows all your PO numbers and their claim status.",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-rewards"]',
          waitFor: '[data-tour="rewards-tab-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="rewards-tab-content"]',
        title: "Rewards Tab",
        message:
          "The Rewards tab shows your current balances across all currency types, including cash, points, credits, and tickets.",
        arrowDirection: "top",
        fallbackMessage:
          "Switch to the Rewards tab to view your current reward balances across all currency types.",
      },
      {
        targetSelector: '[data-tour="transaction-history"]',
        title: "Transaction History",
        message:
          "The Transaction History section shows a detailed log of all your reward activity, including earnings and claims. Use the filter to view specific transaction types or search by description.",
        arrowDirection: "top",
        fallbackMessage:
          "Scroll down on the Rewards tab to find your full transaction history with filters for transaction type.",
      },
    ],
  },
  {
    id: "seller-submit-claim",
    name: "Submit a Claim",
    keywords: [
      "submit",
      "claim",
      "file claim",
      "make claim",
      "how to claim",
      "submit claim",
      "claim process",
    ],
    roles: ["partner-seller", "partner-admin"],
    steps: [
      {
        route: "{rolePrefix}/rewards",
        targetSelector: 'a[href="{rolePrefix}/rewards"]',
        title: "Manage Rewards",
        message:
          "Navigate to the Manage Rewards page to submit and manage your claims.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="claims-table"]',
        title: "Find Your Deal",
        message:
          'Find an eligible PO# in the table with an "Unclaimed" status.',
        arrowDirection: "top",
        delay: 300,
        fallbackMessage:
          'The claims table will appear here once your deals are loaded. Look for PO numbers with an "Unclaimed" status.',
      },
      {
        targetSelector: '[data-tour="claim-button"]',
        title: "Claim Your Reward",
        message:
          'Click the "Claim" button to submit your reward claim. Once submitted, your claim will be reviewed and rewards will be processed.',
        arrowDirection: "left",
        fallbackMessage:
          'The "Claim Now" button appears next to eligible PO numbers in the table. If none are visible, you may not have claimable deals at this time.',
      },
    ],
  },
  {
    id: "seller-view-incentives",
    name: "View Available Incentives",
    keywords: [
      "incentives",
      "available",
      "what incentives",
      "programs",
      "view incentives",
      "browse incentives",
    ],
    roles: ["partner-seller", "partner-admin"],
    steps: [
      {
        route: "{rolePrefix}/incentives",
        targetSelector: 'a[href="{rolePrefix}/incentives"]',
        title: "View Incentives",
        message:
          "The View Incentives page shows all available incentive programs organized by type.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="sales-section"]',
        title: "Sales Incentives",
        message:
          "Sales Incentives reward you for closing qualifying deals. Browse available programs and check eligibility requirements.",
        arrowDirection: "top",
        delay: 300,
        fallbackMessage:
          "No Sales Incentives are available right now. When your admin creates sales programs, they'll appear here.",
      },
      {
        preAction: {
          type: "openDrawer",
          selector:
            '[data-tour="sales-section"] [data-tour="incentive-card"]:first-of-type',
          waitFor: '[role="dialog"]',
          waitTimeout: 2000,
        },
        targetSelector: '[role="dialog"]',
        title: "Incentive Details",
        message:
          "Clicking any incentive card opens the detail drawer where you can view the full program description, eligibility rules, reward structure, and related documents.",
        arrowDirection: "left",
        fallbackMessage:
          "Click any incentive card to open its detail drawer. You'll see the full program description, eligibility rules, reward structure, and attached documents.",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-enablement"]',
          waitFor: '[data-tour="enablement-section"][data-state="active"]',
        },
        targetSelector: '[data-tour="enablement-section"]',
        title: "Enablement Incentives",
        message:
          "Enablement Incentives include two types: Training Incentives that reward you for completing learning courses, and Activity Incentives that reward you for proof-of-execution tasks like uploading evidence of completed activities.",
        arrowDirection: "top",
        fallbackMessage:
          "No Enablement Incentives are available right now. When your admin creates training or activity programs, they'll appear here.",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-journeys"]',
          waitFor: '[data-tour="journey-section"][data-state="active"]',
        },
        targetSelector: '[data-tour="journey-section"]',
        title: "Journeys",
        message:
          "Journeys are milestone-based incentive programs that guide you through a series of stages. Complete each stage's requirements — such as sales targets, training courses, or activities — to unlock rewards as you progress.",
        arrowDirection: "top",
        fallbackMessage:
          "No Journey programs are available right now. When your admin creates journey programs, they'll appear here.",
      },
    ],
  },
  {
    id: "seller-deal-qualifier",
    name: "Check Deal Eligibility",
    keywords: [
      "deal qualifier",
      "qualify",
      "check deal",
      "deal eligibility",
      "qualifies",
      "does my deal qualify",
      "deal eligible",
      "check eligibility",
      "deal check",
      "po qualify",
      "po eligible",
    ],
    roles: ["partner-seller", "partner-admin"],
    steps: [
      {
        route: "{rolePrefix}/deal-qualifier",
        targetSelector: 'a[href="{rolePrefix}/deal-qualifier"]',
        title: "Deal Qualifier",
        message:
          "The Deal Qualifier tool helps you check which incentive programs a specific deal qualifies for before you submit a claim.",
        arrowDirection: "left",
      },
      {
        targetSelector: '[data-tour="deal-qualifier-form"]',
        title: "Enter Deal Details",
        message:
          "Enter the details of your deal — product, deal size, customer info — and the system will show you which incentives apply. This helps maximize your reward potential!",
        arrowDirection: "top",
        delay: 300,
      },
    ],
  },
  // ==================== PARTNER ADMIN EXCLUSIVE TOURS ====================
  {
    id: "admin-monitor-team",
    name: "Monitor Team Performance",
    keywords: [
      "team performance",
      "team",
      "monitor team",
      "seller performance",
      "how is my team",
      "team doing",
      "team metrics",
      "compare sellers",
      "individual performance",
      "company overview",
    ],
    roles: ["partner-admin"],
    steps: [
      {
        route: "/home",
        targetSelector: '[data-tour="earned-rewards"]',
        title: "Your Team's Balances",
        message:
          "The My Balances card shows your company's aggregate reward balances across all currency types — cash, points, credits, and tickets. Click it to view detailed reward history.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="performance-section"]',
        title: "Incentive Performance",
        message:
          "The Incentive Performance section shows key metrics for your team — total rewards earned, participation numbers, and claims made. By default it shows the Company Overview aggregated across all team members.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="team-member-selector"]',
        title: "View by Team Member",
        message:
          "Use this dropdown to switch between Company Overview (all sellers aggregated) and any individual seller's performance. Select a team member to see their personal metrics.",
        arrowDirection: "bottom",
      },
      {
        targetSelector: '[data-tour="engagement-type-filter"]',
        title: "Filter by Incentive Type",
        message:
          "Filter the performance metrics by incentive type — All, Sales, Enablement, or Journeys — to see how your team is performing in each area.",
        arrowDirection: "bottom",
      },
      {
        targetSelector: '[data-tour="suggestions-section"]',
        title: "tenX Suggestions",
        message:
          "The tenX Suggestions section shows personalized recommendations — training courses and incentives tailored to your team's performance patterns. Use these to guide your sellers toward high-value opportunities.",
        arrowDirection: "top",
      },
    ],
  },

  // ==================== PARTNER SELLER EXCLUSIVE TOURS ====================
  {
    id: "seller-track-performance",
    name: "Track Your Performance",
    keywords: [
      "my performance",
      "track performance",
      "how am i doing",
      "performance metrics",
      "my metrics",
      "my stats",
      "personal performance",
      "dashboard metrics",
      "my progress",
    ],
    roles: ["partner-seller"],
    steps: [
      {
        route: "/home",
        targetSelector: '[data-tour="earned-rewards"]',
        title: "Your Reward Balances",
        message:
          "The My Balances card shows your current reward balances — cash, points, credits, and tickets. Click it to go to Manage Rewards for full details.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="seller-performance-section"]',
        title: "Your Incentive Performance",
        message:
          "This section tracks your personal performance — total rewards earned, rewards received, and claims made. Each card shows a trend percentage compared to the previous period.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="engagement-type-filter"]',
        title: "Filter by Incentive Type",
        message:
          "Use this dropdown to filter your metrics by incentive type — All, Sales, Enablement, or Journeys — to understand where your rewards are coming from.",
        arrowDirection: "bottom",
      },
      {
        targetSelector: '[data-tour="suggestions-section"]',
        title: "tenX Suggestions",
        message:
          "The tenX Suggestions section shows training courses and incentives recommended for you based on your performance. These are great opportunities to maximize your earnings!",
        arrowDirection: "top",
      },
    ],
  },
  // ── My Profile tours (shared across roles, different routes) ──────────
  {
    id: "admin-my-profile",
    name: "My Profile",
    keywords: [
      "my profile", "profile", "settings", "account", "edit profile",
      "change password", "update password", "avatar", "profile picture",
      "notifications", "notification settings", "support", "help",
      "personal info", "personal information", "my account",
    ],
    roles: ["client-admin"],
    steps: [
      {
        route: "/client-admin/settings/profile",
        targetSelector: '[data-tour="profile-avatar"]',
        title: "Your Profile Picture",
        message:
          "This is your profile avatar. Click the camera icon to upload a new photo. Your avatar appears across the platform wherever your name is shown.",
        arrowDirection: "right",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="profile-header"]',
        title: "Profile Overview",
        message:
          "Here you can see your name, role, email, and organization at a glance. This info is pulled from your account settings.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-basic-info"]',
        title: "Basic Information",
        message:
          "Your personal details like name, email, and phone number are displayed here. Contact your admin if any of this information needs updating.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-password"]',
        title: "Password & Security",
        message:
          "You can update your password here. Enter your current password and a new one, then click Update to save the change.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-notifications"]',
          waitFor: '[data-tour="notifications-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="notifications-content"]',
        title: "Notification Preferences",
        message:
          "Manage how and when you receive notifications. You can toggle email alerts, in-app notifications, and customize which events you want to be notified about.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-support"]',
          waitFor: '[data-tour="support-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="support-content"]',
        title: "Support & Resources",
        message:
          "Need help? Find links to terms & conditions, privacy policy, and the support portal here. You can also reach our support team directly via email or phone.",
        arrowDirection: "top",
      },
    ],
  },
  {
    id: "partner-admin-my-profile",
    name: "My Profile",
    keywords: [
      "my profile", "profile", "settings", "account", "edit profile",
      "change password", "update password", "avatar", "profile picture",
      "notifications", "notification settings", "support", "help",
      "personal info", "personal information", "my account",
    ],
    roles: ["partner-admin"],
    steps: [
      {
        route: "/partner-admin/settings",
        targetSelector: '[data-tour="profile-avatar"]',
        title: "Your Profile Picture",
        message:
          "This is your profile avatar. Click the camera icon to upload a new photo. Your avatar appears across the platform wherever your name is shown.",
        arrowDirection: "right",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="profile-header"]',
        title: "Profile Overview",
        message:
          "Here you can see your name, role, email, and organization at a glance. This info is pulled from your account settings.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-basic-info"]',
        title: "Basic Information",
        message:
          "Your personal details like name, email, and phone number are displayed here. Contact your company admin or partner support if any of this information needs updating.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-password"]',
        title: "Password & Security",
        message:
          "You can update your password here. Enter your current password and a new one, then click Update to save the change.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-notifications"]',
          waitFor: '[data-tour="notifications-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="notifications-content"]',
        title: "Notification Preferences",
        message:
          "Manage how and when you receive notifications. You can toggle email alerts, in-app notifications, and customize which events you want to be notified about.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-support"]',
          waitFor: '[data-tour="support-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="support-content"]',
        title: "Support & Resources",
        message:
          "Need help? Find links to program terms, privacy policy, and the support portal here. You can also reach our support team directly via email or phone.",
        arrowDirection: "top",
      },
    ],
  },
  {
    id: "partner-seller-my-profile",
    name: "My Profile",
    keywords: [
      "my profile", "profile", "settings", "account", "edit profile",
      "change password", "update password", "avatar", "profile picture",
      "notifications", "notification settings", "support", "help",
      "personal info", "personal information", "my account",
    ],
    roles: ["partner-seller"],
    steps: [
      {
        route: "/partner-seller/settings",
        targetSelector: '[data-tour="profile-avatar"]',
        title: "Your Profile Picture",
        message:
          "This is your profile avatar. Click the camera icon to upload a new photo. Your avatar appears across the platform wherever your name is shown.",
        arrowDirection: "right",
        delay: 300,
      },
      {
        targetSelector: '[data-tour="profile-header"]',
        title: "Profile Overview",
        message:
          "Here you can see your name, role, email, and organization at a glance. This info is pulled from your account settings.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-basic-info"]',
        title: "Basic Information",
        message:
          "Your personal details like name, email, and phone number are displayed here. Contact your company admin or partner support if any of this information needs updating.",
        arrowDirection: "top",
      },
      {
        targetSelector: '[data-tour="profile-password"]',
        title: "Password & Security",
        message:
          "You can update your password here. Enter your current password and a new one, then click Update to save the change.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-notifications"]',
          waitFor: '[data-tour="notifications-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="notifications-content"]',
        title: "Notification Preferences",
        message:
          "Manage how and when you receive notifications. You can toggle email alerts, in-app notifications, and customize which events you want to be notified about.",
        arrowDirection: "top",
      },
      {
        preAction: {
          type: "switchTab",
          selector: '[data-tour="tab-support"]',
          waitFor: '[data-tour="support-content"]',
          waitTimeout: 2000,
        },
        targetSelector: '[data-tour="support-content"]',
        title: "Support & Resources",
        message:
          "Need help? Find links to program terms, privacy policy, and the support portal here. You can also reach our support team directly via email or phone.",
        arrowDirection: "top",
      },
    ],
  },
];

// Irrelevant query patterns that should be rejected
const irrelevantPatterns = [
  "weather",
  "recipe",
  "cook",
  "sports",
  "score",
  "movie",
  "film",
  "joke",
  "funny",
  "game",
  "play",
  "music",
  "song",
  "news",
  "stock",
  "crypto",
  "bitcoin",
  "calculate",
  "math",
  "translate",
  "directions",
  "map",
  "restaurant",
  "food",
  "order",
  "pizza",
  "tax",
  "taxes",
  "accounting",
  "legal",
  "lawyer",
  "invest",
  "insurance",
  "healthcare",
  "doctor",
  "real estate",
  "mortgage",
  "loan",
  "salary",
  "payroll",
  "human resources",
  "vacation",
  "travel",
  "booking",
  "hotel",
  "flight",
  "dating",
  "relationship",
  "homework",
  "essay",
  "coding",
  "debug",
  "politics",
  "election",
  "religion",
];

/** Static keyword matcher — fast, zero-latency, works offline */
function findTourByKeywords(
  query: string,
  role: string,
): { tour: GuidedTour | null; score: number } {
  const q = query.toLowerCase().trim();

  if (q.length < 3) return { tour: null, score: 0 };
  if (irrelevantPatterns.some((p) => q.includes(p)))
    return { tour: null, score: 0 };

  const candidates = guidedTours.filter((t) => t.roles.includes(role));

  let bestTour: GuidedTour | null = null;
  let bestScore = 0;

  for (const tour of candidates) {
    let score = 0;
    for (const keyword of tour.keywords) {
      if (q.includes(keyword)) {
        score += keyword.length;
      }
    }
    if (score > bestScore) {
      bestScore = score;
      bestTour = tour;
    }
  }

  return { tour: bestTour, score: bestScore };
}

/** Look up a tour by ID and resolve routes for the given role */
export function getTourById(tourId: string): GuidedTour | null {
  const tour = guidedTours.find((t) => t.id === tourId);
  if (!tour) return null;
  return {
    ...tour,
    steps: resolveRoutes(tour.steps),
  };
}

/**
 * Get the top N suggested tours for a role, ranked by partial keyword overlap.
 * Used as a fallback when the primary match has low confidence.
 */
export function getSuggestedTours(
  query: string,
  role: string,
  limit = 3,
): GuidedTour[] {
  const q = query.toLowerCase().trim();
  const candidates = guidedTours.filter((t) => t.roles.includes(role));

  if (q.length < 2) {
    // No query — return the most useful tours for this role
    return candidates.slice(0, limit).map((t) => ({
      ...t,
      steps: resolveRoutes(t.steps),
    }));
  }

  const queryWords = q.split(/\s+/);

  const scored = candidates.map((tour) => {
    let score = 0;
    // Full keyword match
    for (const keyword of tour.keywords) {
      if (q.includes(keyword)) {
        score += keyword.length * 2;
      } else if (
        queryWords.some((w) => keyword.includes(w) || w.includes(keyword))
      ) {
        score += 1;
      }
    }
    // Tour name word overlap
    const nameWords = tour.name.toLowerCase().split(/\s+/);
    for (const qw of queryWords) {
      if (nameWords.some((nw) => nw.startsWith(qw) || qw.startsWith(nw))) {
        score += 2;
      }
    }
    return { tour, score };
  });

  const results = scored
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((s) => ({
      ...s.tour,
      steps: resolveRoutes(s.tour.steps),
    }));

  // If no partial matches, return popular tours for role
  if (results.length === 0) {
    return candidates.slice(0, limit).map((t) => ({
      ...t,
      steps: resolveRoutes(t.steps),
    }));
  }

  return results;
}

export interface TextGuideStep {
  title: string;
  description: string;
}

export interface TourMatchResult {
  tour: GuidedTour | null;
  suggestions: GuidedTour[];
  textGuide: TextGuideStep[] | null;
}

/**
 * Find the best tour for a query. Uses static keyword matching first.
 * If the static match has low confidence, falls back to Claude API.
 * Returns a tour, text guide, or suggestions depending on confidence.
 */
export async function findTourForQuery(
  query: string,
  role: string,
): Promise<TourMatchResult> {
  // Reject clearly off-topic queries before any matching
  const q = query.toLowerCase().trim();
  if (q.length >= 3 && irrelevantPatterns.some((p) => q.includes(p))) {
    return { tour: null, suggestions: [], textGuide: null };
  }

  // Try static matching first
  const { tour, score } = findTourByKeywords(query, role);

  // High-confidence static match — use it immediately
  if (tour && score >= 8) {
    return {
      tour: { ...tour, steps: resolveRoutes(tour.steps) },
      suggestions: [],
      textGuide: null,
    };
  }

  // Fall back to AI for low-confidence or no static match
  try {
    const { matchTourWithAi } = await import("@/services/ai-tour.service");
    const result = await matchTourWithAi(query, role);

    // AI returned a tour match with high confidence
    if (result.tourId && result.confidence >= 0.7) {
      const aiTour = getTourById(result.tourId);
      if (aiTour) return { tour: aiTour, suggestions: [], textGuide: null };
    }

    // AI returned a text guide (no tour matched but question is platform-related)
    if (result.textGuide && result.textGuide.length > 0) {
      return { tour: null, suggestions: [], textGuide: result.textGuide };
    }

    // Medium confidence tour — return as suggestion along with others
    if (result.tourId && result.confidence >= 0.3) {
      const aiTour = getTourById(result.tourId);
      const suggestions = getSuggestedTours(query, role);
      if (aiTour && !suggestions.some((s) => s.id === aiTour.id)) {
        suggestions.unshift(aiTour);
        if (suggestions.length > 3) suggestions.pop();
      }
      return { tour: null, suggestions, textGuide: null };
    }
  } catch {
    // AI unavailable — fall through to static result
  }

  // Return static match if we had one (even low-confidence)
  if (tour) {
    return {
      tour: { ...tour, steps: resolveRoutes(tour.steps) },
      suggestions: [],
      textGuide: null,
    };
  }

  // No match — return suggestions
  const suggestions = getSuggestedTours(query, role);
  return { tour: null, suggestions, textGuide: null };
}
