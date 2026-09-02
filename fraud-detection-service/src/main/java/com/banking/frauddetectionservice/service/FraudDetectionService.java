package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.metrics.FraudDetectionMetrics;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final FraudDetectionMetrics fraudDetectionMetrics;

    private static final String VERIFICATION_REQUIRED_TOPIC="verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC="fraud.check.clean";
    @Value("${fraud.max-transaction-per-minumte}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;
    private final RedisTemplate<String,String> redisTemplate;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    public void checkTransaction(Map<String, Object> payload){
        String transactionId=(String)payload.get("transactionId");
        String accountNumber=(String)payload.get("senderAccountNumber");
        Object amountObj = payload.get("amount");
        BigDecimal amount = null;

        if (amountObj != null) {
            amount = new BigDecimal(amountObj.toString());
        }

        /*
         * fetch real balance from account service
         */
        BigDecimal senderBalance= accountServiceClient.getBalance(accountNumber);
        log.info("Verifying the transaction: {} for account: {} for amount: {} and sender balance: {}",transactionId,accountNumber,amount,senderBalance);

        FraudCheckResult result=performFraudChecks(accountNumber,amount,senderBalance);
        if(result.isFraud()){
            log.info("Suspicious activity detected for account: {}" + "reason for suspecting: {}, requesting OTP verification",accountNumber,result.getReason());

            Map<String,Object> verificationEvent= new HashMap<>();
            verificationEvent.put("transactionId",transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());

            fraudDetectionMetrics.fraudsDetects();
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);
        }
        else{

            // else we have a non fraud transaction
            log.info("No fraud transaction");
            Map<String,Object> transactionCleanEvent=new HashMap<>();
            transactionCleanEvent.put("transactionId",transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason",null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,transactionCleanEvent);
        }
    }
    /*
        // perform 3 checks (velocity check( fraudster don't withdraw manually, runs scripts),
        * 2 checks average transaction of person then if transaction goes quite large amount-> marked as suspicious and need verification
        * 3 90% of balance amount is involved transaction (need verification)
    */
    private FraudCheckResult performFraudChecks(
            String accountNumber,
            BigDecimal amount,
            BigDecimal senderBalance
    ){
        // check 1: velocity checks
        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResult(true,"Too many transaction in 1 minute"+ "Velocity limit exceeded");
        }

        // check 2: average transaction amount
        if(isAmountSuspicious(accountNumber,amount)){
            return new FraudCheckResult(true, "Unusual transaction amount exceed the average transaction amount");
        }

        // check 3: balance check
        if(senderBalance.compareTo(BigDecimal.ZERO)>0 && isBalanceCHeckFailed(senderBalance,amount)){
            return new FraudCheckResult(true, "Unusual transaction amount, more than 90 % of account balance");
        }

        fraudDetectionMetrics.fraudsChecks();
        return new FraudCheckResult(false,null);

    }

    private  boolean isVelocityExceeded(String accountNumber){
        String key="fraud:velocity"+ accountNumber;
        Long count=redisTemplate.opsForValue().increment(key);
        if(count!=null && count ==1){
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }
        log.info("Velocity check for account: {} and transaction count: {} out of max transaction per minute {}",accountNumber,count,maxTransactionsPerMinute);
        return count!=null && count> maxTransactionsPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount){
        String avgKey="fraud:avg_amount"+ accountNumber;
        String avgStr= redisTemplate.opsForValue().get(avgKey);

        if(avgStr==null){
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return false;
        }
        BigDecimal avgAmount= new BigDecimal(avgStr);
        BigDecimal threshold=avgAmount.multiply(
                BigDecimal.valueOf(suspiciousAmountMultiplier)
        );

        // updating average amount
        BigDecimal newAvg=avgAmount.add(amount)
                .divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);

        redisTemplate.opsForValue().set(avgKey,newAvg.toString());
        log.info("Amount {} and threshold {} checked found suspicious {}",amount,threshold, amount.compareTo(threshold)>0);
        return amount.compareTo(threshold)>0;
    }

    private boolean isBalanceCHeckFailed(BigDecimal senderBalance, BigDecimal amount){
        BigDecimal maxAllowed= senderBalance.multiply(
                BigDecimal.valueOf(maxBalancePercentage)
        );
        log.info("Balance checked amount: {}, maximum allowed balance: {}, suspicious {}",amount,maxAllowed,amount.compareTo(maxAllowed)>0);
        return amount.compareTo(maxAllowed)>0;
    }
}
