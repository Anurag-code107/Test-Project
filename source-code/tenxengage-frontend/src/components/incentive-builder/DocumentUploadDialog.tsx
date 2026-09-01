import { useState, useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import {
  FileText,
  FileSpreadsheet,
  File,
  Upload,
  X,
  CheckCircle,
  Loader2,
} from "lucide-react";
import { validateFiles } from "@/services/incentive.service";
import type { DocumentInput, DocumentSummary } from "@/types/incentive.types";

export interface PendingUpload {
  file: File;
  category: string;
}

interface DocumentUploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onComplete: (documents: DocumentInput[], files: PendingUpload[]) => void;
  engagementType?: string;
  isSubmitting?: boolean;
  isEditMode?: boolean;
  existingDocuments?: DocumentSummary[];
}

interface PendingDocument {
  id: string;
  category: string;
  file: File;
}

const documentCategories = [
  { value: "terms-conditions", label: "Terms & Conditions" },
  { value: "eligible-products", label: "Eligible Products" },
  { value: "program-rules", label: "Program Rules" },
  { value: "faq", label: "FAQ" },
];

const noEligibleProductsTypes = ["TRAINING", "ACTIVITY"];

const fileTypeIcons: Record<string, React.ReactNode> = {
  pdf: <FileText className="h-4 w-4 text-destructive" />,
  xlsx: <FileSpreadsheet className="h-4 w-4 text-success" />,
  xls: <FileSpreadsheet className="h-4 w-4 text-success" />,
  docx: <File className="h-4 w-4 text-blue-500" />,
  doc: <File className="h-4 w-4 text-blue-500" />,
};

const categoryLabels: Record<string, string> = {
  "eligible-products": "Eligible Products",
  "terms-conditions": "Terms & Conditions",
  "program-rules": "Program Rules",
  faq: "FAQ",
};

function getFileExtension(filename: string): string {
  return filename.split(".").pop()?.toLowerCase() || "pdf";
}

