package com.banking.accountservice.repository;

import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/*
  * To handle the id generated for account
 */
public interface AccountRepository extends JpaRepository<Account,String> {
    boolean existsByEmail(String email);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    // to handle race condition for concurrent requests
    @Modifying
    @Query("""
    UPDATE Account a
       SET a.balance = a.balance - :amount
     WHERE a.accountNumber = :accountNumber
       AND a.status = com.banking.accountservice.entity.AccountStatus.ACTIVE
       AND a.balance >= :amount
    """)
    int debitIfSufficientBalance(
            @Param("accountNumber") String accountNumber,
            @Param("amount") BigDecimal amount,
            @Param("status") AccountStatus status
    );

}
