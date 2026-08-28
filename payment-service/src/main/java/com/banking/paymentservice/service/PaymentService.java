package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${razorpay.key-id}")
    private String keyId;
    @Value("${razorpay.key-secret}")
    private String keySecret;
    private static final String PAYMENT_COMPLETED_TOPIC="payment.completed";
    private static final String PAYMENT_FAILED_TOPIC="payment.failed";
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RestClient accountServiceClient =
            RestClient.builder()
                    .baseUrl("http://localhost:8081")
                    .build();


    /*
     * creating the razor pay payment order firstly by creating the order in the payment gateway
     * saving payment record in db
     * returning the order detail to front end
     * user pays
     * razorpay calls the webhook to update status for backend
     */
    private void verifyAccountExists(String accountNumber) {

        try {
            accountServiceClient.get()
                    .uri("/api/v1/accounts/{accountNumber}", accountNumber)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account " + accountNumber + " does not exist"
            );
        }
    }

    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest request) throws RazorpayException {
        log.info("Creating payment order for account id: {} for amount: {}", request.getAccountNumber(),request.getAmount());

        verifyAccountExists(request.getAccountNumber());
        RazorpayClient razorpayClient=new RazorpayClient(keyId,keySecret);

        // converted amount
        int convertedAmount= request.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();
        JSONObject orderRequest= new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency","INR");
        orderRequest.put("receipt","rcpt_"+ System.currentTimeMillis()+UUID.randomUUID().toString()
                .replace("-","").substring(0,10));

        Order razorpayOrder=razorpayClient.orders.create(orderRequest);

        log.info("Razorpay order created: {}",razorpayOrder.get("id").toString());


        // now saving the payment record
        Payment payment=new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setDescription(request.getDescription());

        Payment savedPayment=paymentRepository.save(payment);
        return new PaymentOrderResponse(savedPayment.getId(),
                razorpayOrder.get("id").toString(),
                request.getAmount(),
                "INR",
                "PENDING",
                keyId
                );
    }
   // for real payment webhook is not mandatory but recommended
    // payment gateway provides the webhook to capture the events on frontend to notify it to backend for actions( if failed, if succeed)
    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received Razorpay webhook: {}",payload.get("event"));
        String event= (String) payload.get("event");
        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        }
        else if("payment.failed".equals(event)){
            handlePaymentFailure(payload);
        }
    }
    public void verifyPayment(
            String internalPaymentId,
            String razorpayPaymentId,
            String razorpayOrderId,
            String razorpaySignature
    ) throws RazorpayException {

        Payment payment =
                paymentRepository.findById(internalPaymentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Payment not found"
                                )
                        );

        // Critical:
        // use the order ID stored in YOUR DB
        if (!payment.getRazorpayOrderId()
                .equals(razorpayOrderId)) {

            throw new RuntimeException(
                    "Razorpay order mismatch"
            );
        }

        JSONObject options = new JSONObject();

        options.put(
                "razorpay_order_id",
                payment.getRazorpayOrderId()
        );

        options.put(
                "razorpay_payment_id",
                razorpayPaymentId
        );

        options.put(
                "razorpay_signature",
                razorpaySignature
        );

        boolean valid =
                Utils.verifyPaymentSignature(
                        options,
                        keySecret
                );

        if (!valid) {
            throw new RuntimeException(
                    "Invalid Razorpay signature"
            );
        }

        // Idempotency:
        // don't credit the account twice
        if (payment.getStatus() ==
                PaymentStatus.COMPLETED) {

            log.info(
                    "Payment {} already processed",
                    payment.getId()
            );

            return;
        }

        payment.setRazorpayPaymentId(
                razorpayPaymentId
        );

        payment.setStatus(
                PaymentStatus.COMPLETED
        );

        paymentRepository.save(payment);

        creditInternalAccount(payment);

        log.info(
                "Razorpay payment verified. Payment {}, Account {}, Amount {}",
                payment.getId(),
                payment.getAccountNumber(),
                payment.getAmount()
        );
    }

    private void creditInternalAccount(Payment payment) {

        accountServiceClient.put()
                .uri(uriBuilder ->
                        uriBuilder
                                .path(
                                        "/api/v1/accounts/{accountNumber}/credit"
                                )
                                .queryParam(
                                        "amount",
                                        payment.getAmount()
                                )
                                .build(
                                        payment.getAccountNumber()
                                )
                )
                .retrieve()
                .toBodilessEntity();

        log.info(
                "Credited ₹{} to internal account {}",
                payment.getAmount(),
                payment.getAccountNumber()
        );
    }

    private void handlePaymentSuccess(Map<String,Object> payload){
        try{
            Map<String,Object> paymentData=extractPaymentData(payload);
            String orderId=(String) paymentData.get("order_id");
            String paymentId=(String) paymentData.get("id");

            Payment payment=paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()-> new RuntimeException(
                            "Payment not found for the order in db "+orderId
                    ));
           // for idempotent behaviour in payment
            if(payment.getStatus()==PaymentStatus.COMPLETED) return;
            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // publish payment completed event to kafka
            Map<String,Object> event=new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("razorpayPaymentId",payment.getRazorpayPaymentId());

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId(),event);
            log.info("payment completed: {}",payment.getId());

        }
        catch (Exception e){
            log.error("Error handling payment success: {}", e.getMessage());
        }
    }
    private void handlePaymentFailure(Map<String,Object> payload){
        try{
            Map<String, Object> paymentData= extractPaymentData(payload);
            String orderId= (String) paymentData.get("order_id");

            Payment payment=paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(()-> new RuntimeException(
                            "Payment not found for the order in db "+orderId
                    ));
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via razorpay");
            paymentRepository.save(payment);

            // publish the payment failed
            Map<String,Object> event=new HashMap<>();
            event.put("paymentId",payment.getId());
            event.put("accountNumber",payment.getAccountNumber());
            event.put("amount",payment.getAmount());
            event.put("reason","failed payment");

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId(),event);
            log.warn("Payment failed: {}", payment.getId());
        }
        catch(Exception e){
            log.error("Error handling for the payment failure: {}",e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private  Map<String,Object> extractPaymentData(Map<String,Object> payload){
        Map<String,Object> entity= (Map<String,Object>) payload.get("payload");
        Map<String, Object> paymentWrapper= (Map<String,Object>) entity.get("payment");
        return (Map<String,Object>) paymentWrapper.get("entity");

    }
}
