package com.finatom.payment

import com.finatom.payment.domain.CreateLedgerEntryCommand
import com.finatom.payment.domain.CreatePaymentCommand
import com.finatom.payment.domain.EntryType
import com.finatom.payment.domain.LedgerEntry
import com.finatom.payment.domain.Payment
import com.finatom.payment.domain.PaymentStatus
import com.finatom.payment.domain.Wallet
import com.finatom.payment.repository.LedgerRepository
import com.finatom.payment.repository.PaymentRepository
import com.finatom.payment.repository.WalletRepository
import com.finatom.payment.service.ExchangeRateService
import com.finatom.payment.service.IdempotencyService
import com.finatom.payment.service.PaymentService
import spock.lang.Specification

import java.time.LocalDateTime

class PaymentServiceSpec extends Specification {

    PaymentRepository paymentRepository     = Mock()
    LedgerRepository ledgerRepository       = Mock()
    WalletRepository walletRepository       = Mock()
    IdempotencyService idempotencyService   = Mock()
    ExchangeRateService exchangeRateService = Mock()

    PaymentService paymentService = new PaymentService(
        paymentRepository, ledgerRepository, walletRepository,
        idempotencyService, exchangeRateService
    )

    def fromWalletId = UUID.randomUUID()
    def toWalletId   = UUID.randomUUID()
    def paymentId    = UUID.randomUUID()

    def fromWallet = new Wallet(fromWalletId, UUID.randomUUID(), "USD", new BigDecimal("1000.00"), LocalDateTime.now())
    def toWallet   = new Wallet(toWalletId,   UUID.randomUUID(), "USD", BigDecimal.ZERO,            LocalDateTime.now())

    def payment = new Payment(paymentId, fromWalletId, toWalletId,
        new BigDecimal("100.00"), "USD", PaymentStatus.COMPLETED,
        "key-123", LocalDateTime.now())

    def "createPayment returns cached result when idempotency key already exists"() {
        given:
        idempotencyService.exists("key-123") >> true
        paymentRepository.findByIdempotencyKey("key-123") >> Optional.of(payment)

        when:
        def result = paymentService.createPayment(fromWalletId, toWalletId, new BigDecimal("100.00"), "USD", "key-123")

        then:
        result == payment
        0 * walletRepository._
        0 * ledgerRepository._
    }

    def "createPayment throws when source wallet not found"() {
        given:
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.empty()

        when:
        paymentService.createPayment(fromWalletId, toWalletId, new BigDecimal("100.00"), "USD", "key-123")

        then:
        thrown(IllegalArgumentException)
    }

    def "createPayment throws when destination wallet not found"() {
        given:
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId) >> Optional.empty()

        when:
        paymentService.createPayment(fromWalletId, toWalletId, new BigDecimal("100.00"), "USD", "key-123")

        then:
        thrown(IllegalArgumentException)
    }

    def "createPayment throws when insufficient funds"() {
        given:
        def poorWallet = new Wallet(fromWalletId, UUID.randomUUID(), "USD", new BigDecimal("50.00"), LocalDateTime.now())
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(poorWallet)
        walletRepository.findById(toWalletId) >> Optional.of(toWallet)
        exchangeRateService.convert(_, _, _) >> new BigDecimal("100.00")

        when:
        paymentService.createPayment(fromWalletId, toWalletId, new BigDecimal("100.00"), "USD", "key-123")

        then:
        thrown(IllegalStateException)
    }

    def "createPayment creates double-entry ledger records"() {
        given:
        def amount = new BigDecimal("100.00")
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId) >> Optional.of(toWallet)
        exchangeRateService.convert(amount, "USD", "USD") >> amount
        paymentRepository.save(_) >> payment
        paymentRepository.findByIdempotencyKey("key-123") >> Optional.of(payment)

        when:
        paymentService.createPayment(fromWalletId, toWalletId, amount, "USD", "key-123")

        then:
        1 * ledgerRepository.save({ CreateLedgerEntryCommand cmd ->
            cmd.walletId() == fromWalletId && cmd.type() == EntryType.DEBIT
        })
        1 * ledgerRepository.save({ CreateLedgerEntryCommand cmd ->
            cmd.walletId() == toWalletId && cmd.type() == EntryType.CREDIT
        })
    }

    def "createPayment debits source and credits destination wallets"() {
        given:
        def amount = new BigDecimal("100.00")
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId) >> Optional.of(toWallet)
        exchangeRateService.convert(amount, "USD", "USD") >> amount
        paymentRepository.save(_) >> payment
        paymentRepository.findByIdempotencyKey("key-123") >> Optional.of(payment)

        when:
        paymentService.createPayment(fromWalletId, toWalletId, amount, "USD", "key-123")

        then:
        1 * walletRepository.debit(fromWalletId, amount)
        1 * walletRepository.credit(toWalletId, amount)
    }

    def "createPayment stores idempotency key after success"() {
        given:
        def amount = new BigDecimal("100.00")
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId) >> Optional.of(toWallet)
        exchangeRateService.convert(amount, "USD", "USD") >> amount
        paymentRepository.save(_) >> payment
        paymentRepository.findByIdempotencyKey("key-123") >> Optional.of(payment)

        when:
        paymentService.createPayment(fromWalletId, toWalletId, amount, "USD", "key-123")

        then:
        1 * idempotencyService.store("key-123", paymentId.toString())
    }

    def "createPayment converts amount for cross-currency transfer"() {
        given:
        def usdAmount = new BigDecimal("100.00")
        def eurAmount = new BigDecimal("92.5926")
        def eurWallet = new Wallet(toWalletId, UUID.randomUUID(), "EUR", BigDecimal.ZERO, LocalDateTime.now())
        idempotencyService.exists("key-123") >> false
        walletRepository.findById(fromWalletId) >> Optional.of(fromWallet)
        walletRepository.findById(toWalletId) >> Optional.of(eurWallet)
        exchangeRateService.convert(usdAmount, "USD", "EUR") >> eurAmount
        paymentRepository.save(_) >> payment
        paymentRepository.findByIdempotencyKey("key-123") >> Optional.of(payment)

        when:
        paymentService.createPayment(fromWalletId, toWalletId, usdAmount, "USD", "key-123")

        then:
        1 * walletRepository.debit(fromWalletId, usdAmount)
        1 * walletRepository.credit(toWalletId, eurAmount)
    }
}
