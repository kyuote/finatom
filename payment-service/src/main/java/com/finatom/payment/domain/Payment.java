package com.finatom.payment.domain;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

public record Payment (
    UUID id,
    UUID fromWalletId,
    UUID toWalletId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String idempotencyKey,
    LocalDateTime createdAt
) {}
