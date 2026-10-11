package com.finatom.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.account.controller.AccountController;
import com.finatom.account.grpc.WalletValidationGrpcService;
import com.finatom.account.repository.AccountRepository;
import com.finatom.account.repository.WalletRepository;
import com.finatom.account.service.AccountService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.port;

public class AccountApplication {

    public static void main(String[] args) throws Exception {
        Config cfg = ConfigFactory.load();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        Flyway.configure().dataSource(dataSource).outOfOrder(true).load().migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);

        AccountRepository accountRepository = new AccountRepository(dataSource);
        WalletRepository walletRepository   = new WalletRepository(dataSource);
        AccountService accountService       = new AccountService(accountRepository, walletRepository);

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        AccountController controller = new AccountController(accountService, mapper);

        int grpcPort = cfg.getInt("grpc.port");
        Server grpcServer = ServerBuilder.forPort(grpcPort)
            .addService(new WalletValidationGrpcService(dsl))
            .build()
            .start();

        Runtime.getRuntime().addShutdownHook(new Thread(grpcServer::shutdown));

        port(cfg.getInt("server.port"));
        before((req, res) -> req.attribute("t", System.currentTimeMillis()));
        after((req, res) -> System.out.printf("[account-service] %s %s -> %d (%dms)%n",
            req.requestMethod(), req.pathInfo(), res.status(),
            System.currentTimeMillis() - (long) req.attribute("t")));
        get("/health", (req, res) -> "OK");
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.status(500);
            res.body(e.getClass().getSimpleName() + ": " + e.getMessage());
        });
        controller.registerRoutes();
    }
}
