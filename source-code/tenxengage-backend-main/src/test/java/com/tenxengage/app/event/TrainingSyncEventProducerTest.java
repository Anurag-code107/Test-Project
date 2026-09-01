package com.tenxengage.app.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrainingSyncEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private TrainingSyncEventProducer producer;

    @Test
    void publish_sendsToCorrectTopic() {
        UUID clientId = UUID.randomUUID();
        TrainingSyncEvent event = new TrainingSyncEvent(
                clientId,
                UUID.randomUUID(),
                List.of(new TrainingSyncEvent.TrainingCompletionRecord(UUID.randomUUID().toString())));

        producer.publish(event);

        verify(kafkaTemplate).send(eq("training-sync-events"), eq(clientId.toString()), anyString());
    }

    @Test
    void publish_usesClientIdAsKey() {
        UUID clientId = UUID.randomUUID();
        TrainingSyncEvent event = new TrainingSyncEvent(
                clientId,
                UUID.randomUUID(),
                List.of(new TrainingSyncEvent.TrainingCompletionRecord(UUID.randomUUID().toString())));

        producer.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo(clientId.toString());
    }
}
