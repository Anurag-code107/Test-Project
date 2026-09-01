import type { AiChatRequest, AiChatCallbacks } from "@/types/ai-chat.types";
import axios from "axios";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

/**
 * Attempt a token refresh. Called before SSE requests and on 401 retry,
 * since raw `fetch` doesn't go through the axios interceptor.
 */
async function refreshTokenIfNeeded(): Promise<boolean> {
  try {
    await axios.post(`${baseURL}/auth/refresh`, null, {
      withCredentials: true,
    });
    return true;
  } catch {
    return false;
  }
}

/**
 * Shared SSE stream reader. Parses events from a fetch Response and dispatches
 * to the appropriate callback.
 */
async function readSseStream(
  response: Response,
  callbacks: AiChatCallbacks,
  signal: AbortSignal,
) {
  const reader = response.body?.getReader();
  if (!reader) {
    callbacks.onError("No response stream available");
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "";
  let eventData = "";
  let doneReceived = false;

  while (true) {
    if (signal.aborted) {
      reader.cancel();
      break;
    }
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // Parse SSE events from buffer
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? ""; // Keep incomplete line in buffer

    for (const line of lines) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        eventData = line.slice(5).trim();
      } else if (line === "" && eventName && eventData) {
        // End of event — dispatch
        try {
          const parsed = JSON.parse(eventData);
          switch (eventName) {
            case "text_delta":
              callbacks.onTextDelta(parsed);
              break;
            case "action":
              callbacks.onAction(parsed);
              break;
            case "suggestions":
              callbacks.onSuggestions(parsed);
              break;
            case "done":
              doneReceived = true;
              callbacks.onDone();
              break;
            case "error":
              callbacks.onError(parsed.message ?? "Unknown error");
              break;
          }
        } catch {
          // Skip malformed events
        }
        eventName = "";
        eventData = "";
      }
    }
  }

  // If the stream closed without a "done" SSE event, fire onDone as fallback
  // so the UI doesn't get stuck in a streaming state. Guard against double-firing.
  if (!doneReceived) {
    callbacks.onDone();
  }
}

/**
 * Streams AI chat responses via POST-based SSE.
 * Uses fetch + ReadableStream because EventSource only supports GET.
 * Returns an AbortController for cancellation.
 */
async function fetchWithAuthRetry(
  url: string,
  init: RequestInit,
): Promise<Response> {
  const response = await fetch(url, init);
  if (response.status === 401) {
    const refreshed = await refreshTokenIfNeeded();
    if (refreshed) {
      return fetch(url, init);
    }
  }
  return response;
}

export function streamAiChat(
  request: AiChatRequest,
  callbacks: AiChatCallbacks,
): AbortController {
  const controller = new AbortController();

  (async () => {
    try {
      const response = await fetchWithAuthRetry(`${baseURL}/ai/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(request),
        signal: controller.signal,
      });

      if (!response.ok) {
        const errorText = await response.text();
        callbacks.onError(
          response.status === 503
            ? "AI service is not configured. Please contact your administrator."
            : `Request failed: ${errorText}`,
        );
        return;
      }

      await readSseStream(response, callbacks, controller.signal);
    } catch (error) {
      if (controller.signal.aborted) return; // Expected cancellation
      callbacks.onError(
        error instanceof Error ? error.message : "Connection failed",
      );
    }
  })();

  return controller;
}

/**
 * Streams AI chat responses with an uploaded document.
 * Sends the file + JSON request as multipart/form-data to the
 * /ai/chat-with-document endpoint, then reads the SSE stream.
 */
export function streamAiChatWithDocument(
  request: AiChatRequest,
  file: File,
  callbacks: AiChatCallbacks,
): AbortController {
  const controller = new AbortController();

  (async () => {
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("request", JSON.stringify(request));

      const response = await fetchWithAuthRetry(
        `${baseURL}/ai/chat-with-document`,
        {
          method: "POST",
          credentials: "include",
          body: formData,
          signal: controller.signal,
        },
      );

      if (!response.ok) {
        const errorText = await response.text();
        callbacks.onError(
          response.status === 503
            ? "AI service is not configured. Please contact your administrator."
            : `Request failed: ${errorText}`,
        );
        return;
      }

      await readSseStream(response, callbacks, controller.signal);
    } catch (error) {
      if (controller.signal.aborted) return;
      callbacks.onError(
        error instanceof Error ? error.message : "Connection failed",
      );
    }
  })();

  return controller;
}
