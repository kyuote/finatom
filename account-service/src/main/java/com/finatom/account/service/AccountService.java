package com.finatom.account.service;

import com.finatom.account.domain.Account;
import com.finatom.account.domain.Wallet;
import com.finatom.account.repository.AccountRepository;
import com.finatom.account.repository.WalletRepository;

import java.util.List;
import java.util.UUID;


public class AccountService {
    private static final List<String> DEFAULT_CURRENCIES = List.of("USD", "EUR", "GBP");

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;

    public AccountService(AccountRepository accountRepository, WalletRepository walletRepository) {
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
    }

    public Account createAccount(String name, String email) {
        Account account = accountRepository.save(name, email);
        DEFAULT_CURRENCIES.forEach(currency -> walletRepository.save(account.id(), currency));
        return account;
    }

    public Account getAccount(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    public List<Wallet> getWallets(UUID accountId) {
        return walletRepository.findByAccountId(accountId);
    }
}

