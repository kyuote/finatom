package com.finatom.payment.client;

import java.util.UUID;

public interface KycValidator {
    void validateKyc(UUID walletId);
}
