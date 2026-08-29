package com.banking.paymentservice.controller;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.service.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    //webhook is inbuilt event, and it captures upon completion and update the backend
    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(
            @Valid @RequestBody CreatePaymentRequest request
            ) throws RazorpayException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPaymentOrder(request));
    }
    //Razorpay webhook endpoint

    @PostMapping("/webhook/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestBody Map<String,Object> payload
    ){
        paymentService.handleWebhook(payload);
        return ResponseEntity.ok("Webhook processed");
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestBody Map<String, String> request
    ) throws RazorpayException {

        paymentService.verifyPayment(
                request.get("paymentId"),
                request.get("razorpayPaymentId"),
                request.get("razorpayOrderId"),
                request.get("razorpaySignature")
        );

        return ResponseEntity.ok(
                Map.of(
                        "status", "COMPLETED",
                        "message", "Payment verified and account credited"
                )
        );
    }
}
