package com.finatom.antifraud.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.antifraud.domain.PaymentCompletedEvent;
import com.finatom.antifraud.service.FraudDetectionService;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class PaymentEventConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    static final String TOPIC = "payment.completed";

    private final KafkaConsumer<String, String> consumer;
    private final FraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;
    private volatile boolean running = true;

    public PaymentEventConsumer(KafkaConsumer<String, String> consumer,
                                FraudDetectionService fraudDetectionService) {
        this.consumer = consumer;
        this.fraudDetectionService = fraudDetectionService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void run() {
        consumer.subscribe(List.of(TOPIC));
        while (running) {
            var records = consumer.poll(Duration.ofMillis(500));
            for (var record : records) {
                try {
                    var event = objectMapper.readValue(record.value(), PaymentCompletedEvent.class);
                    fraudDetectionService.analyze(event);
                } catch (Exception e) {
                    log.error("Failed to process payment event offset={}", record.offset(), e);
                }
            }
        }
        consumer.close();
    }

    public void stop() {
        running = false;
    }
}
