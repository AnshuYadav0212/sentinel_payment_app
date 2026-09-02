package com.banking.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {
    private final Counter paymentCreated;
    private final Counter paymentVerified;
    private final Counter paymentFailed;

    public PaymentMetrics(MeterRegistry meterRegistry) {

        paymentCreated = Counter.builder("payments.created")
                .description("Total number of payment created")
                .register(meterRegistry);

        paymentVerified = Counter.builder("payments.verified")
                .description("Total number of payment verified")
                .register(meterRegistry);


        paymentFailed = Counter.builder("payments.failed")
                .description("Total number of payments failed")
                .register(meterRegistry);
    }

    public void paymentsCreated() {
        paymentCreated.increment();
    }
    public void paymentsVerified() {
        paymentVerified.increment();
    }
    public void paymentsFailed() {
        paymentFailed.increment();
    }



}
