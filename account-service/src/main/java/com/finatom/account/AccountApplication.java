package com.finatom.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finatom.account.controller.AccountController;
import com.finatom.account.repository.AccountRepository;
import com.finatom.account.repository.WalletRepository;
import com.finatom.account.service.AccountService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import static spark.Spark.exception;
import static spark.Spark.port;

public class AccountApplication {
    public static void main(String[] args) {
        Config cfg = ConfigFactory.load();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        Flyway.configure().dataSource(dataSource).load().migrate();

        AccountRepository accountRepository = new AccountRepository(dataSource);
        WalletRepository walletRepository   = new WalletRepository(dataSource);
        AccountService accountService       = new AccountService(accountRepository, walletRepository);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AccountController controller        = new AccountController(accountService, mapper);

        port(cfg.getInt("server.port"));
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.status(500);
            res.body(e.getClass().getSimpleName() + ": " + e.getMessage());
        });
        controller.registerRoutes();
    }
}
