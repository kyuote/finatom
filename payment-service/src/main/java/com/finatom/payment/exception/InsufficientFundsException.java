package com.finatom.payment.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID walletId, BigDecimal available, BigDecimal required) {
        super("Insufficient funds in wallet " + walletId +
              ": available " + available + ", required " + required);
    }
}
