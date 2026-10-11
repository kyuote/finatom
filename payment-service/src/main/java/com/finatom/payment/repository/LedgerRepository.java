package com.finatom.payment.repository;

import com.finatom.payment.domain.CreateLedgerEntryCommand;
import com.finatom.payment.domain.EntryType;
import com.finatom.payment.domain.LedgerEntry;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class LedgerRepository {

    private final DSLContext dsl;

    public LedgerRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public void save(CreateLedgerEntryCommand cmd) {
        dsl.execute(
            "INSERT INTO ledger (wallet_id, amount, type, payment_id) VALUES (?, ?, ?::entry_type, ?)",
            cmd.walletId(), cmd.amount(), cmd.type().name(), cmd.paymentId()
        );
    }

    public List<LedgerEntry> findByWalletId(UUID walletId) {
        return dsl.selectFrom(table("ledger"))
            .where(field("wallet_id").eq(walletId))
            .fetch(r -> new LedgerEntry(
                r.get(field("id"), UUID.class),
                r.get(field("wallet_id"), UUID.class),
                r.get(field("amount"), BigDecimal.class),
                EntryType.valueOf(r.get(field("type"), String.class)),
                r.get(field("payment_id"), UUID.class),
                r.get(field("created_at"), Timestamp.class).toLocalDateTime()
            ));
    }
}
