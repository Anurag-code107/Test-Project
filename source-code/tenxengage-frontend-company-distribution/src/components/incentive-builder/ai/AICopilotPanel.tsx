import { useState, useRef, useEffect, useMemo } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sparkles } from "lucide-react";
import { ThinkingDivider, HUMP_HEIGHT } from "./ThinkingDivider";
import { ChatMessage } from "./ChatMessage";
import { ChatInput } from "./ChatInput";
import { SuggestionChips } from "./SuggestionChips";
import { CreateConfirmationCard } from "./CreateConfirmationCard";
import { BUILDER_STEPS, STEP_LABELS } from "@/types/builder-state.types";
import type {
  ChatMessage as ChatMessageType,
  BuilderStep,
} from "@/types/builder-state.types";
import { INCENTIVE_TYPE_LABELS } from "@/types/incentive.types";
import { useAiChat } from "@/hooks/useAiChat";
import { useCreateFromBuilder } from "@/hooks/useCreateFromBuilder";

const THINKING_PHRASES = [
  "Thinking...",
  "Crunching the numbers...",
  "Cooking something up...",
  "One sec, almost there...",
  "On it...",
  "Let me work my magic...",
  "Pulling it together...",
  "Brewing up a response...",
];

interface AICopilotPanelProps {
  onComplete?: () => void;
  navigateTo?: string;
}

function makeMsg(role: "user" | "assistant", content: string): ChatMessageType {
  return {
    id: `${role}-${Date.now()}-${Math.random()}`,
    role,
    content,
    timestamp: new Date().toISOString(),
  };
}