function getFileTypeIcon(filename: string) {
  const ext = getFileExtension(filename);
  return (
    fileTypeIcons[ext] || <FileText className="h-4 w-4 text-muted-foreground" />
  );
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function DocumentUploadDialog({
  open,
  onOpenChange,
  onComplete,
  engagementType,
  isSubmitting = false,
  isEditMode = false,
  existingDocuments = [],
}: DocumentUploadDialogProps) {
  const [pendingDocs, setPendingDocs] = useState<PendingDocument[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>("");
  const actionLabel = isEditMode ? "Update" : "Create";

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setPendingDocs([]);
      setSelectedCategory("");
    }
  }, [open]);

  const filteredCategories = documentCategories.filter(
    (cat) =>
      !(
        engagementType &&
        noEligibleProductsTypes.includes(engagementType) &&
        cat.value === "eligible-products"
      ),
  );

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || !selectedCategory) return;

    const newDocs: PendingDocument[] = Array.from(files).map((file, idx) => ({
      id: `upload-${Date.now()}-${idx}`,
      category: selectedCategory,
      file,
    }));

    setPendingDocs((prev) => [...prev, ...newDocs]);
    setSelectedCategory("");
    e.target.value = "";
  };

  const handleRemove = (id: string) => {
    setPendingDocs((prev) => prev.filter((d) => d.id !== id));
  };

  const handleComplete = (withDocs: boolean) => {
    if (isSubmitting) return;
    if (withDocs && pendingDocs.length > 0) {
      const uploads = pendingDocs.map((doc) => ({
        file: doc.file,
        category: doc.category,
      }));

      const validationError = validateFiles(uploads);
      if (validationError) {
        alert(validationError);
        return;
      }

      // Convert File objects to document metadata for JSON payload
      const documents: DocumentInput[] = pendingDocs.map((doc) => ({
        name: doc.file.name,
        documentType: doc.category,
        fileType: getFileExtension(doc.file.name),
        size: formatFileSize(doc.file.size),
      }));

      // Also pass raw File objects for actual upload
      const files: PendingUpload[] = pendingDocs.map((doc) => ({
        file: doc.file,
        category: doc.category,
      }));

      onComplete(documents, files);
    } else {
      onComplete([], []);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!isSubmitting) onOpenChange(v);
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Upload Program Documents</DialogTitle>
          <DialogDescription>
            Attach supporting documents like terms & conditions, eligible
            products lists, or program rules. You can also skip this step and
            add documents later.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Existing documents from API (only in edit mode) */}
          {isEditMode && existingDocuments.length > 0 && (
            <div className="space-y-2">
              <label className="text-sm font-medium text-muted-foreground">
                {existingDocuments.length} existing document
                {existingDocuments.length !== 1 ? "s" : ""}
              </label>
              <div className="space-y-2 max-h-32 overflow-y-auto">
                {existingDocuments.map((doc) => (
                  <div
                    key={doc.id}
                    className="flex items-center gap-3 p-3 rounded-lg border bg-muted/30"
                  >
                    <div className="shrink-0">{getFileTypeIcon(doc.name)}</div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{doc.name}</p>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Badge
                          variant="outline"
                          className="text-xs px-1.5 py-0"
                        >
                          {categoryLabels[doc.documentType] ?? doc.documentType}
                        </Badge>
                        <span>{doc.size}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Upload control */}
          <div className="flex items-end gap-2">
            <div className="flex-1 space-y-1.5">
              <label className="text-sm font-medium">Document Category</label>
              <Select
                value={selectedCategory}
                onValueChange={setSelectedCategory}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select category..." />
                </SelectTrigger>
                <SelectContent>
                  {filteredCategories.map((cat) => (
                    <SelectItem key={cat.value} value={cat.value}>
                      {cat.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button
              variant="outline"
              disabled={!selectedCategory}
              className="shrink-0"
              onClick={() => {
                const input = document.createElement("input");
                input.type = "file";
                input.accept = ".pdf,.xlsx,.xls,.docx,.doc";
                input.onchange = (e) =>
                  handleFileSelect(
                    e as unknown as React.ChangeEvent<HTMLInputElement>,
                  );
                input.click();
              }}
            >
              <Upload className="h-4 w-4 mr-2" />
              Choose File
            </Button>
          </div>

          {/* Pending documents list */}
          {pendingDocs.length > 0 && (
            <div className="space-y-2">
              <label className="text-sm font-medium text-muted-foreground">
                {pendingDocs.length} document
                {pendingDocs.length !== 1 ? "s" : ""} ready
              </label>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {pendingDocs.map((doc) => (
                  <div
                    key={doc.id}
                    className="flex items-center gap-3 p-3 rounded-lg border bg-card"
                  >
                    <div className="shrink-0">
                      {getFileTypeIcon(doc.file.name)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">
                        {doc.file.name}
                      </p>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Badge
                          variant="outline"
                          className="text-xs px-1.5 py-0"
                        >
                          {categoryLabels[doc.category]}
                        </Badge>
                        <span>{formatFileSize(doc.file.size)}</span>
                      </div>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 shrink-0"
                      onClick={() => handleRemove(doc.id)}
                    >
                      <X className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {pendingDocs.length === 0 && (
            <div className="flex flex-col items-center justify-center py-6 border border-dashed rounded-lg text-muted-foreground">
              <Upload className="h-8 w-8 mb-2 opacity-50" />
              <p className="text-sm">No documents added yet</p>
              <p className="text-xs">
                Select a category above and choose a file
              </p>
            </div>
          )}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() => handleComplete(false)}
            disabled={isSubmitting}
          >
            {isSubmitting && pendingDocs.length === 0 ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                {isEditMode ? "Updating..." : "Creating..."}
              </>
            ) : (
              `Skip & ${actionLabel}`
            )}
          </Button>
          <Button
            onClick={() => handleComplete(true)}
            disabled={pendingDocs.length === 0 || isSubmitting}
            className="bg-gradient-to-r from-primary to-primary-light"
          >
            {isSubmitting && pendingDocs.length > 0 ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                {isEditMode ? "Updating..." : "Creating..."}
              </>
            ) : (
              <>
                <CheckCircle className="h-4 w-4 mr-2" />
                {`Upload & ${actionLabel}`}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
