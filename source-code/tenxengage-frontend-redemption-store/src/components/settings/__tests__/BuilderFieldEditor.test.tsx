import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BuilderFieldEditor } from "../BuilderFieldEditor";
import type { BuilderFieldConfigResponse } from "@/types/builder-config.types";

// jsdom doesn't provide ResizeObserver, which Radix's Dialog/Select/Tooltip
// internals need. Stub follows the same pattern as AnimatedExpandRow.test.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const useAvailableCustomFieldsMock = vi.fn(() => ({
  data: [] as never[],
  isLoading: false,
}));
vi.mock("@/hooks/useAvailableCustomFields", () => ({
  useAvailableCustomFields: () => useAvailableCustomFieldsMock(),
}));

vi.mock("@/services/builder-config.service", () => ({
  addFieldToSection: vi.fn(),
  updateField: vi.fn(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

function makeField(
  overrides: Partial<BuilderFieldConfigResponse> = {},
): BuilderFieldConfigResponse {
  return {
    id: "field-1",
    fieldKey: "test_field",
    displayName: "Test Field",
    fieldType: "MULTI_SELECT",
    helperText: null,
    isMandatory: false,
    isSystem: false,
    isEligibility: true,
    dataObjectFieldId: "do-field-1",
    dataObjectFieldName: "Test DO Field",
    dataObjectName: "Partner Data",
    valueSource: "DATA_OBJECT_FIELD",
    valueSourceConfig: null,
    supportsExcelUpload: false,
    sortOrder: 0,
    ...overrides,
  };
}

function renderWithProviders(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe("BuilderFieldEditor — Excel upload toggle gating", () => {
  beforeEach(() => {
    vi.stubGlobal("ResizeObserver", ResizeObserverStub);
    useAvailableCustomFieldsMock.mockReset();
    useAvailableCustomFieldsMock.mockReturnValue({ data: [], isLoading: false });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("enables the Excel upload toggle when editing a MULTI_SELECT field", () => {
    const field = makeField({ fieldType: "MULTI_SELECT" });
    renderWithProviders(
      <BuilderFieldEditor
        sectionId="sec-1"
        sectionKey="audience"
        incentiveType="SALES"
        existingFieldKeys={[]}
        field={field}
        onClose={vi.fn()}
      />,
    );
    const toggle = screen.getByRole("switch", {
      name: /supports excel upload/i,
    });
    expect(toggle.getAttribute("disabled")).toBeNull();
    expect(
      screen.queryByText(/only available for list-type fields/i),
    ).toBeNull();
  });

  it("enables the toggle for DROPDOWN fields too", () => {
    const field = makeField({ fieldType: "DROPDOWN" });
    renderWithProviders(
      <BuilderFieldEditor
        sectionId="sec-1"
        sectionKey="audience"
        incentiveType="SALES"
        existingFieldKeys={[]}
        field={field}
        onClose={vi.fn()}
      />,
    );
    const toggle = screen.getByRole("switch", {
      name: /supports excel upload/i,
    });
    expect(toggle.getAttribute("disabled")).toBeNull();
  });

  it("disables the toggle and surfaces helper text for a TEXT_BOX field", () => {
    const field = makeField({ fieldType: "TEXT_BOX" });
    renderWithProviders(
      <BuilderFieldEditor
        sectionId="sec-1"
        sectionKey="audience"
        incentiveType="SALES"
        existingFieldKeys={[]}
        field={field}
        onClose={vi.fn()}
      />,
    );
    const toggle = screen.getByRole("switch", {
      name: /supports excel upload/i,
    });
    expect(toggle.hasAttribute("disabled")).toBe(true);
    expect(
      screen.getByText(/only available for list-type fields/i),
    ).toBeDefined();
  });

  it("disables the toggle for NUMBER_INPUT, DATE_PICKER, and TOGGLE field types", () => {
    for (const ft of ["NUMBER_INPUT", "DATE_PICKER", "TOGGLE"] as const) {
      const field = makeField({ fieldType: ft });
      const { unmount } = renderWithProviders(
        <BuilderFieldEditor
          sectionId="sec-1"
          sectionKey="audience"
          incentiveType="SALES"
          existingFieldKeys={[]}
          field={field}
          onClose={vi.fn()}
        />,
      );
      const toggle = screen.getByRole("switch", {
        name: /supports excel upload/i,
      });
      expect(toggle.hasAttribute("disabled")).toBe(true);
      unmount();
    }
  });

  it("disables the toggle in create mode before any Data Object Field is picked", () => {
    // No `field` prop = create mode. With nothing selected, derivedFieldType
    // defaults to TEXT_BOX, so the toggle should start disabled — preventing
    // an admin from enabling a no-op flag they'd then have to clear.
    renderWithProviders(
      <BuilderFieldEditor
        sectionId="sec-1"
        sectionKey="audience"
        incentiveType="SALES"
        existingFieldKeys={[]}
        onClose={vi.fn()}
      />,
    );
    const toggle = screen.getByRole("switch", {
      name: /supports excel upload/i,
    });
    expect(toggle.hasAttribute("disabled")).toBe(true);
  });
});
