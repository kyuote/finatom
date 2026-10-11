package com.finatom.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.payment.client.AccountServiceClient;
import com.finatom.payment.controller.AccessLogFilter;
import com.finatom.payment.controller.PaymentController;
import com.finatom.payment.messaging.PaymentEventPublisher;
import com.finatom.payment.metrics.PaymentMetrics;
import com.finatom.payment.repository.WalletRepository;
import com.finatom.payment.service.ExchangeRateService;
import com.finatom.payment.service.IdempotencyService;
import com.finatom.payment.service.PaymentService;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Properties;

public class PaymentAppConfig {

    public final PrometheusMeterRegistry registry;
    public final PaymentController controller;
    public final AccessLogFilter accessLogFilter;
    public final ObjectMapper mapper;

    public PaymentAppConfig(Config cfg) {
        registry = buildRegistry();

        HikariDataSource dataSource = buildDataSource(cfg);
        JedisPool jedisPool         = buildJedisPool(cfg);
        DSLContext dsl              = DSL.using(dataSource, SQLDialect.POSTGRES);

        WalletRepository walletRepository       = new WalletRepository(dataSource);
        IdempotencyService idempotencyService   = new IdempotencyService(jedisPool);
        ExchangeRateService exchangeRateService = new ExchangeRateService(jedisPool, registry);
        AccountServiceClient accountServiceClient = new AccountServiceClient(
            cfg.getString("account-service.grpc-host"),
            cfg.getInt("account-service.grpc-port")
        );
        PaymentEventPublisher eventPublisher = new PaymentEventPublisher(buildKafkaProducer(cfg));
        PaymentMetrics paymentMetrics        = new PaymentMetrics(registry);
        PaymentService paymentService        = new PaymentService(
            dsl, walletRepository, idempotencyService, exchangeRateService,
            accountServiceClient, eventPublisher, paymentMetrics
        );

        mapper         = buildObjectMapper();
        controller     = new PaymentController(paymentService, mapper);
        accessLogFilter = new AccessLogFilter();
    }

    private PrometheusMeterRegistry buildRegistry() {
        var reg = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new JvmMemoryMetrics().bindTo(reg);
        new JvmGcMetrics().bindTo(reg);
        new JvmThreadMetrics().bindTo(reg);
        new ProcessorMetrics().bindTo(reg);
        return reg;
    }

    private HikariDataSource buildDataSource(Config cfg) {
        var hikari = new HikariConfig();
        hikari.setJdbcUrl(cfg.getString("db.url"));
        hikari.setUsername(cfg.getString("db.user"));
        hikari.setPassword(cfg.getString("db.password"));
        hikari.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        return new HikariDataSource(hikari);
    }

    private JedisPool buildJedisPool(Config cfg) {
        return new JedisPool(new JedisPoolConfig(),
            cfg.getString("redis.host"), cfg.getInt("redis.port"));
    }

    private KafkaProducer<String, String> buildKafkaProducer(Config cfg) {
        var props = new Properties();
        props.put("bootstrap.servers", cfg.getString("kafka.bootstrap-servers"));
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "all");
        return new KafkaProducer<>(props);
    }

    private ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
