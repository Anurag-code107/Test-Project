package com.tenxengage.app.event;

import com.tenxengage.app.service.JourneyCompletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompletionEventConsumerTest {

    @Mock
    private JourneyCompletionService journeyCompletionService;

    @InjectMocks
    private CompletionEventConsumer consumer;

    @Test
    void handleCompletionEvent_validJson_callsJourneyService() {
        UUID clientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID incentiveId = UUID.randomUUID();
        UUID completionId = UUID.randomUUID();
        String completedAt = Instant.now().toString();
        String json = String.format(
                "{\"clientId\":\"%s\",\"userId\":\"%s\",\"incentiveId\":\"%s\"," +
                "\"completionId\":\"%s\",\"incentiveType\":\"TRAINING\",\"completedAt\":\"%s\"}",
                clientId, userId, incentiveId, completionId, completedAt);

        consumer.handleCompletionEvent(json);

        verify(journeyCompletionService).onIncentiveCompleted(clientId, userId, incentiveId);
    }

    @Test
    void handleCompletionEvent_invalidJson_doesNotCallJourneyService() {
        consumer.handleCompletionEvent("bad");

        verify(journeyCompletionService, never()).onIncentiveCompleted(any(), any(), any());
    }
}
