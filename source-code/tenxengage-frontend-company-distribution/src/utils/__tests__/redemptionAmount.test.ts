import { describe, it, expect } from "vitest";
import { validateRedemptionAmount } from "@/utils/redemptionAmount";

const CASH = { currencyId: "cash", min: "20", max: "2000", availableBalance: "5000" };

describe("validateRedemptionAmount", () => {
  describe("empty / non-numeric input", () => {
    it("rejects an empty value", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "" })).toBe("Enter an amount.");
    });

    it("rejects whitespace only", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "   " })).toBe("Enter an amount.");
    });

    it("rejects a non-numeric value", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "abc" })).toBe("Enter a valid amount.");
    });
  });

  // The API serializes these BigDecimal fields as JSON numbers even though the contract declares
  // decimal strings, so numeric input reaches the validator in the real app. Calling a string
  // method on it (e.g. .trim()) throws during render and blanks the page.
  describe("numeric input (real API payload shape)", () => {
    it("accepts a number amount without throwing", () => {
      expect(() =>
        validateRedemptionAmount({ ...CASH, amount: 20 }),
      ).not.toThrow();
      expect(validateRedemptionAmount({ ...CASH, amount: 20 })).toBeNull();
    });

    it("applies the bounds to a number amount", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: 19 })).toBe("Amount must be at least $20.");
      expect(validateRedemptionAmount({ ...CASH, amount: 2001 })).toBe("Amount must be at most $2,000.");
      expect(validateRedemptionAmount({ ...CASH, amount: 0 })).toBe("Amount must be greater than 0.");
    });

    it("handles numeric bounds and balance too", () => {
      expect(
        validateRedemptionAmount({ amount: 600, currencyId: "cash", min: 20, max: 2000, availableBalance: 500 }),
      ).toBe("Amount exceeds your available balance of $500.");
    });
  });

  describe("positivity", () => {
    it("rejects zero", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "0" })).toBe(
        "Amount must be greater than 0.",
      );
    });

    it("rejects a negative amount", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "-50" })).toBe(
        "Amount must be greater than 0.",
      );
    });

    it("rejects zero even when there are no bounds at all", () => {
      expect(validateRedemptionAmount({ amount: "0", currencyId: "cash" })).toBe(
        "Amount must be greater than 0.",
      );
    });
  });

  describe("catalog bounds", () => {
    it("rejects below the minimum, naming the formatted floor", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "19" })).toBe(
        "Amount must be at least $20.",
      );
    });

    it("rejects above the maximum, naming the formatted ceiling", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "2001" })).toBe(
        "Amount must be at most $2,000.",
      );
    });

    it("accepts both inclusive bounds", () => {
      expect(validateRedemptionAmount({ ...CASH, amount: "20" })).toBeNull();
      expect(validateRedemptionAmount({ ...CASH, amount: "2000" })).toBeNull();
    });

    it("applies no ceiling when max is null (open-value / legacy item)", () => {
      expect(
        validateRedemptionAmount({ ...CASH, amount: "4000", max: null }),
      ).toBeNull();
    });

    it("pins a FIXED denomination — min == max leaves exactly one valid amount", () => {
      const fixed = { currencyId: "cash", min: "50", max: "50", availableBalance: "5000" };
      expect(validateRedemptionAmount({ ...fixed, amount: "50" })).toBeNull();
      expect(validateRedemptionAmount({ ...fixed, amount: "49" })).toBe(
        "Amount must be at least $50.",
      );
      expect(validateRedemptionAmount({ ...fixed, amount: "51" })).toBe(
        "Amount must be at most $50.",
      );
    });

    it("formats non-cash currencies with their own unit", () => {
      expect(
        validateRedemptionAmount({ amount: "10", currencyId: "points", min: "50" }),
      ).toBe("Amount must be at least 50 pts.");
    });
  });

  describe("available balance", () => {
    it("rejects more than the wallet holds", () => {
      expect(
        validateRedemptionAmount({ ...CASH, amount: "600", availableBalance: "500" }),
      ).toBe("Amount exceeds your available balance of $500.");
    });

    it("accepts spending the whole balance", () => {
      expect(
        validateRedemptionAmount({ ...CASH, amount: "500", availableBalance: "500" }),
      ).toBeNull();
    });

    it("skips the balance check when no wallet is known", () => {
      expect(
        validateRedemptionAmount({ ...CASH, amount: "600", availableBalance: null }),
      ).toBeNull();
    });

    it("prefers the range message when the amount breaks both rules", () => {
      expect(
        validateRedemptionAmount({ ...CASH, amount: "9000", availableBalance: "500" }),
      ).toBe("Amount must be at most $2,000.");
    });
  });
});
