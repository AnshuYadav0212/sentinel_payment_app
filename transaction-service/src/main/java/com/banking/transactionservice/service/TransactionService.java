package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountLookupResponse;
import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,Object> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC="transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC="transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC="transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC="fraud.detected";
    /*
    * saga first step : initiate transfer of money
    * deducted from sender with feign
    * save transaction as PROCESSING and then publish even to kafka for fraud check
    *
     */

    private void validateAccount(String accountNumber) {
        AccountLookupResponse receiver =
                accountServiceClient.getAccount(accountNumber);

        if (!"ACTIVE".equals(receiver.status())) {
            throw new IllegalStateException(
                    "Check account, current account is not active/exists"
            );
        }
    }

    public TransactionResponse transfer( TransferRequest request) {
        if (request.getAmount() == null ||
                request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        log.info("SAGA started to transfer from: {} to account {} of rupees: {}", request.getSenderAccountNumber(),request.getReceiverAccountNumber(),request.getAmount());
        validateAccount(request.getReceiverAccountNumber());

        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount()
        );
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        transaction.setCreatedAt(LocalDateTime.now());

        Transaction savedTransaction= transactionRepository.save(transaction);
        log.info(" Transaction saved as PROCESSING: for transaction {}",savedTransaction.getId());

        TransactionInitiatedEvent event=new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("SAGA completed till publish fraud check {}", savedTransaction.getId() );

        return mapToResponse(savedTransaction);
    }



    private TransactionResponse mapToResponse(Transaction transaction ){
        TransactionResponse response=new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());

        return response;
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository
                        .findById(transactionId)
                        .orElseThrow(()-> new RuntimeException(
                                "Transaction not found!! " +transactionId
                        )));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {

        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
       log.info("OTP verification for the transaction: {}",transactionId);
       Transaction transaction=transactionRepository.findById((transactionId))
               .orElseThrow(()->new RuntimeException(
                       "Transaction not found "+transactionId
               ));

        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            log.info(
                    "Transaction {} is already completed. Ignoring duplicate OTP verification.",
                    transactionId
            );
            return mapToResponse(transaction);
        }
        if (transaction.getStatus() == TransactionStatus.FLAGGED ||
                transaction.getStatus() == TransactionStatus.FAILED) {

            log.info(
                    "Transaction {} is already in terminal state {}. Ignoring verification.",
                    transactionId,
                    transaction.getStatus()
            );

            return mapToResponse(transaction);
        }

        if (transaction.getStatus() != TransactionStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException(
                    "Transaction is not awaiting OTP verification"
            );
        }
       String otpKey="verification:otp"+transactionId;
       String storedOtp=(String)redisTemplate.opsForValue().get(otpKey);

       if(storedOtp==null){
           // otp is expired
           log.warn("OTP is expired for transaction : {} ",transactionId);
           compensateTransaction(transaction,"OTP expired, transaction is cancelled and amount is refunded to the sender");
           return mapToResponse(transaction);
       }
       if(!storedOtp.equals(otp)){
           log.warn("wrong ot, blocking account and refunding the money to {}", transactionId);
           redisTemplate.delete(otpKey);
           blockAccountAndCompensate(transaction,"Wrong otp entered, transaction is cancelled, "+ "account is blocked for security");
           return mapToResponse(transaction);
       }

       log.info("otp is verified, completing the transaction: {}",transactionId);
       redisTemplate.delete(otpKey);
       completeTransaction(transaction);
       return mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction,String reason){
        log.warn("saga compensation, refunding to  {} for amount: {}", transaction.getSenderAccountNumber(),transaction.getAmount());
        // credit money back to the sender

        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(),transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason+ " SAGA compensation is executed, amount refunded to sender at :"+ LocalDateTime.now());
        transactionRepository.save(transaction);

        // publish refund event, notification alert will be sent to the sender
        Map<String,Object> refundEvent=new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);
        log.info("SAGA compensation is completed for amount {}, to the account: {} ",transaction.getAmount(),transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason){
         // this will publish te fraud detection and account service will block account
        Map<String,Object> fraudEvent=new HashMap<>();
        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("accountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getSenderAccountNumber(),fraudEvent);
        log.warn("fraud.detected published for account: {}, will be blocked, please contact to bank ", transaction.getSenderAccountNumber());

        //now saga compensation refund sender
        compensateTransaction(transaction,reason);
    }

    private void completeTransaction(Transaction transaction){
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent=new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);
        log.info("saga is completed, transaction {} is completed",transaction.getId());
    }

    public void processCleanResult(String transactionId){
        Transaction transaction=transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException(
                        "Transaction is not found "+transactionId
                ));
        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} is not processing, it is skipping ", transactionId);
            return;
        }
        completeTransaction(transaction);
    }

}
