export interface PartnerOption {
  id: string;
  name: string;
  type: string;
  region: string;
  tier: string;
}

export const regions = ["USA", "CA", "EMEAR", "LATAM", "APJC"] as const;

export const partnerTypes = ["Reseller", "Distributor", "OEM"] as const;

export const partnerTiers = ["Gold", "Silver", "Bronze", "Platinum"] as const;

export const partnerRoles = ["Company Admin", "Partner Seller"] as const;

export const timezones = [
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "America/Toronto",
  "Europe/London",
  "Europe/Paris",
  "Europe/Berlin",
  "Asia/Tokyo",
  "Asia/Singapore",
  "Asia/Shanghai",
  "Australia/Sydney",
] as const;

export const rewardCurrencies = [
  {
    id: "cash",
    label: "Cash",
    type: "MONETARY" as const,
    unit: "",
    isCurrencyFormatted: true,
    conversionRate: 1,
  },
  {
    id: "points",
    label: "Points",
    type: "MONETARY" as const,
    unit: "pts",
    isCurrencyFormatted: false,
    conversionRate: 200,
  },
  {
    id: "tickets",
    label: "Tickets",
    type: "NON_MONETARY" as const,
    unit: "tickets",
    isCurrencyFormatted: false,
  },
  {
    id: "credits",
    label: "Credits",
    type: "NON_MONETARY" as const,
    unit: "credits",
    isCurrencyFormatted: false,
  },
];

export const activityCategories = [
  { id: "CONTENT_CREATION", name: "Content Creation" },
  { id: "COMPLIANCE", name: "Compliance" },
  { id: "EVENT_PARTICIPATION", name: "Event Participation" },
  { id: "CUSTOMER_ENGAGEMENT", name: "Customer Engagement" },
  { id: "IMPLEMENTATION", name: "Implementation" },
];
