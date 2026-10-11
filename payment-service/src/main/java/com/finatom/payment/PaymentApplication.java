package com.finatom.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.payment.controller.PaymentController;
import com.finatom.payment.repository.LedgerRepository;
import com.finatom.payment.repository.PaymentRepository;
import com.finatom.payment.repository.WalletRepository;
import com.finatom.payment.service.ExchangeRateService;
import com.finatom.payment.service.IdempotencyService;
import com.finatom.payment.service.PaymentService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static spark.Spark.exception;
import static spark.Spark.port;

public class PaymentApplication {

    public static void main(String[] args) {
        Config cfg = ConfigFactory.load();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        Flyway.configure()
            .dataSource(dataSource)
            .ignoreMigrationPatterns("*:missing")
            .load()
            .migrate();

        JedisPool jedisPool = new JedisPool(
            new JedisPoolConfig(),
            cfg.getString("redis.host"),
            cfg.getInt("redis.port")
        );

        PaymentRepository paymentRepository   = new PaymentRepository(dataSource);
        LedgerRepository ledgerRepository     = new LedgerRepository(dataSource);
        WalletRepository walletRepository     = new WalletRepository(dataSource);
        IdempotencyService idempotencyService = new IdempotencyService(jedisPool);
        ExchangeRateService exchangeRateService = new ExchangeRateService();

        PaymentService paymentService = new PaymentService(
            paymentRepository, ledgerRepository, walletRepository,
            idempotencyService, exchangeRateService
        );

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PaymentController controller = new PaymentController(paymentService, mapper);

        port(cfg.getInt("server.port"));
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.status(500);
            res.body(e.getClass().getSimpleName() + ": " + e.getMessage());
        });
        controller.registerRoutes();
    }
}
