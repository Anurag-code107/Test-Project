import { useState, useCallback, useRef } from "react";
import type { DealQualifierRequest } from "@/types/deal-qualifier.types";

const API_BASE_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1";

export function useDealQualifierInsights() {
  const [insight, setInsight] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const startStreaming = useCallback(
    async (incentiveId: string, dealInput: DealQualifierRequest) => {
      // Abort any previous stream
      if (abortRef.current) {
        abortRef.current.abort();
      }

      const controller = new AbortController();
      abortRef.current = controller;

      setInsight("");
      setError(null);
      setIsStreaming(true);

      try {
        const response = await fetch(
          `${API_BASE_URL}/deal-qualifier/${incentiveId}/insights`,
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
            },
            credentials: "include",
            body: JSON.stringify(dealInput),
            signal: controller.signal,
          },
        );

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body?.getReader();
        if (!reader) throw new Error("No response body");

        const decoder = new TextDecoder();
        let buffer = "";
        let currentEvent = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith("event:")) {
              currentEvent = trimmed.slice(6).trim();
            } else if (trimmed.startsWith("data:")) {
              const data = trimmed.slice(5).trim();
              if (currentEvent === "insight") {
                setInsight(data);
              } else if (currentEvent === "error") {
                setError(data);
              } else if (currentEvent === "done") {
                setIsStreaming(false);
              }
              currentEvent = "";
            }
          }
        }

        setIsStreaming(false);
      } catch (err) {
        if ((err as Error).name !== "AbortError") {
          setError("Failed to load insights");
          setIsStreaming(false);
        }
      }
    },
    [],
  );

  const stopStreaming = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setIsStreaming(false);
  }, []);

  return { insight, isStreaming, error, startStreaming, stopStreaming };
}
