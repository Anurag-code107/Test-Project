package com.tenxengage.app.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published inside the distribution submit transaction; consumed <b>after</b> it commits.
 *
 * <p>Deliberately its own event rather than reusing {@code RedemptionRequestedEvent}. That event's listener
 * publishes to Kafka (producing "your redemption was submitted" copy, wrong for a recipient who did not ask
 * for anything) and runs the personal CASH-INSTANT dispatch path. Keeping distribution dispatch on a separate
 * event means the two flows cannot interfere, and the recipient-facing notification can say what actually
 * happened.</p>
 *
 * <p>Carries only the id: the listener re-reads committed state rather than trusting an entity snapshot taken
 * before commit.</p>
 */
public class CompanyDistributionSubmittedEvent extends ApplicationEvent {

    private final UUID distributionId;

    public CompanyDistributionSubmittedEvent(Object source, UUID distributionId) {
        super(source);
        this.distributionId = distributionId;
    }

    public UUID getDistributionId() {
        return distributionId;
    }
}
