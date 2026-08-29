package com.banking.transactionservice.controller;

import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest( controllers = TransactionController.class,
        properties = {
                "TRANSACTION_SERVICE_ACCOUNT_SERVICE_URL=http://localhost:8080"
        })
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void transfer_shouldReturnCreatedForValidRequest() throws Exception {
        given(transactionService.transfer(any(TransferRequest.class)))
                .willReturn(transactionResponse(TransactionStatus.PROCESSING));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderAccountNumber":"123456789012",
                                  "receiverAccountNumber":"987654321012",
                                  "amount":500,
                                  "description":"test transfer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void transfer_shouldReturn400WhenSenderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverAccountNumber":"987654321012",
                                  "amount":500
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_shouldReturn400ForNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderAccountNumber":"123456789012",
                                  "receiverAccountNumber":"987654321012",
                                  "amount":-100
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_shouldReturn400ForInvalidReceiverFormat() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderAccountNumber":"123456789012",
                                  "receiverAccountNumber":"123",
                                  "amount":500
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOTP_shouldReturnOkForValidRequest() throws Exception {
        given(transactionService.verifyOTP(eq("tx-1"), eq("123456")))
                .willReturn(transactionResponse(TransactionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/transactions/tx-1/verify")
                        .param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getTransaction_shouldReturnOk() throws Exception {
        TransactionResponse response = transactionResponse(TransactionStatus.PROCESSING);
        response.setId("tx-1");

        given(transactionService.getTransaction("tx-1"))
                .willReturn(response);

        mockMvc.perform(get("/api/v1/transactions/tx-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tx-1"));
    }

    @Test
    void getTransactionHistory_shouldReturnOk() throws Exception {
        given(transactionService.getTransactionHistory("123456789012"))
                .willReturn(java.util.List.of(
                        transactionResponse(TransactionStatus.COMPLETED)
                ));

        mockMvc.perform(get("/api/v1/transactions/account/123456789012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    private TransactionResponse transactionResponse(TransactionStatus status) {
        TransactionResponse response = new TransactionResponse();
        response.setSenderAccountNumber("123456789012");
        response.setReceiverAccountNumber("987654321012");
        response.setAmount(new BigDecimal("500"));
        response.setType(TransactionType.TRANSFER);
        response.setStatus(status);
        return response;
    }
}
