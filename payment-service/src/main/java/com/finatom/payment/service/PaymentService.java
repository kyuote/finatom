package com.finatom.payment.service;

import com.finatom.payment.domain.CreateLedgerEntryCommand;
import com.finatom.payment.domain.CreatePaymentCommand;
import com.finatom.payment.domain.EntryType;
import com.finatom.payment.domain.Payment;
import com.finatom.payment.domain.PaymentStatus;
import com.finatom.payment.repository.LedgerRepository;
import com.finatom.payment.repository.PaymentRepository;
import com.finatom.payment.repository.WalletRepository;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerRepository ledgerRepository;
    private final WalletRepository walletRepository;
    private final IdempotencyService idempotencyService;
    private final ExchangeRateService exchangeRateService;

    public PaymentService(PaymentRepository paymentRepository,
                          LedgerRepository ledgerRepository,
                          WalletRepository walletRepository,
                          IdempotencyService idempotencyService,
                          ExchangeRateService exchangeRateService) {
        this.paymentRepository   = paymentRepository;
        this.ledgerRepository    = ledgerRepository;
        this.walletRepository    = walletRepository;
        this.idempotencyService  = idempotencyService;
        this.exchangeRateService = exchangeRateService;
    }

    public Payment createPayment(UUID fromWalletId, UUID toWalletId,
                                 BigDecimal amount, String currency, String idempotencyKey) {
        if (idempotencyService.exists(idempotencyKey)) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        var fromWallet = walletRepository.findById(fromWalletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + fromWalletId));

        var toWallet = walletRepository.findById(toWalletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + toWalletId));

        // convert amount if wallet currencies differ
        BigDecimal convertedAmount = exchangeRateService.convert(amount, currency, toWallet.currency());

        // check sufficient funds
        if (fromWallet.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        var payment = paymentRepository.save(
            new CreatePaymentCommand(fromWalletId, toWalletId, amount, currency, idempotencyKey)
        );

        // double-entry bookkeeping
        ledgerRepository.save(new CreateLedgerEntryCommand(fromWalletId, amount.negate(), EntryType.DEBIT, payment.id()));
        ledgerRepository.save(new CreateLedgerEntryCommand(toWalletId, convertedAmount, EntryType.CREDIT, payment.id()));

        // update wallet balances
        walletRepository.debit(fromWalletId, amount);
        walletRepository.credit(toWalletId, convertedAmount);

        paymentRepository.updateStatus(payment.id(), PaymentStatus.COMPLETED);

        idempotencyService.store(idempotencyKey, payment.id().toString());

        return paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
    }
}
