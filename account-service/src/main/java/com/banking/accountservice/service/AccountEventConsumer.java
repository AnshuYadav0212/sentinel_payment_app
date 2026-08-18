package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.beans.BeanInfo;
import java.math.BigDecimal;
import java.util.Map;

/*
   * To consume the events publish by the  transactions service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {
     private final AccountService accountService;
    /*
    * Consume transaction.completed event from kafka
    * Credits receiver account
    * @param payload
    *
     */
    @KafkaListener(topics= "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String,Object> payload
            ){
        try{
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting account: {} amount: {}", receiverAccount, amount);
            accountService.creditBalance(receiverAccount,amount);
        }catch (Exception e){
           log.error("Error in crediting account: {}", e.getMessage());
        }

    }

    /*
      * Consume fraude.detected event from kafka and blocks the flagged acc
      * @param payload
     */
    @KafkaListener(topics= "fraud.detected")
    public  void consumeFraudDetected(@Payload Map<String, Object> payload){
        try{
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud detected, Blocking the account: {} ",accountNumber);
           accountService.blockAccount(accountNumber);

        }catch(Exception e){
            log.error("Error blocking account: {}", e.getMessage());
        }
    }




}
