package com.finatom.antifraud.repository;

import com.finatom.antifraud.domain.AlertReason;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class FraudAlertRepository {

    private final DSLContext dsl;

    public FraudAlertRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public void save(UUID paymentId, UUID walletId, AlertReason reason) {
        dsl.insertInto(table("fraud_alerts"))
            .columns(field("payment_id"), field("wallet_id"), field("reason"))
            .values(paymentId, walletId, reason.name())
            .execute();
    }

    public int countRecentPayments(UUID walletId, LocalDateTime since) {
        return dsl.fetchCount(
            dsl.selectFrom(table("payments"))
                .where(field("from_wallet_id").eq(walletId))
                .and(field("created_at").greaterThan(since))
        );
    }
}
