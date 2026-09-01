package com.tenxengage.app.event;

import com.tenxengage.app.service.NotificationDispatcher;
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
class NotificationEventConsumerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationEventConsumer consumer;

    @Test
    void handleNotificationEvent_validJson_callsDispatcher() {
        UUID clientId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        String json = String.format(
                "{\"notificationTypeKey\":\"DEAL_CLAIMED\",\"clientId\":\"%s\"," +
                "\"title\":\"Deal Claimed\",\"message\":\"You claimed a deal\"," +
                "\"resourceType\":\"CLAIM\",\"resourceId\":\"%s\"," +
                "\"actorUserId\":null,\"targetUserIds\":null,\"metadata\":null}",
                clientId, resourceId);

        consumer.handleNotificationEvent(json);

        verify(dispatcher).dispatch(any(NotificationEvent.class));
    }

    @Test
    void handleNotificationEvent_invalidJson_doesNotCallDispatcher() {
        consumer.handleNotificationEvent("invalid-json");

        verify(dispatcher, never()).dispatch(any());
    }
}
