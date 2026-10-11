package com.finatom.notification

import com.finatom.notification.domain.PaymentCompletedEvent
import com.finatom.notification.service.NotificationService
import spock.lang.Specification

import java.time.LocalDateTime

class NotificationServiceSpec extends Specification {

    NotificationService notificationService = new NotificationService()

    def "notify does not throw for valid event"() {
        given:
        def event = new PaymentCompletedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "USD", LocalDateTime.now()
        )

        when:
        notificationService.notify(event)

        then:
        noExceptionThrown()
    }
}
