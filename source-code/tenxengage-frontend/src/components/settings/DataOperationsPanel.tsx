import { useState, useRef, useEffect } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Progress } from "@/components/ui/progress";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { FeatureGate } from "@/components/FeatureGate";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Upload,
  Download,
  RefreshCw,
  Zap,
  Clock,
  FileSpreadsheet,
  CheckCircle2,
  AlertTriangle,
  Info,
  Play,
  Loader2,
  Shield,
  ChevronDown,
  History,
} from "lucide-react";
import { toast } from "sonner";
import type { DataObjectDetailResponse } from "@/types/data-object.types";
import type {
  SyncCadence,
  DataUploadResponse,
  TaggingJobResponse,
} from "@/types/data-operations.types";
import {
  useUploadHistory,
  useUploadFile,
  useDownloadTemplate,
  useConnectorPull,
  useTaggingHistory,
  useRunTaggingJob,
  useSyncSchedule,
  useUpdateSyncSchedule,
} from "@/hooks/useDataOperationsApi";

export type ExpandedSection = "operations" | "tagging" | null;

interface DataOperationsPanelProps {
  dataObject: DataObjectDetailResponse;
  expandedSection?: ExpandedSection;
  onExpandedChange?: (section: ExpandedSection) => void;
}

const cadenceLabels: Record<SyncCadence, string> = {
  MANUAL: "Manual Only",
  HOURLY: "Every Hour",
  DAILY: "Every Day",
  WEEKLY: "Every Week",
  MONTHLY: "Every Month",
};

interface UploadResult {
  totalRows: number;
  newRows: number;
  updatedRows: number;
  skippedRows: number;
  triggeredTagging: boolean;
}

