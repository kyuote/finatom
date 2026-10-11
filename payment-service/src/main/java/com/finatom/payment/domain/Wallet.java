package com.finatom.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Wallet(
    UUID id,
    UUID accountId,
    String currency,
    BigDecimal balance,
    LocalDateTime updatedAt
) {}
