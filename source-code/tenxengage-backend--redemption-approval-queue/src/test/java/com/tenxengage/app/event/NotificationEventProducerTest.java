package com.tenxengage.app.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private NotificationEventProducer producer;

    @Test
    void publish_sendsToCorrectTopic() {
        UUID clientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                "DEAL_CLAIMED", clientId, "Deal Claimed", "You claimed a deal",
                "CLAIM", UUID.randomUUID(), UUID.randomUUID(), null, null);

        producer.publish(event);

        verify(kafkaTemplate).send(eq("notification-events"), eq(clientId.toString()), anyString());
    }

    @Test
    void publish_usesClientIdAsKey() {
        UUID clientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                "INCENTIVE_ACTIVATED", clientId, "Active", "Incentive is active",
                "INCENTIVE", UUID.randomUUID(), null, null, null);

        producer.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo(clientId.toString());
    }

    @Test
    void publish_serializesEventAsJson() {
        UUID clientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                "TEST_TYPE", clientId, "Title", "Body",
                "RESOURCE", UUID.randomUUID(), null, null, null);

        producer.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("TEST_TYPE");
        assertThat(payload).contains("Title");
        assertThat(payload).contains(clientId.toString());
    }

    @Test
    void publish_usesTypeKeyWhenClientIdNull() {
        NotificationEvent event = new NotificationEvent(
                "SYSTEM_EVENT", null, "System", "System event",
                "SYSTEM", null, null, null, null);

        producer.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("SYSTEM_EVENT");
    }
}
