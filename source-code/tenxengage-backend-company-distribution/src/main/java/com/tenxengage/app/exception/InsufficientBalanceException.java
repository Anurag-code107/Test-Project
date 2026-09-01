package com.tenxengage.app.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(UUID walletId, BigDecimal available, BigDecimal requested) {
        super(String.format("Insufficient balance in wallet %s: available=%s, requested=%s",
                walletId, available.toPlainString(), requested.toPlainString()));
    }
}
