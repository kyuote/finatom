package com.finatom.notification;

import com.finatom.notification.messaging.PaymentEventConsumer;
import com.finatom.notification.service.NotificationService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;

public class NotificationApplication {

    public static void main(String[] args) {
        Config cfg = ConfigFactory.load();

        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", cfg.getString("kafka.bootstrap-servers"));
        kafkaProps.put("group.id", cfg.getString("kafka.group-id"));
        kafkaProps.put("key.deserializer", StringDeserializer.class.getName());
        kafkaProps.put("value.deserializer", StringDeserializer.class.getName());
        kafkaProps.put("auto.offset.reset", "earliest");
        kafkaProps.put("enable.auto.commit", "false");

        NotificationService notificationService = new NotificationService();
        PaymentEventConsumer consumer = new PaymentEventConsumer(
            new KafkaConsumer<>(kafkaProps), notificationService
        );

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::stop));

        new Thread(consumer, "payment-event-consumer").start();
    }
}
