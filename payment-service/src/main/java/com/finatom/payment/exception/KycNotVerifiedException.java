package com.finatom.payment.exception;

import java.util.UUID;

public class KycNotVerifiedException extends RuntimeException {
    public KycNotVerifiedException(UUID walletId) {
        super("KYC not verified for wallet owner: " + walletId);
    }
}
