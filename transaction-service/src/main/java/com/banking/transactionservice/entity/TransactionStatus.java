package com.banking.transactionservice.entity;

/*
  * transaction lifecycle flow:
  * pending -> processing -> completed (clean transaction)
  *                       -> pending verification in case of suspicious activity detected
  *                       -> completed(verified)
  *                       -> flagged (saga refund)
  *         -> if failed flag
 */

public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
