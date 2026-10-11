package com.finatom.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
    UUID fromWalletId,
    UUID toWalletId,
    BigDecimal amount,
    String currency,
    String idempotencyKey
) {}
