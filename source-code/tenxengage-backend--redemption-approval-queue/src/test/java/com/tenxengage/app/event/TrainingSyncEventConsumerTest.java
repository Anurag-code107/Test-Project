package com.tenxengage.app.event;

import com.tenxengage.app.service.TrainingCompletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrainingSyncEventConsumerTest {

    @Mock
    private TrainingCompletionService trainingCompletionService;

    @InjectMocks
    private TrainingSyncEventConsumer consumer;

    @Test
    void handleTrainingSyncEvent_validJson_callsTrainingService() {
        UUID clientId = UUID.randomUUID();
        UUID dataUploadId = UUID.randomUUID();
        UUID externalUserId = UUID.randomUUID();
        String json = String.format(
                "{\"clientId\":\"%s\",\"dataUploadId\":\"%s\"," +
                "\"records\":[{\"externalUserId\":\"%s\"}]}",
                clientId, dataUploadId, externalUserId);

        consumer.handleTrainingSyncEvent(json);

        verify(trainingCompletionService).evaluateTrainingCompletions(clientId, externalUserId);
    }

    @Test
    void handleTrainingSyncEvent_invalidJson_doesNotCallService() {
        consumer.handleTrainingSyncEvent("invalid-json");

        verify(trainingCompletionService, never()).evaluateTrainingCompletions(any(), any());
    }

    @Test
    void handleTrainingSyncEvent_nullClientId_doesNotCallService() {
        UUID dataUploadId = UUID.randomUUID();
        UUID externalUserId = UUID.randomUUID();
        String json = String.format(
                "{\"clientId\":null,\"dataUploadId\":\"%s\"," +
                "\"records\":[{\"externalUserId\":\"%s\"}]}",
                dataUploadId, externalUserId);

        consumer.handleTrainingSyncEvent(json);

        verify(trainingCompletionService, never()).evaluateTrainingCompletions(any(), any());
    }

    @Test
    void handleTrainingSyncEvent_nonUuidExternalId_skipsRecord() {
        UUID clientId = UUID.randomUUID();
        UUID dataUploadId = UUID.randomUUID();
        String json = String.format(
                "{\"clientId\":\"%s\",\"dataUploadId\":\"%s\"," +
                "\"records\":[{\"externalUserId\":\"not-a-uuid\"}]}",
                clientId, dataUploadId);

        consumer.handleTrainingSyncEvent(json);

        verify(trainingCompletionService, never()).evaluateTrainingCompletions(any(), any());
    }
}
