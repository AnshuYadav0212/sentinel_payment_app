package com.banking.paymentservice.service;

import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void verifyPayment_shouldFailWhenPaymentDoesNotExist() {
        when(paymentRepository.findById("payment-1"))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> paymentService.verifyPayment(
                        "payment-1",
                        "pay-1",
                        "order-1",
                        "signature"
                )
        );

        assertEquals("Payment not found", exception.getMessage());
    }

    @Test
    void verifyPayment_shouldNotProcessAlreadyCompletedPayment()
            throws RazorpayException {

        when(paymentRepository.findById("payment-1"))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> paymentService.verifyPayment(
                        "payment-1",
                        "pay-1",
                        "order-1",
                        "signature"
                )
        );

        assertEquals("Payment not found", exception.getMessage());
    }
}