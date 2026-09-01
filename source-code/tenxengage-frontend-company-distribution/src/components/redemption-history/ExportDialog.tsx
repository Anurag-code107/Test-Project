// Adapted from: none — no production reference
import { useState, useEffect } from "react";
import { Download, CheckCircle2, XCircle, Loader2 } from "lucide-react";
import axios from "axios";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useTriggerExport } from "@/hooks/redemption-history/useTriggerExport";
import { useExportJob } from "@/hooks/redemption-history/useExportJob";
import { getExportJobDownload } from "@/services/redemption-history/redemption-history.service";
import type {
  ExportFormat,
  ExportJobScope,
  RedemptionHistoryFilters,
} from "@/types/redemption-history/redemption-history.types";

type ExportDialogState = 'idle' | 'syncing' | 'polling' | 'completed' | 'failed' | 'zero-results';

type ExportDialogFilters = RedemptionHistoryFilters & { userName?: string; companyName?: string };

interface ExportDialogProps {
  open: boolean;
  onClose: () => void;
  filters: ExportDialogFilters;
  scope?: ExportJobScope;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function ExportDialog({ open, onClose, filters, scope }: ExportDialogProps) {
  const [format, setFormat] = useState<ExportFormat>('CSV');
  const [dialogState, setDialogState] = useState<ExportDialogState>('idle');
  const [jobId, setJobId] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const { mutate, isPending } = useTriggerExport();
  const { data: jobData } = useExportJob(jobId);

  useEffect(() => {
    if (!jobData) return;
    if (jobData.status === 'COMPLETED') setDialogState('completed');
    if (jobData.status === 'FAILED') setDialogState('failed');
  }, [jobData?.status]);

  const resetAndClose = () => {
    setDialogState('idle');
    setJobId(null);
    setFormat('CSV');
    onClose();
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen && dialogState !== 'syncing') resetAndClose();
  };

  const handleExport = () => {
    setDialogState('syncing');
    mutate(
      {
        format,
        scope,
        dateFrom: filters.dateFrom,
        dateTo: filters.dateTo,
        status: filters.status,
        category: filters.category,
        userName: filters.userName,
        companyName: filters.companyName,
      },
      {
        onSuccess: (result) => {
          if (result.kind === 'sync') {
            downloadBlob(result.blob, result.filename);
            resetAndClose();
          } else {
            setJobId(result.job.id);
            setDialogState('polling');
          }
        },
        onError: (error) => {
          if (axios.isAxiosError(error) && error.response?.status === 422) {
            setDialogState('zero-results');
          } else if (axios.isAxiosError(error) && error.response?.status === 429) {
            toast.error("You've reached the export limit. Please wait before exporting again.");
            setDialogState('idle');
          } else {
            setDialogState('failed');
          }
        },
      },
    );
  };

  const handleDownload = async () => {
    if (!jobId) return;
    setDownloading(true);
    try {
      const detail = await getExportJobDownload(jobId);
      if (detail.downloadUrl) {
        window.open(detail.downloadUrl, '_blank');
      }
    } finally {
      setDownloading(false);
    }
  };

  const handleRetry = () => {
    setDialogState('idle');
    setJobId(null);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Export transactions</DialogTitle>
        </DialogHeader>

        {(dialogState === 'idle' || dialogState === 'zero-results') && (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="export-format">Format</Label>
              <Select value={format} onValueChange={(v) => setFormat(v as ExportFormat)}>
                <SelectTrigger id="export-format">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CSV">CSV (.csv)</SelectItem>
                  <SelectItem value="XLSX">Excel (.xlsx)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {dialogState === 'zero-results' && (
              <p className="text-sm text-destructive">No records match the selected filters</p>
            )}
          </div>
        )}

        {dialogState === 'syncing' && (
          <div className="flex flex-col items-center gap-3 py-6">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <p className="text-sm text-muted-foreground">Preparing your export…</p>
          </div>
        )}

        {dialogState === 'polling' && (
          <div className="flex flex-col items-center gap-3 py-6">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <p className="text-sm text-muted-foreground">Generating your export…</p>
          </div>
        )}

        {dialogState === 'completed' && (
          <div className="flex flex-col items-center gap-3 py-6">
            <CheckCircle2 className="h-8 w-8 text-success" />
            <p className="text-sm font-medium">Your export is ready</p>
            <p className="text-xs text-muted-foreground">Link expires in 24 hours</p>
          </div>
        )}

        {dialogState === 'failed' && (
          <div className="flex flex-col items-center gap-3 py-6">
            <XCircle className="h-8 w-8 text-destructive" />
            <p className="text-sm font-medium">Export failed — please try again</p>
          </div>
        )}

        <DialogFooter>
          {(dialogState === 'idle' || dialogState === 'zero-results') && (
            <>
              <Button variant="outline" onClick={resetAndClose}>Cancel</Button>
              <Button
                onClick={handleExport}
                disabled={isPending || dialogState === 'zero-results'}
              >
                Export
              </Button>
            </>
          )}
          {dialogState === 'completed' && (
            <Button
              onClick={handleDownload}
              disabled={downloading}
              aria-label={downloading ? "Downloading, please wait" : "Download export file"}
            >
              {downloading
                ? <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                : <Download className="h-4 w-4 mr-2" />
              }
              {downloading ? "Downloading…" : "Download"}
            </Button>
          )}
          {dialogState === 'failed' && (
            <Button onClick={handleRetry}>Try again</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
