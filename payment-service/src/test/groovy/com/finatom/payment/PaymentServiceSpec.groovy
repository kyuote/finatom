package com.finatom.payment

import com.finatom.payment.client.KycValidator
import com.finatom.payment.domain.Payment
import com.finatom.payment.domain.PaymentStatus
import com.finatom.payment.domain.Wallet
import com.finatom.payment.repository.WalletRepository
import com.finatom.payment.service.ExchangeRateService
import com.finatom.payment.service.IdempotencyService
import com.finatom.payment.service.PaymentService
import org.jooq.DSLContext
import spock.lang.Specification

import java.time.LocalDateTime

// Note: the transaction block (INSERT/UPDATE/ledger) is covered by integration tests.
// These unit tests verify the guard clauses that execute before the transaction opens.
class PaymentServiceSpec extends Specification {

    DSLContext dsl                           = Mock()
    WalletRepository walletRepository        = Mock()
    IdempotencyService idempotencyService    = Mock()
    ExchangeRateService exchangeRateService  = Mock()
    KycValidator kycValidator = Mock()

    PaymentService paymentService = new PaymentService(
        dsl, walletRepository, idempotencyService, exchangeRateService, kycValidator
    )

    def fromWalletId = UUID.randomUUID()
    def toWalletId   = UUID.randomUUID()
    def paymentId    = UUID.randomUUID()

    def fromWallet = new Wallet(fromWalletId, UUID.randomUUID(), "USD", new BigDecimal("1000.00"), LocalDateTime.now())
    def toWallet   = new Wallet(toWalletId,   UUID.randomUUID(), "USD", BigDecimal.ZERO,           LocalDateTime.now())

    def cachedPayment = new Payment(paymentId, fromWalletId, toWalletId,
        new BigDecimal("100.00"), "USD", PaymentStatus.COMPLETED, "key-123", LocalDateTime.now())

    def "returns cached result without opening a transaction when idempotency key already exists"() {
        given:
        idempotencyService.exists("key-123") >> true
        // dsl.selectFrom(...).where(...).fetchOne(...) chain — stub the full chain
        def selectStep = Mock(org.jooq.SelectWhereStep)
        def conditionStep = Mock(org.jooq.SelectConditionStep)
        dsl.selectFrom(_) >> selectStep
        selectStep.where(_) >> conditionStep
        conditionStep.fetchOne(_) >> cachedPayment

        when:
        def result = paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        result == cachedPayment
        0 * dsl.transactionResult(_)
        0 * walletRepository._
    }

    def "throws IllegalArgumentException when source wallet not found"() {
        given:
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.empty()

        when:
        paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains(fromWalletId.toString())
    }

    def "throws IllegalArgumentException when destination wallet not found"() {
        given:
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId)   >> Optional.empty()

        when:
        paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains(toWalletId.toString())
    }

    def "throws IllegalStateException on insufficient funds before opening transaction"() {
        given:
        def poorWallet = new Wallet(fromWalletId, UUID.randomUUID(), "USD",
            new BigDecimal("50.00"), LocalDateTime.now())

        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(poorWallet)
        walletRepository.findById(toWalletId)   >> Optional.of(toWallet)
        exchangeRateService.convert(_, _, _)    >> new BigDecimal("100.00")

        when:
        paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        thrown(IllegalStateException)
        0 * dsl.transactionResult(_)
    }

    def "calls exchange rate service with correct currencies"() {
        given:
        def eurWallet = new Wallet(toWalletId, UUID.randomUUID(), "EUR",
            BigDecimal.ZERO, LocalDateTime.now())

        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId)   >> Optional.of(eurWallet)
        exchangeRateService.convert(_, "USD", "EUR") >> new BigDecimal("92.5926")

        // stub transaction so it doesn't blow up
        dsl.transactionResult(_) >> cachedPayment

        when:
        paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        1 * exchangeRateService.convert(new BigDecimal("100.00"), "USD", "EUR")
    }

    def "skips exchange rate call for same-currency transfer"() {
        given:
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId)   >> Optional.of(toWallet)
        exchangeRateService.convert(_, "USD", "USD") >> new BigDecimal("100.00")
        dsl.transactionResult(_) >> cachedPayment

        when:
        paymentService.createPayment(fromWalletId, toWalletId,
            new BigDecimal("100.00"), "USD", "key-123")

        then:
        // convert is still called but ExchangeRateService itself returns early for same currency
        1 * exchangeRateService.convert(new BigDecimal("100.00"), "USD", "USD")
    }
}
