import { useState, useCallback, useRef } from "react";
import {
  Upload,
  ArrowLeft,
  FileSpreadsheet,
  Download,
  Loader2,
  Send,
  GraduationCap,
  ClipboardList,
  Route,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { IncentiveType } from "@/types/incentive.types";
import { downloadTemplate } from "@/utils/excelTemplate";

/* ── Per-type theme config ────────────────────────────────────────────── */

const TYPE_CONFIG: Record<
  IncentiveType,
  {
    title: string;
    icon: typeof Send;
    hue: string;
    color: string;
    colorLight: string;
    colorMuted: string;
    pillBorder: string;
    pillBg: string;
    pillText: string;
    iconBg: string;
    iconText: string;
    btnBg: string;
    btnHover: string;
    dropBorderActive: string;
    dropBgActive: string;
    progressBg: string;
    glowBg: string;
  }
> = {
  SALES: {
    title: "Sales Incentive",
    icon: Send,
    hue: "217",
    color: "hsl(217 91% 60%)",
    colorLight: "hsl(217 91% 60% / 0.08)",
    colorMuted: "hsl(217 91% 55%)",
    pillBorder: "border-[hsl(217_91%_60%/0.15)]",
    pillBg: "bg-[hsl(217_91%_60%/0.04)]",
    pillText: "text-[hsl(217_91%_48%)]",
    iconBg: "bg-[hsl(217_91%_60%/0.08)]",
    iconText: "text-[hsl(217_91%_55%)]",
    btnBg: "bg-[hsl(217_91%_60%)]",
    btnHover: "hover:bg-[hsl(217_91%_52%)]",
    dropBorderActive: "border-[hsl(217_91%_60%)]",
    dropBgActive: "bg-[hsl(217_91%_60%/0.04)]",
    progressBg: "bg-[hsl(217_91%_60%)]",
    glowBg: "bg-[hsl(217_91%_60%/0.15)]",
  },
  TRAINING: {
    title: "Training Incentive",
    icon: GraduationCap,
    hue: "38",
    color: "hsl(38 80% 50%)",
    colorLight: "hsl(38 80% 50% / 0.08)",
    colorMuted: "hsl(38 80% 45%)",
    pillBorder: "border-[hsl(38_80%_50%/0.15)]",
    pillBg: "bg-[hsl(38_80%_50%/0.04)]",
    pillText: "text-[hsl(38_80%_38%)]",
    iconBg: "bg-[hsl(38_80%_50%/0.08)]",
    iconText: "text-[hsl(38_80%_45%)]",
    btnBg: "bg-[hsl(38_80%_50%)]",
    btnHover: "hover:bg-[hsl(38_80%_42%)]",
    dropBorderActive: "border-[hsl(38_80%_50%)]",
    dropBgActive: "bg-[hsl(38_80%_50%/0.04)]",
    progressBg: "bg-[hsl(38_80%_50%)]",
    glowBg: "bg-[hsl(38_80%_50%/0.15)]",
  },
  ACTIVITY: {
    title: "Activity Incentive",
    icon: ClipboardList,
    hue: "200",
    color: "hsl(200 80% 50%)",
    colorLight: "hsl(200 80% 50% / 0.08)",
    colorMuted: "hsl(200 80% 45%)",
    pillBorder: "border-[hsl(200_80%_50%/0.15)]",
    pillBg: "bg-[hsl(200_80%_50%/0.04)]",
    pillText: "text-[hsl(200_80%_38%)]",
    iconBg: "bg-[hsl(200_80%_50%/0.08)]",
    iconText: "text-[hsl(200_80%_45%)]",
    btnBg: "bg-[hsl(200_80%_50%)]",
    btnHover: "hover:bg-[hsl(200_80%_42%)]",
    dropBorderActive: "border-[hsl(200_80%_50%)]",
    dropBgActive: "bg-[hsl(200_80%_50%/0.04)]",
    progressBg: "bg-[hsl(200_80%_50%)]",
    glowBg: "bg-[hsl(200_80%_50%/0.15)]",
  },
  JOURNEY: {
    title: "Journey Incentive",
    icon: Route,
    hue: "260",
    color: "hsl(260 50% 55%)",
    colorLight: "hsl(260 50% 55% / 0.08)",
    colorMuted: "hsl(260 50% 50%)",
    pillBorder: "border-[hsl(260_50%_55%/0.15)]",
    pillBg: "bg-[hsl(260_50%_55%/0.04)]",
    pillText: "text-[hsl(260_50%_42%)]",
    iconBg: "bg-[hsl(260_50%_55%/0.08)]",
    iconText: "text-[hsl(260_50%_50%)]",
    btnBg: "bg-[hsl(260_50%_55%)]",
    btnHover: "hover:bg-[hsl(260_50%_47%)]",
    dropBorderActive: "border-[hsl(260_50%_55%)]",
    dropBgActive: "bg-[hsl(260_50%_55%/0.04)]",
    progressBg: "bg-[hsl(260_50%_55%)]",
    glowBg: "bg-[hsl(260_50%_55%/0.15)]",
  },
};

/* ── SVG background art ───────────────────────────────────────────────── */

function BackgroundArt({ hue }: { hue: string }) {
  return (
    <svg
      className="absolute inset-0 h-full w-full"
      viewBox="0 0 1200 700"
      preserveAspectRatio="xMidYMid slice"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* Flowing curves */}
      <path
        d="M-60 200 C200 140, 500 260, 750 180 S1050 100, 1260 160"
        stroke={`hsl(${hue} 60% 55% / 0.14)`}
        strokeWidth="1.2"
        className="home-banner-line home-banner-line-1"
      />
      <path
        d="M-40 420 C220 360, 480 460, 720 380 S1000 300, 1260 360"
        stroke={`hsl(${hue} 60% 55% / 0.08)`}
        strokeWidth="1"
        className="home-banner-line home-banner-line-2"
      />

      {/* Upload box (top-right) */}
      <g className="home-banner-shape home-banner-shape-1">
        <rect
          x="1040"
          y="110"
          width="44"
          height="40"
          rx="5"
          stroke={`hsl(${hue} 60% 55% / 0.18)`}
          strokeWidth="1"
        />
        <path
          d={`M1062 140 v-14 m-6 6 l6 -7 6 7`}
          stroke={`hsl(${hue} 60% 55% / 0.22)`}
          strokeWidth="1"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </g>

      {/* Spreadsheet (left) */}
      <g className="home-banner-shape home-banner-shape-2">
        <rect
          x="80"
          y="280"
          width="36"
          height="44"
          rx="4"
          stroke={`hsl(${hue} 60% 55% / 0.14)`}
          strokeWidth="0.8"
        />
        <line
          x1="80"
          y1="294"
          x2="116"
          y2="294"
          stroke={`hsl(${hue} 60% 55% / 0.10)`}
          strokeWidth="0.6"
        />
        <line
          x1="80"
          y1="306"
          x2="116"
          y2="306"
          stroke={`hsl(${hue} 60% 55% / 0.08)`}
          strokeWidth="0.6"
        />
        <line
          x1="96"
          y1="280"
          x2="96"
          y2="324"
          stroke={`hsl(${hue} 60% 55% / 0.08)`}
          strokeWidth="0.6"
        />
      </g>

      {/* Dots */}
      <circle
        cx="200"
        cy="150"
        r="3"
        fill={`hsl(${hue} 60% 55% / 0.18)`}
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="900"
        cy="240"
        r="2.5"
        fill={`hsl(${hue} 60% 55% / 0.14)`}
        className="home-banner-dot home-banner-dot-2"
      />
      <circle
        cx="150"
        cy="480"
        r="2"
        fill={`hsl(${hue} 60% 55% / 0.14)`}
        className="home-banner-dot home-banner-dot-3"
      />
      <circle
        cx="1100"
        cy="340"
        r="3"
        fill={`hsl(${hue} 60% 55% / 0.10)`}
        className="home-banner-dot home-banner-dot-1"
      />
      <circle
        cx="600"
        cy="100"
        r="2"
        fill={`hsl(${hue} 60% 55% / 0.12)`}
        className="home-banner-dot home-banner-dot-2"
      />
    </svg>
  );
}

