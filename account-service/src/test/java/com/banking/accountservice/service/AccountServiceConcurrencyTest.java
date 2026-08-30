package com.banking.accountservice.service;

import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ACCOUNT_SERVICE_SERVER_PORT=8081",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class AccountServiceConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountService accountService;

    private static final String ACCOUNT_NUMBER =
            "123456789012";

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        Account account = new Account();

        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setAccountHolderName("Test User");
        account.setEmail("concurrency@test.com");
        account.setPhone("9999999999");
        account.setAccountType(AccountType.SAVINGS);
        account.setBalance(new BigDecimal("1000"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setDailyTransactionLimit(new BigDecimal("100000"));

        accountRepository.saveAndFlush(account);
    }

    @Test
    void concurrentDebit_shouldAllowOnlyOneTransaction() throws Exception {

        BigDecimal amount = new BigDecimal("700");

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        Callable<Boolean> debitTask = () -> {

            ready.countDown();

            start.await();

            try {
                accountService.deductBalance(
                        ACCOUNT_NUMBER,
                        amount
                );

                return true;

            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> future1 =
                executor.submit(debitTask);

        Future<Boolean> future2 =
                executor.submit(debitTask);

        ready.await();

        start.countDown();

        boolean result1 = future1.get();
        boolean result2 = future2.get();

        int successfulDebits =
                (result1 ? 1 : 0) +
                        (result2 ? 1 : 0);

        assertThat(successfulDebits)
                .isEqualTo(1);

        Account account =
                accountRepository
                        .findByAccountNumber(ACCOUNT_NUMBER)
                        .orElseThrow();

        assertThat(account.getBalance())
                .isEqualByComparingTo("300");

        executor.shutdown();
    }
}
