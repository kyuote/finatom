package com.finatom.account

import com.finatom.account.domain.KycStatus
import com.finatom.account.exception.AccountNotFoundException
import com.finatom.account.repository.AccountRepository
import com.finatom.account.repository.WalletRepository
import com.finatom.account.service.AccountService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.math.BigDecimal

@Testcontainers
class AccountServiceIntegrationSpec extends Specification {

    @Shared
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("finatom_test")
        .withUsername("test")
        .withPassword("test")

    @Shared
    AccountService accountService

    def setupSpec() {
        def hikariConfig = new HikariConfig()
        hikariConfig.jdbcUrl      = postgres.jdbcUrl
        hikariConfig.username     = postgres.username
        hikariConfig.password     = postgres.password
        hikariConfig.maximumPoolSize = 5
        def dataSource = new HikariDataSource(hikariConfig)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load()
            .migrate()

        accountService = new AccountService(
            new AccountRepository(dataSource),
            new WalletRepository(dataSource)
        )
    }

    def "createAccount creates account with USD, EUR and GBP wallets"() {
        when:
        def account = accountService.createAccount("Alice", "alice@test.com")

        then:
        account.id() != null
        account.name() == "Alice"
        account.email() == "alice@test.com"
        account.kycStatus() == KycStatus.PENDING

        and:
        def wallets = accountService.getWallets(account.id())
        wallets.size() == 3
        wallets*.currency().sort() == ["EUR", "GBP", "USD"]
    }

    def "getAccount returns existing account"() {
        given:
        def created = accountService.createAccount("Bob", "bob@test.com")

        when:
        def found = accountService.getAccount(created.id())

        then:
        found.id() == created.id()
        found.name() == "Bob"
    }

    def "getAccount throws AccountNotFoundException for unknown id"() {
        when:
        accountService.getAccount(UUID.randomUUID())

        then:
        thrown(AccountNotFoundException)
    }

    def "verifyKyc changes status to VERIFIED"() {
        given:
        def account = accountService.createAccount("Carol", "carol@test.com")
        assert account.kycStatus() == KycStatus.PENDING

        when:
        def updated = accountService.verifyKyc(account.id())

        then:
        updated.kycStatus() == KycStatus.VERIFIED
    }

    def "deposit increases wallet balance"() {
        given:
        def account = accountService.createAccount("Dave", "dave@test.com")
        def wallets = accountService.getWallets(account.id())
        def usdWallet = wallets.find { it.currency() == "USD" }
        assert usdWallet.balance() == BigDecimal.ZERO

        when:
        def updated = accountService.deposit(usdWallet.id(), new BigDecimal("1000.00"))

        then:
        updated.balance() == new BigDecimal("1000.00")
    }
}
