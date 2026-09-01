import { useState, useCallback } from "react";

export function useRecommendationInsights() {
  const [insight, setInsight] = useState<string>("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startStreaming = useCallback(async (type: string, targetId: string) => {
    setIsStreaming(true);
    setInsight("");
    setError(null);

    const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";
    try {
      const response = await fetch(
        `${baseURL}/recommendations/${type}/${targetId}/insight`,
        { method: "POST", credentials: "include" },
      );
      if (!response.ok) throw new Error(`Request failed: ${response.status}`);
      if (!response.body) throw new Error("No response body");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      let eventName = "";
      let eventData = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.replace(/\r$/, "");
          if (trimmed.startsWith("event:")) {
            eventName = trimmed.slice(6).trim();
          } else if (trimmed.startsWith("data:")) {
            eventData = trimmed.slice(5).trim();
          } else if (trimmed === "" && eventData) {
            if (eventName === "insight") {
              setInsight(eventData);
            } else if (eventName === "error") {
              setError(eventData);
            }
            eventName = "";
            eventData = "";
          }
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load insights");
    } finally {
      setIsStreaming(false);
    }
  }, []);

  return { insight, isStreaming, error, startStreaming };
}
