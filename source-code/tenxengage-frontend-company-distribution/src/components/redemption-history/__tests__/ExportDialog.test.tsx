import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/hooks/redemption-history/useTriggerExport", () => ({
  useTriggerExport: vi.fn(),
}));
vi.mock("@/hooks/redemption-history/useExportJob", () => ({
  useExportJob: vi.fn(),
}));
vi.mock("@/services/redemption-history/redemption-history.service", () => ({
  getExportJobDownload: vi.fn(),
  triggerExport: vi.fn(),
  getPersonalRedemptions: vi.fn(),
  getRedemptionDetail: vi.fn(),
  getExportJob: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

import { useTriggerExport } from "@/hooks/redemption-history/useTriggerExport";
import { useExportJob } from "@/hooks/redemption-history/useExportJob";
import { toast } from "sonner";
import { ExportDialog } from "@/components/redemption-history/ExportDialog";

const mockUseTriggerExport = vi.mocked(useTriggerExport);
const mockUseExportJob = vi.mocked(useExportJob);
const mockToastError = vi.mocked(toast.error);

function mockAxiosError(status: number) {
  const err = new Error(`Request failed with status code ${status}`);
  (err as unknown as Record<string, unknown>).isAxiosError = true;
  (err as unknown as Record<string, unknown>).response = { status };
  return err;
}

beforeEach(() => {
  mockUseExportJob.mockReturnValue({ data: undefined } as unknown as ReturnType<typeof useExportJob>);
});

describe("ExportDialog", () => {
  it("idle state: shows format selector, Export, and Cancel buttons", () => {
    mockUseTriggerExport.mockReturnValue({ mutate: vi.fn(), isPending: false } as unknown as ReturnType<typeof useTriggerExport>);

    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    expect(screen.getByText("Export transactions")).toBeDefined();
    expect(screen.getByRole("button", { name: "Export" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDefined();
  });

  it("clicking Export calls useTriggerExport mutation", async () => {
    const mutate = vi.fn();
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));
    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ format: 'CSV' }),
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    );
  });

  it("202 response → transitions to polling state with 'Generating your export…'", async () => {
    const mutate = vi.fn().mockImplementation((_vars: unknown, opts: { onSuccess: (r: unknown) => void }) => {
      opts.onSuccess({ kind: 'async', job: { id: 'job-001', status: 'PENDING' } });
    });
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));
    expect(screen.getByText("Generating your export…")).toBeDefined();
  });

  it("COMPLETED polling response shows 'Your export is ready' + Download button", async () => {
    const mutate = vi.fn().mockImplementation((_vars: unknown, opts: { onSuccess: (r: unknown) => void }) => {
      opts.onSuccess({ kind: 'async', job: { id: 'job-001', status: 'PENDING' } });
    });
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);
    mockUseExportJob.mockImplementation((id) => ({
      data: id === 'job-001' ? { id: 'job-001', status: 'COMPLETED', format: 'CSV', scope: 'PERSONAL', createdAt: '', updatedAt: '' } : undefined,
    }) as unknown as ReturnType<typeof useExportJob>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));

    // Completed state from useEffect on job status
    await act(async () => {});
    expect(screen.getByText("Your export is ready")).toBeDefined();
    expect(screen.getByRole("button", { name: /download/i })).toBeDefined();
  });

  it("FAILED polling response shows 'Export failed' + Try again button", async () => {
    const mutate = vi.fn().mockImplementation((_vars: unknown, opts: { onSuccess: (r: unknown) => void }) => {
      opts.onSuccess({ kind: 'async', job: { id: 'job-001', status: 'PENDING' } });
    });
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);
    mockUseExportJob.mockImplementation((id) => ({
      data: id === 'job-001' ? { id: 'job-001', status: 'FAILED', format: 'CSV', scope: 'PERSONAL', failureReason: 'Storage error', createdAt: '', updatedAt: '' } : undefined,
    }) as unknown as ReturnType<typeof useExportJob>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));

    await act(async () => {});
    expect(screen.getByText("Export failed — please try again")).toBeDefined();
    expect(screen.getByRole("button", { name: "Try again" })).toBeDefined();
  });

  it("422 response shows inline zero-results error and disables Export button", async () => {
    const mutate = vi.fn().mockImplementation((_vars: unknown, opts: { onError: (e: unknown) => void }) => {
      opts.onError(mockAxiosError(422));
    });
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));
    expect(screen.getByText("No records match the selected filters")).toBeDefined();
    expect((screen.getByRole("button", { name: "Export" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("429 response calls toast.error with rate limit message", async () => {
    const mutate = vi.fn().mockImplementation((_vars: unknown, opts: { onError: (e: unknown) => void }) => {
      opts.onError(mockAxiosError(429));
    });
    mockUseTriggerExport.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useTriggerExport>);

    const user = userEvent.setup();
    render(<ExportDialog open={true} onClose={vi.fn()} filters={{}} />);

    await user.click(screen.getByRole("button", { name: "Export" }));
    expect(mockToastError).toHaveBeenCalledWith(
      "You've reached the export limit. Please wait before exporting again.",
    );
  });
});
