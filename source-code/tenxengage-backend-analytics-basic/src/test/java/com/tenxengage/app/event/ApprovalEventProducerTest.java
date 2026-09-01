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
class ApprovalEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private ApprovalEventProducer producer;

    @Test
    void publish_sendsToCorrectTopic() {
        UUID incentiveId = UUID.randomUUID();
        ApprovalRequestEvent event = new ApprovalRequestEvent(
                incentiveId,
                "Test Incentive",
                1,
                List.of(new ApprovalRequestEvent.ApproverInfo("approver@example.com", "MANAGER")));

        producer.publish(event);

        verify(kafkaTemplate).send(eq("approval-events"), eq(incentiveId.toString()), anyString());
    }

    @Test
    void publish_usesIncentiveIdAsKey() {
        UUID incentiveId = UUID.randomUUID();
        ApprovalRequestEvent event = new ApprovalRequestEvent(
                incentiveId,
                "Another Incentive",
                2,
                List.of(new ApprovalRequestEvent.ApproverInfo("other@example.com", "DIRECTOR")));

        producer.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo(incentiveId.toString());
    }
}
