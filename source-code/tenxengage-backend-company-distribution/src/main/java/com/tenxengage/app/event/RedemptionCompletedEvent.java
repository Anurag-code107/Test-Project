package com.tenxengage.app.event;

import com.tenxengage.app.entity.RedemptionRequest;
import org.springframework.context.ApplicationEvent;

public class RedemptionCompletedEvent extends ApplicationEvent {

    private final RedemptionRequest request;

    public RedemptionCompletedEvent(Object source, RedemptionRequest request) {
        super(source);
        this.request = request;
    }

    public RedemptionRequest getRequest() {
        return request;
    }
}
