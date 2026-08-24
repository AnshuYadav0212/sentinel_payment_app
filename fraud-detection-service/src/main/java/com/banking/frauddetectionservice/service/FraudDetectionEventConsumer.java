package com.banking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class FraudDetectionEventConsumer {
    private final FraudDetectionService fraudDetectionService;

    /*
    * listens to transaction.initiated topic
    * every transaction goes through fraud check before completing
     */
    @KafkaListener(topics="transaction.initiated",groupId = "fraud-detection-group")
    public void consumeTransactionInitiated(
            @Payload Map<String,Object> payload
            ){
        log.info("Received transaction for fraud detection for transactionId: {}",payload.get("transactionId"));
        try{
            fraudDetectionService.checkTransaction(payload);
        }catch(Exception e){

        }
    }

}
