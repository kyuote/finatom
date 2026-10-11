package com.finatom.antifraud.config;

import com.finatom.antifraud.controller.AccessLogFilter;
import com.finatom.antifraud.messaging.PaymentEventConsumer;
import com.finatom.antifraud.repository.FraudAlertRepository;
import com.finatom.antifraud.service.FraudDetectionService;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;

public class AntiFraudAppConfig {
    public final PrometheusMeterRegistry registry;
    public final AccessLogFilter accessLogFilter;
    public final PaymentEventConsumer consumer;

    public AntiFraudAppConfig(Config cfg) {
        this.registry = buildRegistry();
        var dataSource = buildDataSource(cfg);
        this.consumer = buildConsumer(cfg, dataSource);
        this.accessLogFilter = new AccessLogFilter();
    }

    private static PrometheusMeterRegistry buildRegistry() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        return registry;
    }

    private static HikariDataSource buildDataSource(Config cfg) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        return new HikariDataSource(hikariConfig);
    }

    private PaymentEventConsumer buildConsumer(Config cfg, HikariDataSource dataSource) {
        var props = new Properties();
        props.put("bootstrap.servers", cfg.getString("kafka.bootstrap-servers"));
        props.put("group.id", cfg.getString("kafka.group-id"));
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");

        var alertRepository = new FraudAlertRepository(dataSource);
        var fraudService    = new FraudDetectionService(alertRepository, registry);
        return new PaymentEventConsumer(new KafkaConsumer<>(props), fraudService);
    }
}
