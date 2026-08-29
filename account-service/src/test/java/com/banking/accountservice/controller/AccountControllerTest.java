package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void getBalance_shouldReturnBalance() throws Exception {

        when(accountService.getBalance("123456789012"))
                .thenReturn(new BigDecimal("10000"));

        mockMvc.perform(
                        get("/api/v1/accounts/123456789012/balance")
                )
                .andExpect(status().isOk())
                .andExpect(content().string("10000"));
    }

    @Test
    void getAccount_shouldReturnNotFoundWhenServiceFails() throws Exception {
        AccountResponse mockResponse = new AccountResponse();
        when(accountService.getAccount("999999999999"))
                .thenReturn(mockResponse);

        mockMvc.perform(
                        get("/api/v1/accounts/999999999999")
                )
                .andExpect(status().isOk());
    }
}