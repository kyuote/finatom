package com.finatom.payment

import com.finatom.payment.client.KycValidator
import com.finatom.payment.domain.PaymentCompletedEvent
import com.finatom.payment.domain.PaymentStatus
import com.finatom.payment.exception.InsufficientFundsException
import com.finatom.payment.exception.WalletNotFoundException
import com.finatom.payment.messaging.PaymentEventPublisher
import com.finatom.payment.metrics.PaymentMetrics
import com.finatom.payment.repository.WalletRepository
import com.finatom.payment.service.ExchangeRateService
import com.finatom.payment.service.IdempotencyService
import com.finatom.payment.service.PaymentService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import redis.clients.jedis.JedisPool
import spock.lang.Shared
import spock.lang.Specification

import java.math.BigDecimal

import static org.jooq.impl.DSL.field
import static org.jooq.impl.DSL.table

@Testcontainers
class PaymentServiceIntegrationSpec extends Specification {

    @Shared
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("finatom_test")
        .withUsername("test")
        .withPassword("test")

    @Shared
    GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)

    @Shared DSLContext       dsl
    @Shared WalletRepository walletRepository
    @Shared PaymentService   paymentService

    def setupSpec() {
        def hikariConfig = new HikariConfig()
        hikariConfig.jdbcUrl         = postgres.jdbcUrl
        hikariConfig.username        = postgres.username
        hikariConfig.password        = postgres.password
        hikariConfig.maximumPoolSize = 5
        def dataSource = new HikariDataSource(hikariConfig)

        def migrationPath = System.getProperty('db.migration.path',
            '../account-service/src/main/resources/db/migration')
        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:${migrationPath}")
            .outOfOrder(true)
            .load()
            .migrate()

        dsl              = DSL.using(dataSource, SQLDialect.POSTGRES)
        walletRepository = new WalletRepository(dataSource)

        def jedisPool          = new JedisPool(redis.host, redis.getMappedPort(6379))
        def registry           = new SimpleMeterRegistry()
        def idempotencyService = new IdempotencyService(jedisPool)
        def exchangeRateService = new ExchangeRateService(jedisPool, registry)
        def metrics            = new PaymentMetrics(registry)

        // KYC always passes (gRPC stub — not testing the gRPC layer here)
        KycValidator kycValidator = (UUID id) -> {}

        // Kafka event publish is best-effort; no-op in tests
        def eventPublisher = new PaymentEventPublisher(null) {
            @Override void publish(PaymentCompletedEvent e) {}
        }

        paymentService = new PaymentService(
            dsl, walletRepository, idempotencyService,
            exchangeRateService, kycValidator, eventPublisher, metrics
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID insertAccount() {
        String email = "test-" + UUID.randomUUID() + "@test.com"
        dsl.execute("INSERT INTO accounts (name, email) VALUES ('Test', ?)", email)
        dsl.select(field("id")).from(table("accounts"))
            .where(field("email").eq(email))
            .fetchOne(r -> r.get(field("id"), UUID.class))
    }

    private UUID insertWallet(UUID accountId, String currency = "USD") {
        dsl.execute("INSERT INTO wallets (account_id, currency) VALUES (?, ?)", accountId, currency)
        dsl.select(field("id")).from(table("wallets"))
            .where(field("account_id").eq(accountId).and(field("currency").eq(currency)))
            .fetchOne(r -> r.get(field("id"), UUID.class))
    }

    private void setBalance(UUID walletId, BigDecimal amount) {
        dsl.execute("UPDATE wallets SET balance = ? WHERE id = ?", amount, walletId)
    }

    private BigDecimal balance(UUID walletId) {
        walletRepository.findById(walletId).get().balance()
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    def "successful payment moves balance from sender to receiver"() {
        given:
        def walletA = insertWallet(insertAccount())
        def walletB = insertWallet(insertAccount())
        setBalance(walletA, new BigDecimal("500.00"))

        when:
        def payment = paymentService.createPayment(walletA, walletB,
            new BigDecimal("200.00"), "USD", "key-transfer-${UUID.randomUUID()}")

        then:
        payment.status() == PaymentStatus.COMPLETED
        balance(walletA) == new BigDecimal("300.0000")
        balance(walletB) == new BigDecimal("200.0000")
    }

    def "idempotent payment: same key does not double-debit"() {
        given:
        def walletA = insertWallet(insertAccount())
        def walletB = insertWallet(insertAccount())
        setBalance(walletA, new BigDecimal("500.00"))
        def key = "key-idempotent-${UUID.randomUUID()}"

        when:
        def p1 = paymentService.createPayment(walletA, walletB, new BigDecimal("100.00"), "USD", key)
        def p2 = paymentService.createPayment(walletA, walletB, new BigDecimal("100.00"), "USD", key)

        then:
        p1.id() == p2.id()
        balance(walletA) == new BigDecimal("400.0000")
    }

    def "insufficient funds: balance unchanged and exception thrown"() {
        given:
        def walletA = insertWallet(insertAccount())
        def walletB = insertWallet(insertAccount())
        setBalance(walletA, new BigDecimal("50.00"))

        when:
        paymentService.createPayment(walletA, walletB,
            new BigDecimal("200.00"), "USD", "key-insuf-${UUID.randomUUID()}")

        then:
        thrown(InsufficientFundsException)
        balance(walletA) == new BigDecimal("50.0000")
    }

    def "unknown source wallet throws WalletNotFoundException"() {
        given:
        def walletB = insertWallet(insertAccount())

        when:
        paymentService.createPayment(UUID.randomUUID(), walletB,
            new BigDecimal("100.00"), "USD", "key-notfound-${UUID.randomUUID()}")

        then:
        thrown(WalletNotFoundException)
    }

    def "payment creates ledger debit and credit entries"() {
        given:
        def walletA = insertWallet(insertAccount())
        def walletB = insertWallet(insertAccount())
        setBalance(walletA, new BigDecimal("300.00"))
        def key = "key-ledger-${UUID.randomUUID()}"

        when:
        def payment = paymentService.createPayment(walletA, walletB,
            new BigDecimal("100.00"), "USD", key)

        then:
        def entries = dsl.fetch("SELECT type, amount FROM ledger WHERE payment_id = ?", payment.id())
        entries.size() == 2
        entries.find { it.get("type") == "DEBIT" }.get("amount", BigDecimal)  == new BigDecimal("-100.0000")
        entries.find { it.get("type") == "CREDIT" }.get("amount", BigDecimal) == new BigDecimal("100.0000")
    }
}
