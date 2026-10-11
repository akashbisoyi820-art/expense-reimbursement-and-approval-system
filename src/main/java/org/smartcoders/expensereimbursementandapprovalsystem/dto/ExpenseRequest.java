package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseRequest {

    @NotBlank
    private String category;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    // Optional: a 3-letter code such as USD. Missing means INR.
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
    private String currency;

    @NotNull
    private LocalDate expenseDate;

    private String description;
}