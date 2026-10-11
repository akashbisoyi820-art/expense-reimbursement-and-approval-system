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
    private String currency;
    private BigDecimal amountInBase;
    private BigDecimal exchangeRate;
    private LocalDate rateDate;
    private LocalDate expenseDate;
    private String description;
    private ExpenseStatus status;
    private String employeeName;
    private String employeeEmail;
    private String employeeDepartment;
    private String rejectionReason;
    private Boolean hasReceipt;
}