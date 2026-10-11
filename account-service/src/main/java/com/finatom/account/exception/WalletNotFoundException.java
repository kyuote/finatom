package com.finatom.account.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(UUID id) {
        super("Wallet not found: " + id);
    }
}
