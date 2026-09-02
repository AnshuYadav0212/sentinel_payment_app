package com.banking.frauddetectionservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;


@Component
public class FraudDetectionMetrics {
    private final Counter fraudChecks;
    private final Counter fraudDetected;

    public FraudDetectionMetrics(MeterRegistry meterRegistry) {

        fraudChecks = Counter.builder("fraud.checks")
                .description("Total number of fraud checks")
                .register(meterRegistry);

        fraudDetected = Counter.builder("fraud.detects")
                .description("Total number of fraud detected")
                .register(meterRegistry);
    }

    public void fraudsChecks() {
        fraudChecks.increment();
    }

    public void fraudsDetects() {
        fraudDetected.increment();
    }

}
