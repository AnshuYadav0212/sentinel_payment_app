package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id("1")
                .accountNumber("123456789012")
                .accountHolderName("Anshu Yadav")
                .email("anshu@test.com")
                .phone("9876543210")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10000"))
                .dailyTransactionLimit(new BigDecimal("100000"))
                .build();
    }

    @Test
    void createAccount_shouldCreateAccountSuccessfully() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountHolderName("Anshu Yadav");
        request.setEmail("anshu@test.com");
        request.setPhone("9876543210");
        request.setAccountType(AccountType.SAVINGS);
        request.setInitialDeposit(new BigDecimal("10000"));

        when(accountRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);

        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(false);

        when(accountRepository.save(any(Account.class)))
                .thenReturn(account);

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals("123456789012", response.getAccountNumber());
        assertEquals(new BigDecimal("10000"), response.getBalance());

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_shouldFailWhenEmailAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setEmail("anshu@test.com");

        when(accountRepository.existsByEmail(request.getEmail()))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> accountService.createAccount(request)
        );

        assertEquals(
                "account already exists for email: anshu@test.com",
                exception.getMessage()
        );

        verify(accountRepository, never()).save(any());
    }

    @Test
    void deductBalance_shouldFailWhenInsufficientBalance() {
        when(accountRepository.debitIfSufficientBalance(
                "123456789012",
                new BigDecimal("20000")
        )).thenReturn(0);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> accountService.deductBalance(
                        "123456789012",
                        new BigDecimal("20000")
                )
        );

        assertTrue(exception.getMessage().contains("Insufficient balance"));

        verify(accountRepository).debitIfSufficientBalance(
                "123456789012",
                new BigDecimal("20000")
        );
    }

    @Test
    void creditBalance_shouldIncreaseBalance() {
        when(accountRepository.findByAccountNumber("123456789012"))
                .thenReturn(Optional.of(account));

        accountService.creditBalance(
                "123456789012",
                new BigDecimal("500")
        );

        assertEquals(
                new BigDecimal("10500"),
                account.getBalance()
        );

        verify(accountRepository).save(account);
    }
}