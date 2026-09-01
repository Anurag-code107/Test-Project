package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record AiChatRequest(
        @NotEmpty @Size(max = 50)
        List<ChatMessageEntry> conversationHistory,

        @NotNull
        Map<String, Object> currentState,

        String incentiveType
) {
    public record ChatMessageEntry(
            @NotEmpty String role,
            @NotEmpty @Size(max = 10000) String content
    ) {}
}
