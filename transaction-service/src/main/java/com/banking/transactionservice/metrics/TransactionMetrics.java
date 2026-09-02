package com.banking.transactionservice.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransactionMetrics {

    private final Counter transactionsInitiated;
    private final Counter transactionsCompleted;
    private final Counter transactionsRefunded;
    private final Counter transactionsFraudDetected;

    public TransactionMetrics(MeterRegistry meterRegistry) {

        transactionsInitiated = Counter.builder("transactions.initiated")
                .description("Total number of transactions initiated")
                .register(meterRegistry);

        transactionsCompleted = Counter.builder("transactions.completed")
                .description("Total number of transactions completed")
                .register(meterRegistry);

        transactionsRefunded = Counter.builder("transactions.failed")
                .description("Total number of transactions refunded")
                .register(meterRegistry);

        transactionsFraudDetected = Counter.builder("transactions.fraud.detected")
                .description("Total number of transactions flagged as fraud")
                .register(meterRegistry);
    }

    public void transactionInitiated() {
        transactionsInitiated.increment();
    }

    public void transactionCompleted() {
        transactionsCompleted.increment();
    }

    public void transactionRefunded() {
        transactionsRefunded.increment();
    }

    public void fraudDetected() {
        transactionsFraudDetected.increment();
    }
}