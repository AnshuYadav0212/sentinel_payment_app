package com.banking.transactionservice.service;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private  final RedisTemplate<String,Object> redisTemplate;
    private final TransactionService transactionService;
    private static final long OTP_EXPIRY_MINUTES=5;

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static  final String TRANSACTION_OTP_GENERATED_TOPIC="transaction.otp.generated";

    @KafkaListener(topics="verification.required")
    public void consumeVerificationRequired(
             @Payload Map<String,Object> payload
     ){
          try{
             String transactionId=(String) payload.get("transactionId");
             String accountNumber=(String) payload.get("accountNumber");
             String reason=(String) payload.get("reason");
             log.info("Verification is required for transaction Id: {} due to reason: {}",transactionId,reason);

             Transaction transaction=transactionRepository.findById(transactionId)
                                      .orElseThrow(()-> new RuntimeException("Transaction is not found "+ transactionId));
             if(transaction.getStatus() != TransactionStatus.PROCESSING){
                 log.warn("Transaction {} is not processing, it is skipping ", transactionId);
                 return;
             }
             //to generate 6 digit otp
             String otp=String.format("%06d",(int)(Math.random()*900000)+100000);

             //storing the otp in redis
              String otpKey= "verification:otp"+transactionId;
              redisTemplate.opsForValue().set(otpKey,otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

              //updating the status of transaction
              transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
              transactionRepository.save(transaction);

              log.info("OTP generated for transaction: {} will expires in {} minutes",transactionId,OTP_EXPIRY_MINUTES);
             // notification to the user
             Map<String,Object> otpEvent=new HashMap<>();
             otpEvent.put("transactionId", transactionId);
             otpEvent.put("accountNumber",accountNumber);
             otpEvent.put("reason",reason);
             otpEvent.put("otp",otp);
             otpEvent.put("amount",payload.get("amount"));

             kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);
          }catch(Exception e){
            log.error("Error handling verification is required {}",e.getMessage());
         }
     }
     @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResult(
            @Payload Map<String,Object> payload
     ){
        try{
            String transactionId=(String)payload.get("transactionId");
            String accountNumber=(String)payload.get("accountNumber");
            transactionService.processCleanResult(transactionId);

        }
        catch(Exception e){
            log.error("Error processing fraud check result: {}", e.getMessage());
        }
    }
}
