package com.banking.transactionservice.repository;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
@DataJpaTest
class TransactionRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("transaction_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add(
                "TRANSACTION_SERVICE_ACCOUNT_SERVICE_URL",
                () -> "http://localhost:8080"
        );

    }

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
    }

    @Test
    void saveTransaction_shouldPersistAndReadBack() {
        Transaction transaction = transaction();

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        Transaction found = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getSenderAccountNumber()).isEqualTo("123456789012");
        assertThat(found.getReceiverAccountNumber()).isEqualTo("987654321012");
        assertThat(found.getAmount()).isEqualByComparingTo("500");
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    void saveTransaction_shouldPersistCreatedAtAndKeepCompletedAt() {
        Transaction transaction = transaction();
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCompletedAt(null);

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    void findBySenderAccountNumber_shouldReturnTransactionHistory() {
        Transaction first = transaction();
        first.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        Transaction second = transaction();
        second.setCreatedAt(LocalDateTime.now());
        second.setReceiverAccountNumber("999999999999");

        Transaction otherSender = transaction();
        otherSender.setSenderAccountNumber("555555555555");

        transactionRepository.saveAllAndFlush(List.of(first, second, otherSender));

        List<Transaction> history =
                transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(
                        "123456789012"
                );

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getCreatedAt())
                .isAfter(history.get(1).getCreatedAt());
    }

    @Test
    void transactionSchema_shouldPersistEnumValuesCorrectly() {
        Transaction transaction = transaction();
        transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
        transaction.setType(TransactionType.TRANSFER);

        Transaction saved = transactionRepository.saveAndFlush(transaction);
        transactionRepository.flush();

        Transaction found = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(TransactionStatus.PENDING_VERIFICATION);
        assertThat(found.getType()).isEqualTo(TransactionType.TRANSFER);
    }

    private Transaction transaction() {
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber("123456789012");
        transaction.setReceiverAccountNumber("987654321012");
        transaction.setAmount(new BigDecimal("500"));
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription("integration test");
        transaction.setReferenceNumber("ref-1");
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setCompletedAt(null);
        return transaction;
    }
}