/* ── Upload illustration SVG inside drop zone ─────────────────────────── */

function UploadIllustration({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 120 90" fill="none" className="w-28 h-auto mb-2">
      {/* Cloud shape */}
      <path
        d="M30 55 C30 55, 18 55, 18 44 C18 35, 26 30, 34 32 C36 24, 46 18, 56 20 C64 16, 78 18, 82 28 C92 28, 100 36, 98 46 C100 54, 92 58, 86 55"
        stroke={color}
        strokeWidth="1.2"
        fill="none"
        opacity="0.6"
      />
      {/* Upload arrow */}
      <path
        d="M58 68 v-22 m-8 8 l8 -10 8 10"
        stroke={color}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.8"
      />
      {/* Base line */}
      <line
        x1="40"
        y1="72"
        x2="76"
        y2="72"
        stroke={color}
        strokeWidth="1.2"
        strokeLinecap="round"
        opacity="0.4"
      />
      {/* Sparkle dots */}
      <circle cx="26" cy="62" r="1.5" fill={color} opacity="0.3" />
      <circle cx="92" cy="50" r="1.5" fill={color} opacity="0.3" />
      <circle cx="44" cy="16" r="1" fill={color} opacity="0.25" />
      <circle cx="78" cy="14" r="1" fill={color} opacity="0.25" />
    </svg>
  );
}

