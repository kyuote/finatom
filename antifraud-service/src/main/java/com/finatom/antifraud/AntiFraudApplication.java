package com.finatom.antifraud;

import com.finatom.antifraud.messaging.PaymentEventConsumer;
import com.finatom.antifraud.repository.FraudAlertRepository;
import com.finatom.antifraud.service.FraudDetectionService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.Properties;

public class AntiFraudApplication {

    public static void main(String[] args) {
        Config cfg = ConfigFactory.load();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getString("db.url"));
        hikariConfig.setUsername(cfg.getString("db.user"));
        hikariConfig.setPassword(cfg.getString("db.password"));
        hikariConfig.setMaximumPoolSize(cfg.getInt("db.pool-size"));
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", cfg.getString("kafka.bootstrap-servers"));
        kafkaProps.put("group.id", cfg.getString("kafka.group-id"));
        kafkaProps.put("key.deserializer", StringDeserializer.class.getName());
        kafkaProps.put("value.deserializer", StringDeserializer.class.getName());
        kafkaProps.put("auto.offset.reset", "earliest");
        kafkaProps.put("enable.auto.commit", "false");

        FraudAlertRepository alertRepository   = new FraudAlertRepository(dataSource);
        FraudDetectionService fraudService     = new FraudDetectionService(alertRepository);
        PaymentEventConsumer consumer          = new PaymentEventConsumer(
            new KafkaConsumer<>(kafkaProps), fraudService
        );

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::stop));

        new Thread(consumer, "payment-event-consumer").start();
    }
}
