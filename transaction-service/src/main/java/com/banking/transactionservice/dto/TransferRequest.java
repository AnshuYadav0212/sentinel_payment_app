package com.banking.transactionservice.dto;


import jakarta.validation.constraints.*;
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

    @DecimalMin(value = "1", message = "Transfer amount must be greater than 0")
    @NotNull(message= "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    private String description;
}
