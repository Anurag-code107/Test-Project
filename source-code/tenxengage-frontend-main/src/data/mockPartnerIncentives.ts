export interface PartnerIncentive {
  id: string;
  name: string;
  engagementType:
    | "Sales Incentive"
    | "Training Incentive"
    | "Activity Incentive"
    | "Multistage Incentive";
  description: string;
  objective: string;
  eligibility: string[];
  proofRequirements: string[];
  rewardAmount: number;
  rewardCap: number;
  rewardMessage: string;
  rewardCurrencies: string[];
  startDate: string;
  endDate: string;
  status: "active" | "ending-soon" | "closed";
  claimersPerPo?: number;
}

export const mockPartnerIncentives: PartnerIncentive[] = [
  {
    id: "inc-001",
    name: "Q1 Product Launch Promo",
    engagementType: "Sales Incentive",
    description:
      "Drive adoption of new enterprise security suite through partner promotions with exclusive early access and comprehensive support resources.",
    objective:
      "Close deals featuring the new enterprise security suite product line during launch window.",
    eligibility: [
      "All certified partners",
      "Product training completion required",
      "Active in any region",
    ],
    proofRequirements: [
      "Signed customer contract with SKU details",
      "Deal registration confirmation",
      "Proof of product deployment (optional)",
    ],
    rewardAmount: 2500,
    rewardCap: 150000,
    rewardMessage: "Earn up to $3,000",
    rewardCurrencies: ["cash"],
    startDate: "2025-01-01",
    endDate: "2025-03-31",
    status: "active",
    claimersPerPo: 1,
  },
  {
    id: "inc-002",
    name: "Summer Sales Blitz",
    engagementType: "Sales Incentive",
    description:
      "Summer sales promotion with tiered rewards targeting high-velocity deal closures across all product lines.",
    objective:
      "Close 100+ deals during summer quarter with accelerated payout structure.",
    eligibility: [
      "All partner tiers",
      "Deal size $10K–$50K",
      "Active partner portal access",
    ],
    proofRequirements: [
      "Signed agreement or PO",
      "Deal registration confirmation",
      "Payment proof",
    ],
    rewardAmount: 1500,
    rewardCap: 200000,
    rewardMessage: "Earn up to $4,000",
    rewardCurrencies: ["cash", "points"],
    startDate: "2025-06-01",
    endDate: "2025-08-31",
    status: "active",
    claimersPerPo: 5,
  },
  {
    id: "inc-003",
    name: "EMEA Regional Push",
    engagementType: "Sales Incentive",
    description:
      "Targeted promotional campaign for EMEA expansion with focus on UK, Germany, and France. Includes localized marketing assets and dedicated partner support.",
    objective:
      "Drive regional revenue growth through localized co-marketing campaigns and partner enablement.",
    eligibility: [
      "Active in EMEA region",
      "Completed EMEA compliance training",
      "Minimum $25K quarterly revenue",
    ],
    proofRequirements: [
      "Campaign spend report (invoices, receipts)",
      "Lead generation report with contact details",
      "Post-campaign summary with metrics",
    ],
    rewardAmount: 5000,
    rewardCap: 100000,
    rewardMessage: "Earn up to $2,000",
    rewardCurrencies: ["cash", "tickets"],
    startDate: "2025-02-01",
    endDate: "2025-04-30",
    status: "active",
    claimersPerPo: 3,
  },
  {
    id: "inc-014",
    name: "APAC Market Entry",
    engagementType: "Sales Incentive",
    description:
      "Launch promotion for new APAC partner network covering Singapore, Japan, and Australia. New partners receive enhanced onboarding incentives and first-deal bonuses.",
    objective:
      "Establish presence in 3+ new APAC markets with local partner events and co-marketing.",
    eligibility: [
      "APAC-based partners",
      "Completed onboarding training",
      "Regional market expertise",
    ],
    proofRequirements: [
      "Event plan and budget",
      "Attendee list and lead capture",
      "Post-event report with photos",
      "Press coverage or social media reach metrics",
    ],
    rewardAmount: 3000,
    rewardCap: 175000,
    rewardMessage: "Earn up to $3,500",
    rewardCurrencies: ["cash", "points", "credits"],
    startDate: "2025-04-01",
    endDate: "2025-06-30",
    status: "active",
    claimersPerPo: 1,
  },
  {
    id: "inc-015",
    name: "Holiday Season Promo",
    engagementType: "Sales Incentive",
    description:
      "End-of-year promotional push with accelerated deal registration. Double points on all enterprise deals closed before year-end with exclusive holiday bundle pricing.",
    objective:
      "Maximize year-end revenue with accelerated deal closures and bundle promotions.",
    eligibility: [
      "All partner tiers",
      "Minimum $50K annual revenue",
      "Active deal registration",
    ],
    proofRequirements: [
      "Quarterly revenue report",
      "Invoice copies for verification",
      "Deal registration confirmation",
    ],
    rewardAmount: 0,
    rewardCap: 250000,
    rewardMessage: "Earn up to $5,000",
    rewardCurrencies: ["cash", "points"],
    startDate: "2024-11-01",
    endDate: "2024-12-31",
    status: "closed",
    claimersPerPo: 10,
  },
  {
    id: "inc-040",
    name: "Product Certification Training",
    engagementType: "Training Incentive",
    description:
      "Complete core product certification modules covering architecture, deployment, and troubleshooting. Certification valid for 12 months with renewal pathway. Required for Tier 1 partner status.",
    objective:
      "Certify partner teams on core product suite to improve solution selling capability.",
    eligibility: [
      "All certified partners",
      "USA, Canada, EMEAR, APJC regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Complete 3 of 4 assigned training courses",
      "Courses tracked automatically via LMS",
    ],
    rewardAmount: 500,
    rewardCap: 50000,
    rewardMessage: "Earn 500 points",
    rewardCurrencies: ["points", "credits"],
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    status: "active",
  },
  {
    id: "inc-041",
    name: "Advanced Solutions Selling",
    engagementType: "Training Incentive",
    description:
      "Master consultative selling techniques for complex enterprise solutions. Covers discovery frameworks, ROI modeling, and executive presentation skills. Graduates earn Advanced Seller badge.",
    objective: "Elevate partner sales methodology for enterprise deal pursuit.",
    eligibility: [
      "Partner Sellers in USA and EMEAR",
      "Reseller partner type",
      "Prior product training completion recommended",
    ],
    proofRequirements: [
      "Complete all 3 assigned sales methodology courses",
      "Courses tracked automatically via LMS",
    ],
    rewardAmount: 750,
    rewardCap: 35000,
    rewardMessage: "Earn 750 points",
    rewardCurrencies: ["points", "credits"],
    startDate: "2025-02-15",
    endDate: "2025-08-31",
    status: "active",
  },
  {
    id: "inc-011",
    name: "Case Study Submission",
    engagementType: "Activity Incentive",
    description:
      "Rewards for submitting approved customer case studies showcasing implementation success. Marketing team provides templates and editorial support. Published case studies earn ongoing referral bonuses.",
    objective:
      "Generate compelling customer case studies that demonstrate product value and drive prospect engagement.",
    eligibility: [
      "All certified partners",
      "USA, Canada, EMEAR, APJC, LATAM regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Customer Interview Recording & Transcript",
      "Draft Case Study Document using provided template",
      "Signed Customer Approval Form",
    ],
    rewardAmount: 1500,
    rewardCap: 40000,
    rewardMessage: "Earn $1,500",
    rewardCurrencies: ["cash", "points"],
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    status: "active",
  },
  {
    id: "inc-012",
    name: "Event Attendance Proof",
    engagementType: "Activity Incentive",
    description:
      "Submit proof of attendance for partner events including photos and session notes. Covers regional roadshows, virtual summits, and industry conferences. Bonus rewards for presenting or speaking roles.",
    objective:
      "Drive partner participation in key industry and vendor events to strengthen relationships and knowledge.",
    eligibility: [
      "All certified partners",
      "USA, Canada, EMEAR regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Event Registration Confirmation Email",
      "Badge Photo & Event Photos",
      "Session Notes from at least 2 sessions",
    ],
    rewardAmount: 400,
    rewardCap: 30000,
    rewardMessage: "Earn 400 points",
    rewardCurrencies: ["points", "tickets"],
    startDate: "2025-02-01",
    endDate: "2025-11-30",
    status: "active",
  },
  {
    id: "inc-023",
    name: "Reference Call Documentation",
    engagementType: "Activity Incentive",
    description:
      "Document successful customer reference calls with prospect feedback summary. Includes recording consent and call quality scoring. Top references featured in partner success stories.",
    objective:
      "Build a library of customer reference calls to accelerate prospect decision-making.",
    eligibility: [
      "All certified partners",
      "USA, Canada, EMEAR regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Reference Call Recording (with consent form)",
      "Prospect Feedback Summary Document",
    ],
    rewardAmount: 500,
    rewardCap: 25000,
    rewardMessage: "Earn $500",
    rewardCurrencies: ["cash", "credits"],
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    status: "active",
  },
  {
    id: "inc-024",
    name: "Implementation Success Stories",
    engagementType: "Activity Incentive",
    description:
      "Submit proof of successful implementations with customer testimonials and metrics. Documentation must include go-live confirmation and satisfaction survey. Exceptional stories eligible for annual awards.",
    objective:
      "Capture and showcase successful customer implementations to build credibility and attract new business.",
    eligibility: [
      "All certified partners",
      "USA, LATAM, APJC regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Signed Go-Live Confirmation Document",
      "Completed Customer Satisfaction Survey",
      "Implementation Metrics Report & ROI Analysis",
    ],
    rewardAmount: 2000,
    rewardCap: 35000,
    rewardMessage: "Earn $2,000",
    rewardCurrencies: ["cash", "points", "tickets"],
    startDate: "2025-03-01",
    endDate: "2025-08-31",
    status: "active",
  },
  {
    id: "inc-100",
    name: "Partner Certification Journey",
    engagementType: "Multistage Incentive",
    description:
      "Complete end-to-end partner certification program combining Product Certification Training, Q1 Product Launch Promo, and Case Study Submission. Sequential completion required.",
    objective:
      "Achieve full partner certification status through training, sales execution, and customer documentation.",
    eligibility: [
      "All certified partners",
      "USA, Canada, EMEAR, APJC regions",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Complete all 3 journey stages in order",
      "Each stage has its own proof requirements",
    ],
    rewardAmount: 5000,
    rewardCap: 500000,
    rewardMessage: "Earn up to $5,000",
    rewardCurrencies: ["cash", "points", "credits"],
    startDate: "2025-01-01",
    endDate: "2025-12-31",
    status: "active",
  },
  {
    id: "inc-102",
    name: "Partner Excellence Track",
    engagementType: "Multistage Incentive",
    description:
      "Comprehensive excellence program combining Advanced Solutions Selling training, EMEA Regional Push sales promotion, and Reference Call Documentation.",
    objective:
      "Elevate partner capabilities through advanced training, regional sales execution, and customer reference building.",
    eligibility: [
      "USA and EMEAR regions",
      "Reseller partner type",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Complete all 3 journey stages in order",
      "Each stage has its own proof requirements",
    ],
    rewardAmount: 4000,
    rewardCap: 275000,
    rewardMessage: "Earn up to $4,000",
    rewardCurrencies: ["cash", "tickets"],
    startDate: "2025-03-01",
    endDate: "2025-09-30",
    status: "active",
  },
  {
    id: "inc-103",
    name: "Channel Accelerator Program",
    engagementType: "Multistage Incentive",
    description:
      "Fast-track program combining Product Certification Training, APAC Market Entry promotion, and Implementation Success Stories. Stages can be completed in any order.",
    objective:
      "Accelerate partner growth through product mastery, APAC expansion, and implementation documentation.",
    eligibility: [
      "USA, Canada, APJC, LATAM regions",
      "All partner types",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Complete all 3 journey stages in any order",
      "Each stage has its own proof requirements",
    ],
    rewardAmount: 6000,
    rewardCap: 400000,
    rewardMessage: "Earn up to $6,000",
    rewardCurrencies: ["cash", "points", "credits", "tickets"],
    startDate: "2025-02-15",
    endDate: "2025-08-15",
    status: "active",
  },
  {
    id: "inc-104",
    name: "Strategic Partner Onboarding",
    engagementType: "Multistage Incentive",
    description:
      "End-to-end onboarding journey combining Advanced Solutions Selling, Q1 Product Launch Promo, and Case Study Submission. No additional journey-level rewards — partners earn from each incentive independently.",
    objective:
      "Onboard new strategic partners through a structured growth path covering training, sales, and documentation.",
    eligibility: [
      "USA, Canada, EMEAR regions",
      "Reseller and Distributor partner types",
      "Company Admin and Partner Seller roles",
    ],
    proofRequirements: [
      "Complete all 3 journey stages in order",
      "Each stage has its own proof requirements",
    ],
    rewardAmount: 0,
    rewardCap: 0,
    rewardMessage: "Earn rewards from each stage",
    rewardCurrencies: ["cash", "points", "credits"],
    startDate: "2025-04-01",
    endDate: "2025-10-31",
    status: "active",
  },
];
