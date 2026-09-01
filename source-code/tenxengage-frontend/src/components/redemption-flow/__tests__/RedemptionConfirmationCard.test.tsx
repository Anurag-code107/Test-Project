import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RedemptionConfirmationCard } from "@/components/redemption-flow/RedemptionConfirmationCard";
import type { RedemptionRequestDetailResponse } from "@/types/redemption-flow.types";

const BASE: RedemptionRequestDetailResponse = {
  id: "req-1",
  status: "RESERVED",
  amount: "50",
  currencyId: "points",
  catalogItemId: "item-1",
  catalogItemName: "Amazon Gift Card",
  processingMode: "INSTANT",
  category: "NON_CASH",
  walletType: "INDIVIDUAL",
  estimatedDelivery: "Within 24 hours",
  submittedAt: "2026-05-22T00:00:00Z",
  createdAt: "2026-05-22T00:00:00Z",
  updatedAt: "2026-05-22T00:00:00Z",
};

describe("RedemptionConfirmationCard", () => {
  it("renders_instantMode_withDeliveryDate", () => {
    render(<RedemptionConfirmationCard redemption={BASE} />);
    expect(screen.getByText("Redemption Submitted")).toBeDefined();
    expect(screen.getByTestId("delivery-text").textContent).toContain(
      "Estimated delivery: Within 24 hours",
    );
  });

  it("renders_batchMode_withScheduledDate", () => {
    const redemption: RedemptionRequestDetailResponse = {
      ...BASE,
      processingMode: "BATCH",
      scheduledBatchDate: "2026-05-25",
    };
    render(<RedemptionConfirmationCard redemption={redemption} />);
    expect(screen.getByTestId("delivery-text").textContent).toContain(
      "Scheduled for processing on May 25, 2026",
    );
  });

  it("renders_approvalRequired_withPendingMessage", () => {
    const redemption: RedemptionRequestDetailResponse = {
      ...BASE,
      status: "PENDING_APPROVAL",
      processingMode: "APPROVAL_REQUIRED",
    };
    render(<RedemptionConfirmationCard redemption={redemption} />);
    expect(screen.getByTestId("delivery-text").textContent).toContain(
      "Your redemption is pending approval",
    );
  });
});
