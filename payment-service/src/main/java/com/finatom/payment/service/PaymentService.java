package com.finatom.payment.service;

import com.finatom.payment.client.KycValidator;
import com.finatom.payment.domain.EntryType;
import com.finatom.payment.domain.Payment;
import com.finatom.payment.domain.PaymentCompletedEvent;
import com.finatom.payment.domain.PaymentStatus;
import com.finatom.payment.exception.InsufficientFundsException;
import com.finatom.payment.exception.WalletNotFoundException;
import com.finatom.payment.messaging.PaymentEventPublisher;
import com.finatom.payment.metrics.PaymentMetrics;
import com.finatom.payment.repository.WalletRepository;
import io.micrometer.core.instrument.Timer;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.postgresql.util.PSQLState;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class PaymentService {

    private final DSLContext dsl;
    private final WalletRepository walletRepository;
    private final IdempotencyService idempotencyService;
    private final ExchangeRateService exchangeRateService;
    private final KycValidator kycValidator;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentMetrics metrics;

    public PaymentService(DSLContext dsl,
                          WalletRepository walletRepository,
                          IdempotencyService idempotencyService,
                          ExchangeRateService exchangeRateService,
                          KycValidator kycValidator,
                          PaymentEventPublisher eventPublisher,
                          PaymentMetrics metrics) {
        this.dsl = dsl;
        this.walletRepository = walletRepository;
        this.idempotencyService = idempotencyService;
        this.exchangeRateService = exchangeRateService;
        this.kycValidator = kycValidator;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public Payment createPayment(UUID fromWalletId, UUID toWalletId,
                                 BigDecimal amount, String currency, String idempotencyKey) {
        Timer.Sample sample = Timer.start();
        try {
            return doCreatePayment(fromWalletId, toWalletId, amount, currency, idempotencyKey);
        } finally {
            sample.stop(metrics.processingTimer());
        }
    }

    private Payment doCreatePayment(UUID fromWalletId, UUID toWalletId,
                                    BigDecimal amount, String currency, String idempotencyKey) {
        if (idempotencyService.exists(idempotencyKey)) {
            return findPaymentByKey(dsl, idempotencyKey);
        }

        kycValidator.validateKyc(fromWalletId);
        kycValidator.validateKyc(toWalletId);

        var from = walletRepository.findById(fromWalletId)
            .orElseThrow(() -> new WalletNotFoundException(fromWalletId));
        var to = walletRepository.findById(toWalletId)
            .orElseThrow(() -> new WalletNotFoundException(toWalletId));

        BigDecimal converted = exchangeRateService.convert(amount, currency, to.currency());

        if (from.balance().compareTo(amount) < 0) {
            metrics.recordFailed();
            throw new InsufficientFundsException(fromWalletId, from.balance(), amount);
        }

        return transfer(fromWalletId, toWalletId, amount, converted, currency, idempotencyKey);
    }

    private Payment transfer(UUID fromWalletId, UUID toWalletId,
                              BigDecimal amount, BigDecimal converted,
                              String currency, String idempotencyKey) {
        try {
            Payment payment = dsl.transactionResult(ctx -> {
                DSLContext tx = DSL.using(ctx);
                UUID paymentId = insertPayment(tx, fromWalletId, toWalletId, amount, currency, idempotencyKey);
                insertLedgerEntries(tx, fromWalletId, toWalletId, amount, converted, paymentId);
                debitOrFail(tx, fromWalletId, amount);
                credit(tx, toWalletId, converted);
                markCompleted(tx, paymentId);
                return findPaymentByKey(tx, idempotencyKey);
            });
            // best-effort: Redis and Kafka failures do not fail a completed payment
            idempotencyService.store(idempotencyKey, payment.id().toString());
            eventPublisher.publish(new PaymentCompletedEvent(
                payment.id(), fromWalletId, toWalletId, amount, currency, payment.createdAt()
            ));
            metrics.recordCompleted();
            return payment;
        } catch (DataAccessException e) {
            if (isUniqueViolation(e)) return findPaymentByKey(dsl, idempotencyKey);
            metrics.recordFailed();
            throw e;
        }
    }

    private UUID insertPayment(DSLContext tx, UUID fromWalletId, UUID toWalletId,
                                BigDecimal amount, String currency, String idempotencyKey) {
        tx.insertInto(table("payments"))
            .columns(
                field("from_wallet_id"), field("to_wallet_id"),
                field("amount"), field("currency"), field("idempotency_key")
            )
            .values(fromWalletId, toWalletId, amount, currency, idempotencyKey)
            .execute();
        return tx.select(field("id"))
            .from(table("payments"))
            .where(field("idempotency_key").eq(idempotencyKey))
            .fetchOne(r -> r.get(field("id"), UUID.class));
    }

    private void insertLedgerEntries(DSLContext tx, UUID fromWalletId, UUID toWalletId,
                                     BigDecimal amount, BigDecimal converted, UUID paymentId) {
        tx.execute(
            "INSERT INTO ledger (wallet_id, amount, type, payment_id) VALUES (?, ?, ?::entry_type, ?)",
            fromWalletId, amount.negate(), EntryType.DEBIT.name(), paymentId
        );
        tx.execute(
            "INSERT INTO ledger (wallet_id, amount, type, payment_id) VALUES (?, ?, ?::entry_type, ?)",
            toWalletId, converted, EntryType.CREDIT.name(), paymentId
        );
    }

    private void debitOrFail(DSLContext tx, UUID walletId, BigDecimal amount) {
        int rows = tx.execute(
            "UPDATE wallets SET balance = balance - ?, updated_at = now() WHERE id = ? AND balance >= ?",
            amount, walletId, amount
        );
        if (rows == 0) throw new InsufficientFundsException(walletId, BigDecimal.ZERO, amount);
    }

    private void credit(DSLContext tx, UUID walletId, BigDecimal amount) {
        tx.execute("UPDATE wallets SET balance = balance + ?, updated_at = now() WHERE id = ?",
            amount, walletId);
    }

    private void markCompleted(DSLContext tx, UUID paymentId) {
        tx.execute("UPDATE payments SET status = ?::payment_status WHERE id = ?",
            PaymentStatus.COMPLETED.name(), paymentId);
    }

    private Payment findPaymentByKey(DSLContext ctx, String idempotencyKey) {
        return ctx.selectFrom(table("payments"))
            .where(field("idempotency_key").eq(idempotencyKey))
            .fetchOne(r -> new Payment(
                r.get(field("id"), UUID.class),
                r.get(field("from_wallet_id"), UUID.class),
                r.get(field("to_wallet_id"), UUID.class),
                r.get(field("amount"), BigDecimal.class),
                r.get(field("currency"), String.class),
                PaymentStatus.valueOf(r.get(field("status"), String.class)),
                r.get(field("idempotency_key"), String.class),
                r.get(field("created_at"), Timestamp.class).toLocalDateTime()
            ));
    }

    private static boolean isUniqueViolation(DataAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SQLException sqlEx) {
            return PSQLState.UNIQUE_VIOLATION.getState().equals(sqlEx.getSQLState());
        }
        return false;
    }
}
