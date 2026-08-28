package com.banking.transactionservice.client;

public record AccountLookupResponse (
    String accountNumber,
    String status){
}
