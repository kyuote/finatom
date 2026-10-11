package com.finatom.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.account.controller.AccessLogFilter;
import com.finatom.account.controller.AccountController;
import com.finatom.account.grpc.WalletValidationGrpcService;
import com.finatom.account.repository.AccountRepository;
import com.finatom.account.repository.WalletRepository;
import com.finatom.account.service.AccountService;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class AccountAppConfig {
    public final AccountController controller;
    public final AccessLogFilter accessLogFilter;
    public final Server grpcServer;

    public AccountAppConfig(Config cfg) throws Exception {
        var dataSource = buildDataSource(cfg);
        Flyway.configure().dataSource(dataSource).outOfOrder(true).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        var accountRepository = new AccountRepository(dataSource);
        var walletRepository  = new WalletRepository(dataSource);
        var accountService    = new AccountService(accountRepository, walletRepository);
        var mapper            = buildObjectMapper();

        this.controller      = new AccountController(accountService, mapper);
        this.accessLogFilter = new AccessLogFilter();
        this.grpcServer      = ServerBuilder.forPort(cfg.getInt("grpc.port"))
                                   .addService(new WalletValidationGrpcService(dsl))
                                   .build()
                                   .start();
    }

    private static HikariDataSource buildDataSource(Config cfg) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        return new HikariDataSource(hikariConfig);
    }

    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
