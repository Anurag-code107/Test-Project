import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { XtrmAccountStatus } from "../XtrmAccountStatus";

/**
 * The status row is the only place an admin can see why a company cannot send rewards, and the only way to
 * retry without a support ticket.
 */
describe("XtrmAccountStatus", () => {
  const noop = vi.fn();

  it("offers Connect when the company has no account at all", () => {
    const onConnect = vi.fn();
    render(
      <XtrmAccountStatus
        account={undefined}
        onConnect={onConnect}
        isConnecting={false}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /connect/i }));
    expect(onConnect).toHaveBeenCalled();
  });

  it("reads as not connected when there is no account", () => {
    render(
      <XtrmAccountStatus
        account={undefined}
        onConnect={noop}
        isConnecting={false}
      />,
    );

    expect(screen.getByText(/not connected/i)).toBeInTheDocument();
  });

  it("shows the failure reason and offers a retry when pending", () => {
    render(
      <XtrmAccountStatus
        account={{ status: "PENDING", lastError: "Could not reach XTRM" }}
        onConnect={noop}
        isConnecting={false}
      />,
    );

    // Verbatim, because "could not reach the provider" and "admin details missing" need different
    // responses from the admin.
    expect(screen.getByText(/could not reach xtrm/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
  });

  it("shows the account number and no action when connected", () => {
    render(
      <XtrmAccountStatus
        account={{
          status: "CONNECTED",
          accountNumber: "SPN26241004",
          identityLevel: "Basic",
        }}
        onConnect={noop}
        isConnecting={false}
      />,
    );

    expect(screen.getByText(/SPN26241004/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /connect|retry/i }),
    ).not.toBeInTheDocument();
  });

  it("hides a stale error once connected", () => {
    render(
      <XtrmAccountStatus
        account={{
          status: "CONNECTED",
          accountNumber: "SPN26241004",
          lastError: "Wallet lookup failed",
        }}
        onConnect={noop}
        isConnecting={false}
      />,
    );

    expect(screen.queryByText(/wallet lookup failed/i)).not.toBeInTheDocument();
  });

  it("disables the action while connecting", () => {
    render(
      <XtrmAccountStatus
        account={{ status: "PENDING" }}
        onConnect={noop}
        isConnecting
      />,
    );

    expect(screen.getByRole("button", { name: /connect|retry/i })).toBeDisabled();
  });

  it("explains why connecting is unavailable without admin details", () => {
    render(
      <XtrmAccountStatus
        account={undefined}
        onConnect={noop}
        isConnecting={false}
        canConnect={false}
      />,
    );

    expect(screen.getByRole("button", { name: /connect/i })).toBeDisabled();
    expect(screen.getByText(/company admin details/i)).toBeInTheDocument();
  });

  /**
   * Identity verification happens on XTRM's own site.
   *
   * It cannot be embedded: every xtrm.com host sends X-Frame-Options: SAMEORIGIN (checked 2026-09-01), so a
   * browser refuses to frame it and an iframe renders an empty box. These cases pin the outbound link, and
   * one of them pins the absence of a frame — the mistake is tempting enough to be worth a test.
   */
  describe("identity verification link", () => {
    const connected = { status: "CONNECTED", accountNumber: "SPN26240019" };

    it("links out to the portal in a new tab, safely", () => {
      render(
        <XtrmAccountStatus
          account={connected}
          onConnect={noop}
          isConnecting={false}
          portalUrl="https://sandbox.xtrm.com/login/"
        />,
      );

      const link = screen.getByRole("link", { name: /verify your identity/i });
      expect(link).toHaveAttribute("href", "https://sandbox.xtrm.com/login/");
      expect(link).toHaveAttribute("target", "_blank");
      // noreferrer implies noopener, but both are named so a later edit cannot drop opener protection
      // while looking correct — the opened page must never reach back through window.opener.
      expect(link.getAttribute("rel")).toContain("noopener");
      expect(link.getAttribute("rel")).toContain("noreferrer");
    });

    it("never embeds the provider", () => {
      const { container } = render(
        <XtrmAccountStatus
          account={connected}
          onConnect={noop}
          isConnecting={false}
          portalUrl="https://sandbox.xtrm.com/login/"
        />,
      );

      expect(container.querySelector("iframe")).toBeNull();
    });

    it("says the level is only what we knew at setup", () => {
      render(
        <XtrmAccountStatus
          account={{ ...connected, identityLevel: "Basic" }}
          onConnect={noop}
          isConnecting={false}
          portalUrl="https://sandbox.xtrm.com/login/"
        />,
      );

      // We read AccountIdentityLevel once, at creation, and have no way to re-read it. Presenting a stale
      // value as current would tell an admin their verification did nothing.
      expect(screen.getByText(/can't see changes you make at XTRM/i)).toBeInTheDocument();
    });

    it("offers no link when the portal is not configured", () => {
      render(
        <XtrmAccountStatus account={connected} onConnect={noop} isConnecting={false} />,
      );

      expect(screen.queryByRole("link", { name: /verify your identity/i })).toBeNull();
    });
  });

  it("shows a disabled account as disabled", () => {
    render(
      <XtrmAccountStatus
        account={{ status: "DISABLED" }}
        onConnect={noop}
        isConnecting={false}
      />,
    );

    expect(screen.getByText(/disabled/i)).toBeInTheDocument();
  });
});
