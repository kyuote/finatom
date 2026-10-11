package com.finatom.antifraud.service;

import com.finatom.antifraud.domain.AlertReason;
import com.finatom.antifraud.domain.PaymentCompletedEvent;
import com.finatom.antifraud.repository.FraudAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FraudDetectionService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000");
    private static final int HIGH_FREQUENCY_LIMIT = 10;
    private static final int FREQUENCY_WINDOW_MINUTES = 60;

    private final FraudAlertRepository alertRepository;
    private final Counter highAmountCounter;
    private final Counter highFrequencyCounter;

    public FraudDetectionService(FraudAlertRepository alertRepository, MeterRegistry registry) {
        this.alertRepository = alertRepository;
        this.highAmountCounter = Counter.builder("fraud.alerts.total")
            .tag("reason", AlertReason.HIGH_AMOUNT.name())
            .description("Fraud alerts triggered by high payment amount")
            .register(registry);
        this.highFrequencyCounter = Counter.builder("fraud.alerts.total")
            .tag("reason", AlertReason.HIGH_FREQUENCY.name())
            .description("Fraud alerts triggered by high payment frequency")
            .register(registry);
    }

    public void analyze(PaymentCompletedEvent event) {
        if (event.amount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            alertRepository.save(event.paymentId(), event.fromWalletId(), AlertReason.HIGH_AMOUNT);
            highAmountCounter.increment();
        }

        LocalDateTime windowStart = event.completedAt().minusMinutes(FREQUENCY_WINDOW_MINUTES);
        int recentCount = alertRepository.countRecentPayments(event.fromWalletId(), windowStart);
        if (recentCount > HIGH_FREQUENCY_LIMIT) {
            alertRepository.save(event.paymentId(), event.fromWalletId(), AlertReason.HIGH_FREQUENCY);
            highFrequencyCounter.increment();
        }
    }
}
