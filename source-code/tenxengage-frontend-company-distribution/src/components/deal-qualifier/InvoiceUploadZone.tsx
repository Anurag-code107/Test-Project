import { useState, useCallback } from "react";
import { useInvoiceUpload } from "@/hooks/useDealQualifier";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Upload,
  FileText,
  Loader2,
  CheckCircle2,
  XCircle,
  ArrowRight,
} from "lucide-react";
import type { InvoiceExtractionResponse } from "@/types/deal-qualifier.types";

interface InvoiceUploadZoneProps {
  onExtracted: (response: InvoiceExtractionResponse) => void;
}

export function InvoiceUploadZone({ onExtracted }: InvoiceUploadZoneProps) {
  const [dragOver, setDragOver] = useState(false);
  const [extraction, setExtraction] =
    useState<InvoiceExtractionResponse | null>(null);
  const uploadMutation = useInvoiceUpload();

  const handleFile = useCallback(
    (file: File) => {
      if (!file.name.toLowerCase().endsWith(".pdf")) {
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        return;
      }
      setExtraction(null);
      uploadMutation.mutate(file, {
        onSuccess: (response) => {
          setExtraction(response);
        },
      });
    },
    [uploadMutation],
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      const file = e.dataTransfer.files[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFile(file);
      e.target.value = "";
    },
    [handleFile],
  );

  return (
    <div className="space-y-3">
      {/* Drop zone */}
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`relative border-2 border-dashed rounded-lg p-4 text-center transition-colors cursor-pointer ${
          dragOver
            ? "border-primary bg-primary/5"
            : "border-muted-foreground/25 hover:border-primary/50"
        }`}
        onClick={() => document.getElementById("invoice-upload-input")?.click()}
      >
        <input
          id="invoice-upload-input"
          type="file"
          accept=".pdf"
          className="hidden"
          onChange={handleInputChange}
        />
        {uploadMutation.isPending ? (
          <div className="flex flex-col items-center gap-2 py-2">
            <Loader2 className="h-6 w-6 text-primary animate-spin" />
            <span className="text-sm text-muted-foreground">
              Analyzing invoice with AI...
            </span>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2 py-2">
            <Upload className="h-6 w-6 text-muted-foreground" />
            <div>
              <span className="text-sm font-medium text-foreground">
                Upload invoice
              </span>
              <span className="text-sm text-muted-foreground">
                {" "}
                to auto-fill
              </span>
            </div>
            <span className="text-xs text-muted-foreground">
              PDF only, max 10 MB
            </span>
          </div>
        )}
      </div>

      {/* Error */}
      {uploadMutation.isError && (
        <div className="flex items-center gap-2 text-sm text-destructive">
          <XCircle className="h-4 w-4" />
          <span>
            {uploadMutation.error instanceof Error
              ? uploadMutation.error.message
              : "Failed to process invoice"}
          </span>
        </div>
      )}

      {/* Extraction results */}
      {extraction && (
        <Card className="p-3 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">Extracted Data</span>
            </div>
            <Badge variant="outline" className="text-xs">
              <CheckCircle2 className="h-3 w-3 mr-1" />
              {extraction.lineItems.length} item
              {extraction.lineItems.length !== 1 ? "s" : ""}
            </Badge>
          </div>

          {/* Line items preview */}
          <div className="space-y-1.5">
            {extraction.lineItems.slice(0, 5).map((item, i) => {
              const mapping = extraction.skuMappings[i];
              return (
                <div
                  key={i}
                  className="flex items-center justify-between text-xs"
                >
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    <span className="truncate text-muted-foreground">
                      {item.productName}
                    </span>
                    {mapping?.matchedSku ? (
                      <Badge
                        variant="outline"
                        className="text-[10px] shrink-0 text-green-600 border-green-200"
                      >
                        {mapping.matchedProductName || mapping.matchedSku}
                      </Badge>
                    ) : (
                      <Badge
                        variant="outline"
                        className="text-[10px] shrink-0 text-yellow-600 border-yellow-200"
                      >
                        No match
                      </Badge>
                    )}
                  </div>
                  {item.lineTotal != null && (
                    <span className="text-muted-foreground shrink-0 ml-2">
                      ${item.lineTotal.toLocaleString()}
                    </span>
                  )}
                </div>
              );
            })}
          </div>

          {extraction.totalValue != null && (
            <div className="flex items-center justify-between text-sm pt-1 border-t">
              <span className="text-muted-foreground">Total</span>
              <span className="font-semibold">
                ${extraction.totalValue.toLocaleString()}
              </span>
            </div>
          )}

          <Button
            size="sm"
            className="w-full"
            onClick={() => onExtracted(extraction)}
          >
            Apply to Form
            <ArrowRight className="h-3.5 w-3.5 ml-1.5" />
          </Button>
        </Card>
      )}
    </div>
  );
}
