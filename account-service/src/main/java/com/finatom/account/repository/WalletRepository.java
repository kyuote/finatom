package com.finatom.account.repository;

import com.finatom.account.domain.Wallet;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class WalletRepository {
    private final DSLContext dsl;

    public WalletRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public Wallet save(UUID accountId, String currency) {
        dsl.insertInto(table("wallets"))
            .columns(field("account_id"), field("currency"))
            .values(accountId, currency)
            .execute();
        return findByAccountIdAndCurrency(accountId, currency).orElseThrow();
    }

    private Optional<Wallet> findByAccountIdAndCurrency(UUID accountId, String currency) {
        return dsl.selectFrom(table("wallets"))
            .where(field("account_id").eq(accountId).and(field("currency").eq(currency)))
            .fetchOptional(r -> new Wallet(
                r.get(field("id"), UUID.class),
                r.get(field("account_id"), UUID.class),
                r.get(field("currency"), String.class),
                r.get(field("balance"), BigDecimal.class),
                r.get(field("updated_at"), Timestamp.class).toLocalDateTime()
            ));
    }

    public List<Wallet> findByAccountId(UUID accountId) {
        return dsl.selectFrom(table("wallets"))
            .where(field("account_id").eq(accountId))
            .fetch(r -> new Wallet(
                r.get(field("id"), UUID.class),
                r.get(field("account_id"), UUID.class),
                r.get(field("currency"), String.class),
                r.get(field("balance"), BigDecimal.class),
                r.get(field("updated_at"), Timestamp.class).toLocalDateTime()
            ));
    }
}
