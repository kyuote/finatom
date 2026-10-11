package com.finatom.payment.repository;

import com.finatom.payment.domain.CreatePaymentCommand;
import com.finatom.payment.domain.Payment;
import com.finatom.payment.domain.PaymentStatus;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class PaymentRepository {

    private final DSLContext dsl;

    public PaymentRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public Payment save(CreatePaymentCommand cmd) {
        dsl.insertInto(table("payments"))
            .columns(
                field("from_wallet_id"), field("to_wallet_id"),
                field("amount"), field("currency"), field("idempotency_key")
            )
            .values(
                cmd.fromWalletId(), cmd.toWalletId(),
                cmd.amount(), cmd.currency(), cmd.idempotencyKey()
            )
            .execute();
        return findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow();
    }

    public Optional<Payment> findByIdempotencyKey(String key) {
        return dsl.selectFrom(table("payments"))
            .where(field("idempotency_key").eq(key))
            .fetchOptional(r -> new Payment(
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

    public void updateStatus(UUID id, PaymentStatus status) {
        dsl.execute(
            "UPDATE payments SET status = ?::payment_status WHERE id = ?",
            status.name(), id
        );
    }
}
