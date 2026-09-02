package com.banking.accountservice.service;


import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.exception.InsufficientBalanceException;
import com.banking.accountservice.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating Account for: {}", request.getEmail());
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("account already exists for email: " + request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
               request.getAccountType()== AccountType.SAVINGS ? new BigDecimal("100000"): new BigDecimal("500000")
        );
        account.setCreatedAt(LocalDateTime.now());

        Account savedAccount= accountRepository.save(account);
        log.info("Account created: {}", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);

    }

    /*  get the account using this function
    * @param account No.
     */

    public AccountResponse getAccount(String accountNumber ){
        Account account=accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Account " + accountNumber + " not found"
                        )
                );

        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        return (account.getBalance());
    }

    /*
    *  the method blockAccount will be called by fraud detection service from kafka
    * @param  accountNumber
     */
    public void blockAccount(String accountNumber){
        log.info("Blocking account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}, ", accountNumber);
    }
    /*
    * Deducting balance from sender account
    * called by Transaction service
    * @param accountNumber
    * @param amount
    */

    @Transactional
    public void deductBalance(String accountNumber, BigDecimal amount){
        log.info("Debiting balance {} from account: {}", amount,accountNumber);

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        int updatedRows =
                accountRepository.debitIfSufficientBalance(
                        accountNumber,
                        amount
                );

        if (updatedRows == 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance"
            );
        }

        log.info(
                "Balance debited successfully from account: {}",
                accountNumber
        );
    }

    /*
    * crediting balance called by Transaction service
    * @param accountumber
    * @param amount
     */

    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount){
        log.info("Crediting amount {} from Account {}",amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance credit new balance {} ", account.getBalance());
    }

     /*
     * we have 12 digit number for account number
     * and generated the unique number
      */
    private String generateAccountNumber(){
       String accountNumber;
       do{
           long number=secureRandom.nextLong(1_000_000_000_000L);
            accountNumber=String.format("%012d",number);

       } while(accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account){
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());
        return response;

    }

}
