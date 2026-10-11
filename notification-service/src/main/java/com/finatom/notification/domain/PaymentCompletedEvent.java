package com.finatom.notification.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCompletedEvent(
    UUID paymentId,
    UUID fromWalletId,
    UUID toWalletId,
    BigDecimal amount,
    String currency,
    LocalDateTime completedAt
) {}
