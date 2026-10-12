package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long expenseId,
        PaymentMethod paymentMethod,
        String referenceNo,
        LocalDate paidOn,
        BigDecimal amount,
        String note,
        String paidByName,
        LocalDateTime createdAt
) {
}