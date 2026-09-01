package com.tenxengage.app.event;

import com.tenxengage.app.entity.RedemptionRequest;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;
import java.util.UUID;

public class RedemptionApprovedEvent extends ApplicationEvent {

    private final RedemptionRequest request;
    private final UUID approverId;

    public RedemptionApprovedEvent(Object source, RedemptionRequest request, UUID approverId) {
        super(source);
        this.request = Objects.requireNonNull(request, "request");
        this.approverId = Objects.requireNonNull(approverId, "approverId");
    }

    public RedemptionRequest getRequest() {
        return request;
    }

    public UUID getApproverId() {
        return approverId;
    }
}
