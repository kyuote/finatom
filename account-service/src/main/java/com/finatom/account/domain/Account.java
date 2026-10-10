package com.finatom.account.domain;

import java.time.LocalDateTime;
import java.util.UUID;


public record Account(
    UUID id,
    String name,
    String email,
    KycStatus kycStatus,
    LocalDateTime createdAt
) {}
