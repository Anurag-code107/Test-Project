import { PartnerIncentive } from "@/data/mockPartnerIncentives";
import { isWithinInterval, parseISO } from "date-fns";

export interface DealCriteria {
  dealValue: number;
  products: string[];
  customerType: "SMB" | "Mid-Market" | "Enterprise";
  region: "AMER" | "EMEA" | "APAC";
  closeDate: Date;
}

export interface QualifiedIncentive {
  incentive: PartnerIncentive;
  matchPercentage: number;
  estimatedReward: number;
  matchedCriteria: string[];
  unmetCriteria: string[];
}

function calculateReward(
  incentive: PartnerIncentive,
  criteria: DealCriteria,
): number {
  const baseReward = incentive.rewardAmount;

  if (incentive.rewardAmount === 0) {
    return criteria.dealValue * 0.05;
  }

  return baseReward;
}

export function qualifyDeal(
  criteria: DealCriteria,
  incentives: PartnerIncentive[],
): QualifiedIncentive[] {
  return incentives
    .filter((inc) => inc.status === "active")
    .map((incentive) => {
      const matched: string[] = [];
      const unmet: string[] = [];

      try {
        const withinPeriod = isWithinInterval(criteria.closeDate, {
          start: parseISO(incentive.startDate),
          end: parseISO(incentive.endDate),
        });

        if (!withinPeriod) {
          unmet.push("Deal close date outside incentive period");
        } else {
          matched.push("Deal close date within incentive period");
        }
      } catch {
        unmet.push("Invalid date range");
      }

      incentive.eligibility.forEach((req) => {
        const lowerReq = req.toLowerCase();

        if (lowerReq.includes("tier")) {
          // Partner tier matching - skip
        }

        if (lowerReq.includes(criteria.region.toLowerCase())) {
          matched.push("Region requirement");
        } else if (
          (lowerReq.includes("amer") ||
            lowerReq.includes("emea") ||
            lowerReq.includes("apac")) &&
          !matched.includes("Region requirement")
        ) {
          unmet.push(req);
        }

        if (lowerReq.includes("smb") && criteria.customerType === "SMB") {
          matched.push("Customer type requirement");
        } else if (
          lowerReq.includes("enterprise") &&
          criteria.customerType === "Enterprise"
        ) {
          matched.push("Customer type requirement");
        } else if (
          lowerReq.includes("mid-market") &&
          criteria.customerType === "Mid-Market"
        ) {
          matched.push("Customer type requirement");
        } else if (
          (lowerReq.includes("smb") ||
            lowerReq.includes("enterprise") ||
            lowerReq.includes("mid-market")) &&
          !matched.includes("Customer type requirement")
        ) {
          unmet.push(req);
        }

        if (criteria.products.some((p) => lowerReq.includes(p.toLowerCase()))) {
          matched.push("Product requirement");
        }

        if (lowerReq.includes("$") || lowerReq.includes("minimum")) {
          const minValue = parseFloat(lowerReq.replace(/[^0-9]/g, ""));
          if (!isNaN(minValue) && criteria.dealValue >= minValue) {
            matched.push("Minimum deal value requirement");
          } else if (!isNaN(minValue)) {
            unmet.push(req);
          }
        }
      });

      const totalCriteria = incentive.eligibility.length + 1;
      const matchPercentage = (matched.length / totalCriteria) * 100;

      return {
        incentive,
        matchPercentage: Math.round(matchPercentage),
        estimatedReward: calculateReward(incentive, criteria),
        matchedCriteria: matched,
        unmetCriteria: unmet,
      };
    })
    .filter((result) => result.matchPercentage > 0)
    .sort((a, b) => b.matchPercentage - a.matchPercentage);
}
