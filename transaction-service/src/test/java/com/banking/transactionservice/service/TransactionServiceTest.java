package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountLookupResponse;
import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                accountServiceClient,
                kafkaTemplate,
                redisTemplate
        );
    }

    @Test
    void transfer_shouldCreateProcessingTransactionForValidRequest() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                new BigDecimal("500"),
                "test transfer"
        );

        when(accountServiceClient.getAccount("987654321012"))
                .thenReturn(new AccountLookupResponse("987654321012", "ACTIVE"));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.transfer(request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(response.getSenderAccountNumber()).isEqualTo("123456789012");
        assertThat(response.getReceiverAccountNumber()).isEqualTo("987654321012");
        assertThat(response.getAmount()).isEqualByComparingTo("500");

        verify(accountServiceClient).deductBalance("123456789012", new BigDecimal("500"));
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaTemplate).send(eq("transaction.initiated"), eq(response.getId()), any());
    }

    @Test
    void transfer_shouldRejectInactiveReceiver() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                new BigDecimal("500"),
                "test transfer"
        );

        when(accountServiceClient.getAccount("987654321012"))
                .thenReturn(new AccountLookupResponse("987654321012", "BLOCKED"));

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");

        verify(accountServiceClient, never())
                .deductBalance(anyString(), any(BigDecimal.class));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_shouldValidateReceiverBeforeDebitingSender() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                new BigDecimal("500"),
                "test transfer"
        );

        when(accountServiceClient.getAccount("987654321012"))
                .thenReturn(new AccountLookupResponse("987654321012", "ACTIVE"));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.transfer(request);

        InOrder inOrder = inOrder(accountServiceClient);
        inOrder.verify(accountServiceClient).getAccount("987654321012");
        inOrder.verify(accountServiceClient)
                .deductBalance("123456789012", new BigDecimal("500"));
    }

    @Test
    void transfer_shouldRejectNegativeAmount() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                new BigDecimal("-1"),
                "test transfer"
        );

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        verify(accountServiceClient, never()).deductBalance(anyString(), any());
    }

    @Test
    void transfer_shouldRejectZeroAmount() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                BigDecimal.ZERO,
                "test transfer"
        );

        assertThatThrownBy(() -> transactionService.transfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        verify(accountServiceClient, never()).deductBalance(anyString(), any());
    }

    @Test
    void transfer_shouldCreateTransactionWithNullCompletedAt() {
        TransferRequest request = new TransferRequest(
                "123456789012",
                "987654321012",
                new BigDecimal("500"),
                "test transfer"
        );

        when(accountServiceClient.getAccount("987654321012"))
                .thenReturn(new AccountLookupResponse("987654321012", "ACTIVE"));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.transfer(request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    void verifyOTP_shouldCompleteTransactionForValidOtp() {
        Transaction transaction = processingTransaction(TransactionStatus.PENDING_VERIFICATION);
        transaction.setId("tx-1");
        transaction.setCreatedAt(LocalDateTime.now());

        when(transactionRepository.findById("tx-1"))
                .thenReturn(Optional.of(transaction));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("verification:otptx-1")).thenReturn("123456");
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.verifyOTP("tx-1", "123456");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getCompletedAt()).isNotNull();
        verify(redisTemplate).delete("verification:otptx-1");
        verify(transactionRepository).save(transaction);
        verify(kafkaTemplate).send(eq("transaction.completed"), eq("tx-1"), any());
    }

    @Test
    void verifyOTP_shouldIgnoreDuplicateVerificationAfterCompletion() {
        Transaction transaction = processingTransaction(TransactionStatus.COMPLETED);
        transaction.setId("tx-2");
        transaction.setCompletedAt(LocalDateTime.now());

        when(transactionRepository.findById("tx-2"))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response = transactionService.verifyOTP("tx-2", "123456");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verifyNoInteractions(redisTemplate);
        verify(accountServiceClient, never()).creditBalance(anyString(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void verifyOTP_shouldCompensateExpiredOtp() {
        Transaction transaction = processingTransaction(TransactionStatus.PENDING_VERIFICATION);
        transaction.setId("tx-3");

        when(transactionRepository.findById("tx-3"))
                .thenReturn(Optional.of(transaction));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("verification:otptx-3")).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.verifyOTP("tx-3", "123456");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.FLAGGED);
        verify(accountServiceClient)
                .creditBalance("123456789012", new BigDecimal("500"));
        verify(transactionRepository).save(transaction);
        verify(kafkaTemplate).send(eq("transaction.refunded"), eq("tx-3"), any());
    }

    private Transaction processingTransaction(TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber("123456789012");
        transaction.setReceiverAccountNumber("987654321012");
        transaction.setAmount(new BigDecimal("500"));
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(status);
        transaction.setDescription("test transfer");
        transaction.setReferenceNumber("ref-1");
        transaction.setCreatedAt(LocalDateTime.now());
        return transaction;
    }
}
