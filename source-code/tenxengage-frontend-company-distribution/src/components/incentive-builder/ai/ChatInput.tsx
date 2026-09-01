import { useLayoutEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Send, Paperclip, FileText, X } from "lucide-react";
import { cn } from "@/lib/utils";

const MIN_TEXTAREA_HEIGHT = 32;
const MAX_TEXTAREA_HEIGHT = 160;

const ACCEPTED_TYPES = ".pdf,.pptx,.xlsx,.xls,.txt,.csv,.md";
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB — must match backend multipart limit

interface ChatInputProps {
  value: string;
  onChange: (value: string) => void;
  onSend: (value: string) => void;
  onSendWithFile?: (value: string, file: File) => void;
  disabled?: boolean;
  pendingFile?: File | null;
  onFileSelect?: (file: File | null) => void;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function ChatInput({
  value,
  onChange,
  onSend,
  onSendWithFile,
  disabled,
  pendingFile,
  onFileSelect,
}: ChatInputProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-resize the textarea as content grows, capped at MAX_TEXTAREA_HEIGHT.
  useLayoutEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(
      Math.max(el.scrollHeight, MIN_TEXTAREA_HEIGHT),
      MAX_TEXTAREA_HEIGHT,
    );
    el.style.height = `${next}px`;
    el.style.overflowY = el.scrollHeight > MAX_TEXTAREA_HEIGHT ? "auto" : "hidden";
  }, [value]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (disabled) return;

    if (pendingFile && onSendWithFile) {
      onSendWithFile(value, pendingFile);
      onFileSelect?.(null);
    } else if (value.trim()) {
      onSend(value);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends, Shift+Enter inserts a newline.
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e as unknown as React.FormEvent);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file && onFileSelect) {
      if (file.size > MAX_FILE_SIZE) {
        alert(
          `File is too large (${formatSize(file.size)}). Maximum size is ${formatSize(MAX_FILE_SIZE)}.`,
        );
        e.target.value = "";
        return;
      }
      onFileSelect(file);
    }
    // Reset the input so the same file can be re-selected
    e.target.value = "";
  }

  const canSend = !disabled && (!!value.trim() || !!pendingFile);

  return (
    <div className="space-y-2">
      {/* File preview chip */}
      {pendingFile && (
        <div className="flex items-center gap-2 px-2 py-1.5 bg-muted/30 border border-border rounded-lg text-sm">
          <FileText className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
          <span className="flex-1 truncate text-foreground">
            {pendingFile.name}
          </span>
          <span className="text-xs text-muted-foreground shrink-0">
            {formatSize(pendingFile.size)}
          </span>
          <button
            type="button"
            onClick={() => onFileSelect?.(null)}
            className="p-0.5 rounded hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex items-end gap-2">
        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_TYPES}
          className="hidden"
          onChange={handleFileChange}
        />

        <button
          type="button"
          disabled={disabled}
          onClick={() => fileInputRef.current?.click()}
          className="flex items-center justify-center w-8 h-8 shrink-0 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          title="Attach a document (PDF, PPTX)"
        >
          <Paperclip className="h-3.5 w-3.5" />
        </button>
        <textarea
          ref={textareaRef}
          rows={1}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={
            pendingFile
              ? "Add a message or send the file..."
              : "Type your message..."
          }
          disabled={disabled}
          className={cn(
            "flex-1 resize-none px-3 py-1.5 text-sm leading-5 rounded-md bg-background",
            "border border-border",
            "placeholder:text-muted-foreground",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 focus-visible:ring-offset-0",
            "disabled:cursor-not-allowed disabled:opacity-50",
          )}
          style={{ minHeight: MIN_TEXTAREA_HEIGHT, maxHeight: MAX_TEXTAREA_HEIGHT }}
        />
        <Button
          type="submit"
          size="icon"
          className="h-8 w-8 shrink-0 bg-primary hover:bg-primary/90"
          disabled={!canSend}
        >
          <Send className="h-3.5 w-3.5" />
        </Button>
      </form>
    </div>
  );
}
