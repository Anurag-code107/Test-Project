import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import {
  PartnerCompanyAdminFields,
  findMissingAdminFields,
  hasCompleteAdminDetails,
} from "../PartnerCompanyAdminFields";
import type { CompanyAdminIdentity } from "@/types/partner-company.types";

const COMPLETE: CompanyAdminIdentity = {
  adminFirstName: "TestP",
  adminLastName: "Singh",
  adminEmail: "admin@acme.test",
  adminMobileNumber: "4085556245",
  adminCountryIso2: "US",
};

/**
 * The admin block is all-or-nothing, and the server enforces the same rule with a 422. Catching it here
 * means the user is told which field is missing while the form is still open.
 */
describe("findMissingAdminFields", () => {
  it("accepts a completely empty group", () => {
    // A company with no payout intent is legitimate, and every company predating this feature looks so.
    expect(findMissingAdminFields({})).toEqual([]);
  });

  it("accepts a fully filled group", () => {
    expect(findMissingAdminFields(COMPLETE)).toEqual([]);
  });

  it("names what is missing when the group is partial", () => {
    const missing = findMissingAdminFields({ adminEmail: "admin@acme.test" });

    expect(missing).toHaveLength(4);
    expect(missing).toContain("Admin First Name");
    expect(missing).not.toContain("Admin Email");
  });

  it("treats whitespace as missing", () => {
    expect(findMissingAdminFields({ ...COMPLETE, adminLastName: "   " })).toEqual([
      "Admin Last Name",
    ]);
  });
});

describe("hasCompleteAdminDetails", () => {
  it("is false for an empty group", () => {
    expect(hasCompleteAdminDetails({})).toBe(false);
  });

  it("is true only when every field is present", () => {
    expect(hasCompleteAdminDetails(COMPLETE)).toBe(true);
    expect(hasCompleteAdminDetails({ ...COMPLETE, adminCountryIso2: "" })).toBe(
      false,
    );
  });
});

describe("PartnerCompanyAdminFields", () => {
  const noop = vi.fn();

  it("renders the five identity fields for every tenant", () => {
    render(
      <PartnerCompanyAdminFields values={{}} onChange={noop} idPrefix="add" />,
    );

    // Static by design: these are a provider contract, not tenant-configurable, so none can be absent.
    expect(screen.getByLabelText(/admin first name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/admin last name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/admin email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/admin mobile/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^country$/i)).toBeInTheDocument();
  });

  it("does not ask a client admin for the address", () => {
    render(
      <PartnerCompanyAdminFields values={{}} onChange={noop} idPrefix="add" />,
    );

    // The admin supplies these themselves after signing in — they know their own address, and a client
    // admin guessing at it is what this split exists to avoid.
    expect(screen.queryByLabelText(/^city$/i)).toBeNull();
    expect(screen.queryByLabelText(/state \/ region/i)).toBeNull();
    expect(screen.queryByLabelText(/postal code/i)).toBeNull();
  });

  it("reports edits by field key", () => {
    const onChange = vi.fn();
    render(
      <PartnerCompanyAdminFields
        values={{}}
        onChange={onChange}
        idPrefix="add"
      />,
    );

    fireEvent.change(screen.getByLabelText(/admin email/i), {
      target: { value: "admin@acme.test" },
    });

    expect(onChange).toHaveBeenCalledWith("adminEmail", "admin@acme.test");
  });

  it("pre-fills from existing values", () => {
    render(
      <PartnerCompanyAdminFields
        values={COMPLETE}
        onChange={noop}
        idPrefix="edit"
      />,
    );

    expect(screen.getByLabelText(/admin email/i)).toHaveValue(
      "admin@acme.test",
    );
    expect(screen.getByLabelText(/^country$/i)).toHaveValue("US");
  });

  it("scopes input ids by prefix so add and edit can both be mounted", () => {
    const { container } = render(
      <PartnerCompanyAdminFields values={{}} onChange={noop} idPrefix="edit" />,
    );

    expect(container.querySelector("#edit-adminEmail")).not.toBeNull();
  });
});