export function DataOperationsPanel({
  dataObject,
  expandedSection,
  onExpandedChange,
}: DataOperationsPanelProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [showCadenceSetup, setShowCadenceSetup] = useState(false);
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null);
  const [showUploadResult, setShowUploadResult] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [isUploading, setIsUploading] = useState(false);

  const [opsOpen, setOpsOpen] = useState(false);
  const [taggingOpen, setTaggingOpen] = useState(false);
  const [showUploadHistory, setShowUploadHistory] = useState(false);
  const [showTaggingHistory, setShowTaggingHistory] = useState(false);

  // Cadence dialog local state
  const [cadenceEnabled, setCadenceEnabled] = useState(false);
  const [cadenceValue, setCadenceValue] = useState<SyncCadence>("MANUAL");

  const opsRef = useRef<HTMLDivElement>(null);
  const taggingRef = useRef<HTMLDivElement>(null);

  // API hooks
  const { data: uploadHistoryData } = useUploadHistory(dataObject.id);
  const uploadFileMutation = useUploadFile();
  const downloadTemplateMutation = useDownloadTemplate();
  const connectorPullMutation = useConnectorPull();
  const { data: taggingHistoryData } = useTaggingHistory();
  const runTaggingMutation = useRunTaggingJob();
  const { data: syncScheduleData } = useSyncSchedule(dataObject.id);
  const updateScheduleMutation = useUpdateSyncSchedule();

  const uploadHistory = uploadHistoryData ?? [];
  const taggingHistory = taggingHistoryData ?? [];

  useEffect(() => {
    if (expandedSection === "operations") {
      setOpsOpen(true);
      setTaggingOpen(false);
      setTimeout(
        () =>
          opsRef.current?.scrollIntoView({
            behavior: "smooth",
            block: "start",
          }),
        100,
      );
    } else if (expandedSection === "tagging") {
      setTaggingOpen(true);
      setOpsOpen(false);
      setTimeout(
        () =>
          taggingRef.current?.scrollIntoView({
            behavior: "smooth",
            block: "start",
          }),
        100,
      );
    }
  }, [expandedSection]);

  // Initialize cadence dialog from server data
  useEffect(() => {
    if (syncScheduleData) {
      setCadenceEnabled(syncScheduleData.enabled);
      setCadenceValue(syncScheduleData.cadence);
    }
  }, [syncScheduleData]);

  const [hasLoadedData, setHasLoadedData] = useState(false);

  const isSalesData = dataObject.name === "Sales Data";
  const hasConnectorMapping = !!dataObject.connectorMapping;
  const mappedFieldCount = dataObject.connectorMapping?.mappings.length ?? 0;
  const totalFields = dataObject.fields.length;
  const isPulling = connectorPullMutation.isPending;
  const isTagging = runTaggingMutation.isPending;

  const simulateProgress = (
    setProgress: (v: number) => void,
    duration: number,
  ): Promise<void> => {
    return new Promise((resolve) => {
      let p = 0;
      const interval = setInterval(() => {
        p += Math.random() * 15 + 5;
        if (p >= 100) {
          p = 100;
          clearInterval(interval);
          setProgress(100);
          setTimeout(resolve, 300);
        } else {
          setProgress(Math.min(p, 95));
        }
      }, duration / 8);
    });
  };

  const handleDownloadTemplate = () => {
    downloadTemplateMutation.mutate(dataObject.id, {
      onSuccess: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `${dataObject.name.replace(/\s+/g, "_")}_Template.csv`;
        a.click();
        URL.revokeObjectURL(url);
        toast.success(`Template downloaded for ${dataObject.name}`);
      },
      onError: () => toast.error("Failed to download template"),
    });
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validExtensions = [".csv", ".xlsx", ".xls"];
    const hasValidExt = validExtensions.some((ext) =>
      file.name.toLowerCase().endsWith(ext),
    );
    if (!hasValidExt) {
      toast.error("Please upload a CSV or Excel file (.csv, .xlsx, .xls)");
      return;
    }

    setIsUploading(true);
    setUploadProgress(0);

    // Simulate progress while uploading
    const progressPromise = simulateProgress(setUploadProgress, 3000);

    uploadFileMutation.mutate(
      { dataObjectId: dataObject.id, file },
      {
        onSuccess: async (data) => {
          await progressPromise;
          const result: UploadResult = {
            totalRows: data.totalRows,
            newRows: data.newRows,
            updatedRows: data.updatedRows,
            skippedRows: data.skippedRows,
            triggeredTagging: isSalesData,
          };
          setUploadResult(result);
          setIsUploading(false);
          setUploadProgress(0);
          setShowUploadResult(true);
          setHasLoadedData(true);
          toast.success(`${file.name} processed successfully`);

          if (isSalesData) {
            setTimeout(() => handleRunTagging(), 500);
          }
        },
        onError: async () => {
          await progressPromise;
          setIsUploading(false);
          setUploadProgress(0);
          toast.error("File upload failed");
        },
      },
    );

    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handlePullData = () => {
    if (!hasConnectorMapping) return;
    connectorPullMutation.mutate(dataObject.id, {
      onSuccess: (data) => {
        setHasLoadedData(true);
        toast.success(
          `Pulled from ${dataObject.connectorMapping!.connectorName}: ${data.newRows} new, ${data.updatedRows} updated records`,
        );
        if (isSalesData) {
          setTimeout(() => handleRunTagging(), 500);
        }
      },
      onError: () => toast.error("Connector pull failed"),
    });
  };

  const handleRunTagging = () => {
    setTaggingOpen(true);
    runTaggingMutation.mutate(undefined, {
      onSuccess: (data) => {
        toast.success(
          `Tagging complete: ${data.posAnalyzed} POs analyzed, ${data.eligibleDeals} eligible deals across ${data.incentivesMatched} incentives`,
        );
      },
      onError: () => toast.error("Tagging job failed"),
    });
  };

  const handleSaveCadence = () => {
    updateScheduleMutation.mutate(
      {
        dataObjectId: dataObject.id,
        data: { enabled: cadenceEnabled, cadence: cadenceValue },
      },
      {
        onSuccess: () => {
          setShowCadenceSetup(false);
          if (cadenceEnabled && cadenceValue !== "MANUAL") {
            toast.success(
              `Auto-sync scheduled: ${cadenceLabels[cadenceValue]} for ${dataObject.name}`,
            );
          } else {
            toast.info(
              "Auto-sync disabled. You can pull data manually anytime.",
            );
          }
        },
        onError: () => toast.error("Failed to update schedule"),
      },
    );
  };

  const formatDate = (iso: string) => {
    return new Date(iso).toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      hour12: true,
    });
  };

  return (
    <div className="space-y-3">
      {/* Manual Data Uploads Collapsible */}
      <FeatureGate feature="bulk_import">
        <div ref={opsRef}>
        <Collapsible
          open={opsOpen}
          onOpenChange={(open) => {
            setOpsOpen(open);
            onExpandedChange?.(open ? "operations" : null);
          }}
        >
          <Card>
            <CollapsibleTrigger asChild>
              <CardHeader className="pb-3 cursor-pointer hover:bg-muted/30 transition-colors rounded-t-lg">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Zap className="h-5 w-5 text-primary" />
                    <div>
                      <CardTitle className="text-base text-foreground">
                        Manual Data Uploads
                      </CardTitle>
                      <CardDescription className="text-xs mt-0.5">
                        {hasConnectorMapping
                          ? `Pull data from ${dataObject.connectorMapping!.connectorName} or upload files manually`
                          : "Upload data files or download a template to get started"}
                      </CardDescription>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {syncScheduleData?.lastRunAt && (
                      <span className="text-xs text-muted-foreground hidden sm:inline">
                        Last sync: {formatDate(syncScheduleData.lastRunAt)}
                      </span>
                    )}
                    <ChevronDown
                      className={`h-4 w-4 text-muted-foreground transition-transform duration-200 ${opsOpen ? "rotate-180" : ""}`}
                    />
                  </div>
                </div>
              </CardHeader>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <CardContent className="space-y-4 pt-0">
                {/* Connector Pull Section */}
                {hasConnectorMapping && (
                  <div className="rounded-lg border bg-muted/20 p-4 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <RefreshCw
                          className={`h-4 w-4 text-primary ${isPulling ? "animate-spin" : ""}`}
                        />
                        <span className="text-sm font-medium text-foreground">
                          Connector Sync —{" "}
                          {dataObject.connectorMapping!.connectorName}
                        </span>
                        <Badge variant="outline" className="text-xs">
                          {mappedFieldCount}/{totalFields} fields mapped
                        </Badge>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          className="gap-1.5"
                          onClick={() => setShowCadenceSetup(true)}
                        >
                          <Clock className="h-3.5 w-3.5" />
                          {syncScheduleData?.enabled &&
                          syncScheduleData.cadence !== "MANUAL"
                            ? cadenceLabels[syncScheduleData.cadence]
                            : "Schedule"}
                        </Button>
                        <Button
                          size="sm"
                          className="gap-1.5"
                          onClick={handlePullData}
                          disabled={isPulling || isTagging}
                        >
                          {isPulling ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Play className="h-3.5 w-3.5" />
                          )}
                          {isPulling ? "Pulling..." : "Pull Now"}
                        </Button>
                      </div>
                    </div>
                    {isPulling && (
                      <div className="space-y-1">
                        <Progress value={65} className="h-2" />
                        <p className="text-xs text-muted-foreground">
                          Fetching records from{" "}
                          {dataObject.connectorMapping!.connectorName}...
                        </p>
                      </div>
                    )}
                    {syncScheduleData?.enabled &&
                      syncScheduleData.cadence !== "MANUAL" && (
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          <Clock className="h-3 w-3" />
                          Auto-sync enabled:{" "}
                          {cadenceLabels[syncScheduleData.cadence]}
                        </div>
                      )}
                  </div>
                )}

                {/* Manual Upload Section */}
                <div className="rounded-lg border bg-muted/20 p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Upload className="h-4 w-4 text-primary" />
                      <span className="text-sm font-medium text-foreground">
                        Manual File Upload
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        className="gap-1.5"
                        onClick={handleDownloadTemplate}
                      >
                        <Download className="h-3.5 w-3.5" />
                        Download Template
                      </Button>
                      <Button
                        size="sm"
                        className="gap-1.5"
                        onClick={() => fileInputRef.current?.click()}
                        disabled={isUploading || isTagging}
                      >
                        {isUploading ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <FileSpreadsheet className="h-3.5 w-3.5" />
                        )}
                        {isUploading ? "Processing..." : "Upload File"}
                      </Button>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept=".csv,.xlsx,.xls"
                        className="hidden"
                        onChange={handleFileSelect}
                      />
                    </div>
                  </div>
                  {isUploading && (
                    <div className="space-y-1">
                      <Progress value={uploadProgress} className="h-2" />
                      <p className="text-xs text-muted-foreground">
                        Processing file — checking for duplicates and validating
                        records...
                      </p>
                    </div>
                  )}
                  {isSalesData && (
                    <div className="flex items-start gap-2 text-xs text-muted-foreground bg-muted/40 rounded-md p-2.5">
                      <Info className="h-3.5 w-3.5 mt-0.5 shrink-0" />
                      <span>
                        Sales data uploads use <strong>Transaction ID</strong>{" "}
                        for deduplication. Duplicate rows are skipped, changed
                        records are updated, and new records are added. A
                        tagging job will automatically run after upload.
                      </span>
                    </div>
                  )}
                </div>

                {/* Upload Audit Trail */}
                <div className="rounded-lg border bg-muted/20 p-4 space-y-2">
                  <button
                    className="flex items-center justify-between w-full text-left"
                    onClick={() => setShowUploadHistory((v) => !v)}
                  >
                    <div className="flex items-center gap-2">
                      <History className="h-4 w-4 text-muted-foreground" />
                      <span className="text-sm font-medium text-foreground">
                        Upload History
                      </span>
                      <Badge variant="secondary" className="text-xs">
                        {uploadHistory.length}
                      </Badge>
                    </div>
                    <ChevronDown
                      className={`h-3.5 w-3.5 text-muted-foreground transition-transform duration-200 ${showUploadHistory ? "rotate-180" : ""}`}
                    />
                  </button>
                  {showUploadHistory && (
                    <ScrollArea className="h-[180px] mt-2">
                      <div className="space-y-2 pr-3">
                        {uploadHistory.length === 0 ? (
                          <p className="text-xs text-muted-foreground text-center py-4">
                            No uploads yet
                          </p>
                        ) : (
                          uploadHistory.map((entry: DataUploadResponse) => (
                            <div
                              key={entry.id}
                              className="rounded-md border bg-background p-2.5 space-y-1"
                            >
                              <div className="flex items-center justify-between">
                                <span className="text-xs font-medium text-foreground truncate max-w-[200px]">
                                  {entry.fileName}
                                </span>
                                <span className="text-xs text-muted-foreground">
                                  {formatDate(entry.createdAt)}
                                </span>
                              </div>
                              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                                <span>
                                  <strong className="text-foreground">
                                    {entry.totalRows}
                                  </strong>{" "}
                                  total
                                </span>
                                <span className="text-emerald-600 dark:text-emerald-400">
                                  <strong>{entry.newRows}</strong> new
                                </span>
                                <span className="text-amber-600 dark:text-amber-400">
                                  <strong>{entry.updatedRows}</strong> updated
                                </span>
                                <span>
                                  <strong>{entry.skippedRows}</strong> skipped
                                </span>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </ScrollArea>
                  )}
                </div>
              </CardContent>
            </CollapsibleContent>
          </Card>
        </Collapsible>
        </div>
      </FeatureGate>

      {/* Tag Eligible Deals Collapsible — Sales Data Only */}
      {isSalesData && (
        <div ref={taggingRef}>
          <Collapsible
            open={taggingOpen}
            onOpenChange={(open) => {
              setTaggingOpen(open);
              onExpandedChange?.(open ? "tagging" : null);
            }}
          >
            <Card className="border-primary/20">
              <CollapsibleTrigger asChild>
                <CardHeader className="pb-3 cursor-pointer hover:bg-muted/30 transition-colors rounded-t-lg">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Shield className="h-5 w-5 text-primary" />
                      <div>
                        <CardTitle className="text-base text-foreground">
                          Tag Eligible Deals
                        </CardTitle>
                        <CardDescription className="text-xs mt-0.5">
                          Analyzes sales POs against active incentive
                          eligibility rules to determine reward qualification
                        </CardDescription>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <ChevronDown
                        className={`h-4 w-4 text-muted-foreground transition-transform duration-200 ${taggingOpen ? "rotate-180" : ""}`}
                      />
                    </div>
                  </div>
                </CardHeader>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <CardContent className="space-y-3 pt-0">
                  {!hasConnectorMapping && !hasLoadedData ? (
                    <div className="flex items-start gap-2 text-xs bg-amber-500/5 border border-amber-500/20 rounded-md p-3">
                      <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
                      <div>
                        <p className="font-medium text-amber-700 dark:text-amber-300">
                          No sales data loaded yet
                        </p>
                        <p className="text-muted-foreground mt-0.5">
                          Upload a sales data file or set up a connector mapping
                          to pull data before running the tagging job.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <>
                      {isTagging && (
                        <div className="space-y-1">
                          <Progress value={65} className="h-2" />
                          <p className="text-xs text-muted-foreground">
                            Comparing sales POs against incentive eligibility
                            requirements...
                          </p>
                        </div>
                      )}
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                          <Info className="h-3.5 w-3.5" />
                          Analyzes all sales POs against active incentive
                          eligibility rules. Runs automatically after data
                          uploads.
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-1.5 border-primary/30 text-primary hover:bg-primary/5"
                          onClick={handleRunTagging}
                          disabled={isTagging || isPulling || isUploading}
                        >
                          {isTagging ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : (
                            <Zap className="h-3.5 w-3.5" />
                          )}
                          {isTagging ? "Running..." : "Run Tagging Job"}
                        </Button>
                      </div>
                    </>
                  )}

                  {/* Tagging Audit Trail */}
                  <div className="rounded-lg border bg-muted/20 p-4 space-y-2">
                    <button
                      className="flex items-center justify-between w-full text-left"
                      onClick={() => setShowTaggingHistory((v) => !v)}
                    >
                      <div className="flex items-center gap-2">
                        <History className="h-4 w-4 text-muted-foreground" />
                        <span className="text-sm font-medium text-foreground">
                          Tagging History
                        </span>
                        <Badge variant="secondary" className="text-xs">
                          {taggingHistory.length}
                        </Badge>
                      </div>
                      <ChevronDown
                        className={`h-3.5 w-3.5 text-muted-foreground transition-transform duration-200 ${showTaggingHistory ? "rotate-180" : ""}`}
                      />
                    </button>
                    {showTaggingHistory && (
                      <ScrollArea className="h-[180px] mt-2">
                        <div className="space-y-2 pr-3">
                          {taggingHistory.length === 0 ? (
                            <p className="text-xs text-muted-foreground text-center py-4">
                              No tagging jobs run yet
                            </p>
                          ) : (
                            taggingHistory.map((entry: TaggingJobResponse) => (
                              <div
                                key={entry.id}
                                className="rounded-md border bg-background p-2.5 space-y-1"
                              >
                                <div className="flex items-center justify-between">
                                  <span className="text-xs font-medium text-foreground">
                                    Tagging Job
                                  </span>
                                  <span className="text-xs text-muted-foreground">
                                    {formatDate(entry.createdAt)}
                                  </span>
                                </div>
                                <div className="flex items-center gap-3 text-xs text-muted-foreground">
                                  <span>
                                    <strong className="text-foreground">
                                      {entry.posAnalyzed}
                                    </strong>{" "}
                                    POs analyzed
                                  </span>
                                  <span className="text-emerald-600 dark:text-emerald-400">
                                    <strong>{entry.eligibleDeals}</strong>{" "}
                                    eligible deals
                                  </span>
                                  <span>
                                    <strong>{entry.incentivesMatched}</strong>{" "}
                                    incentives matched
                                  </span>
                                </div>
                              </div>
                            ))
                          )}
                        </div>
                      </ScrollArea>
                    )}
                  </div>
                </CardContent>
              </CollapsibleContent>
            </Card>
          </Collapsible>
        </div>
      )}

      {/* Upload Result Dialog */}
      <Dialog open={showUploadResult} onOpenChange={setShowUploadResult}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-emerald-600" />
              Upload Complete
            </DialogTitle>
            <DialogDescription>
              File processed for {dataObject.name}
            </DialogDescription>
          </DialogHeader>
          {uploadResult && (
            <div className="space-y-3 py-2">
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-lg bg-muted/50 p-3 text-center">
                  <p className="text-2xl font-semibold text-foreground">
                    {uploadResult.totalRows}
                  </p>
                  <p className="text-xs text-muted-foreground">Total Rows</p>
                </div>
                <div className="rounded-lg bg-emerald-500/10 p-3 text-center">
                  <p className="text-2xl font-semibold text-emerald-600 dark:text-emerald-400">
                    {uploadResult.newRows}
                  </p>
                  <p className="text-xs text-muted-foreground">New Records</p>
                </div>
                <div className="rounded-lg bg-amber-500/10 p-3 text-center">
                  <p className="text-2xl font-semibold text-amber-600 dark:text-amber-400">
                    {uploadResult.updatedRows}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Updated Records
                  </p>
                </div>
                <div className="rounded-lg bg-muted/50 p-3 text-center">
                  <p className="text-2xl font-semibold text-muted-foreground">
                    {uploadResult.skippedRows}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Skipped (Duplicates)
                  </p>
                </div>
              </div>
              {uploadResult.triggeredTagging && (
                <div className="flex items-center gap-2 text-xs bg-primary/5 border border-primary/20 rounded-md p-2.5">
                  <Zap className="h-3.5 w-3.5 text-primary" />
                  <span className="text-foreground">
                    Eligibility tagging job has been triggered automatically.
                  </span>
                </div>
              )}
            </div>
          )}
          <DialogFooter>
            <Button onClick={() => setShowUploadResult(false)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Cadence Setup Dialog */}
      <Dialog open={showCadenceSetup} onOpenChange={setShowCadenceSetup}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5 text-primary" />
              Sync Schedule — {dataObject.name}
            </DialogTitle>
            <DialogDescription>
              Configure how often data should be automatically pulled from{" "}
              {dataObject.connectorMapping?.connectorName || "the connector"}.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="cadence-toggle" className="text-sm">
                Enable Auto-Sync
              </Label>
              <Switch
                id="cadence-toggle"
                checked={cadenceEnabled}
                onCheckedChange={setCadenceEnabled}
              />
            </div>
            {cadenceEnabled && (
              <div className="space-y-2">
                <Label>Sync Frequency</Label>
                <Select
                  value={cadenceValue}
                  onValueChange={(v) => setCadenceValue(v as SyncCadence)}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(Object.entries(cadenceLabels) as [SyncCadence, string][])
                      .filter(([k]) => k !== "MANUAL")
                      .map(([value, label]) => (
                        <SelectItem key={value} value={value}>
                          {label}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
                {isSalesData && (
                  <p className="text-xs text-muted-foreground">
                    The tagging job will automatically run after each scheduled
                    sync.
                  </p>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowCadenceSetup(false)}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSaveCadence}
              disabled={updateScheduleMutation.isPending}
            >
              {updateScheduleMutation.isPending && (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              )}
              Save Schedule
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
