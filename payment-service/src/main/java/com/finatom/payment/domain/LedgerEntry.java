package com.finatom.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    UUID walletId,
    BigDecimal amount,
    EntryType type,
    UUID paymentId,
    LocalDateTime createdAt
) {}
