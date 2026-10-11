package com.finatom.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLedgerEntryCommand(
    UUID walletId,
    BigDecimal amount,
    EntryType type,
    UUID paymentId
) {}
