package com.finatom.payment.service;

import com.finatom.payment.domain.EntryType;
import com.finatom.payment.domain.Payment;
import com.finatom.payment.domain.PaymentStatus;
import com.finatom.payment.domain.Wallet;
import com.finatom.payment.repository.WalletRepository;
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

    public PaymentService(DSLContext dsl,
                          WalletRepository walletRepository,
                          IdempotencyService idempotencyService,
                          ExchangeRateService exchangeRateService) {
        this.dsl = dsl;
        this.walletRepository = walletRepository;
        this.idempotencyService = idempotencyService;
        this.exchangeRateService = exchangeRateService;
    }

    public Payment createPayment(UUID fromWalletId, UUID toWalletId,
                                 BigDecimal amount, String currency, String idempotencyKey) {
        // Redis fast-path: avoids hitting the DB for obvious duplicates
        if (idempotencyService.exists(idempotencyKey)) {
            return findPaymentByKey(dsl, idempotencyKey);
        }

        Wallet fromWallet = walletRepository.findById(fromWalletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + fromWalletId));
        Wallet toWallet = walletRepository.findById(toWalletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + toWalletId));

        // network call outside the transaction — don't hold a DB connection open for this
        BigDecimal convertedAmount = exchangeRateService.convert(amount, currency, toWallet.currency());

        // fast-fail on stale balance before entering the transaction
        if (fromWallet.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        Payment payment;
        try {
            payment = dsl.transactionResult(ctx -> {
                DSLContext tx = DSL.using(ctx);

                tx.insertInto(table("payments"))
                    .columns(
                        field("from_wallet_id"), field("to_wallet_id"),
                        field("amount"), field("currency"), field("idempotency_key")
                    )
                    .values(fromWalletId, toWalletId, amount, currency, idempotencyKey)
                    .execute();

                UUID paymentId = tx.select(field("id"))
                    .from(table("payments"))
                    .where(field("idempotency_key").eq(idempotencyKey))
                    .fetchOne(r -> r.get(field("id"), UUID.class));

                tx.execute(
                    "INSERT INTO ledger (wallet_id, amount, type, payment_id) VALUES (?, ?, ?::entry_type, ?)",
                    fromWalletId, amount.negate(), EntryType.DEBIT.name(), paymentId
                );
                tx.execute(
                    "INSERT INTO ledger (wallet_id, amount, type, payment_id) VALUES (?, ?, ?::entry_type, ?)",
                    toWalletId, convertedAmount, EntryType.CREDIT.name(), paymentId
                );

                // atomic debit: if balance dropped below amount between the fast-fail check
                // above and now, this UPDATE matches 0 rows and we throw before crediting
                int debited = tx.execute(
                    "UPDATE wallets SET balance = balance - ?, updated_at = now() WHERE id = ? AND balance >= ?",
                    amount, fromWalletId, amount
                );
                if (debited == 0) {
                    throw new IllegalStateException("Insufficient funds");
                }

                tx.execute("UPDATE wallets SET balance = balance + ?, updated_at = now() WHERE id = ?",
                    convertedAmount, toWalletId);

                tx.execute("UPDATE payments SET status = ?::payment_status WHERE id = ?",
                    PaymentStatus.COMPLETED.name(), paymentId);

                return findPaymentByKey(tx, idempotencyKey);
            });
        } catch (DataAccessException e) {
            // Two concurrent requests with the same key both passed the Redis check;
            // the DB unique constraint on idempotency_key stops the second one here.
            if (isUniqueViolation(e)) {
                return findPaymentByKey(dsl, idempotencyKey);
            }
            throw e;
        }

        // best-effort: if Redis is down the payment still completed
        idempotencyService.store(idempotencyKey, payment.id().toString());

        return payment;
    }

    private static boolean isUniqueViolation(DataAccessException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SQLException sqlEx) {
            return PSQLState.UNIQUE_VIOLATION.getState().equals(sqlEx.getSQLState());
        }
        return false;
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
}
