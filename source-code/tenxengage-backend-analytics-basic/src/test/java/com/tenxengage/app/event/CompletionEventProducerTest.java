package com.tenxengage.app.event;

import com.tenxengage.app.entity.enums.IncentiveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompletionEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private CompletionEventProducer producer;

    @Test
    void publish_sendsToCorrectTopic() {
        UUID clientId = UUID.randomUUID();
        CompletionEvent event = new CompletionEvent(
                clientId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                IncentiveType.TRAINING,
                Instant.now());

        producer.publish(event);

        verify(kafkaTemplate).send(eq("completion-events"), eq(clientId.toString()), anyString());
    }

    @Test
    void publish_usesClientIdAsKey() {
        UUID clientId = UUID.randomUUID();
        CompletionEvent event = new CompletionEvent(
                clientId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                IncentiveType.SALES,
                Instant.now());

        producer.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo(clientId.toString());
    }

    @Test
    void publish_payloadContainsEventFields() {
        UUID clientId = UUID.randomUUID();
        UUID incentiveId = UUID.randomUUID();
        CompletionEvent event = new CompletionEvent(
                clientId,
                UUID.randomUUID(),
                incentiveId,
                UUID.randomUUID(),
                IncentiveType.ACTIVITY,
                Instant.now());

        producer.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload).contains(clientId.toString());
        assertThat(payload).contains(incentiveId.toString());
        assertThat(payload).contains("ACTIVITY");
    }
}
