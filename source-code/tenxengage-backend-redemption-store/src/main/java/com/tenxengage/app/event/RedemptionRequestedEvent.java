package com.tenxengage.app.event;

import com.tenxengage.app.entity.RedemptionRequest;
import org.springframework.context.ApplicationEvent;

public class RedemptionRequestedEvent extends ApplicationEvent {

    private final RedemptionRequest request;

    public RedemptionRequestedEvent(Object source, RedemptionRequest request) {
        super(source);
        this.request = request;
    }

    public RedemptionRequest getRequest() {
        return request;
    }
}
