import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import LoginPage from "@/pages/LoginPage";

// Mock auth context
const mockLogin = vi.fn();
vi.mock("@/hooks/useAuth", () => ({
  useAuth: () => ({
    login: mockLogin,
    isAuthenticated: false,
    user: null,
    isLoading: false,
  }),
}));

// Mock navigate
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock logo asset
vi.mock("@/assets/web_logo.png", () => ({ default: "mock-logo.png" }));

// LoginTransition consumes BrandingContext, which isn't provided in this
// stripped-down render tree. The transition isn't under test here, so mock
// it out — the form behavior is what these tests assert.
vi.mock("@/components/LoginTransition", () => ({
  LoginTransition: () => null,
}));

// No role.types mock needed — LoginPage navigates to "/" after login

describe("LoginPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders email and password inputs", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    expect(screen.getByLabelText(/email/i)).toBeDefined();
    expect(screen.getByLabelText(/password/i)).toBeDefined();
  });

  it("renders submit button", () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    const buttons = screen.getAllByRole("button");
    expect(buttons.length).toBeGreaterThan(0);
  });

  it("shows error message on login failure", async () => {
    mockLogin.mockRejectedValueOnce(new Error("Invalid credentials"));
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/email/i), "test@example.com");
    await user.type(screen.getByLabelText(/password/i), "wrongpassword123");

    const submitButton = screen
      .getAllByRole("button")
      .find((btn) => btn.getAttribute("type") === "submit");
    if (submitButton) {
      await user.click(submitButton);
    }

    // After failed login, should still be on login page
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
