package com.tenxengage.app.exception;

/**
 * A downstream/external service (e.g. XTRM) was unreachable or returned a transient, retryable error —
 * as opposed to a definitive domain rejection (which is a {@link BusinessRuleException} → 422).
 *
 * <p>Maps to <b>503 Service Unavailable</b>: the caller should retry later, and monitoring should treat it
 * as an outage rather than a client error.</p>
 */
public class ExternalServiceException extends RuntimeException {

    private final String errorCode;

    public ExternalServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