/* ── Component ────────────────────────────────────────────────────────── */

interface TemplateUploadPageProps {
  type: IncentiveType;
  onUpload: (file: File) => void;
  onBack: () => void;
  isProcessing?: boolean;
}

export function TemplateUploadPage({
  type,
  onUpload,
  onBack,
  isProcessing = false,
}: TemplateUploadPageProps) {
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const theme = TYPE_CONFIG[type];
  const TypeIcon = theme.icon;

  const handleDrag = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragActive(false);
      const file = e.dataTransfer.files[0];
      if (file) onUpload(file);
    },
    [onUpload],
  );

  const handleFileSelect = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        onUpload(file);
        e.target.value = "";
      }
    },
    [onUpload],
  );

  return (
    <div className="relative min-h-[calc(100vh-64px)] overflow-hidden">
      {/* Full-page SVG art background */}
      <div className="pointer-events-none absolute inset-0 select-none">
        <BackgroundArt hue={theme.hue} />
      </div>

      {/* Radial gradient overlay */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 50% at 50% 35%, transparent 0%, hsl(var(--background) / 0.8) 100%)",
        }}
      />

      <div className="relative z-10 max-w-2xl mx-auto px-6 py-12">
        {/* Top bar: back */}
        <div className="flex items-center mb-4">
          <button
            type="button"
            onClick={onBack}
            disabled={isProcessing}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors disabled:opacity-50"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Back</span>
          </button>
        </div>

        {/* Hero heading */}
        <div className="text-center mb-10">
          <div
            className={cn(
              "inline-flex items-center gap-2 px-4 py-1.5 rounded-full border mb-6",
              theme.pillBorder,
              theme.pillBg,
            )}
          >
            <TypeIcon className={cn("h-3.5 w-3.5", theme.iconText)} />
            <span className={cn("text-xs font-medium", theme.pillText)}>
              {theme.title}
            </span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            Upload Your Template
          </h1>
          <p className="text-base text-muted-foreground mt-3 mx-auto">
            Upload a template, document, or presentation to auto-populate the
            builder
          </p>
        </div>

        {/* Drop zone — frosted glass card */}
        <div
          className={cn(
            "relative rounded-2xl border border-dashed p-10 transition-[border-color,background-color] duration-300 backdrop-blur-sm overflow-hidden",
            isProcessing
              ? cn(
                  theme.dropBorderActive,
                  theme.dropBgActive,
                  "border-opacity-30",
                )
              : dragActive
                ? cn(theme.dropBorderActive, theme.dropBgActive)
                : "border-border bg-background/70",
          )}
          onDragEnter={!isProcessing ? handleDrag : undefined}
          onDragLeave={!isProcessing ? handleDrag : undefined}
          onDragOver={!isProcessing ? handleDrag : undefined}
          onDrop={!isProcessing ? handleDrop : undefined}
        >
          {isProcessing ? (
            <div className="flex flex-col items-center gap-6 py-6">
              <div className="relative">
                <div
                  className={cn(
                    "absolute inset-0 rounded-full blur-xl",
                    theme.glowBg,
                  )}
                />
                <div
                  className="relative w-16 h-16 rounded-full flex items-center justify-center"
                  style={{ background: theme.color }}
                >
                  <Loader2 className="w-7 h-7 text-white animate-spin" />
                </div>
              </div>
              <div className="flex flex-col items-center gap-1.5">
                <h3 className="font-semibold text-sm text-foreground">
                  Processing your template...
                </h3>
                <p className="text-sm text-muted-foreground">
                  Extracting fields and preparing your builder
                </p>
              </div>
              <div className="w-48 h-1.5 bg-muted rounded-full overflow-hidden">
                <div
                  className={cn("h-full rounded-full", theme.progressBg)}
                  style={{
                    animation: "template-progress 2.5s ease-in-out forwards",
                  }}
                />
              </div>
              <style>{`
                @keyframes template-progress {
                  0% { width: 0%; }
                  30% { width: 40%; }
                  60% { width: 70%; }
                  90% { width: 90%; }
                  100% { width: 100%; }
                }
              `}</style>
            </div>
          ) : (
            <div className="flex flex-col items-center">
              <UploadIllustration color={theme.color} />

              <p className="font-semibold text-sm text-foreground mb-1">
                Drop your file here or click to upload
              </p>
              <p className="text-sm text-muted-foreground mb-6">
                Accepts .xlsx, .xls, .csv, .pdf, or .pptx files
              </p>

              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls,.csv,.pdf,.pptx"
                className="hidden"
                onChange={handleFileSelect}
              />
              <Button
                onClick={() => fileInputRef.current?.click()}
                className={cn("h-9 px-5 text-sm", theme.btnBg, theme.btnHover)}
              >
                <Upload className="h-3.5 w-3.5 mr-2" />
                Choose File
              </Button>
            </div>
          )}
        </div>

        {/* Download template hint — frosted glass */}
        <div className="group flex items-center gap-4 rounded-2xl border border-border bg-background/70 backdrop-blur-sm p-5 mt-5 transition-[border-color,box-shadow] duration-300 hover:border-muted-foreground/40 hover:shadow-[0_2px_12px_hsl(var(--muted-foreground)/0.04)]">
          <div
            className={cn(
              "flex items-center justify-center w-10 h-10 rounded-xl flex-shrink-0",
              theme.iconBg,
            )}
          >
            <FileSpreadsheet className={cn("h-4.5 w-4.5", theme.iconText)} />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-sm text-foreground">
              Don't have a template yet?
            </p>
            <p className="text-sm text-muted-foreground mt-0.5">
              Download one first, fill it out, then come back to upload.
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => downloadTemplate()}
            className="flex-shrink-0 h-9 px-4 text-xs border-border text-foreground hover:bg-muted"
          >
            <Download className="h-3.5 w-3.5 mr-1.5" />
            Download
          </Button>
        </div>
      </div>
    </div>
  );
}
