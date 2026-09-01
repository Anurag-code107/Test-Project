import { useState } from "react";
import {
  FileText,
  Download,
  Eye,
  FileSpreadsheet,
  File,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { downloadDocument, viewDocument } from "@/services/incentive.service";
import type { DocumentSummary } from "@/types/incentive.types";

interface DocumentsPopoverProps {
  documents: DocumentSummary[];
  incentiveId: string;
}

const fileTypeIcons: Record<string, React.ReactNode> = {
  pdf: <FileText className="h-4 w-4 text-destructive" />,
  xlsx: <FileSpreadsheet className="h-4 w-4 text-success" />,
  docx: <File className="h-4 w-4 text-blue-500" />,
};

const documentTypeLabels: Record<string, string> = {
  "eligible-products": "Eligible Products",
  "terms-conditions": "Terms & Conditions",
  "program-rules": "Program Rules",
  faq: "FAQ",
};

export function DocumentsPopover({
  documents,
  incentiveId,
}: DocumentsPopoverProps) {
  const [loadingId, setLoadingId] = useState<string | null>(null);

  if (!documents || documents.length === 0) {
    return null;
  }

  const handleView = async (doc: DocumentSummary, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!doc.downloadUrl || loadingId) return;
    setLoadingId(doc.id);
    try {
      await viewDocument(incentiveId, doc.id);
    } finally {
      setLoadingId(null);
    }
  };

  const handleDownload = async (doc: DocumentSummary, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!doc.downloadUrl || loadingId) return;
    setLoadingId(doc.id);
    try {
      await downloadDocument(incentiveId, doc.id, doc.name);
    } finally {
      setLoadingId(null);
    }
  };

  return (
    <Popover>
      <PopoverTrigger asChild onClick={(e) => e.stopPropagation()}>
        <Button variant="ghost" size="sm" className="shrink-0 h-8 w-8 p-0">
          <FileText className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-80 p-0"
        align="end"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b bg-muted/30">
          <h4 className="font-semibold text-sm">Documents</h4>
          <p className="text-xs text-muted-foreground mt-0.5">
            {documents.length} document{documents.length !== 1 ? "s" : ""}{" "}
            available
          </p>
        </div>
        <div className="max-h-64 overflow-y-auto">
          {documents.map((doc) => {
            const isLoading = loadingId === doc.id;
            const isDisabled = !doc.downloadUrl || !!loadingId;

            return (
              <div
                key={doc.id}
                className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50 transition-colors border-b last:border-b-0"
              >
                <div className="shrink-0">
                  {fileTypeIcons[doc.fileType] ?? (
                    <FileText className="h-4 w-4 text-muted-foreground" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{doc.name}</p>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <span>
                      {documentTypeLabels[doc.documentType] ?? doc.documentType}
                    </span>
                    <span>&middot;</span>
                    <span>{doc.size}</span>
                  </div>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {isLoading ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground mx-1" />
                  ) : (
                    <>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={(e) => handleView(doc, e)}
                        disabled={isDisabled}
                        title={
                          doc.downloadUrl ? "View document" : "No file uploaded"
                        }
                      >
                        <Eye className="h-3.5 w-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={(e) => handleDownload(doc, e)}
                        disabled={isDisabled}
                        title={
                          doc.downloadUrl
                            ? "Download document"
                            : "No file uploaded"
                        }
                      >
                        <Download className="h-3.5 w-3.5" />
                      </Button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
