package com.finatom.account

import com.finatom.account.domain.Account
import com.finatom.account.domain.KycStatus
import com.finatom.account.domain.Wallet
import com.finatom.account.exception.AccountNotFoundException
import com.finatom.account.repository.AccountRepository
import com.finatom.account.repository.WalletRepository
import com.finatom.account.service.AccountService
import spock.lang.Specification

import java.time.LocalDateTime

class AccountServiceSpec extends Specification {

    AccountRepository accountRepository = Mock()
    WalletRepository walletRepository   = Mock()
    AccountService accountService       = new AccountService(accountRepository, walletRepository)

    def "createAccount saves account and creates 3 wallets"() {
        given:
        def account = new Account(UUID.randomUUID(), "Ivan", "ivan@test.com", KycStatus.PENDING, LocalDateTime.now())
        accountRepository.save("Ivan", "ivan@test.com") >> account

        when:
        def result = accountService.createAccount("Ivan", "ivan@test.com")

        then:
        result == account
        3 * walletRepository.save(account.id(), _)
    }

    def "createAccount creates wallets for USD, EUR and GBP"() {
        given:
        def account = new Account(UUID.randomUUID(), "Ivan", "ivan@test.com", KycStatus.PENDING, LocalDateTime.now())
        accountRepository.save("Ivan", "ivan@test.com") >> account

        when:
        accountService.createAccount("Ivan", "ivan@test.com")

        then:
        1 * walletRepository.save(account.id(), "USD")
        1 * walletRepository.save(account.id(), "EUR")
        1 * walletRepository.save(account.id(), "GBP")
    }

    def "getAccount throws when account not found"() {
        given:
        def id = UUID.randomUUID()
        accountRepository.findById(id) >> Optional.empty()

        when:
        accountService.getAccount(id)

        then:
        thrown(AccountNotFoundException)
    }

    def "getAccount returns account when found"() {
        given:
        def id = UUID.randomUUID()
        def account = new Account(id, "Ivan", "ivan@test.com", KycStatus.PENDING, LocalDateTime.now())
        accountRepository.findById(id) >> Optional.of(account)

        when:
        def result = accountService.getAccount(id)

        then:
        result == account
    }

    def "getWallets returns wallets for account"() {
        given:
        def accountId = UUID.randomUUID()
        def wallets = [
            new Wallet(UUID.randomUUID(), accountId, "USD", BigDecimal.ZERO, LocalDateTime.now()),
            new Wallet(UUID.randomUUID(), accountId, "EUR", BigDecimal.ZERO, LocalDateTime.now()),
            new Wallet(UUID.randomUUID(), accountId, "GBP", BigDecimal.ZERO, LocalDateTime.now())
        ]
        walletRepository.findByAccountId(accountId) >> wallets

        when:
        def result = accountService.getWallets(accountId)

        then:
        result.size() == 3
        result*.currency == ["USD", "EUR", "GBP"]
    }
}
