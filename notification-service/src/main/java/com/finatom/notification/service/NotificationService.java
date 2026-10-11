package com.finatom.notification.service;

import com.finatom.notification.domain.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void notify(PaymentCompletedEvent event) {
        log.info("[PAYMENT] id={} amount={} {} from={} to={}",
            event.paymentId(),
            event.amount(),
            event.currency(),
            event.fromWalletId(),
            event.toWalletId()
        );
    }
}
