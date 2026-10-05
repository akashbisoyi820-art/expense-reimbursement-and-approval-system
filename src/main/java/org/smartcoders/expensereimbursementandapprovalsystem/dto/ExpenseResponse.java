package org.smartcoders.expensereimbursementandapprovalsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.smartcoders.expensereimbursementandapprovalsystem.entity.ExpenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String category;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private ExpenseStatus status;
    private String employeeName;
    private String employeeEmail;
    private String rejectionReason;
    private Boolean hasReceipt;
}