package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Account Holder Name is required ")
    private String accountHolderName;

    @NotBlank(message = "Email should be not blank")
    @Email(message = "Invalid Email Format")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Account Type is Required")
    private AccountType accountType;

    @NotBlank(message = "Initial Deposit is required")
    @Positive(message = "Initial deposit must be positive")
    private BigDecimal initialDeposit;

}
