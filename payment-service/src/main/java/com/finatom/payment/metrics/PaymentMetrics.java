package com.finatom.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class PaymentMetrics {

    private final Counter completedCounter;
    private final Counter failedCounter;
    private final Timer   processingTimer;

    public PaymentMetrics(MeterRegistry registry) {
        completedCounter = Counter.builder("payments.completed.total")
            .description("Total number of successfully completed payments")
            .register(registry);

        failedCounter = Counter.builder("payments.failed.total")
            .description("Total number of failed payments")
            .register(registry);

        processingTimer = Timer.builder("payment.processing.duration.seconds")
            .description("End-to-end payment processing latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void recordCompleted() { completedCounter.increment(); }
    public void recordFailed()    { failedCounter.increment(); }
    public Timer processingTimer() { return processingTimer; }
}
