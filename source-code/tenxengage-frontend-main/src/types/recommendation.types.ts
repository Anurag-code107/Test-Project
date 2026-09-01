export type RecommendationType = "TRAINING" | "INCENTIVE";

export interface TrainingRecommendationResponse {
  courseId: string;
  courseName: string;
  courseDescription: string;
  courseCategory: string;
  productCategory: string;
  score: number;
  rank: number;
  reasonCode: string;
  reasonSummary: string;
  rewardAmount: number;
  rewardCurrencyId: string | null;
  daysUntilQuarterEnd: number;
}

export interface IncentiveRecommendationResponse {
  incentiveId: string;
  incentiveName: string;
  incentiveType: string;
  description: string;
  startDate: string | null;
  endDate: string | null;
  score: number;
  rank: number;
  reasonCode: string;
  reasonSummary: string;
  budgetRemainingPct: number;
  rewardCurrency: string | null;
  rewardAmount: number;
}

export interface RecommendationConfigResponse {
  id: string | null;
  trainingEnabled: boolean;
  incentiveEnabled: boolean;
  maxTrainingRecommendations: number;
  maxIncentiveRecommendations: number;
  rewardCurrencyId: string | null;
  trainingCompletionReward: number;
  incentiveCompletionReward: number;
}

export interface UpdateRecommendationConfigRequest {
  trainingEnabled: boolean;
  incentiveEnabled: boolean;
  maxTrainingRecommendations: number;
  maxIncentiveRecommendations: number;
  rewardCurrencyId: string | null;
  trainingCompletionReward: number;
  incentiveCompletionReward: number;
}

export interface RecommendationCompletionResponse {
  rewardEarned: boolean;
  rewardAmount: number;
  rewardCurrencyId: string | null;
}