export function AICopilotPanel({
  onComplete,
  navigateTo,
}: AICopilotPanelProps = {}) {
  const { state, dispatch } = useBuilder();
  const [inputValue, setInputValue] = useState("");
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);

  const {
    sendMessage,
    sendMessageWithFile,
    cancel,
    isStreaming,
    isFillingFields,
    streamingText,
    suggestions,
  } = useAiChat();
  const { execute: executeCreate } = useCreateFromBuilder({
    navigateTo,
    onSuccess: onComplete,
  });

  // Pick a random thinking phrase once per streaming session
  const thinkingPhraseRef = useRef<string>(THINKING_PHRASES[0]!);
  const wasStreamingRef = useRef(false);
  useEffect(() => {
    if (isStreaming && !wasStreamingRef.current) {
      thinkingPhraseRef.current =
        THINKING_PHRASES[Math.floor(Math.random() * THINKING_PHRASES.length)] ??
        THINKING_PHRASES[0]!;
    }
    wasStreamingRef.current = isStreaming;
  }, [isStreaming]);

  const thinkingLabel = isFillingFields
    ? "Filling in fields..."
    : thinkingPhraseRef.current;

  const typeName = state.basics.incentiveType
    ? INCENTIVE_TYPE_LABELS[state.basics.incentiveType]
    : "Incentive";

  const completedCount = state.completedSteps.length;
  const totalSteps = BUILDER_STEPS.length;

  const origin = state.builderOrigin;

  const welcomeMessage = useMemo(() => {
    if (origin === "edit") {
      return makeMsg(
        "assistant",
        `Hey! Looks like you're working on "${state.basics.name || typeName}". I can help you tweak any section. What would you like to change?`,
      );
    }
    if (origin === "existing" || origin === "template") {
      return makeMsg(
        "assistant",
        `Hey! I see you've pulled in a ${typeName} template with some fields already set. Let me know what you'd like to adjust, or we can knock out the remaining steps together.`,
      );
    }
    return makeMsg(
      "assistant",
      `Hey! Let's build your ${typeName} program together. We'll work through five sections and I'll fill things in as we go.\n\nTo kick things off, what would you like to name this incentive?`,
    );
  }, [origin, typeName, state.basics.name]);

  // Bookend acknowledgments for "silent builders" (manual-only, no chat interaction):
  // 1. First step completed → welcome + ack with remaining steps
  // 2. All steps completed → congrats + prompt next action
  // Middle steps stay silent to avoid being annoying.
  const hasShownFirstAck = useRef(false);
  const hasShownAllDoneAck = useRef(false);

  // First step ack
  useEffect(() => {
    if (
      hasShownFirstAck.current ||
      origin !== "scratch" ||
      state.chatMessages.length > 0 ||
      state.completedSteps.length !== 1
    )
      return;

    hasShownFirstAck.current = true;
    const completedStep = state.completedSteps[0]!;
    const completedLabel = STEP_LABELS[completedStep];
    const remaining = BUILDER_STEPS.filter((s) => s !== completedStep);
    const remainingList = remaining
      .map((s: BuilderStep, i: number) => `${i + 1}. ${STEP_LABELS[s]}`)
      .join("\n");

    // Add the welcome message first so it isn't lost when chatMessages becomes non-empty
    dispatch({ type: "ADD_CHAT_MESSAGE", payload: welcomeMessage });
    dispatch({
      type: "ADD_CHAT_MESSAGE",
      payload: makeMsg(
        "assistant",
        `Nice, **${completedLabel}** is done! If you want a hand with any of the remaining sections, just let me know.\n\n${remainingList}`,
      ),
    });
  }, [
    origin,
    state.completedSteps,
    state.chatMessages.length,
    dispatch,
    welcomeMessage,
  ]);

  // All steps complete ack
  useEffect(() => {
    if (
      hasShownAllDoneAck.current ||
      !hasShownFirstAck.current ||
      state.completedSteps.length !== BUILDER_STEPS.length
    )
      return;

    hasShownAllDoneAck.current = true;
    dispatch({
      type: "ADD_CHAT_MESSAGE",
      payload: makeMsg(
        "assistant",
        `All sections are filled in! Want to preview the forecasting, or should we go ahead and create it?`,
      ),
    });
  }, [state.completedSteps.length, dispatch]);

  // Auto-process a document file queued from the template upload flow.
  // Fires once on mount when pendingDocumentFile is set, then clears it.
  const hasAutoProcessed = useRef(false);
  useEffect(() => {
    if (hasAutoProcessed.current || !state.pendingDocumentFile || isStreaming)
      return;
    hasAutoProcessed.current = true;

    const file = state.pendingDocumentFile;
    dispatch({ type: "SET_PENDING_DOCUMENT", payload: null });

    // Add the welcome message so the chat isn't empty
    dispatch({ type: "ADD_CHAT_MESSAGE", payload: welcomeMessage });

    sendMessageWithFile(
      `I've uploaded a document (${file.name}). Please extract all the incentive details from it and help me pre-fill the builder.`,
      file,
    );
  }, [
    state.pendingDocumentFile,
    isStreaming,
    dispatch,
    welcomeMessage,
    sendMessageWithFile,
  ]);

  const messages: ChatMessageType[] =
    state.chatMessages.length === 0 ? [welcomeMessage] : state.chatMessages;

  // Sync aiLocked with field-fill activity — lock the builder only when the AI is actively
  // dispatching UPDATE_* actions, not during plain text answers.
  useEffect(() => {
    dispatch({ type: "SET_AI_LOCKED", payload: isFillingFields });
  }, [isFillingFields, dispatch]);

  function handlePauseAi() {
    cancel();
    dispatch({ type: "SET_AI_LOCKED", payload: false });
    dispatch({
      type: "ADD_CHAT_MESSAGE",
      payload: makeMsg(
        "assistant",
        "Paused. You have full control of the builder now. Let me know when you'd like to continue.",
      ),
    });
  }

  // Auto-scroll on new messages, streaming text updates, or when
  // streaming starts/stops.  The rAF ensures the spacer height change
  // has been laid out before we compute the scroll target.
  useEffect(() => {
    requestAnimationFrame(() => {
      chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
    });
  }, [messages.length, streamingText, isStreaming]);

  function handleSend(text?: string) {
    const messageText = text ?? inputValue;
    if ((!messageText.trim() && !pendingFile) || isStreaming) return;

    if (state.chatMessages.length === 0) {
      dispatch({ type: "ADD_CHAT_MESSAGE", payload: welcomeMessage });
    }

    setInputValue("");
    sendMessage(messageText.trim());
  }

  function handleSendWithFile(text: string, file: File) {
    if (isStreaming) return;

    if (state.chatMessages.length === 0) {
      dispatch({ type: "ADD_CHAT_MESSAGE", payload: welcomeMessage });
    }

    setInputValue("");
    setPendingFile(null);
    sendMessageWithFile(text, file);
  }

  return (
    <div className="flex flex-col h-full rounded-xl border border-border bg-background overflow-hidden">
      {/* Header */}
      <div className="border-b border-border p-4 shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" />
            <span className="text-2xl font-semibold text-foreground">
              AI Incentive Copilot
            </span>
          </div>
          <span className="text-xs text-muted-foreground tabular-nums">
            Step {Math.min(completedCount + 1, totalSteps)} of {totalSteps}
          </span>
        </div>
      </div>

      {/* Messages + divider + input */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Chat + hump wrapper — relative so the hump can overlay the
            bottom of the scroll area without taking layout space. */}
        <div className="relative flex-1 flex flex-col min-h-0">
          <ScrollArea className="flex-1 p-5">
            <div className="space-y-4">
              {messages.map((msg) => (
                <ChatMessage key={msg.id} message={msg} />
              ))}

              {/* Streaming message */}
              {isStreaming && streamingText && (
                <ChatMessage
                  message={{
                    id: "streaming",
                    role: "assistant",
                    content: streamingText,
                    timestamp: new Date().toISOString(),
                    isStreaming: true,
                  }}
                />
              )}

              {/* Spacer — when the dome is active, push the scroll anchor
                  up by the dome height so auto-scroll positions the last
                  message above the dome rather than behind it. */}
              <div style={{ height: isStreaming ? HUMP_HEIGHT : 0 }} />
              <div ref={chatEndRef} />
            </div>
          </ScrollArea>

          {/* Thinking hump — positioned at the bottom of the chat area.
              The hump dome overlays upward; the flat border sits at the seam. */}
          <ThinkingDivider
            isActive={isStreaming}
            label={thinkingLabel}
            onPause={handlePauseAi}
            showPause={isFillingFields}
          />
        </div>

        {/* Sticky confirmation card — docked above the input, outside chat scroll */}
        <CreateConfirmationCard
          visible={state.pendingCreate}
          onConfirm={async () => {
            await executeCreate();
            dispatch({
              type: "ADD_CHAT_MESSAGE",
              payload: makeMsg(
                "assistant",
                "All set, your incentive is live! Taking you there now.",
              ),
            });
          }}
          onCancel={() => dispatch({ type: "DISMISS_CREATE_CONFIRMATION" })}
        />

        {/* Input area */}
        <div className="px-4 py-5 space-y-3">
          <SuggestionChips
            suggestions={suggestions}
            onSelect={(s) => handleSend(s)}
            disabled={isStreaming || state.isCreating}
          />
          <ChatInput
            value={inputValue}
            onChange={setInputValue}
            onSend={(val) => handleSend(val)}
            onSendWithFile={handleSendWithFile}
            disabled={isStreaming || state.isCreating}
            pendingFile={pendingFile}
            onFileSelect={setPendingFile}
          />
        </div>
      </div>
    </div>
  );
}
