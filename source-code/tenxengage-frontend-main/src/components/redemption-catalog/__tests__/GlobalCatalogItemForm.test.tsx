import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GlobalCatalogItemForm } from "../GlobalCatalogItemForm";

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useCreateCatalogItem: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateCatalogItem: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      {children}
    </QueryClientProvider>
  );
}

describe("GlobalCatalogItemForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders all fields for NON_CASH category", () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    expect(screen.getByLabelText(/name/i)).toBeDefined();
    expect(screen.getByLabelText(/description/i)).toBeDefined();
    expect(screen.getByLabelText(/currency/i)).toBeDefined();
    expect(screen.getByLabelText(/min redemption amount/i)).toBeDefined();
    expect(screen.getByLabelText(/processing mode/i)).toBeDefined();
    expect(screen.getByLabelText(/provider item id/i)).toBeDefined();
    expect(screen.getByText(/geographic scope/i)).toBeDefined();
    expect(screen.getByTestId("isReturnable-checkbox")).toBeDefined();
  });

  it("disables isReturnable for CASH category", async () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    const categoryTrigger = screen.getByRole("combobox", { name: /category/i });
    fireEvent.click(categoryTrigger);

    await waitFor(() => {
      const cashOption = screen.getByRole("option", { name: /^cash$/i });
      fireEvent.click(cashOption);
    });

    await waitFor(() => {
      const checkbox = screen.getByTestId("isReturnable-checkbox");
      expect(checkbox).toHaveAttribute("disabled");
    });
  });

  it("shows validation error when name is empty", async () => {
    render(<GlobalCatalogItemForm onSave={vi.fn()} />, { wrapper });

    const submitButton = screen.getByRole("button", { name: /create item/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText(/name is required/i)).toBeDefined();
    });
  });
});
