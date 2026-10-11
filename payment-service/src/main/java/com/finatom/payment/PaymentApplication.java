package com.finatom.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.payment.client.AccountServiceClient;
import com.finatom.payment.controller.PaymentController;
import com.finatom.payment.messaging.PaymentEventPublisher;
import com.finatom.payment.metrics.PaymentMetrics;
import com.finatom.payment.repository.WalletRepository;
import com.finatom.payment.service.ExchangeRateService;
import com.finatom.payment.service.IdempotencyService;
import com.finatom.payment.service.PaymentService;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.port;

public class PaymentApplication {

    public static void main(String[] args) {
        Config cfg = ConfigFactory.load();

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        JedisPool jedisPool = new JedisPool(
            new JedisPoolConfig(),
            cfg.getString("redis.host"),
            cfg.getInt("redis.port")
        );

        DSLContext dsl = DSL.using(dataSource, SQLDialect.POSTGRES);

        WalletRepository walletRepository       = new WalletRepository(dataSource);
        IdempotencyService idempotencyService   = new IdempotencyService(jedisPool);
        ExchangeRateService exchangeRateService = new ExchangeRateService(jedisPool, registry);
        AccountServiceClient accountServiceClient = new AccountServiceClient(
            cfg.getString("account-service.grpc-host"),
            cfg.getInt("account-service.grpc-port")
        );

        var kafkaProps = new java.util.Properties();
        kafkaProps.put("bootstrap.servers", cfg.getString("kafka.bootstrap-servers"));
        kafkaProps.put("key.serializer", StringSerializer.class.getName());
        kafkaProps.put("value.serializer", StringSerializer.class.getName());
        kafkaProps.put("acks", "all");
        PaymentEventPublisher eventPublisher = new PaymentEventPublisher(new KafkaProducer<>(kafkaProps));

        PaymentMetrics paymentMetrics = new PaymentMetrics(registry);
        PaymentService paymentService = new PaymentService(
            dsl, walletRepository, idempotencyService, exchangeRateService,
            accountServiceClient, eventPublisher, paymentMetrics
        );

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PaymentController controller = new PaymentController(paymentService, mapper);

        port(cfg.getInt("server.port"));
        before((req, res) -> req.attribute("t", System.currentTimeMillis()));
        after((req, res) -> System.out.printf("[payment-service] %s %s -> %d (%dms)%n",
            req.requestMethod(), req.pathInfo(), res.status(),
            System.currentTimeMillis() - (long) req.attribute("t")));
        get("/health", (req, res) -> "OK");
        get("/metrics", (req, res) -> {
            res.type("text/plain; version=0.0.4");
            return registry.scrape();
        });
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.status(500);
            res.body(e.getClass().getSimpleName() + ": " + e.getMessage());
        });
        controller.registerRoutes();
    }
}
