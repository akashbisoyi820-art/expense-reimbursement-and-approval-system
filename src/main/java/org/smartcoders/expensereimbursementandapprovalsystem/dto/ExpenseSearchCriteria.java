package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseSearchCriteria(
        String keyword,
        ExpenseStatus status,
        String category,
        String employee,
        LocalDate from,
        LocalDate to,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}