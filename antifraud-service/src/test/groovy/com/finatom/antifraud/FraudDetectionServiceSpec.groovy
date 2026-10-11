package com.finatom.antifraud

import com.finatom.antifraud.domain.AlertReason
import com.finatom.antifraud.domain.PaymentCompletedEvent
import com.finatom.antifraud.repository.FraudAlertRepository
import com.finatom.antifraud.service.FraudDetectionService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.LocalDateTime

class FraudDetectionServiceSpec extends Specification {

    FraudAlertRepository alertRepository   = Mock()
    FraudDetectionService fraudService     = new FraudDetectionService(alertRepository, new SimpleMeterRegistry())

    def paymentId    = UUID.randomUUID()
    def fromWalletId = UUID.randomUUID()
    def toWalletId   = UUID.randomUUID()

    def "does not flag normal payment"() {
        given:
        def event = event("500.00")
        alertRepository.countRecentPayments(fromWalletId, _) >> 3

        when:
        fraudService.analyze(event)

        then:
        0 * alertRepository.save(_, _, _)
    }

    def "flags payment above 10000 as HIGH_AMOUNT"() {
        given:
        def event = event("15000.00")
        alertRepository.countRecentPayments(fromWalletId, _) >> 1

        when:
        fraudService.analyze(event)

        then:
        1 * alertRepository.save(paymentId, fromWalletId, AlertReason.HIGH_AMOUNT)
    }

    def "flags wallet with more than 10 payments in last hour as HIGH_FREQUENCY"() {
        given:
        def event = event("100.00")
        alertRepository.countRecentPayments(fromWalletId, _) >> 11

        when:
        fraudService.analyze(event)

        then:
        1 * alertRepository.save(paymentId, fromWalletId, AlertReason.HIGH_FREQUENCY)
    }

    def "flags both HIGH_AMOUNT and HIGH_FREQUENCY when both rules trigger"() {
        given:
        def event = event("50000.00")
        alertRepository.countRecentPayments(fromWalletId, _) >> 15

        when:
        fraudService.analyze(event)

        then:
        1 * alertRepository.save(paymentId, fromWalletId, AlertReason.HIGH_AMOUNT)
        1 * alertRepository.save(paymentId, fromWalletId, AlertReason.HIGH_FREQUENCY)
    }

    def "exactly 10000 does not trigger HIGH_AMOUNT alert"() {
        given:
        def event = event("10000.00")
        alertRepository.countRecentPayments(fromWalletId, _) >> 1

        when:
        fraudService.analyze(event)

        then:
        0 * alertRepository.save(_, _, AlertReason.HIGH_AMOUNT)
    }

    private PaymentCompletedEvent event(String amount) {
        new PaymentCompletedEvent(paymentId, fromWalletId, toWalletId,
            new BigDecimal(amount), "USD", LocalDateTime.now())
    }
}
