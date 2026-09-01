import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DynamicFieldRenderer } from "../DynamicFieldRenderer";
import type { BuilderFieldConfigResponse } from "@/types/builder-config.types";

const useFieldValuesMock = vi.fn(() => ({ data: [] as { value: string; label: string }[] }));
vi.mock("@/hooks/useBuilderConfig", () => ({
  useFieldValues: () => useFieldValuesMock(),
}));

const toastMock = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: toastMock }),
}));

const xlsxReadMock = vi.fn();
const xlsxSheetToJsonMock = vi.fn();
vi.mock("xlsx", () => ({
  read: (...args: unknown[]) => xlsxReadMock(...args),
  utils: {
    sheet_to_json: (...args: unknown[]) => xlsxSheetToJsonMock(...args),
  },
}));

function createField(
  overrides: Partial<BuilderFieldConfigResponse> = {},
): BuilderFieldConfigResponse {
  return {
    id: "field-1",
    fieldKey: "test_field",
    displayName: "Test Field",
    fieldType: "TEXT_BOX",
    helperText: null,
    isMandatory: false,
    isSystem: false,
    isEligibility: false,
    dataObjectFieldId: null,
    dataObjectFieldName: null,
    dataObjectName: null,
    valueSource: null,
    valueSourceConfig: null,
    supportsExcelUpload: false,
    sortOrder: 0,
    ...overrides,
  };
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

describe("DynamicFieldRenderer", () => {
  beforeEach(() => {
    useFieldValuesMock.mockReset();
    useFieldValuesMock.mockReturnValue({ data: [] });
    toastMock.mockReset();
    xlsxReadMock.mockReset();
    xlsxSheetToJsonMock.mockReset();
  });

  it("renders TEXT_BOX as input", () => {
    const field = createField({ fieldType: "TEXT_BOX", displayName: "Name" });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    expect(screen.getByPlaceholderText("Name")).toBeDefined();
  });

  it("renders TOGGLE as switch", () => {
    const field = createField({
      fieldType: "TOGGLE",
      displayName: "Is Active",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value={false} onChange={vi.fn()} />,
    );
    expect(screen.getByRole("switch")).toBeDefined();
  });

  it("shows mandatory indicator", () => {
    const field = createField({
      isMandatory: true,
      displayName: "Required Field",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    expect(screen.getByText("*")).toBeDefined();
  });

  it("shows helper text", () => {
    const field = createField({
      helperText: "Enter a valid name for this incentive",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    expect(
      screen.getByText("Enter a valid name for this incentive"),
    ).toBeDefined();
  });

  it("renders NUMBER_INPUT as number field", () => {
    const field = createField({
      fieldType: "NUMBER_INPUT",
      displayName: "Amount",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    const input = screen.getByPlaceholderText("Amount");
    expect(input.getAttribute("type")).toBe("number");
  });

  it("renders DATE_PICKER as date input", () => {
    const field = createField({
      fieldType: "DATE_PICKER",
      displayName: "Start Date",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    const input = document.querySelector('input[type="date"]');
    expect(input).not.toBeNull();
  });

  it("does not show mandatory indicator when isMandatory is false", () => {
    const field = createField({
      isMandatory: false,
      displayName: "Optional Field",
    });
    renderWithProviders(
      <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
    );
    expect(screen.queryByText("*")).toBeNull();
  });

  describe("Excel upload affordance (BUG-058)", () => {
    it("renders the upload button on a MULTI_SELECT field when supportsExcelUpload is true", () => {
      const field = createField({
        fieldType: "MULTI_SELECT",
        displayName: "Regions",
        supportsExcelUpload: true,
        valueSource: "DATA_OBJECT_FIELD",
      });
      useFieldValuesMock.mockReturnValue({
        data: [{ value: "EMEA", label: "EMEA" }],
      });
      renderWithProviders(
        <DynamicFieldRenderer field={field} value={[]} onChange={vi.fn()} />,
      );
      expect(
        screen.getByRole("button", { name: /upload excel file for regions/i }),
      ).toBeDefined();
    });

    it("renders the upload button on a DROPDOWN field when supportsExcelUpload is true", () => {
      const field = createField({
        fieldType: "DROPDOWN",
        displayName: "Tier",
        supportsExcelUpload: true,
        valueSource: "DATA_OBJECT_FIELD",
      });
      useFieldValuesMock.mockReturnValue({
        data: [{ value: "Gold", label: "Gold" }],
      });
      renderWithProviders(
        <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
      );
      expect(
        screen.getByRole("button", { name: /upload excel file for tier/i }),
      ).toBeDefined();
    });

    it("does NOT render the upload button when supportsExcelUpload is false", () => {
      const field = createField({
        fieldType: "MULTI_SELECT",
        displayName: "Regions",
        supportsExcelUpload: false,
        valueSource: "DATA_OBJECT_FIELD",
      });
      renderWithProviders(
        <DynamicFieldRenderer field={field} value={[]} onChange={vi.fn()} />,
      );
      expect(
        screen.queryByRole("button", { name: /upload excel file for regions/i }),
      ).toBeNull();
    });

    it("does NOT render the upload button on incompatible field types even when the flag is true", () => {
      // The toggle is meaningless for free-form inputs — there's no canonical
      // option list to map a sheet onto. Confirms the flag is treated as a
      // no-op rather than rendering a non-functional button.
      const field = createField({
        fieldType: "TEXT_BOX",
        displayName: "Notes",
        supportsExcelUpload: true,
      });
      renderWithProviders(
        <DynamicFieldRenderer field={field} value="" onChange={vi.fn()} />,
      );
      expect(
        screen.queryByRole("button", { name: /upload excel file for notes/i }),
      ).toBeNull();
    });

    it("appends matched sheet values to a MULTI_SELECT, drops a header row, and skips unknown values", async () => {
      const field = createField({
        fieldType: "MULTI_SELECT",
        displayName: "Regions",
        supportsExcelUpload: true,
        valueSource: "DATA_OBJECT_FIELD",
      });
      useFieldValuesMock.mockReturnValue({
        data: [
          { value: "EMEA", label: "EMEA" },
          { value: "AMER", label: "AMER" },
          { value: "APAC", label: "APAC" },
        ],
      });
      // Sheet contents: a header row, two real values that match (case-insensitively),
      // one duplicate, and one value that doesn't exist in the option list.
      xlsxReadMock.mockReturnValue({
        SheetNames: ["Sheet1"],
        Sheets: { Sheet1: { "!ref": "A1:A5" } },
      });
      xlsxSheetToJsonMock.mockReturnValue([
        ["Regions"], // header row — should be dropped
        ["emea"],
        ["AMER"],
        ["EMEA"], // case-insensitive duplicate of "emea"
        ["Antarctica"], // unknown — should be reported as skipped
      ]);

      const onChange = vi.fn();
      renderWithProviders(
        <DynamicFieldRenderer field={field} value={[]} onChange={onChange} />,
      );

      const button = screen.getByRole("button", {
        name: /upload excel file for regions/i,
      });

      // The component creates a hidden <input type="file"> on click rather than
      // rendering one in the tree. Stub the click so we can grab the input and
      // simulate selection without invoking the native file picker.
      let createdInput: HTMLInputElement | null = null;
      const realCreate = document.createElement.bind(document);
      const createSpy = vi
        .spyOn(document, "createElement")
        .mockImplementation((tagName: string) => {
          const el = realCreate(tagName) as HTMLElement;
          if (tagName === "input") {
            createdInput = el as HTMLInputElement;
            // Block the click so the native picker doesn't try to fire.
            (el as HTMLInputElement).click = () => {};
          }
          return el as HTMLElement;
        });

      fireEvent.click(button);
      createSpy.mockRestore();

      if (!createdInput) throw new Error("File input was not created");
      const input = createdInput as HTMLInputElement;

      const fakeFile = {
        arrayBuffer: vi.fn().mockResolvedValue(new ArrayBuffer(8)),
      } as unknown as File;
      Object.defineProperty(input, "files", {
        value: [fakeFile],
        configurable: true,
      });
      // Trigger the onchange handler the component installed. Wrapped in act
      // because the parse path also flips the internal isParsing flag.
      await act(async () => {
        await input.onchange?.({ target: input } as unknown as Event);
      });

      await waitFor(() => expect(onChange).toHaveBeenCalled());
      expect(onChange).toHaveBeenCalledWith(["EMEA", "AMER"]);
      expect(toastMock).toHaveBeenCalledWith(
        expect.objectContaining({
          title: expect.stringMatching(/added 2 values/i),
          description: expect.stringContaining("not recognized"),
        }),
      );
    });
  });
});
