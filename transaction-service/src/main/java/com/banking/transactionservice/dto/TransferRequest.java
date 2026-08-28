package com.banking.transactionservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @Pattern(
            regexp = "\\d{12}",
            message = "Receiver account number must be exactly 12 digits"
    )
    @NotBlank(message = "Sender account number is required")
    private String senderAccountNumber;

    @Pattern(
            regexp = "\\d{12}",
            message = "Receiver account number must be exactly 12 digits"
    )
    @NotBlank(message = "Receiver account number is required")
    private String receiverAccountNumber;

    @NotNull(message= "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    private String description;
}
