package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(
            @Payload Map<String,Object> payload
    ) {
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String transactionId = (String) payload.get("transactionId");
            String amount = (String) payload.get("amount");
            String reason = (String) payload.get("reason");

            sendAlert(accountNumber,
                    "Transaction Verification is Required!! ", String.format(
                            "Suspicious activity detected from your account. " +
                                    "Reason: %s " +
                                    " A transaction of %s is pending verification. " +
                                    "Your OTP is: %s. Valid for 5 minutes. " +
                                    "If this is you Ignore this message."
                    )
            );
        } catch (Exception e) {
             log.error("Error occurred while sending Notification {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.completed")
    public  void consumeTransactionCompleted(
            @Payload Map<String, Object> payload
    ){
        try{
            String senderAccount=(String) payload.get("senderAccountNumber");
            String receiverAccount=(String) payload.get("receiverAccountNumber");
            String amount=payload.get("amount").toString();

            // debit alert for sender
            sendAlert(senderAccount,"debit alert", String.format("%s is debited from account %s",amount,senderAccount));

            //credit alert for the reciever
            sendAlert(receiverAccount,"credit alert", String.format("%s is credited to account %s",amount,receiverAccount));

        }
        catch (Exception e){
            log.error("Error in sending transaction notification: {}",e.getMessage());
        }

    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String, Object> payload
    ){
        try{
            String accountNumber= (String) payload.get("accountNumber");
            String reason= (String) payload.get("reason");

            sendAlert(accountNumber,"Suspicious activity Detected", String.format("Your account has been blocked. "+ "Reason: %s"+"Please contact bank",accountNumber,reason));
        }
        catch (Exception e){
            log.error("Error in sending fraud alert: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefund(
            @Payload Map<String, Object> payload
    ){
        try{
            String senderAccountNumber= (String) payload.get("senderAccountNumber");
            String amount=payload.get("amount").toString();
            String reason= (String) payload.get("reason");

            sendAlert(senderAccountNumber,"Refund processed", String.format("Your transaction is %s cancelled. "+ "Reason: %s"+"%s is refunded to account: %s ",amount,reason,amount,senderAccountNumber));
        }
        catch (Exception e){
            log.error("Error in sending refund notification alert: {}", e.getMessage());
        }
    }
    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
            @Payload Map<String, Object> payload
    ){
        try {
            String accountNumber= (String) payload.get("accountNumber");
            String amount=payload.get("amount").toString();

            sendAlert(accountNumber,"Payment successfully processed", String.format("Your Payment of %s is successful. "+ "RazorpayId: %s" , amount,payload.get("razorpayPaymentId")));

        }
        catch (Exception e){
            log.error("Error in sending payment notification alert: {}", e.getMessage());

        }

    }
    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(
            @Payload Map<String, Object> payload
    ){
        try {
            String accountNumber= (String) payload.get("accountNumber");
            String amount=payload.get("amount").toString();

            sendAlert(accountNumber,"Payment failed ", String.format("Your Payment of %s is NOT successful. "+ "Please try again or contact support" , amount));

        }
        catch (Exception e){
            log.error("Error in sending payment failure notification alert: {}", e.getMessage());

        }

    }



    private void sendAlert(String accountNumber,String subject,String message){
        log.info("..........................");
        log.info("account: {}",accountNumber);
        log.info("subject: {}",subject);
        log.info("message: {}", message);
    }
}
