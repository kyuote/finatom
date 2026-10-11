package com.finatom.antifraud.service;

import com.finatom.antifraud.domain.AlertReason;
import com.finatom.antifraud.domain.PaymentCompletedEvent;
import com.finatom.antifraud.repository.FraudAlertRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FraudDetectionService {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("10000");
    private static final int HIGH_FREQUENCY_LIMIT = 10;
    private static final int FREQUENCY_WINDOW_MINUTES = 60;

    private final FraudAlertRepository alertRepository;

    public FraudDetectionService(FraudAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public void analyze(PaymentCompletedEvent event) {
        if (event.amount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            alertRepository.save(event.paymentId(), event.fromWalletId(), AlertReason.HIGH_AMOUNT);
        }

        LocalDateTime windowStart = event.completedAt().minusMinutes(FREQUENCY_WINDOW_MINUTES);
        int recentCount = alertRepository.countRecentPayments(event.fromWalletId(), windowStart);
        if (recentCount > HIGH_FREQUENCY_LIMIT) {
            alertRepository.save(event.paymentId(), event.fromWalletId(), AlertReason.HIGH_FREQUENCY);
        }
    }
}
