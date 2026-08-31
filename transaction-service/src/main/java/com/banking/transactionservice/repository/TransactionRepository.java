package com.banking.transactionservice.repository;

import com.banking.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,String> {
    List<Transaction> findBySenderAccountNumberOrderByCreatedAtDesc(String accountNumber);

    @Modifying
    @Query("""
    UPDATE Transaction t
       SET t.status = com.banking.transactionservice.entity.TransactionStatus.FLAGGED,
           t.failureReason = :reason
     WHERE t.id = :transactionId
       AND t.status = com.banking.transactionservice.entity.TransactionStatus.PENDING_VERIFICATION
""")
    int markForCompensation(
            @Param("transactionId") String transactionId,
            @Param("reason") String reason
    );
}
