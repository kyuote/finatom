package com.finatom.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finatom.payment.domain.PaymentCompletedEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class PaymentEventPublisher {

    static final String TOPIC = "payment.completed";

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publish(PaymentCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            producer.send(new ProducerRecord<>(TOPIC, event.paymentId().toString(), payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish payment event: " + event.paymentId(), e);
        }
    }
}
