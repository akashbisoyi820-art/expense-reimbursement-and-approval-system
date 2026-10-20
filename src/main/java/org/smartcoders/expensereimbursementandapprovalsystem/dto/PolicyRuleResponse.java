package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PolicyRuleResponse(
        Long id,
        String category,
        BigDecimal maxPerClaim,
        BigDecimal monthlyLimit,
        BigDecimal receiptRequiredAbove,
        LocalDateTime updatedAt,
        String updatedByName
) {
}