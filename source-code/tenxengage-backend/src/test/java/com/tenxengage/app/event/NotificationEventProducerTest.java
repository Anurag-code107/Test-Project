package com.tenxengage.app.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private NotificationEventProducer producer;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper (Spring-managed equivalent) — the producer injects it via constructor
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new NotificationEventProducer(kafkaTemplate, objectMapper);

        // Stub kafkaTemplate.send() to return a completed future (prevents NPE in .whenComplete)
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

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

    @Test
    void publish_handlesKafkaSendFuture_withWhenComplete() {
        UUID clientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                "BALANCE_EXPIRING_SOON", clientId, "Title", "Body",
                "BALANCE_EXPIRY_NOTICE", UUID.randomUUID(), null, null, null);

        // kafkaTemplate.send() already stubbed to return a completed future in setUp()
        // This test just asserts the call completes without exception (not NPE from .whenComplete)
        producer.publish(event);

        verify(kafkaTemplate).send(eq("notification-events"), eq(clientId.toString()), anyString());
    }
}
