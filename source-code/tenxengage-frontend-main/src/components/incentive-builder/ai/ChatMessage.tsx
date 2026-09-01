import Markdown from "react-markdown";
import { Bot, User } from "lucide-react";
import type { ChatMessage as ChatMessageType } from "@/types/builder-state.types";

interface ChatMessageProps {
  message: ChatMessageType;
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === "user";

  return (
    <div className={`flex gap-3 ${isUser ? "justify-end" : "justify-start"}`}>
      {/* AI avatar */}
      {!isUser && (
        <div className="flex items-center justify-center w-9 h-9 rounded-xl bg-primary/10 shrink-0 self-start mt-0.5">
          <Bot className="h-5 w-5 text-primary" />
        </div>
      )}

      {/* Message bubble */}
      <div
        className={`max-w-[80%] rounded-xl px-4 py-3 ${
          isUser
            ? "bg-primary text-primary-foreground"
            : "bg-muted/50 text-foreground"
        }`}
      >
        {isUser ? (
          <p className="text-sm whitespace-pre-line leading-relaxed">
            {message.content}
          </p>
        ) : (
          <div className="text-sm prose-chat leading-relaxed">
            <Markdown
              components={{
                p: ({ children }) => (
                  <p className="mb-2 last:mb-0">{children}</p>
                ),
                strong: ({ children }) => (
                  <strong className="font-semibold">{children}</strong>
                ),
                em: ({ children }) => <em>{children}</em>,
                ul: ({ children }) => (
                  <ul className="list-disc pl-4 mb-2 last:mb-0 space-y-0.5">
                    {children}
                  </ul>
                ),
                ol: ({ children }) => (
                  <ol className="list-decimal pl-4 mb-2 last:mb-0 space-y-0.5">
                    {children}
                  </ol>
                ),
                li: ({ children }) => <li>{children}</li>,
              }}
            >
              {message.content}
            </Markdown>
          </div>
        )}
      </div>

      {/* User avatar */}
      {isUser && (
        <div className="flex items-center justify-center w-9 h-9 rounded-xl bg-muted shrink-0 self-start mt-0.5">
          <User className="h-5 w-5 text-muted-foreground" />
        </div>
      )}
    </div>
  );
}
