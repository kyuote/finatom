package com.finatom.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentCommand(
    UUID fromWalletId,
    UUID toWalletId,
    BigDecimal amount,
    String currency,
    String idempotencyKey
) {}
