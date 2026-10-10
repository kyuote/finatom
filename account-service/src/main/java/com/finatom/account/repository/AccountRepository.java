package com.finatom.account.repository;

import com.finatom.account.domain.KycStatus;
import com.finatom.account.domain.Account;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;


public class AccountRepository {
    private final DSLContext dsl;

    public AccountRepository(DataSource dataSource) {
        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public Account save(String name, String email) {
        dsl.insertInto(table("accounts"))
            .columns(field("name"), field("email"))
            .values(name, email)
            .execute();
        return findByEmail(email).orElseThrow();
    }

    private Optional<Account> findByEmail(String email) {
        return dsl.selectFrom(table("accounts"))
            .where(field("email").eq(email))
            .fetchOptional(r -> new Account(
                r.get(field("id"), UUID.class),
                r.get(field("name"), String.class),
                r.get(field("email"), String.class),
                KycStatus.valueOf(r.get(field("kyc_status"), String.class)),
                r.get(field("created_at"), Timestamp.class).toLocalDateTime()
            ));
    }

    public Optional<Account> findById(UUID id) {
        return dsl.selectFrom(table("accounts"))
            .where(field("id").eq(id))
            .fetchOptional(r -> new Account(
                r.get(field("id"), UUID.class),
                r.get(field("name"), String.class),
                r.get(field("email"), String.class),
                KycStatus.valueOf(r.get(field("kyc_status"), String.class)),
                r.get(field("created_at"), Timestamp.class).toLocalDateTime()
            ));
    }
}
