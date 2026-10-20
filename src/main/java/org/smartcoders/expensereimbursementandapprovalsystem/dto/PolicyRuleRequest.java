package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PolicyRuleRequest {

    @NotBlank
    private String category;

    // All amounts are in INR. Leave a value out (null) to remove that rule.
    @DecimalMin(value = "0", message = "Amounts cannot be negative")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal maxPerClaim;

    @DecimalMin(value = "0", message = "Amounts cannot be negative")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal monthlyLimit;

    @DecimalMin(value = "0", message = "Amounts cannot be negative")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal receiptRequiredAbove;
}