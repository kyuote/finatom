package com.finatom.payment.repository;

import com.finatom.payment.domain.Wallet;
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

public class WalletRepository {

    private final DSLContext dsl;

    public WalletRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public Optional<Wallet> findById(UUID id) {
        return dsl.selectFrom(table("wallets"))
            .where(field("id").eq(id))
            .fetchOptional(r -> new Wallet(
                r.get(field("id"), UUID.class),
                r.get(field("account_id"), UUID.class),
                r.get(field("currency"), String.class),
                r.get(field("balance"), BigDecimal.class),
                r.get(field("updated_at"), Timestamp.class).toLocalDateTime()
            ));
    }

    public void debit(UUID walletId, BigDecimal amount) {
        dsl.execute("UPDATE wallets SET balance = balance - ?, updated_at = now() WHERE id = ?",
            amount, walletId);
    }

    public void credit(UUID walletId, BigDecimal amount) {
        dsl.execute("UPDATE wallets SET balance = balance + ?, updated_at = now() WHERE id = ?",
            amount, walletId);
    }
}
